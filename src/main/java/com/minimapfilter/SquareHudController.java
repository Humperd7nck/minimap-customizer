package com.minimapfilter;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Arrays;
import java.util.function.LongSupplier;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.minimapfilter.SquareHudWidgetEdits.Field.*;

/** Repositions the real interface; walking and orb actions stay with the game. */
@Singleton
final class SquareHudController
{
    private static final Logger LOG = LoggerFactory.getLogger(SquareHudController.class);
    private static final int PRIVATE_MASK_BASE = 821248;
    private static final int[] WORLD_MAP_PARTS = {
        InterfaceID.Worldmap.WINDOW, InterfaceID.Worldmap.FRAME,
        InterfaceID.Worldmap.CLOSE, InterfaceID.Worldmap.MAP_CONTAINER
    };
    private static final int[] CONTAINERS = {
        InterfaceID.Orbs.ORB_HEALTH, InterfaceID.Orbs.ORB_PRAYER,
        InterfaceID.Orbs.ORB_RUNENERGY, InterfaceID.Orbs.ORB_SPECENERGY
    };
    private static final int[] BUTTONS = {
        InterfaceID.Orbs.HEALTHBUTTON, InterfaceID.Orbs.PRAYERBUTTON,
        InterfaceID.Orbs.RUNBUTTON, InterfaceID.Orbs.SPECBUTTON
    };
    private static final int[] ICONS = {
        InterfaceID.Orbs.ORB_HEALTH_HEART_ICON, InterfaceID.Orbs.PRAYER_ICON,
        InterfaceID.Orbs.RUNENERGY_ICON, InterfaceID.Orbs.SPECENERGY_ICON
    };
    private static final int[] PVP_INDICATOR_PARTS = {
        InterfaceID.PvpIcons.PVP, InterfaceID.PvpIcons.PVPW_SAFE,
        InterfaceID.PvpIcons.PVP_RISK, InterfaceID.PvpIcons.WILDERNESSLEVEL,
        InterfaceID.PvpIcons.COMLEVELRANGE, InterfaceID.PvpIcons.SKULLDURATION,
        InterfaceID.PvpIcons.QUITTERWARNING, InterfaceID.PvpIcons.ATTACKSTYLE
    };
    private static final int[] UTILITIES = {
        InterfaceID.Orbs.ORB_STORE, InterfaceID.Orbs.ORB_CONTENTRECOM
    };

    static final class Snapshot
    {
        final SquareHudGeometry geometry;
        final Shape markerClip;
        final int[] values;
        final int[] maxima;
        final BufferedImage[] icons;
        // Native action availability, independent of the displayed resource values.
        final boolean[] available;
        final boolean[] selected;
        final int healthState;
        final boolean stamina;
        final boolean soulreaper;
        final double northAngle;
        final boolean paint;
        final boolean compassClickable;
        final boolean footerVisible;
        final String infoText;
        final String infoTooltip;
        final boolean showCompass;
        final Rectangle[] footerButtons;

        Snapshot(SquareHudGeometry geometry, int[] values, int[] maxima, BufferedImage[] icons,
            boolean[] available, boolean[] selected, int healthState, boolean stamina,
            boolean soulreaper, double northAngle, boolean paint, boolean compassClickable)
        {
            this(geometry, values, maxima, icons, available, selected, healthState, stamina,
                soulreaper, northAngle, paint, compassClickable, false, "", new Rectangle[0]);
        }

        Snapshot(SquareHudGeometry geometry, int[] values, int[] maxima, BufferedImage[] icons,
            boolean[] available, boolean[] selected, int healthState, boolean stamina,
            boolean soulreaper, double northAngle, boolean paint, boolean compassClickable,
            boolean footerVisible, String zoneName, Rectangle[] footerButtons)
        {
            this(geometry, values, maxima, icons, available, selected, healthState, stamina,
                soulreaper, northAngle, paint, compassClickable, footerVisible, zoneName,
                zoneName, footerButtons, true);
        }

        Snapshot(SquareHudGeometry geometry, int[] values, int[] maxima, BufferedImage[] icons,
            boolean[] available, boolean[] selected, int healthState, boolean stamina,
            boolean soulreaper, double northAngle, boolean paint, boolean compassClickable,
            boolean footerVisible, String infoText, String infoTooltip, Rectangle[] footerButtons,
            boolean showCompass)
        {
            this.footerVisible = footerVisible;
            this.infoText = infoText;
            this.infoTooltip = infoTooltip;
            this.showCompass = showCompass;
            this.footerButtons = footerButtons;
            this.geometry = geometry;
            Area clip = new Area(geometry.map());
            if (showCompass) { clip.subtract(new Area(geometry.compass())); }
            this.markerClip = clip;
            this.values = values;
            this.maxima = maxima;
            this.icons = icons;
            this.available = available;
            this.selected = selected;
            this.healthState = healthState;
            this.stamina = stamina;
            this.soulreaper = soulreaper;
            this.northAngle = northAngle;
            this.paint = paint;
            this.compassClickable = compassClickable;
        }
    }

    private static final class Bindings
    {
        final int block, inner, mask, compass, compassClick, host, ring, multiCombat;
        final int[] shims;

        Bindings(boolean modern)
        {
            block = modern ? InterfaceID.ToplevelPreEoc.MAP_CONTAINER : InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER;
            inner = modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP;
            mask = modern ? InterfaceID.ToplevelPreEoc.MINIMAP : InterfaceID.ToplevelOsrsStretch.MINIMAP;
            compass = modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP_GRAPHIC6 : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP_GRAPHIC6;
            compassClick = modern ? InterfaceID.ToplevelPreEoc.COMPASSCLICK : InterfaceID.ToplevelOsrsStretch.COMPASSCLICK;
            host = modern ? InterfaceID.ToplevelPreEoc.ORBS : InterfaceID.ToplevelOsrsStretch.ORBS;
            // GRAPHIC9 is the circular surround, not the live minimap (MINIMAP).
            // It stays hidden in Square HUD and must never be a readiness condition.
            ring = modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP_GRAPHIC9 : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP_GRAPHIC9;
            multiCombat = modern ? InterfaceID.ToplevelPreEoc.MULTIWAY_ICON
                : InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON;
            shims = modern ? new int[] {
                InterfaceID.ToplevelPreEoc.MAP_NOCLICK_0, InterfaceID.ToplevelPreEoc.MAP_NOCLICK_1,
                InterfaceID.ToplevelPreEoc.MAP_NOCLICK_2, InterfaceID.ToplevelPreEoc.MAP_NOCLICK_3,
                InterfaceID.ToplevelPreEoc.MAP_NOCLICK_4, InterfaceID.ToplevelPreEoc.MAP_NOCLICK_5
            } : new int[] {
                InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_0, InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_1,
                InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_2, InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_3,
                InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_4, InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_5
            };
        }
    }

    private static final Bindings MODERN = new Bindings(true);
    private static final Bindings CLASSIC_RESIZABLE = new Bindings(false);
    private final LongSupplier clock;
    private final Client client;
    private final ClientThread clientThread;
    private final MinimapFilterConfig config;
    private final MouseManager mouseManager;
    private final SpriteManager spriteManager;
    private final SquareHudWidgetEdits edits = new SquareHudWidgetEdits();
    private final SquareHudSpriteLease masks = new SquareHudSpriteLease();
    private final SquareHudWidgetEdits worldMapEdits = new SquareHudWidgetEdits();
    private final SquareHudControlLease nativeControls = new SquareHudControlLease();
    private final SquareHudNativeMap nativeMap = new SquareHudNativeMap();
    private final NativeCompassVisibility nativeCompass;
    private final SquareHudInfoText infoText = new SquareHudInfoText();
    private final BufferedImage[] cachedIcons = new BufferedImage[4];
    private final int[] cachedIconIds = {-1, -1, -1, -1};
    private volatile Snapshot snapshot;
    private volatile boolean started;
    private boolean faulted;
    // A real game-tab change reflows the Orbs interface after Classic rollback.
    // Perform its group-level layout refresh over the next few client frames.
    private int classicGroupRefreshFrames;
    private Widget activeBlock;
    // Captured from the *live* Classic widget before Square HUD edits its layout.
    // Relative widget modes can resolve against the entire game canvas during rollback.
    private ClassicMinimapBaseline classicBaseline;

    private static final class ClassicMinimapBaseline
    {
        final Widget widget;
        final int width;
        final int height;
        final int originalWidth;
        final int originalHeight;
        final int widthMode;
        final int heightMode;

        ClassicMinimapBaseline(Widget widget)
        {
            this.widget = widget;
            width = widget.getWidth();
            height = widget.getHeight();
            originalWidth = widget.getOriginalWidth();
            originalHeight = widget.getOriginalHeight();
            widthMode = widget.getWidthMode();
            heightMode = widget.getHeightMode();
        }
    }

    private SpritePixels maskSprite;
    private int privateMaskId = -1;
    private boolean consumedCompassPress;
    private boolean consumedCompassClick;
    private int consumedFooterButton;
    private int consumedFooterClick;
    private final SquareHudFooterVisibility footerVisibility = new SquareHudFooterVisibility();
    private final SquareHudWidgetEdits footerHiddenEdits = new SquareHudWidgetEdits();
    private final Set<Integer> footerWidgetIds = new HashSet<>();
    private boolean locationInitialized;
    private String zoneName = "";
    private WorldPoint currentLocation;

    @Inject
    SquareHudController(Client client, ClientThread clientThread, MinimapFilterConfig config,
        MouseManager mouseManager, SpriteManager spriteManager)
    {
        this(client, clientThread, config, mouseManager, spriteManager,
            () -> System.nanoTime() / 1_000_000L);
    }

    SquareHudController(Client client, ClientThread clientThread, MinimapFilterConfig config,
        MouseManager mouseManager, SpriteManager spriteManager, LongSupplier clock)
    {
        this.clock = clock;
        this.client = client;
        this.nativeCompass = new NativeCompassVisibility(client);
        this.clientThread = clientThread;
        this.config = config;
        this.mouseManager = mouseManager;
        this.spriteManager = spriteManager;
    }

    private final MouseAdapter compassMouse = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent event)
        {
            consumedCompassClick = false;
            consumedFooterButton = 0;
            consumedFooterClick = 0;
            Snapshot view = snapshot;
            if (!event.isConsumed() && event.getButton() == MouseEvent.BUTTON1
                && !event.isAltDown() && !event.isShiftDown() && !event.isControlDown()
                && view != null && view.compassClickable && view.geometry.compass().contains(event.getPoint()))
            {
                consumedCompassPress = true;
                consumedCompassClick = true;
                event.consume();
                clientThread.invokeLater(() ->
                {
                    Snapshot current = snapshot;
                    if (started && current != null && current.compassClickable && !client.isMenuOpen())
                    {
                        client.setCameraYawTarget(0);
                    }
                });
            }
            // The visible footer's empty space is not a walking target. Native
            // XP/World Map controls still receive their own ordinary mouse events.
            if (!event.isConsumed() && !client.isMenuOpen() && view != null && view.paint
                && view.footerVisible && view.geometry.footer().contains(event.getPoint())
                && Arrays.stream(view.footerButtons).noneMatch(r -> r.contains(event.getPoint()))
                && (event.getButton() == MouseEvent.BUTTON1 || event.getButton() == MouseEvent.BUTTON3))
            {
                consumedFooterButton = event.getButton();
                consumedFooterClick = event.getButton();
                event.consume();
            }
            return event;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent event)
        {
            if (consumedFooterButton != 0 && event.getButton() == consumedFooterButton)
            {
                consumedFooterButton = 0;
                event.consume();
            }
            if (event.getButton() == MouseEvent.BUTTON1 && consumedCompassPress)
            {
                consumedCompassPress = false;
                event.consume();
            }
            return event;
        }

        @Override
        public MouseEvent mouseClicked(MouseEvent event)
        {
            if (consumedFooterClick != 0 && event.getButton() == consumedFooterClick)
            {
                consumedFooterClick = 0;
                event.consume();
            }
            if (event.getButton() == MouseEvent.BUTTON1 && consumedCompassClick)
            {
                consumedCompassClick = false;
                event.consume();
            }
            return event;
        }
    };

    void start()
    {
        if (started)
        {
            return;
        }
        started = true;
        faulted = false;
        // Append after Stretched Mode's coordinate translation; do not translate twice.
        mouseManager.registerMouseListener(compassMouse);
    }

    void stop()
    {
        started = false;
        snapshot = null;
        consumedCompassPress = false;
        consumedCompassClick = false;
        consumedFooterButton = 0;
        consumedFooterClick = 0;
        mouseManager.unregisterMouseListener(compassMouse);
        clientThread.invokeLater(() ->
        {
            restore();
            // Plugin shutdown stops update() from running. Drain the Classic
            // Orbs refresh here too; otherwise disabling during Square HUD
            // leaves native buttons waiting for a game-tab layout refresh.
            while (classicGroupRefreshFrames > 0)
            {
                refreshClassicOrbsGroup();
            }
            nativeCompass.restore();
        });
    }

    void onGameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN || state == GameState.STARTING || state == GameState.LOGIN_SCREEN_AUTHENTICATOR)
        {
            restore();
            nativeCompass.restore();
        }
        else if (state != GameState.LOGGED_IN)
        {
            // Region loads keep the same widgets. Keep their rollback records.
            snapshot = null;
            zoneName = "";
            currentLocation = null;
            locationInitialized = false;
        }
    }

    void settingsChanged()
    {
        clientThread.invokeLater(() ->
        {
            faulted = false;
            locationInitialized = false;
            clearIconCache();
        });
    }

    void update()
    {
        if (!started)
        {
            return;
        }
        int top = client.getTopLevelInterfaceId();
        if (!config.enableSquareMinimap())
        {
            faulted = false;
            restore();
            refreshClassicOrbsGroup();
            nativeCompass.update(config.showCompass());
            return;
        }
        if (top != InterfaceID.TOPLEVEL_PRE_EOC && top != InterfaceID.TOPLEVEL_OSRS_STRETCH)
        {
            restore();
            nativeCompass.update(config.showCompass());
            return;
        }
        if (faulted)
        {
            nativeCompass.update(config.showCompass());
            return;
        }
        classicGroupRefreshFrames = 0;
        // Never let a Classic-mode visibility lease become Square HUD's original.
        nativeCompass.restore();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            snapshot = null;
            return;
        }
        try
        {
            apply(top == InterfaceID.TOPLEVEL_PRE_EOC ? MODERN : CLASSIC_RESIZABLE);
            if (snapshot == null) { nativeCompass.update(config.showCompass()); }
        }
        catch (RuntimeException ex)
        {
            // Fail back to the game's interface instead of leaving a half-applied HUD.
            faulted = true;
            restore();
            nativeCompass.update(config.showCompass());
            LOG.warn("Square HUD was restored after an interface error. Turn Enable square minimap off and on to retry.", ex);
        }
    }

    private void apply(Bindings ids)
    {
        Widget block = client.getWidget(ids.block);
        Widget inner = client.getWidget(ids.inner);
        Widget map = client.getWidget(ids.mask);
        Widget host = client.getWidget(ids.host);
        Widget orbs = client.getWidget(InterfaceID.Orbs.UNIVERSE);
        if (block == null || inner == null || map == null || host == null || orbs == null
            || block.getParent() == null || block.isHidden() || inner.isHidden() || map.isHidden())
        {
            restore();
            return;
        }
        for (int i = 0; i < 4; i++)
        {
            if (client.getWidget(CONTAINERS[i]) == null || client.getWidget(BUTTONS[i]) == null)
            {
                restore();
                return;
            }
        }
        if (activeBlock != null && activeBlock != block)
        {
            restore();
        }

        Widget wilderness = client.getWidget(InterfaceID.PvpIcons.ICONS);
        int wildernessWidth = validIndicator(wilderness, SquareHudGeometry.WIDTH, 200)
            ? wilderness.getWidth() : 0;
        int wildernessHeight = wildernessWidth > 0 ? wilderness.getHeight() : 0;
        boolean wildernessVisible = wildernessWidth > 0 && indicatorHasContent(wilderness);
        int footerHeight = SquareHudGeometry.footerHeightForControls(
            footerControlHeight(InterfaceID.Orbs.XP_DROPS),
            footerControlHeight(InterfaceID.Orbs.ORB_WORLDMAP));
        List<UtilityPlacement> utilities = utilityPlacements(Math.max(
            SquareHudGeometry.FOOTER_SLOT_WIDTH,
            wildernessWidth + SquareHudGeometry.BORDER + SquareHudGeometry.GAP), footerHeight);
        int totalHeight = SquareHudGeometry.HEIGHT + footerHeight;
        for (UtilityPlacement utility : utilities)
        {
            totalHeight = Math.max(totalHeight, utility.bounds.y + utility.bounds.height + 2);
        }
        Widget multiCombat = client.getWidget(ids.multiCombat);
        // Reserve the full native skull/level block, including longer combat-range
        // text, and a separate row for crossed swords. Footer hover never moves them.
        int indicatorBottom = SquareHudGeometry.HEIGHT + footerHeight
            + SquareHudGeometry.GAP;
        if (wildernessHeight > 0)
        {
            indicatorBottom += wildernessHeight + SquareHudGeometry.GAP;
        }
        if (validIndicator(multiCombat, 64, 64))
        {
            indicatorBottom += multiCombat.getHeight();
        }
        totalHeight = Math.max(totalHeight, indicatorBottom);
        Widget parent = block.getParent();
        if (parent.getWidth() < SquareHudGeometry.WIDTH || parent.getHeight() < totalHeight)
        {
            restore();
            return;
        }
        if (!installMasks())
        {
            restore();
            return;
        }
        // Snapshot the stock buttons before any HUD field or layout edits.
        nativeControls.capture(client);
        if (activeBlock == null)
        {
            classicBaseline = new ClassicMinimapBaseline(block);
        }
        activeBlock = block;
        SquareHudGeometry local = new SquareHudGeometry(0, 0, footerHeight);
        Rectangle localMap = local.map();
        boolean dirty = edits.set(block, WIDTH_MODE, WidgetSizeMode.ABSOLUTE);
        dirty |= edits.set(block, HEIGHT_MODE, WidgetSizeMode.ABSOLUTE);
        dirty |= edits.set(block, WIDTH, SquareHudGeometry.WIDTH);
        dirty |= edits.set(block, HEIGHT, totalHeight);
        // Keep the game's existing anchor/position, including a user's minimap placement.
        dirty |= edits.place(inner, localMap.x, localMap.y, localMap.width, localMap.height);
        dirty |= edits.place(map, 0, 0, localMap.width, localMap.height);
        // The stock widget may have a separate click mask installed by game scripts.
        // Leave that state and the stock sprite overrides alone. A new native map
        // child has its own mask, while the old widget retains its position for
        // plugins that use it as a coordinate reference.
        if (map.getContentType() != 0 && map.getContentType() != SquareHudNativeMap.CONTENT_TYPE)
        {
            throw new IllegalStateException("Unexpected minimap content type");
        }
        Widget replacement = nativeMap.ensure(inner, localMap.width, localMap.height, privateMaskId);
        if (replacement == null)
        {
            throw new IllegalStateException("Unable to create native square minimap");
        }
        dirty |= edits.set(map, CONTENT_TYPE, 0);
        dirty |= edits.set(map, TYPE, WidgetType.LAYER);
        dirty |= edits.set(map, NO_CLICK, 0);
        dirty |= edits.place(host, 0, 0, SquareHudGeometry.WIDTH, totalHeight);
        // This host covers the map too. Its empty area must never intercept map clicks.
        dirty |= edits.set(host, NO_CLICK, 0);
        dirty |= edits.place(orbs, 0, 0, SquareHudGeometry.WIDTH, totalHeight);
        dirty |= edits.set(orbs, NO_CLICK, 0);
        dirty |= edits.set(client.getWidget(ids.ring), HIDDEN, 1);
        dirty |= edits.set(client.getWidget(ids.compass), HIDDEN, 1);
        dirty |= edits.set(client.getWidget(ids.compassClick), HIDDEN, 1);
        for (int id : ids.shims)
        {
            dirty |= edits.set(client.getWidget(id), HIDDEN, 1);
        }
        if (dirty)
        {
            revalidate(block);
            revalidate(orbs);
        }

        net.runelite.api.Point location = inner.getCanvasLocation();
        if (location == null || inner.getWidth() != localMap.width || inner.getHeight() != localMap.height)
        {
            restore();
            return;
        }
        SquareHudGeometry geometry = new SquareHudGeometry(location.getX() - localMap.x,
            location.getY() - localMap.y, footerHeight);
        Rectangle footprint = geometry.panel();
        footprint.height = totalHeight;
        boolean coveredByWorldMap = worldMapOverlaps(footprint);
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        boolean hovered = mouse != null && geometry.footerReveal().contains(mouse.getX(), mouse.getY());
        boolean footerVisible = footerVisibility.update(config.squareHudFooterMode(), hovered,
            client.isMenuOpen(), coveredByWorldMap, clock.getAsLong());
        if (footerVisible)
        {
            footerHiddenEdits.restore();
        }
        // The parent's blank footer space must not block the scene when hidden.
        // Individual native buttons and the map retain their own click protection.
        edits.set(client.getWidget(InterfaceID.Orbs.WIKI), HIDDEN, 1);
        if (!coveredByWorldMap)
        {
            worldMapEdits.restore();
        }
        // A separate World Map window must not disable the minimap. Yield only
        // when its actual window or controls cover our panel or utility row.
        edits.set(block, NO_CLICK, !coveredByWorldMap && footerVisible ? 1 : 0);
        edits.set(inner, NO_CLICK, coveredByWorldMap ? 0 : 1);
        replacement.setNoClickThrough(!coveredByWorldMap);
        replacement.setHidden(coveredByWorldMap);
        for (int i = 0; i < 4; i++)
        {
            Widget orb = client.getWidget(CONTAINERS[i]);
            Widget button = client.getWidget(BUTTONS[i]);
            Rectangle rect = geometry.block(i);
            placeOnCanvas(orb, rect);
            makeTransparent(orb);
            // Keep the actual button, its onOp listener, flags, menu options and keybinds.
            // Only its visual type and hit rectangle change.
            edits.set(button, TYPE, WidgetType.RECTANGLE);
            edits.set(button, OPACITY, 255);
            edits.set(button, NO_CLICK, coveredByWorldMap ? 0 : 1);
            placeOnCanvas(button, rect);
            if (coveredByWorldMap)
            {
                // Do not leave invisible actions beneath a covering World Map.
                // The separate lease preserves naturally disabled/hidden actions.
                worldMapEdits.set(button, HIDDEN, 1);
            }
        }
        Rectangle panel = geometry.panel();
        for (UtilityPlacement utility : utilities)
        {
            Rectangle target = new Rectangle(utility.bounds);
            target.translate(panel.x, panel.y);
            placeOnCanvas(utility.widget, target);
        }
        List<Rectangle> footerButtons = new ArrayList<>();
        footerWidgetIds.clear();
        placeFooterControl(InterfaceID.Orbs.XP_DROPS, geometry, false, footerVisible, footerButtons);
        placeFooterControl(InterfaceID.Orbs.ORB_WORLDMAP, geometry, true, footerVisible, footerButtons);
        Rectangle wildernessBounds = null;
        if (wildernessWidth > 0)
        {
            Rectangle target = geometry.wilderness(wildernessWidth, wildernessHeight);
            placeOnCanvas(wilderness, target);
            if (wildernessVisible) { wildernessBounds = target; }
            if (coveredByWorldMap) { worldMapEdits.set(wilderness, HIDDEN, 1); }
        }
        placeMultiCombat(multiCombat, geometry, wildernessBounds, coveredByWorldMap);
        if (!locationInitialized) { refreshLocation(); }
        snapshot = readSnapshot(geometry, !coveredByWorldMap, footerVisible,
            footerButtons.toArray(new Rectangle[0]));
        filterHiddenFooterMenus();
    }

    private void placeMultiCombat(Widget widget, SquareHudGeometry geometry,
        Rectangle wildernessBounds, boolean coveredByWorldMap)
    {
        if (widget == null || widget.getWidth() <= 0 || widget.getWidth() > 64
            || widget.getHeight() <= 0 || widget.getHeight() > 64)
        {
            return;
        }
        Widget globe = client.getWidget(InterfaceID.Orbs.WORLDMAP);
        Rectangle globeBounds = globe == null ? null : globe.getBounds();
        Rectangle target = geometry.multiCombat(globeBounds, widget.getWidth(), widget.getHeight(), wildernessBounds);
        placeOnCanvas(widget, target);
        // The game still decides when this indicator is visible. Footer hover
        // changes neither that state nor its position outside the frame.
        if (coveredByWorldMap)
        {
            worldMapEdits.set(widget, HIDDEN, 1);
        }
    }

    private static boolean validIndicator(Widget widget, int maxWidth, int maxHeight)
    {
        return widget != null && widget.getWidth() > 0 && widget.getWidth() <= maxWidth
            && widget.getHeight() > 0 && widget.getHeight() <= maxHeight;
    }

    private boolean naturallyHidden(Widget widget)
    {
        for (int depth = 0; widget != null && depth < 32; depth++, widget = widget.getParent())
        {
            if (worldMapEdits.valueBeforeEdit(widget, HIDDEN) != 0) { return true; }
        }
        return false;
    }

    private boolean indicatorHasContent(Widget root)
    {
        if (naturallyHidden(root)) { return false; }
        if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0) { return true; }
        // The same native group also displays PvP-world/teleblock information.
        // Reserve it whenever the game has real visible content in that group.
        for (int id : PVP_INDICATOR_PARTS)
        {
            Widget part = client.getWidget(id);
            if (part == null || naturallyHidden(part) || part.getOpacity() == 255) { continue; }
            String text = part.getText();
            if (part.getSpriteId() >= 0 || (text != null && !text.trim().isEmpty()))
            {
                return true;
            }
        }
        return false;
    }

    private static final class UtilityPlacement
    {
        final Widget widget;
        final Rectangle bounds;

        UtilityPlacement(Widget widget, Rectangle bounds)
        {
            this.widget = widget;
            this.bounds = bounds;
        }
    }

    private List<UtilityPlacement> utilityPlacements(int indicatorColumnWidth, int footerHeight)
    {
        List<UtilityPlacement> result = new ArrayList<>();
        int x = 0;
        int y = SquareHudGeometry.HEIGHT + footerHeight + 3;
        int rowHeight = 0;
        for (int id : UTILITIES)
        {
            Widget widget = client.getWidget(id);
            if (widget == null || widget.isSelfHidden())
            {
                continue;
            }
            int width = widget.getWidth();
            int height = widget.getHeight();
            if (width <= 0 || height <= 0 || width > SquareHudGeometry.WIDTH || height > 80)
            {
                continue;
            }
            if (x > 0 && x + width > SquareHudGeometry.WIDTH - indicatorColumnWidth)
            {
                x = 0;
                y += rowHeight + 2;
                rowHeight = 0;
            }
            result.add(new UtilityPlacement(widget, new Rectangle(x, y, width, height)));
            x += width + 2;
            rowHeight = Math.max(rowHeight, height);
        }
        return result;
    }

    private int footerControlHeight(int id)
    {
        Widget widget = client.getWidget(id);
        if (widget == null || widget.isHidden()) { return 0; }
        int width = widget.getWidth();
        int height = widget.getHeight();
        return width > 0 && width <= SquareHudGeometry.FOOTER_SLOT_WIDTH
            && height > 0 && height <= 80 ? height : 0;
    }

    private void placeFooterControl(int id, SquareHudGeometry geometry, boolean right,
        boolean visible, List<Rectangle> buttons)
    {
        Widget widget = client.getWidget(id);
        if (widget == null || widget.isHidden()) { return; }
        int width = widget.getWidth();
        int height = widget.getHeight();
        Rectangle footer = geometry.footer();
        if (width <= 0 || width > SquareHudGeometry.FOOTER_SLOT_WIDTH || height <= 0
            || height > footer.height - SquareHudGeometry.FOOTER_PADDING * 2) { return; }
        int x = right ? footer.x + footer.width - SquareHudGeometry.BORDER - width
            : footer.x + SquareHudGeometry.BORDER + 3;
        Rectangle target = new Rectangle(x, footer.y + (footer.height - height) / 2, width, height);
        placeOnCanvas(widget, target);
        buttons.add(new Rectangle(target));
        visitFooter(widget, Collections.newSetFromMap(new IdentityHashMap<>()), 0, !visible);
    }

    private void visitFooter(Widget widget, Set<Widget> visited, int depth, boolean invisible)
    {
        if (widget == null || depth > 16 || !visited.add(widget)) { return; }
        footerWidgetIds.add(widget.getId());
        if (invisible)
        {
            // Do not hide, collapse or detach the World Map widget: its native
            // keyboard shortcut must remain attached. Op listeners are untouched.
            footerHiddenEdits.set(widget, OPACITY, 255);
            footerHiddenEdits.set(widget, NO_CLICK, 0);
        }
        for (Widget[] children : new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren()})
        {
            if (children != null)
            {
                for (Widget child : children) { visitFooter(child, visited, depth + 1, invisible); }
            }
        }
    }

    void filterHiddenFooterMenus()
    {
        Snapshot view = snapshot;
        if (!started || view == null || view.footerVisible || footerWidgetIds.isEmpty()
            || client.isMenuOpen()) { return; }
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null || entries.length == 0) { return; }
        MenuEntry[] kept = Arrays.stream(entries).filter(e -> !isFooterEntry(e)).toArray(MenuEntry[]::new);
        if (kept.length != entries.length) { client.setMenuEntries(kept); }
    }

    private boolean isFooterEntry(MenuEntry entry)
    {
        if (entry == null) { return false; }
        MenuAction type = entry.getType();
        return (type == MenuAction.CC_OP || type == MenuAction.CC_OP_LOW_PRIORITY)
            && footerWidgetIds.contains(entry.getParam1());
    }

    void refreshLocation()
    {
        locationInitialized = true;
        zoneName = "";
        currentLocation = null;
        if (client.getGameState() != GameState.LOGGED_IN) { return; }
        Player player = client.getLocalPlayer();
        if (player == null || player.getLocalLocation() == null) { return; }
        try
        {
            WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
            if (point != null)
            {
                currentLocation = point;
                zoneName = SquareHudZones.lookup(point.getRegionID());
            }
        }
        catch (RuntimeException ignored)
        {
            // An interface/scene transition must not retain a stale location label.
            zoneName = "";
        }
    }

    private boolean installMasks()
    {
        Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
        if (overrides == null)
        {
            return false;
        }
        if (maskSprite == null)
        {
            int size = SquareHudGeometry.MAP_SIZE;
            maskSprite = client.createSpritePixels(new int[size * size], size, size);
            privateMaskId = PRIVATE_MASK_BASE;
            while (overrides.containsKey(privateMaskId) && privateMaskId < PRIVATE_MASK_BASE + 1024)
            {
                privateMaskId++;
            }
            if (maskSprite == null || overrides.containsKey(privateMaskId))
            {
                maskSprite = null;
                privateMaskId = -1;
                return false;
            }
        }
        boolean changed = masks.put(overrides, privateMaskId, maskSprite);
        if (changed)
        {
            client.getWidgetSpriteCache().reset();
        }
        return true;
    }

    private boolean worldMapOverlaps(Rectangle footprint)
    {
        Widget root = client.getWidget(InterfaceID.Worldmap.UNIVERSE);
        if (root == null || root.isHidden())
        {
            return false;
        }
        // UNIVERSE can cover the entire canvas even for a smaller map window.
        // Use the actual visible window and its frame/close control, not that root.
        boolean measuredWindow = false;
        for (int id : WORLD_MAP_PARTS)
        {
            Widget part = client.getWidget(id);
            if (part == null || part.isHidden())
            {
                continue;
            }
            Rectangle bounds = part.getBounds();
            if (bounds == null || bounds.isEmpty())
            {
                continue;
            }
            measuredWindow = true;
            if (bounds.intersects(footprint))
            {
                return true;
            }
        }
        // During a partial interface load, do not intercept the World Map until
        // its real bounds are known. A later update resumes the HUD on its own.
        return !measuredWindow;
    }

    private void placeOnCanvas(Widget widget, Rectangle rect)
    {
        Widget parent = widget.getParent();
        if (parent == null)
        {
            return;
        }
        net.runelite.api.Point p = parent.getCanvasLocation();
        if (p != null && edits.place(widget, rect.x - p.getX(), rect.y - p.getY(), rect.width, rect.height))
        {
            revalidate(widget);
        }
    }

    private void makeTransparent(Widget root)
    {
        visit(root, Collections.newSetFromMap(new IdentityHashMap<>()), 0, true);
    }

    private void revalidate(Widget root)
    {
        visit(root, Collections.newSetFromMap(new IdentityHashMap<>()), 0, false);
    }

    private void visit(Widget widget, Set<Widget> visited, int depth, boolean transparent)
    {
        if (widget == null || depth > 16 || !visited.add(widget))
        {
            return;
        }
        if (transparent)
        {
            edits.set(widget, OPACITY, 255);
        }
        else
        {
            widget.revalidate();
        }
        Widget[][] groups = {widget.getStaticChildren(), widget.getDynamicChildren()};
        for (Widget[] group : groups)
        {
            if (group != null)
            {
                for (Widget child : group)
                {
                    visit(child, visited, depth + 1, transparent);
                }
            }
        }
    }

    private Snapshot readSnapshot(SquareHudGeometry geometry, boolean paint,
        boolean footerVisible, Rectangle[] footerButtons)
    {
        boolean soulreaper = soulreaperEquipped();
        int[] values = {
            Math.max(0, client.getBoostedSkillLevel(Skill.HITPOINTS)),
            Math.max(0, client.getBoostedSkillLevel(Skill.PRAYER)),
            SquareHudValues.runValue(client.getEnergy()),
            soulreaper ? Math.max(0, client.getVarpValue(VarPlayerID.SOULREAPER_STACKS))
                : SquareHudValues.specialValue(client.getVarpValue(VarPlayerID.SA_ENERGY))
        };
        int[] maxima = {client.getRealSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.PRAYER), 100, soulreaper ? 5 : 100};
        boolean[] selected = {false, client.getVarbitValue(VarbitID.QUICKPRAYER_ACTIVE) != 0,
            client.getVarpValue(VarPlayerID.OPTION_RUN) != 0, client.getVarpValue(VarPlayerID.SA_ATTACK) != 0};
        boolean stamina = client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
        boolean[] available = new boolean[4];
        BufferedImage[] icons = new BufferedImage[4];
        for (int i = 0; i < 4; i++)
        {
            Widget button = client.getWidget(BUTTONS[i]);
            available[i] = button != null && !button.isHidden();
            Widget icon = client.getWidget(ICONS[i]);
            int sprite = i == 2 ? SquareHudResourceStyle.runIconSprite(selected[2], stamina)
                : icon == null ? -1 : icon.getSpriteId();
            if (sprite != cachedIconIds[i] || cachedIcons[i] == null)
            {
                cachedIconIds[i] = sprite;
                cachedIcons[i] = sprite < 0 || spriteManager == null ? null : spriteManager.getSprite(sprite, 0);
            }
            icons[i] = cachedIcons[i];
        }
        int poison = client.getVarpValue(VarPlayerID.POISON);
        int health = poison >= 1_000_000 ? 2 : poison > 0 ? 1
            : client.getVarpValue(VarPlayerID.DISEASE) > 0 ? 3 : 0;
        SquareHudInfoText.Content content = currentInfoText();
        boolean showCompass = config.showCompass();
        return new Snapshot(geometry, values, maxima, icons, available, selected, health,
            stamina, soulreaper,
            showCompass ? northAngle() : 0, paint, paint && showCompass && !client.isMenuOpen(), footerVisible,
            content.text, content.tooltip, footerButtons, showCompass);
    }

    private SquareHudInfoText.Content currentInfoText()
    {
        InfoPanelText mode = config.infoPanelText();
        if (mode == InfoPanelText.SYSTEM_TIME) { return infoText.systemTime(); }
        if (mode == InfoPanelText.COORDS)
        {
            WorldPoint point = currentLocation;
            return point == null ? SquareHudInfoText.Content.EMPTY
                : SquareHudInfoText.coordinates(point.getX(), point.getY(), point.getPlane());
        }
        return SquareHudInfoText.zone(zoneName);
    }

    private boolean soulreaperEquipped()
    {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        return weapon != null && (weapon.getId() == ItemID.SOULREAPER || weapon.getId() == ItemID.SOULREAPER_AXE_ORN);
    }

    private double northAngle()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getLocalLocation() == null)
        {
            return 0;
        }
        LocalPoint origin = player.getLocalLocation();
        LocalPoint north = new LocalPoint(origin.getX(), origin.getY() + 512, origin.getWorldView());
        net.runelite.api.Point a = project(origin);
        net.runelite.api.Point b = project(north);
        return a == null || b == null ? 0 : Math.atan2(b.getX() - a.getX(), a.getY() - b.getY());
    }

    Snapshot snapshot()
    {
        return snapshot;
    }

    boolean isActive()
    {
        return snapshot != null;
    }

    boolean isPaintable()
    {
        Snapshot view = snapshot;
        return view != null && view.paint;
    }

    net.runelite.api.Point project(LocalPoint point)
    {
        return point == null ? null : Perspective.localToMinimap(client, point, 30_000);
    }

    Rectangle mapBounds()
    {
        Snapshot view = snapshot;
        return view == null ? null : view.geometry.map();
    }

    Shape markerClip()
    {
        Snapshot view = snapshot;
        return view == null ? null : view.markerClip;
    }

    private void clearIconCache()
    {
        for (int i = 0; i < cachedIcons.length; i++)
        {
            cachedIcons[i] = null;
            cachedIconIds[i] = -1;
        }
    }

    private void revalidateClassicHierarchy(Widget block, ClassicMinimapBaseline baseline)
    {
        Widget parent = block.getParent();
        if (parent == null)
        {
            return;
        }
        List<Widget> ancestors = new ArrayList<>();
        Widget node = parent;
        for (int i = 0; node != null && i < 8; i++, node = node.getParent())
        {
            ancestors.add(node);
        }
        for (int i = ancestors.size() - 1; i >= 0; i--)
        {
            ancestors.get(i).revalidate();
        }
        block.revalidate();
        parent.revalidate();
        block.revalidate();

        // Verify the actual rendered bounds, not just originalWidth/originalHeight.
        // In the live client a relative mode has produced 1524x912 here while
        // the true Classic minimap was only ~213px wide. Its parent can also be
        // temporarily full-canvas, making the old parent-only guard ineffective.
        if (baseline != null && baseline.widget == block
            && needsClassicBaselineRepair(block.getWidth(), block.getHeight(),
                baseline.width, baseline.height))
        {
                block.setWidthMode(WidgetSizeMode.ABSOLUTE);
            block.setHeightMode(WidgetSizeMode.ABSOLUTE);
            block.setOriginalWidth(baseline.width);
            block.setOriginalHeight(baseline.height);
            block.revalidate();
            if (needsClassicBaselineRepair(block.getWidth(), block.getHeight(),
                baseline.width, baseline.height))
            {
                LOG.warn("Square HUD: Classic minimap remains oversized after correction: {}x{}",
                    block.getWidth(), block.getHeight());
            }
        }
        Widget orbs = client.getWidget(InterfaceID.Orbs.UNIVERSE);
        if (orbs != null)
        {
            revalidate(orbs);
        }
        // Do not revalidate(block) again here: that previously re-expanded the
        // relative container immediately after correcting it.
    }

    static boolean needsClassicBaselineRepair(int actualWidth, int actualHeight,
        int baselineWidth, int baselineHeight)
    {
        return baselineWidth >= 100 && baselineWidth <= 500
            && baselineHeight >= 100 && baselineHeight <= 600
            && (actualWidth > Math.max(baselineWidth + 24, baselineWidth * 2)
                || actualHeight > Math.max(baselineHeight + 24, baselineHeight * 2));
    }

    static boolean needsClassicContainerSizeRepair(int blockWidth, int blockHeight,
        int parentWidth, int parentHeight)
    {
        return parentWidth > 0 && parentHeight > 0 && parentWidth <= 500 && parentHeight <= 600
            && (blockWidth > parentWidth + 4 || blockHeight > parentHeight + 4);
    }

    private void refreshClassicOrbsGroup()
    {
        if (classicGroupRefreshFrames <= 0)
        {
            return;
        }
        classicGroupRefreshFrames--;
        // Widget.revalidate() adjusts only the receiver. The game tab change
        // also recomputes the interface group (including child scroll/layout).
        // Restrict this to Orbs: never change selected tabs or run UI scripts.
        Widget orbs = client.getWidget(InterfaceID.Orbs.UNIVERSE);
        if (orbs == null)
        {
            return;
        }
        orbs.revalidateScroll();
        Widget parent = orbs.getParent();
        if (parent != null)
        {
            parent.revalidateScroll();
        }
    }

    private void restore()
    {
        boolean leavingSquare = activeBlock != null;
        snapshot = null;
        footerVisibility.reset();
        footerHiddenEdits.restore();
        footerWidgetIds.clear();
        locationInitialized = false;
        zoneName = "";
        currentLocation = null;
        worldMapEdits.restore();
        // Detach only our child before the original widget starts rendering again.
        nativeMap.remove();
        Widget previousBlock = activeBlock;
        ClassicMinimapBaseline previousBaseline = classicBaseline;
        if (!edits.isEmpty())
        {
            edits.restore();
            if (previousBlock != null)
            {
                revalidate(previousBlock);
            }
            Widget orbs = client.getWidget(InterfaceID.Orbs.UNIVERSE);
            if (orbs != null)
            {
                revalidate(orbs);
            }
        }
        // Last-write restoration for buttons whose baseline can otherwise drift
        // while interface scripts run during Square HUD mode.
        nativeControls.restore(client);
        // The real client can defer its native minimap layout when the Square HUD
        // switches off.  Revalidate from the native ancestors down: revalidating
        // only the modified children can leave MAP_CONTAINER sized as the full
        // canvas (rather than the actual minimap parent) until a later UI script.
        if (previousBlock != null)
        {
            revalidateClassicHierarchy(previousBlock, previousBaseline);
        }
        if (masks.restore(client.getSpriteOverrides()))
        {
            client.getWidgetSpriteCache().reset();
        }
        if (leavingSquare)
        {
            classicGroupRefreshFrames = 3;
        }
        activeBlock = null;
        classicBaseline = null;
        maskSprite = null;
        privateMaskId = -1;
    }
}
