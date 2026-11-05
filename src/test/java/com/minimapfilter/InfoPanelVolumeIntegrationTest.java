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

/** Full-build regression tests: actual service/controller code with a simulated client/audio backend. */
public class InfoPanelVolumeIntegrationTest
{
    @Test
    public void inlineAudioAndNativeButtonRouting() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.frame();
            assertEquals(65, f.bar().displayedPercent());
            assertEquals(0, f.nativeAudio.applies);
            java.awt.Point speaker = f.speaker();
            f.tap(speaker); f.frame();
            assertEquals(0, f.nativeAudio.actual);
            assertFalse(f.service.isPopupOpen());
            assertEquals(65, f.bar().displayedPercent());
            f.tap(f.slider(25)); f.frame();
            assertEquals(0, f.nativeAudio.actual);
            f.tap(speaker); f.frame();
            assertEquals(25, f.nativeAudio.actual);
            assertEquals(23, f.nativeAudio.vars[168]);
            assertEquals(77, f.nativeAudio.vars[169]);
            assertEquals(39, f.nativeAudio.vars[872]);
            assertTrue(f.send(MouseEvent.MOUSE_PRESSED, f.slider(25), 1).isConsumed());
            assertTrue(f.send(MouseEvent.MOUSE_DRAGGED, f.slider(65), 0).isConsumed());
            assertTrue(f.service.isVolumeDragging());
            assertEquals(25, f.nativeAudio.actual);
            assertTrue(f.send(MouseEvent.MOUSE_RELEASED, f.slider(65), 1).isConsumed());
            f.frame(); assertTrue(Math.abs(f.nativeAudio.actual - 65) <= 1);
            assertFalse(f.service.isVolumeDragging());
            assertTrue(f.send(MouseEvent.MOUSE_CLICKED, f.slider(65), 1).isConsumed());
            f.service.openFromClick(InfoPanelText.VOLUME, f.event(MouseEvent.MOUSE_CLICKED, speaker, 1));
            assertFalse(f.service.isPopupOpen());
            Rectangle xp = f.view.footerButtons[0], map = f.view.footerButtons[1];
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED, new java.awt.Point(xp.x + 10, xp.y + 10), 1).isConsumed());
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED, new java.awt.Point(map.x + 10, map.y + 10), 1).isConsumed());
            f.menu = true;
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED, speaker, 1).isConsumed());
            f.menu = false;
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED, new java.awt.Point(500, 500), 1).isConsumed());
        }
    }

    @Test
    public void currentFrameContextCancelsPendingAudio() throws Exception
    {
        for (String change : new String[]{"mode", "footer", "cover", "focus", "menu", "classic", "hop", "profile", "stop"})
        {
            try (Fixture f = new Fixture())
            {
                f.frame();
                f.send(MouseEvent.MOUSE_PRESSED, f.slider(25), 1);
                f.send(MouseEvent.MOUSE_RELEASED, f.slider(25), 1); // queued, not yet applied
                switch (change)
                {
                    case "mode": f.mode = InfoPanelText.ZONE; break;
                    case "footer": f.footer = false; break;
                    case "cover": f.paint = false; break;
                    case "focus": f.focused = false; break;
                    case "menu": f.menu = true; break;
                    case "classic": f.square = false; break;
                    case "hop": f.gameState = GameState.HOPPING; f.service.gameStateChanged(f.gameState); break;
                    case "profile": f.service.profileChanged(); break;
                    case "stop": f.service.stop(); break;
                    default: throw new AssertionError(change);
                }
                f.frame();
                assertEquals(change, 65, f.nativeAudio.actual);
                assertEquals(change, 0, f.nativeAudio.applies);
                assertFalse(change, f.service.isVolumeDragging());
            }
        }
        try (Fixture f = new Fixture())
        {
            f.frame(); f.tap(f.slider(25));
            f.nativeAudio.actual = 42; f.nativeAudio.vars[3796] = 42;
            f.frame();
            assertEquals(42, f.nativeAudio.actual); assertEquals(0, f.nativeAudio.applies);
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        boolean focused = true, menu, square = true, footer = true, paint = true;
        GameState gameState = GameState.LOGGED_IN;
        InfoPanelText mode = InfoPanelText.VOLUME;
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
            view = new SquareHudController.Snapshot(geometry, new int[4], new int[4], new BufferedImage[4], new boolean[4],
                new boolean[4], 0, false, false, 0, paint, false, footer, "", "", buttons, false, "", mode);
            field(SquareHudController.class, "snapshot").set(controller, view);
            service.updateOnClientThread(); service.afterHudUpdate(view);
        }
        InfoPanelVolumeBar bar() { return service.volumeBar(); }
        java.awt.Point speaker() { Rectangle r = bar().layout().speaker; return new java.awt.Point(r.x + 10, r.y + r.height / 2); }
        java.awt.Point slider(int value) { InfoPanelVolumeBar.Layout l = bar().layout(); return new java.awt.Point(l.position(value), l.cy); }
        MouseEvent event(int id, java.awt.Point point, int button)
        { return new MouseEvent(canvas, id, 1L, id == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : 0, point.x, point.y, 1, false, button); }
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
