package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.input.MouseAdapter;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes with real RuneLite dependencies in the updater's Gradle suite. Client/audio are simulated. */
public class InfoPanelToolDrawerIntegrationTest
{
    @Test public void independentReadoutAndSharedToolState() throws Exception
    {
        try(Fixture f = new Fixture())
        {
            f.openDrawer();
            InfoPanelTools tools=(InfoPanelTools)field(InfoPanelService.class,"tools").get(f.service);
            tools.activate(); tools.startTimer(60); tools.startStopwatch();
            f.choose(0);
            assertEquals(InfoPanelText.ZONE,f.mode);
            assertTrue(f.service.content(InfoPanelText.TIMER).text.startsWith("Timer "));
            f.tap(center(f.view.drawer.layout.back)); f.frame();
            assertTrue(f.service.drawer().state().expanded); assertNull(f.service.drawer().state().selected);
            assertTrue(tools.snapshot().timerRunning); assertTrue(tools.snapshot().stopwatchRunning);
            f.choose(4); // Same notebook used by the main Info Panel and drawer.
            SwingUtilities.invokeAndWait(() -> {}); // This fixture's canvas is not a visible window.
            assertEquals("Notes",f.service.content(InfoPanelText.NOTES).text);
            f.footer=false;f.frame();assertNull(f.service.drawer().state().selected);
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED,center(f.view.drawer.layout.content),1).isConsumed());
            f.footer=true;f.frame();f.tap(center(f.view.drawer.layout.hammer));f.frame();
            assertFalse(f.service.drawer().state().expanded);
            assertTrue(tools.snapshot().timerRunning); assertTrue(tools.snapshot().stopwatchRunning);
            f.openDrawer();assertNull(f.service.drawer().state().selected);
        }
    }

    @Test public void directIconsToggleWithoutTheOutsideDismissHandler() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.openDrawer();
            for (int index : new int[]{3,4})
            {
                InfoPanelText tool = InfoPanelToolDrawer.TOOLS[index];
                // Pretend the real popup is already open. A toolbar press must not
                // run the outside-canvas dismissal before its release can toggle it.
                field(InfoPanelService.class,"popupMode").set(f.service,tool);
                field(InfoPanelService.class,"popupFromDrawer").setBoolean(f.service,true);
                field(InfoPanelService.class,"popupOpen").setBoolean(f.service,true);
                f.focused = false; // Native focus returns to the owner during the click.
                f.frame();
                java.awt.Point point = center(f.view.drawer.layout.icons[index]);
                assertTrue(f.send(MouseEvent.MOUSE_PRESSED,point,1).isConsumed());
                assertTrue("icon press must not eagerly dismiss",f.service.isPopupOpen());
                f.focused = true;
                assertTrue(f.send(MouseEvent.MOUSE_RELEASED,point,1).isConsumed());
                assertFalse("release toggles the matching popup off",f.service.isPopupOpen());
                assertTrue(f.send(MouseEvent.MOUSE_CLICKED,point,1).isConsumed());
                assertNull(f.service.drawer().state().selected);
                assertEquals(InfoPanelText.ZONE,f.mode);
                SwingUtilities.invokeAndWait(() -> {});
            }
        }
    }

    @Test public void drawerNativeVolumeAndBothViewsShareState() throws Exception
    {
        try(Fixture f=new Fixture())
        {
            f.openDrawer(); f.choose(5);
            InfoPanelVolumeBar bar=f.service.drawerVolumeBar();
            assertTrue(bar.isVisible());assertFalse(f.service.volumeBar().isVisible());
            assertEquals("Control volume",f.service.content(InfoPanelText.VOLUME).tooltip);
            f.tap(center(bar.layout().speaker));f.frame();assertEquals(0,f.nativeAudio.actual);
            f.mode=InfoPanelText.VOLUME;f.frame();
            assertTrue(f.service.volumeBar().isVisible());assertTrue(bar.isVisible());
            f.tap(f.slider(30));f.frame();assertEquals(0,f.nativeAudio.actual);
            f.tap(center(bar.layout().speaker));f.frame();assertTrue(Math.abs(f.nativeAudio.actual-30)<=1);
            assertEquals(23,f.nativeAudio.vars[168]);assertEquals(77,f.nativeAudio.vars[169]);assertEquals(39,f.nativeAudio.vars[872]);
            assertEquals(f.service.volumeBar().displayedPercent(),bar.displayedPercent());
            f.tap(center(f.view.drawer.layout.back));f.frame();
            assertTrue(f.service.volumeBar().isVisible());assertFalse(bar.isVisible());
            assertFalse(f.service.isPopupOpen());
        }
    }

    @Test public void canceledDrawerGesturesNeverWriteAudioOrClickThrough() throws Exception
    {
        for(String change:new String[]{"hidden","cover","focus","menu","profile","hop","classic"})
        {
            try(Fixture f=new Fixture())
            {
                f.openDrawer();f.choose(5);InfoPanelVolumeBar bar=f.service.drawerVolumeBar();
                java.awt.Point p=new java.awt.Point(bar.layout().position(20),bar.layout().cy);
                assertTrue(f.send(MouseEvent.MOUSE_PRESSED,p,1).isConsumed());
                switch(change)
                {
                    case "hidden":f.footer=false;break;
                    case "cover":f.paint=false;break;
                    case "focus":f.focused=false;break;
                    case "menu":f.menu=true;break;
                    case "profile":f.service.profileChanged();break;
                    case "hop":f.gameState=GameState.HOPPING;f.service.gameStateChanged(f.gameState);break;
                    case "classic":f.square=false;break;
                    default:throw new AssertionError(change);
                }
                f.frame(); assertTrue(change,f.send(MouseEvent.MOUSE_RELEASED,p,1).isConsumed());
                assertTrue(change,f.send(MouseEvent.MOUSE_CLICKED,p,1).isConsumed());
                f.frame();assertEquals(change,65,f.nativeAudio.actual);assertEquals(change,0,f.nativeAudio.applies);
            }
        }
    }
    @Test public void squareControlsUseTheServiceModelsWithoutOpeningOrClosingPopups() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            InfoPanelTools tools=(InfoPanelTools)field(InfoPanelService.class,"tools").get(f.service);
            InfoPanelReminders reminders=(InfoPanelReminders)field(InfoPanelService.class,"reminders").get(f.service);
            f.openDrawer(); f.choose(0);
            assertEquals("Set timer",f.service.content(InfoPanelText.TIMER).text);
            assertEquals("Set timer",f.service.content(InfoPanelText.TIMER).tooltip);
            assertEquals("Set alarm",f.service.content(InfoPanelText.ALARM).tooltip);
            assertEquals("Open stopwatch",f.service.content(InfoPanelText.STOPWATCH).tooltip);
            assertEquals("Control volume",InfoPanelFormatting.toolHover(InfoPanelText.VOLUME));
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertEquals(0L,tools.snapshot().timerDuration);
            assertFalse(f.service.isPopupOpen());
            tools.startTimer(60); f.frame();
            // A matching existing editor stays open when a transport button is used.
            field(InfoPanelService.class,"popupMode").set(f.service,InfoPanelText.TIMER);
            field(InfoPanelService.class,"popupFromDrawer").setBoolean(f.service,true);
            field(InfoPanelService.class,"popupOpen").setBoolean(f.service,true);
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertFalse(tools.snapshot().timerRunning);
            assertTrue(f.service.isPopupOpen());
            assertEquals(InfoPanelText.TIMER,f.service.openPopupMode());
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertTrue(tools.snapshot().timerRunning);
            f.tap(center(f.view.drawer.controls.buttons.get(1).bounds)); f.frame();
            assertFalse(tools.snapshot().timerRunning);
            assertEquals(60000L,tools.snapshot().timerRemaining);
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertTrue(tools.snapshot().timerRunning);
            f.tap(center(f.view.drawer.layout.back)); f.frame(); f.choose(2);
            assertFalse(f.service.isPopupOpen());
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertTrue(tools.snapshot().stopwatchRunning);
            f.tap(center(f.view.drawer.controls.buttons.get(1).bounds)); f.frame();
            assertEquals(1,tools.snapshot().laps.size());
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertFalse(tools.snapshot().stopwatchRunning);
            f.tap(center(f.view.drawer.controls.buttons.get(2).bounds)); f.frame();
            assertEquals(0L,tools.snapshot().stopwatchElapsed);
            assertTrue(tools.snapshot().laps.isEmpty());
            f.tap(center(f.view.drawer.layout.back)); f.frame(); f.choose(1);
            reminders.alarm.arm(12,0,InfoPanelAlarm.TimeZone.UTC,java.time.ZoneOffset.UTC,"Cancel me");
            reminders.test(InfoPanelText.TIMER); reminders.test(InfoPanelText.ALARM); f.frame();
            f.tap(center(f.view.drawer.controls.buttons.get(0).bounds)); f.frame();
            assertFalse(reminders.alarm.snapshot().armed);
            assertNull(reminders.notice(InfoPanelText.ALARM));
            assertNotNull(reminders.notice(InfoPanelText.TIMER));
            assertTrue(tools.snapshot().timerRunning);
            assertEquals(InfoPanelText.ZONE,f.mode);
            assertFalse(f.service.isPopupOpen());
        }
    }

    @Test public void squareControlGesturesCancelOnContextOrModelChanges() throws Exception
    {
        for (String change : new String[]{"hidden","cover","focus","menu","hop","classic","reset"})
        {
            try (Fixture f = new Fixture())
            {
                InfoPanelTools tools=(InfoPanelTools)field(InfoPanelService.class,"tools").get(f.service);
                tools.startTimer(60); f.openDrawer(); f.choose(0);
                java.awt.Point p=center(f.view.drawer.controls.buttons.get(1).bounds);
                assertTrue(f.send(MouseEvent.MOUSE_PRESSED,p,1).isConsumed());
                switch(change)
                {
                    case "hidden":f.footer=false;break;
                    case "cover":f.paint=false;break;
                    case "focus":f.focused=false;break;
                    case "menu":f.menu=true;break;
                    case "hop":f.gameState=GameState.HOPPING;f.service.gameStateChanged(f.gameState);break;
                    case "classic":f.square=false;break;
                    case "reset":tools.startTimer(60);break;
                    default:throw new AssertionError(change);
                }
                f.frame();
                assertTrue(change,f.send(MouseEvent.MOUSE_RELEASED,p,1).isConsumed());
                assertTrue(change,f.send(MouseEvent.MOUSE_CLICKED,p,1).isConsumed());
                assertTrue(change,tools.snapshot().timerRunning);
                assertFalse(f.service.isPopupOpen());
            }
        }
    }

    private static java.awt.Point center(Rectangle r){return new java.awt.Point(r.x+r.width/2,r.y+r.height/2);}
    private static final class Fixture implements AutoCloseable
    {
        boolean focused = true, menu, square = true, footer = true, paint = true;
        GameState gameState = GameState.LOGGED_IN;
        InfoPanelText mode = InfoPanelText.ZONE;
        final InfoPanelVolumeChecks.NativeAccess nativeAudio = new InfoPanelVolumeChecks.NativeAccess();
        final Canvas canvas = new Canvas() { @Override public boolean isFocusOwner() { return focused; } };
        final Player player = proxy(Player.class, (p, m, a) -> empty(m.getReturnType()));
        final MinimapFilterConfig config = new MinimapFilterConfig()
        {
            @Override public boolean enableSquareMinimap() { return square; }
            @Override public InfoPanelText infoPanelText() { return mode; }
        };
        final Client client = proxy(Client.class, (p, m, a) ->
        {
            switch (m.getName())
            {
                case "getGameState": return gameState;
                case "getLocalPlayer": return player;
                case "getCanvas": return canvas;
                case "isMenuOpen": return menu;
                case "getWorld": return 330;
                case "getCanvasWidth": return 800;
                case "getCanvasHeight": return 600;
                case "getIndexScripts": return Proxy.newProxyInstance(m.getReturnType().getClassLoader(), new Class<?>[]{m.getReturnType()},
                    (ip, im, ia) -> im.getName().equals("loadData") ? nativeAudio.script((int) ia[0]) : empty(im.getReturnType()));
                case "getVarps": return nativeAudio.vars;
                case "setIntStackSize": nativeAudio.size = (int) a[0]; return null;
                case "getIntStackSize": return nativeAudio.size;
                case "getIntStack": return new int[]{nativeAudio.top};
                case "queueChangedVarp": nativeAudio.changed((int) a[0]); return null;
                case "runScript":
                    Object[] args = (Object[]) a[0];
                    if (args.length == 1) { nativeAudio.run((int) args[0]); }
                    else { nativeAudio.run((int) args[0], (int) args[1]); }
                    return null;
                default: return empty(m.getReturnType());
            }
        });
        final InfoPanelService service = new InfoPanelService(client, config, null, null, null);
        final SquareHudController controller = new SquareHudController(client, null, config, null, null, () -> 0L);
        final MouseAdapter listener;
        SquareHudController.Snapshot view;
        Fixture() throws Exception
        {
            field(InfoPanelService.class, "running").setBoolean(service, true);
            field(SquareHudController.class, "started").setBoolean(controller, true);
            field(SquareHudController.class, "infoPanel").set(controller, service);
            listener = (MouseAdapter) field(SquareHudController.class, "compassMouse").get(controller);
        }
        void frame() throws Exception
        {
            SquareHudGeometry geometry = new SquareHudGeometry(0, 0);
            Rectangle foot = geometry.footer();
            Rectangle[] buttons = {new Rectangle(3, foot.y + 2, 34, 30), new Rectangle(176, foot.y + 2, 34, 30)};
            InfoPanelToolDrawer.State state = service.drawer().state();
            InfoPanelToolDrawer.View drawerView = new InfoPanelToolDrawer.View(state,
                new InfoPanelToolDrawer.Layout(foot, geometry.zoneText(buttons), state.expanded, true, 40), paint && footer,
                service.quickControls(state.selected, new InfoPanelToolDrawer.Layout(foot, geometry.zoneText(buttons),
                    state.expanded, true, 40).content));
            view = new SquareHudController.Snapshot(geometry, new int[4], new int[4], new BufferedImage[4], new boolean[4],
                new boolean[4], 0, false, false, 0, paint, false, footer, "", "", buttons, false, "", mode, drawerView);
            field(SquareHudController.class, "snapshot").set(controller, view);
            service.updateOnClientThread(); service.afterHudUpdate(view);
        }
        void openDrawer() throws Exception { frame(); tap(center(view.drawer.layout.hammer)); frame(); }
        void choose(int index) throws Exception { tap(center(view.drawer.layout.icons[index])); frame(); }
        InfoPanelVolumeBar bar() { return service.volumeBar(); }
        java.awt.Point speaker() { Rectangle r = bar().layout().speaker; return new java.awt.Point(r.x + 10, r.y + r.height / 2); }
        java.awt.Point slider(int value) { InfoPanelVolumeBar.Layout l = bar().layout(); return new java.awt.Point(l.position(value), l.cy); }
        MouseEvent event(int id, java.awt.Point point, int button)
        { return new MouseEvent(canvas, id, 1L, id == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : 0, point.x, point.y, point.x, point.y, 1, false, button); }
        MouseEvent send(int id, java.awt.Point point, int button)
        {
            MouseEvent e = event(id, point, button);
            switch (id)
            {
                case MouseEvent.MOUSE_PRESSED: listener.mousePressed(e); break;
                case MouseEvent.MOUSE_RELEASED: listener.mouseReleased(e); break;
                case MouseEvent.MOUSE_CLICKED: listener.mouseClicked(e); break;
                case MouseEvent.MOUSE_DRAGGED: listener.mouseDragged(e); break;
                default: throw new AssertionError(id);
            }
            return e;
        }
        void tap(java.awt.Point point) { send(MouseEvent.MOUSE_PRESSED, point, 1); send(MouseEvent.MOUSE_RELEASED, point, 1); send(MouseEvent.MOUSE_CLICKED, point, 1); }
        @Override public void close() throws Exception { service.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    private static Field field(Class<?> cls, String name) throws Exception { Field f = cls.getDeclaredField(name); f.setAccessible(true); return f; }
    private static <T> T proxy(Class<T> cls, java.lang.reflect.InvocationHandler handler)
    { return cls.cast(Proxy.newProxyInstance(cls.getClassLoader(), new Class<?>[]{cls}, handler)); }
    private static Object empty(Class<?> type)
    {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        if (type == float.class) { return 0F; }
        if (type == double.class) { return 0D; }
        return null;
    }
}
