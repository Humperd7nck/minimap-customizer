package com.minimapfilter;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client data stays on the client thread; network I/O and Swing never run there. */
@Singleton
final class InfoPanelService
{
    private static final Logger LOG = LoggerFactory.getLogger(InfoPanelService.class);
    private final Client client;
    private final MinimapFilterConfig config;
    private final ScheduledExecutorService scheduler;
    private final Notifier notifier;
    private final WorldService worldService;
    private final InfoPanelTools tools = new InfoPanelTools();
    @Inject private ConfigManager profileConfig;
    private final InfoPanelReminders reminders = new InfoPanelReminders(tools, this::sendToolNotification);
    private final InfoPanelQuickControls quickControls = new InfoPanelQuickControls(tools, reminders);
    private final InfoPanelReminderInput reminderInput = new InfoPanelReminderInput(this::dismissNotice);
    private volatile InfoPanelNotebook notebook = InfoPanelNotebook.memory();
    private InfoPanelNotebookStore notebookStore;
    private InfoPanelNotebookStore.Session notebookSession;
    private final InfoPanelVolume volume = new InfoPanelVolume();
    private final InfoPanelVolumeBar volumeBar = new InfoPanelVolumeBar(volume, false);
    private final InfoPanelVolumeBar drawerVolumeBar = new InfoPanelVolumeBar(volume, false);
    private final InfoPanelToolDrawer drawer = new InfoPanelToolDrawer();
    private Rectangle previousPrimaryVolume, previousDrawerVolume;
    private volatile Rectangle popupAnchorLocal;
    private long previousDrawerGeneration = -1;
    private volatile boolean popupFromDrawer;
    private final InfoPanelNativeVolume nativeVolume;
    private final InfoPanelPingState ping = new InfoPanelPingState();
    private final AtomicLong uiGeneration = new AtomicLong();
    private final InfoPanelPopup popup = new InfoPanelPopup(tools, () -> { this.popupOpen = false; volume.cancelPending(); },
        () -> popupAllowed(),
        reminders, () -> notebook);
    private InfoPanelSelectorPopup selector;
    private TrackingPopup trackingPopup;
    private TrackingVisibility trackingVisibility = new TrackingVisibility(null);
    private volatile boolean running;
    private volatile boolean volumeResetPending = true;
    private volatile boolean popupOpen;
    private volatile boolean trackingPopupOpen;
    private volatile boolean hudAvailable;
    private volatile InfoPanelText activeMode = InfoPanelText.ZONE;
    private volatile InfoPanelText popupMode;
    private volatile InfoPanelMetrics metrics = InfoPanelMetrics.EMPTY;
    private volatile long lastSample = Long.MIN_VALUE;
    private int sampledWorld;
    private boolean warned;
    private ScheduledFuture<?> completionTask;
    private volatile ExecutorService pingWorker;

    @Inject
    InfoPanelService(Client client, MinimapFilterConfig config, ScheduledExecutorService scheduler,
        Notifier notifier, WorldService worldService)
    {
        this.client = client;
        this.config = config;
        this.scheduler = scheduler;
        this.notifier = notifier;
        this.worldService = worldService;
        nativeVolume = new InfoPanelNativeVolume(new InfoPanelNativeVolume.Access()
        {
            @Override public boolean ready() { return client.getGameState() == GameState.LOGGED_IN; }
            @Override public byte[] script(int id)
            {
                return client.getIndexScripts() == null ? null : client.getIndexScripts().loadData(id, 0);
            }
            @Override public int[] varps() { return client.getVarps(); }
            @Override public void run(int id, int... arguments)
            {
                // Called only by updateOnClientThread(), outside the script interpreter.
                if (arguments.length == 0) { client.runScript(id); }
                else { client.runScript(id, arguments[0]); }
            }
            @Override public void clearStack() { client.setIntStackSize(0); }
            @Override public int stackSize() { return client.getIntStackSize(); }
            @Override public int stackTop() { return client.getIntStack()[0]; }
            @Override public void changed(int varp) { client.queueChangedVarp(varp); }
            @Override public void report(String detail) { LOG.warn("Volume compatibility report:\n{}", detail); }
        });
    }

    @Inject
    void setTrackingVisibility(TrackingVisibility trackingVisibility)
    {
        if (trackingVisibility != null) { this.trackingVisibility = trackingVisibility; }
    }

    synchronized void start()
    {
        if (running) { return; }
        running = true;
        if (profileConfig != null && config.infoPanelText().isTool())
        { profileConfig.setConfiguration(MinimapFilterConfig.GROUP, "infoPanelText", InfoPanelText.ZONE.name()); }
        drawer.reset();
        popupAnchorLocal = null;
        reminderInput.reset();
        volumeResetPending = true;
        tools.activate();
        reminders.activate();
        loadNotebook();
        lastSample = Long.MIN_VALUE;
        pingWorker = Executors.newSingleThreadExecutor(task ->
        {
            Thread thread = new Thread(task, "minimap-customizer-ping");
            thread.setDaemon(true);
            return thread;
        });
        completionTask = scheduler.scheduleWithFixedDelay(this::notificationTick, 250, 250, TimeUnit.MILLISECONDS);
    }

    synchronized void stop()
    {
        running = false;
        drawer.reset();
        drawerVolumeBar.suspend();
        reminderInput.suspend();
        hudAvailable = false;
        popupAnchorLocal = null;
        volumeBar.suspend();
        closePopup();
        closeSelector();
        closeTracking();
        tools.suspend();
        reminders.suspend();
        releaseNotebook();
        volume.suspend();
        ping.reset();
        metrics = InfoPanelMetrics.EMPTY;
        if (completionTask != null) { completionTask.cancel(false); completionTask = null; }
        ExecutorService worker = pingWorker;
        pingWorker = null;
        if (worker != null) { worker.shutdownNow(); }
    }

    private synchronized void notificationTick()
    {
        if (!running) { return; }
        try
        {
            reminders.poll();
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Could not process a tool completion alert", ex);
        }
    }

    private void sendToolNotification(String message)
    {
        if (!running || notifier == null) { return; }
        try { notifier.notify(config.toolNotifications(), message); }
        catch (RuntimeException ex)
        {
            LOG.warn("Could not deliver a tool notification", ex);
            throw ex;
        }
    }

    java.util.List<InfoPanelReminders.Notice> notices()
    {
        return running ? reminders.notices() : java.util.Collections.emptyList();
    }

    InfoPanelReminderInput prepareReminderInput()
    {
        boolean visible = running && config.toolCompletionBanner() && client != null
            && client.getGameState() == GameState.LOGGED_IN;
        boolean interactive = visible && !client.isMenuOpen() && client.getCanvas() != null
            && client.getCanvas().isFocusOwner();
        reminderInput.context(client == null ? 0 : client.getCanvasWidth(),
            client == null ? 0 : client.getCanvasHeight(), visible, interactive, notices());
        return reminderInput;
    }

    private synchronized void dismissNotice(InfoPanelReminders.Notice notice)
    {
        if (running && config.toolCompletionBanner() && client != null
            && client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen())
        { reminders.dismiss(notice); }
    }

    private void releaseNotebook()
    {
        notebook.deactivate();
        if (notebookStore != null && notebookSession != null) { notebookStore.release(notebookSession); }
        notebookSession = null;
    }

    private void loadNotebook()
    {
        releaseNotebook();
        if (profileConfig == null || profileConfig.getProfile() == null)
        {
            notebook = new InfoPanelNotebook();
            notebook.failure("RuneLite profile unavailable; notes not loaded.");
            return;
        }
        if (notebookStore == null)
        { notebookStore = new InfoPanelNotebookStore(RuneLite.RUNELITE_DIR.toPath().resolve("minimap-customizer")); }
        notebookSession = notebookStore.open(profileConfig.getProfile().getId());
        notebook = notebookSession.notebook;
    }

    void settingsChanged()
    {
        closeSelector();
        closeTracking();
        if (!config.enableToolsButton()) { drawer.reset(); }
        else { drawer.suspend(); }
        drawerVolumeBar.suspend();
        volume.cancelPending();
        reminderInput.suspend();
        lastSample = Long.MIN_VALUE;
        volumeBar.suspend();
    }

    synchronized void profileChanged()
    {
        closeSelector();
        closeTracking();
        drawer.reset();
        drawerVolumeBar.suspend();
        reminderInput.suspend();
        volumeBar.suspend();
        closePopup();
        tools.clearAll();
        reminders.clear();
        if (running) { loadNotebook(); }
        volume.reset();
        volumeResetPending = true;
        ping.invalidate();
        metrics = InfoPanelMetrics.EMPTY;
        lastSample = Long.MIN_VALUE;
    }

    void gameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING) { volumeResetPending = true; }
        if (state != GameState.LOGGED_IN)
        {
            drawer.suspend();
            drawerVolumeBar.suspend();
            reminderInput.suspend();
            hudAvailable = false;
            volumeBar.suspend();
            volume.suspend();
            closePopup();
            closeSelector();
            closeTracking();
            metrics = InfoPanelMetrics.EMPTY;
            lastSample = Long.MIN_VALUE;
            ping.invalidate();
        }
        // Timer and stopwatch continue through logouts, loads and world hops.
    }

    void updateOnClientThread()
    {
        if (!running) { return; }
        if (volumeResetPending) { nativeVolume.reset(); volumeResetPending = false; }
        InfoPanelText mode = config.infoPanelText();
        if (mode == null) { mode = InfoPanelText.ZONE; }
        if (mode != activeMode)
        {
            activeMode = mode;
            volumeBar.suspend();
            lastSample = Long.MIN_VALUE;
            volume.cancelPending();
            if (!popupFromDrawer) { closePopup(); }
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            metrics = InfoPanelMetrics.EMPTY;
            volume.suspend();
            return;
        }
        int world = client.getWorld();
        if (world != sampledWorld) { sampledWorld = world; lastSample = Long.MIN_VALUE; }
        long now = now();
        if (lastSample != Long.MIN_VALUE && now - lastSample < 1000) { return; }
        lastSample = now;
        try
        {
            int friends = mode.usesFriends() ? friendsOnline() : -1;
            int clanmates = mode.usesClan() ? clanmatesOnline() : -1;
            int fps = mode == InfoPanelText.FPS || mode == InfoPanelText.FPS_AND_PING ? client.getFPS() : -1;
            int inventory = -1;
            if (mode == InfoPanelText.INVENTORY)
            {
                ItemContainer container = client.getItemContainer(InventoryID.INV);
                if (container != null)
                {
                    int count = container.count();
                    if (count >= 0 && count <= 28) { inventory = count; }
                }
            }
            long xp = mode == InfoPanelText.TOTAL_XP ? client.getOverallExperience() : -1;
            int level = mode == InfoPanelText.TOTAL_LEVEL ? client.getTotalLevel() : -1;
            metrics = new InfoPanelMetrics(world > 0 ? world : -1, friends, clanmates, fps, inventory, xp,
                level > 0 ? level : -1);
            warned = false;
        }
        catch (RuntimeException ex)
        {
            metrics = InfoPanelMetrics.EMPTY;
            if (!warned) { LOG.warn("Info panel data is temporarily unavailable", ex); warned = true; }
        }
    }

    void afterHudUpdate(SquareHudController.Snapshot view)
    {
        hudAvailable = running && view != null && view.paint;
        if (hudAvailable)
        {
            Rectangle anchor = view.geometry.panel();
            anchor.add(view.geometry.footer());
            if (view.drawer != null && !view.drawer.layout.panel.isEmpty()) { anchor.add(view.drawer.layout.panel); }
            popupAnchorLocal = new Rectangle(anchor);
        }
        else { popupAnchorLocal = null; }
        if (popupOpen && !popupAllowed()) { closePopup(); }
        prepareDrawerInput(view, running);
        boolean usable = hudAvailable && view.footerVisible && config.enableSquareMinimap()
            && config.infoPanelText() == view.infoMode && client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null;
        boolean focused = usable && !client.isMenuOpen() && client.getCanvas() != null
            && client.getCanvas().isFocusOwner();
        Rectangle primaryBounds = usable && view.infoMode == InfoPanelText.VOLUME ? view.infoBounds : null;
        Rectangle drawerBounds = usable && view.drawer != null && view.drawer.shows(InfoPanelText.VOLUME)
            && drawer.allows(InfoPanelText.VOLUME) && view.drawer.state.generation == drawer.state().generation
            ? view.drawer.layout.content : null;
        long drawerGeneration = drawer.state().generation;
        if (!java.util.Objects.equals(previousPrimaryVolume, primaryBounds)
            || !java.util.Objects.equals(previousDrawerVolume, drawerBounds)
            || previousDrawerGeneration != drawerGeneration)
        { volume.cancelPending(); }
        previousPrimaryVolume = primaryBounds == null ? null : new Rectangle(primaryBounds);
        previousDrawerVolume = drawerBounds == null ? null : new Rectangle(drawerBounds);
        previousDrawerGeneration = drawerGeneration;
        volumeBar.update(primaryBounds, primaryBounds != null, focused);
        drawerVolumeBar.update(drawerBounds, drawerBounds != null, focused);
        volume.tick(nativeVolume, primaryBounds != null || drawerBounds != null,
            focused && (volumeBar.isVisible() || drawerVolumeBar.isVisible()), now());
        volumeBar.update(primaryBounds, primaryBounds != null, focused);
        drawerVolumeBar.update(drawerBounds, drawerBounds != null, focused);
        int world = hudAvailable && activeMode.usesPing() ? metrics.world : 0;
        ping.target(world);
        if (world <= 0) { return; }
        InfoPanelPingState.Request request = ping.begin(now());
        if (request == null) { return; }
        ExecutorService worker = pingWorker;
        if (worker == null) { ping.complete(request, -1, now()); return; }
        try
        {
            worker.execute(() ->
            {
                int latency = -1;
                try
                {
                    // WorldService.getWorlds() can wait for its first result. It is only called here,
                    // on our worker, and never from updateOnClientThread() or rendering.
                    WorldResult result = worldService.getWorlds();
                    if (result != null && result.getWorlds() != null && !Thread.currentThread().isInterrupted())
                    {
                        for (World candidate : result.getWorlds())
                        {
                            if (candidate != null && candidate.getId() == request.world)
                            {
                                try { latency = Ping.ping(candidate, false); }
                                catch (RuntimeException | LinkageError noNativePing) { latency = -1; }
                                if (latency < 0 && !Thread.currentThread().isInterrupted())
                                {
                                    latency = InfoPanelLatency.tcp(candidate.getAddress(), 43594, 2000);
                                }
                                break;
                            }
                        }
                    }
                }
                catch (RuntimeException | LinkageError unavailable)
                {
                    // Native ping support, DNS or connectivity can be unavailable. Never report 0 for failure.
                    latency = -1;
                }
                finally { ping.complete(request, latency, now()); }
            });
        }
        catch (RejectedExecutionException stopped) { ping.complete(request, -1, now()); }
    }

    SquareHudInfoText.Content content(InfoPanelText mode)
    {
        if (mode == InfoPanelText.VOLUME) { return volume.content(); }
        if (mode == InfoPanelText.NOTES)
        {
            InfoPanelNotebook.Snapshot n = notebook.snapshot();
            return new SquareHudInfoText.Content(n.tasks.isEmpty() ? "Notes" : "Tasks: " + n.completed() + "/" + n.tasks.size(),
                "Click for notes and checklist");
        }
        if (mode == InfoPanelText.ALARM)
        {
            InfoPanelAlarm.Snapshot a = reminders.alarm.snapshot();
            return new SquareHudInfoText.Content(a.fired ? "Alarm finished" : a.armed ? "Alarm " + a.time() : "Alarm: Off",
                InfoPanelFormatting.toolHover(mode));
        }
        return mode.isTool() ? InfoPanelFormatting.tool(mode, tools.snapshot())
            : InfoPanelFormatting.metric(mode, metrics, ping.value(now()));
    }

    boolean isPopupOpen() { return popupOpen; }
    InfoPanelText openPopupMode() { return popupOpen ? popupMode : null; }

    InfoPanelVolumeBar volumeBar() { return volumeBar; }
    boolean isVolumeDragging() { return volumeBar.isHolding() || drawerVolumeBar.isHolding(); }
    boolean volumeMuted() { return volume.snapshot().muted; }
    InfoPanelVolumeBar drawerVolumeBar() { return drawerVolumeBar; }
    InfoPanelToolDrawer drawer() { return drawer; }

    InfoPanelToolDrawer prepareDrawerInput(SquareHudController.Snapshot view, boolean started)
    {
        boolean ready = running && started && config.enableSquareMinimap() && view != null && view.paint
            && view.footerVisible && config.infoPanelText() == view.infoMode
            && client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen()
            && client.getCanvas() != null && (client.getCanvas().isFocusOwner() || popupOpen || trackingPopupOpen);
        drawer.publish(view == null ? null : view.drawer, ready);
        return drawer;
    }

    InfoPanelQuickControls.View quickControls(InfoPanelText mode, Rectangle content)
    { return quickControls.view(mode, content); }

    synchronized void drawerAction(InfoPanelToolDrawer.Action action, MouseEvent event)
    {
        if (action == null) { return; }
        if (action.toggleTracking)
        {
            toggleTracking(event);
            return;
        }
        if (action.control != null)
        {
            // Local tool state only. The existing listener owns the entire mouse gesture.
            // Recheck lifecycle/context and the painted command revision before executing.
            if (running && hudAvailable && config.enableSquareMinimap() && drawer.allows(action.tool)
                && drawer.owns(event.getPoint()) && client.getGameState() == GameState.LOGGED_IN
                && !client.isMenuOpen() && client.getCanvas() != null
                && (client.getCanvas().isFocusOwner() || popupOpen))
            { quickControls.execute(action.control); }
            return;
        }
        volume.cancelPending();
        volumeBar.suspend(); drawerVolumeBar.suspend();
        if (action.toggleTool && popupOpen && popupMode == action.tool) { closePopup(); }
        else if (action.openTool) { openTool(action.tool, event, true); }
        else if (popupFromDrawer && popupOpen) { closePopup(); }
    }

    private boolean popupAllowed()
    {
        return running && hudAvailable && popupOpen && popupMode != null && popupMode.isTool()
            && (popupFromDrawer ? drawer.allows(popupMode) : popupMode == activeMode);
    }


    void openFromClick(InfoPanelText mode, MouseEvent event)
    {
        if (mode != null && mode.isTool()) { openTool(mode, event, false); }
        else { toggleSelector(event); }
    }

    private void toggleTracking(MouseEvent event)
    {
        if (!running || !hudAvailable || !config.enableSquareMinimap() || !config.enableTrackingButton() || event == null) { return; }
        final Component source = event.getComponent();
        final Point fallback = new Point(event.getXOnScreen(), event.getYOnScreen());
        SwingUtilities.invokeLater(() ->
        {
            if (!running || !hudAvailable || !config.enableTrackingButton() || source == null || !source.isShowing()) { return; }
            if (trackingPopup == null)
            { trackingPopup = new TrackingPopup(trackingVisibility, () -> trackingPopupOpen = false); }
            trackingPopup.toggle(source, popupAnchor(source, fallback),
                savedPopupPosition(InfoPanelPopupPosition.TRACKING_KEY),
                point -> savePopupPosition(InfoPanelPopupPosition.TRACKING_KEY, point));
            trackingPopupOpen = trackingPopup.isShowing();
        });
    }

    private void toggleSelector(MouseEvent event)
    {
        if (!running || !hudAvailable || !config.enableSquareMinimap() || event == null) { return; }
        final Component source = event.getComponent();
        final Point fallback = new Point(event.getXOnScreen(), event.getYOnScreen());
        SwingUtilities.invokeLater(() ->
        {
            if (!running || !hudAvailable || source == null || !source.isShowing()) { return; }
            if (selector == null)
            {
                selector = new InfoPanelSelectorPopup(() -> config.infoPanelText(), option ->
                {
                    if (profileConfig != null)
                    { profileConfig.setConfiguration(MinimapFilterConfig.GROUP, "infoPanelText", option.name()); }
                });
            }
            selector.toggle(source, popupAnchor(source, fallback),
                savedPopupPosition(InfoPanelPopupPosition.SELECTOR_KEY),
                point -> savePopupPosition(InfoPanelPopupPosition.SELECTOR_KEY, point));
        });
    }

    private Rectangle popupAnchor(Component source, Point fallback)
    {
        Rectangle local = popupAnchorLocal;
        if (source != null && source.isShowing() && local != null && !local.isEmpty())
        {
            Point origin = source.getLocationOnScreen();
            return new Rectangle(origin.x + local.x, origin.y + local.y, local.width, local.height);
        }
        Point point = fallback == null ? new Point(0, 0) : fallback;
        return new Rectangle(point.x, point.y, 1, 1);
    }

    private Point savedPopupPosition(String key)
    {
        if (profileConfig == null || key == null) { return null; }
        return InfoPanelPopupPosition.decode(profileConfig.getConfiguration(MinimapFilterConfig.GROUP, key));
    }

    private void savePopupPosition(String key, Point point)
    {
        if (profileConfig == null || key == null || point == null) { return; }
        profileConfig.setConfiguration(MinimapFilterConfig.GROUP, key, InfoPanelPopupPosition.encode(point));
    }

    private void closeSelector()
    {
        InfoPanelSelectorPopup current = selector;
        if (current == null) { return; }
        SwingUtilities.invokeLater(() -> { if (selector == current) { current.close(); } });
    }

    private void closeTracking()
    {
        TrackingPopup current = trackingPopup;
        trackingPopupOpen = false;
        if (current == null) { return; }
        SwingUtilities.invokeLater(() -> { if (trackingPopup == current) { current.close(); } });
    }

    private void openTool(InfoPanelText mode, MouseEvent event, boolean fromDrawer)
    {
        if (!running || !hudAvailable || mode == null || !mode.isTool()
            || (fromDrawer ? !drawer.allows(mode) : mode != activeMode)) { return; }
        final Component component = event.getComponent();
        final Point fallback = new Point(event.getXOnScreen(), event.getYOnScreen());
        final long generation = uiGeneration.incrementAndGet();
        popupMode = mode;
        popupFromDrawer = fromDrawer;
        popupOpen = true; // The original single-row view pins its footer; an expanded drawer uses shared hover.
        SwingUtilities.invokeLater(() ->
        {
            if (generation != uiGeneration.get() || !popupAllowed()) { return; }
            try
            {
                String positionKey = InfoPanelPopupPosition.key(mode);
                popup.open(mode, component, popupAnchor(component, fallback), savedPopupPosition(positionKey),
                    point -> savePopupPosition(positionKey, point));
                popupOpen = popup.isShowing();
            }
            catch (RuntimeException ex)
            {
                popupOpen = false;
                popup.close();
                LOG.warn("Could not open the Info panel tool", ex);
            }
        });
    }

    /** An outside canvas click dismisses a tool without also moving the player. */
    boolean dismissOnCanvasPress()
    {
        if (!popupOpen) { return false; }
        closePopup();
        return true;
    }

    private void closePopup()
    {
        long generation = uiGeneration.incrementAndGet();
        volume.cancelPending();
        popupOpen = false;
        popupMode = null;
        popupFromDrawer = false;
        SwingUtilities.invokeLater(() ->
        {
            if (generation == uiGeneration.get()) { popup.close(); }
        });
    }

    private int friendsOnline()
    {
        FriendContainer container = client.getFriendContainer();
        if (container == null || container.getMembers() == null) { return -1; }
        List<InfoPanelCounts.Member> members = new ArrayList<>();
        for (Friend friend : container.getMembers())
        {
            if (friend != null) { members.add(new InfoPanelCounts.Member(friend.getName(), friend.getWorld())); }
        }
        return InfoPanelCounts.friends(members);
    }

    private int clanmatesOnline()
    {
        ClanChannel channel = client.getClanChannel();
        ClanSettings settings = client.getClanSettings();
        Player local = client.getLocalPlayer();
        if (channel == null || settings == null || local == null
            || channel.getMembers() == null || settings.getMembers() == null) { return -1; }
        List<String> roster = new ArrayList<>();
        for (ClanMember member : settings.getMembers()) { if (member != null) { roster.add(member.getName()); } }
        List<InfoPanelCounts.Member> online = new ArrayList<>();
        for (ClanChannelMember member : channel.getMembers())
        {
            if (member != null) { online.add(new InfoPanelCounts.Member(member.getName(), member.getWorld())); }
        }
        return InfoPanelCounts.clanmates(roster, online, local.getName());
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }
}
