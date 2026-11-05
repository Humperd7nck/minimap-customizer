package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Independent checks of production inline layout, input ownership and model integration. */
public final class InfoPanelVolumeBarChecks
{
    private int checks;
    public static void main(String[] args) { System.out.println("Inline Volume checks passed: " + runAll()); }
    public static int runAll()
    {
        InfoPanelVolumeBarChecks t = new InfoPanelVolumeBarChecks();
        t.layout(); t.thumbNoJump(); t.speakerAndSlider(); t.drag(); t.cancellation(); t.exclusions(); t.errors(); t.paint(); t.hover();
        return t.checks;
    }
    private void check(String name, boolean ok) { checks++; if (!ok) { throw new AssertionError(name); } }
    static final class Fixture
    {
        final Canvas canvas = new Canvas();
        final InfoPanelVolume model = new InfoPanelVolume();
        final InfoPanelVolumeChecks.Audio audio = new InfoPanelVolumeChecks.Audio();
        final InfoPanelVolumeBar bar = new InfoPanelVolumeBar(model);
        final Rectangle region = new Rectangle(40, 10, 132, 28);
        long now;
        Fixture() { tick(); }
        void tick()
        {
            bar.update(region, true, true);
            model.tick(audio, true, true, now += 300);
            bar.update(region, true, true);
        }
        Point speaker() { Rectangle r = bar.layout().speaker; return new Point(r.x + 10, r.y + r.height / 2); }
        Point slider(int value) { InfoPanelVolumeBar.Layout l = bar.layout(); return new Point(l.position(value), l.cy); }
        MouseEvent event(int id, Point point, int button, int mods)
        { return new MouseEvent(canvas, id, 1L, mods, point.x, point.y, 1, false, button); }
        MouseEvent press(Point point, int button, int mods)
        { MouseEvent e = event(MouseEvent.MOUSE_PRESSED, point, button, mods); bar.mousePressed(e); return e; }
        MouseEvent release(Point point, int button, int mods)
        { MouseEvent e = event(MouseEvent.MOUSE_RELEASED, point, button, mods); bar.mouseReleased(e); return e; }
        MouseEvent click(Point point, int button)
        { MouseEvent e = event(MouseEvent.MOUSE_CLICKED, point, button, 0); bar.mouseClicked(e); return e; }
        MouseEvent drag(Point point, int mods)
        { MouseEvent e = event(MouseEvent.MOUSE_DRAGGED, point, MouseEvent.NOBUTTON, mods); bar.mouseDragged(e); return e; }
        void tap(Point point) { press(point, 1, 0); release(point, 1, 0); click(point, 1); tick(); }
    }

    private void layout()
    {
        for (int width : new int[] {60, 93, 132, 200, 270})
        {
            InfoPanelVolumeBar.Layout l = new InfoPanelVolumeBar.Layout(new Rectangle(40, 10, width, 28));
            check("layout fits " + width, l.usable() && l.bounds.contains(l.speaker) && l.bounds.contains(l.slider));
            check("speaker and track separate " + width, !l.speaker.intersects(l.slider));
            int previous = -1;
            for (int value = 0; value <= 100; value++)
            {
                check("thumb inside free region " + width + ":" + value, l.bounds.contains(l.thumb(value)));
                int mapped = l.value(l.position(value));
                check("monotonic quantized slider " + width + ":" + value,
                    mapped >= previous && mapped >= 0 && mapped <= 100);
                previous = mapped;
            }
            check("end stops exact " + width, l.value(Integer.MIN_VALUE) == 0 && l.value(Integer.MAX_VALUE) == 100);
        }
        check("too small disabled", !new InfoPanelVolumeBar.Layout(new Rectangle(0, 0, 59, 28)).usable());
        check("too short disabled", !new InfoPanelVolumeBar.Layout(new Rectangle(0, 0, 132, 19)).usable());
        check("null geometry disabled", !new InfoPanelVolumeBar.Layout(null).usable());
        SquareHudGeometry hud = new SquareHudGeometry(20, 20);
        Rectangle footer = hud.footer();
        Rectangle xp = new Rectangle(footer.x + 3, footer.y + 2, 34, 30);
        Rectangle map = new Rectangle(footer.x + footer.width - 37, footer.y + 2, 34, 30);
        InfoPanelVolumeBar.Layout l = new InfoPanelVolumeBar.Layout(hud.zoneText(new Rectangle[]{xp, map}));
        check("XP untouched", !l.bounds.intersects(xp)); check("World Map untouched", !l.bounds.intersects(map));
        check("volume keeps footer dimensions", footer.equals(hud.footer()));
    }
    private void thumbNoJump()
    {
        for (int value = 1; value <= 100; value++)
        {
            Fixture f = new Fixture(); f.audio.actual = value; f.tick();
            Point thumb = f.slider(value);
            f.press(thumb, 1, 0); f.release(thumb, 1, 0); f.tick();
            check("stationary thumb doesn't quantize " + value, f.audio.actual == value && f.audio.writes == 0);
        }
    }
    private void speakerAndSlider()
    {
        Fixture f = new Fixture();
        check("selection is read only", f.audio.actual == 65 && f.audio.writes == 0);
        check("speaker tooltip", f.bar.tooltip(f.speaker()).equals("Mute master volume"));
        check("slider tooltip", f.bar.tooltip(f.slider(65)).equals("Master volume: 65%"));
        int reads = f.audio.reads;
        check("speaker press owned", f.press(f.speaker(), 1, 0).isConsumed());
        check("no native operation on press", f.audio.reads == reads && f.audio.writes == 0);
        check("speaker release owned", f.release(f.speaker(), 1, 0).isConsumed());
        check("speaker release queues only", f.audio.writes == 0 && f.model.snapshot().pending);
        check("speaker click swallowed", f.click(f.speaker(), 1).isConsumed());
        f.tick(); check("speaker mutes once", f.audio.actual == 0 && f.audio.writes == 1);
        check("restore level remembered", f.model.snapshot().selected == 65);
        check("unmute tooltip", f.bar.tooltip(f.speaker()).equals("Unmute master volume"));
        f.tap(f.slider(25)); check("muted slider stages without sound", f.audio.actual == 0 && f.model.snapshot().selected == 25);
        check("staged tooltip", f.bar.tooltip(f.slider(25)).contains("Unmute at 25%"));
        f.tap(f.speaker()); check("speaker restores selected level", f.audio.actual == 25);
        f.tap(f.slider(100)); check("max track click", f.audio.actual == 100);
        f.tap(f.slider(0)); check("zero track click mutes", f.audio.actual == 0 && f.model.snapshot().selected == 100);
        f.tap(f.speaker()); check("zero preserves previous level", f.audio.actual == 100);
        f.audio.actual = 0; f.model.reset(); f.tick();
        f.tap(f.speaker()); check("unknown mute restore doesn't invent loudness", f.audio.actual == 0);
        check("zero restore explains next step", f.bar.tooltip(f.speaker()).contains("Choose a level"));
        f.tap(f.slider(25)); f.tap(f.speaker()); check("fresh muted level can be selected then unmuted", f.audio.actual == 25);
        f.audio.actual = 42; f.tick(); check("native changes followed", f.bar.displayedPercent() == 42);
    }
    private void drag()
    {
        Fixture f = new Fixture(); Point thumb = f.slider(65);
        f.press(new Point(thumb.x + 2, thumb.y), 1, 0);
        check("thumb offset preserved on press", f.bar.displayedPercent() == f.bar.layout().value(thumb.x));
        int reads = f.audio.reads;
        Point target = new Point(f.slider(25).x + 2, thumb.y);
        check("drag owned", f.drag(target, InputEvent.BUTTON1_DOWN_MASK).isConsumed());
        check("preview follows cursor", f.bar.displayedPercent() == 25);
        check("no native API from drag", f.audio.reads == reads && f.audio.writes == 0);
        check("hover bar pinned", f.bar.isHolding());
        f.release(target, 1, 0); f.tick();
        check("release applies final level", f.audio.actual == 25 && f.audio.writes == 1);
        check("released bar unpinned", !f.bar.isHolding());
        f.press(f.slider(25), 1, 0);
        Point outside = new Point(1500, 300);
        check("outside drag captured", f.drag(outside, InputEvent.BUTTON1_DOWN_MASK).isConsumed());
        check("outside clamps max", f.bar.displayedPercent() == 100);
        check("outside release captured", f.release(outside, 1, 0).isConsumed());
        f.tick(); check("outside release applies clamp", f.audio.actual == 100);
        check("outside clicked captured", f.click(outside, 1).isConsumed());
        f.press(f.speaker(), 1, 0); f.drag(f.slider(50), InputEvent.BUTTON1_DOWN_MASK);
        f.release(f.speaker(), 1, 0); f.tick(); check("dragging speaker doesn't toggle", f.audio.actual == 100);
    }
    private void cancellation()
    {
        for (String reason : new String[]{"hidden", "mode", "focus", "menu", "resize", "suspend", "external", "consumed"})
        {
            Fixture f = new Fixture(); f.press(f.slider(65), 1, 0); f.drag(f.slider(25), InputEvent.BUTTON1_DOWN_MASK);
            if (reason.equals("hidden") || reason.equals("mode")) { f.bar.update(f.region, false, false); }
            if (reason.equals("focus") || reason.equals("menu")) { f.bar.update(f.region, true, false); }
            if (reason.equals("resize")) { Rectangle changed = new Rectangle(f.region); changed.x++; f.bar.update(changed, true, true); }
            if (reason.equals("suspend")) { f.bar.suspend(); }
            if (reason.equals("external")) { f.audio.actual = 31; f.tick(); }
            if (reason.equals("consumed"))
            {
                MouseEvent e = f.event(MouseEvent.MOUSE_DRAGGED, f.slider(25), 0, InputEvent.BUTTON1_DOWN_MASK);
                e.consume(); f.bar.mouseDragged(e);
            }
            check(reason + " release swallowed", f.release(f.slider(25), 1, 0).isConsumed());
            check(reason + " click swallowed", f.click(f.slider(25), 1).isConsumed());
            f.tick(); check(reason + " no writes", f.audio.writes == 0);
            check(reason + " no held gesture", !f.bar.isHolding());
        }
        Fixture f = new Fixture(); f.press(f.slider(25), 1, 0); f.release(f.slider(25), 1, 0);
        f.bar.update(f.region, true, false); f.tick();
        check("queued release canceled on focus/menu loss", f.audio.writes == 0);
        f.press(f.slider(25), 1, 0); f.release(f.slider(25), 1, 0); f.audio.actual = 17; f.tick();
        check("native change after release rejects stale target", f.audio.actual == 17 && f.audio.writes == 0);
        f.press(f.slider(17), 1, 0);
        f.bar.mouseMoved(f.event(MouseEvent.MOUSE_MOVED, f.slider(17), 0, 0));
        check("missing release recovered", !f.bar.isHolding());
        f.release(f.slider(25), 1, 0); f.tick(); check("missing release doesn't apply", f.audio.writes == 0);
    }
    private void exclusions()
    {
        for (int button : new int[]{2, 3})
        {
            Fixture f = new Fixture();
            check("nonprimary owned " + button, f.press(f.speaker(), button, 0).isConsumed());
            f.release(f.speaker(), button, 0); f.click(f.speaker(), button); f.tick();
            check("nonprimary harmless " + button, f.audio.writes == 0);
        }
        for (int mods : new int[]{InputEvent.ALT_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK})
        {
            Fixture f = new Fixture(); f.press(f.slider(25), 1, mods); f.release(f.slider(25), 1, mods); f.tick();
            check("modifier has no action " + mods, f.audio.writes == 0);
        }
        Fixture f = new Fixture(); Point outside = new Point(1, 1);
        check("world/XP press not captured", !f.press(outside, 1, 0).isConsumed());
        check("world drag crossing bar not captured", !f.drag(f.slider(25), InputEvent.BUTTON1_DOWN_MASK).isConsumed());
        check("world release in bar not captured", !f.release(f.slider(25), 1, 0).isConsumed());
        check("world click in bar not captured", !f.click(f.slider(25), 1).isConsumed());
        MouseEvent used = f.event(MouseEvent.MOUSE_PRESSED, f.slider(25), 1, 0); used.consume();
        check("earlier listener owns consumed press", !f.bar.mousePressed(used));
        f.press(f.slider(65), 1, 0); f.press(f.speaker(), 3, 0);
        f.release(f.slider(25), 1, 0); f.release(f.speaker(), 3, 0); f.tick();
        check("second button cancels first gesture", f.audio.writes == 0);
        f.bar.update(f.region, false, false);
        check("hidden bar doesn't catch world clicks", !f.press(f.slider(25), 1, 0).isConsumed());
        f.bar.update(f.region, true, false);
        check("open native menu gets click", !f.press(f.speaker(), 1, 0).isConsumed());
    }
    private void errors()
    {
        Fixture f = new Fixture(); f.audio.error = true; f.tick();
        check("unavailable tooltip", f.bar.tooltip(f.speaker()).equals("Unavailable for test"));
        f.tap(f.speaker()); f.tap(f.slider(50)); check("unavailable controls do nothing", f.audio.writes == 0);
        f.audio.error = false; f.tick(); check("recovered model returns", f.bar.displayedPercent() == 65);
        check("volume no popup", !InfoPanelText.VOLUME.isTool());
    }
    private void paint()
    {
        Fixture f = new Fixture();
        for (int value : new int[]{0, 1, 25, 50, 99, 100})
        {
            f.audio.actual = value; f.tick();
            BufferedImage image = new BufferedImage(220, 60, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics(); f.bar.paint(g, f.slider(value)); g.dispose();
            boolean inside = false, outside = false;
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++)
                {
                    if ((image.getRGB(x, y) >>> 24) == 0) { continue; }
                    if (f.region.contains(x, y)) { inside = true; } else { outside = true; }
                }
            }
            check("painted usable bar " + value, inside); check("no button overlap " + value, !outside);
        }
        f.bar.suspend(); BufferedImage blank = new BufferedImage(220, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = blank.createGraphics(); f.bar.paint(g, f.speaker()); g.dispose();
        boolean painted = false;
        for (int value : blank.getRGB(0, 0, 220, 60, null, 0, 220)) { painted |= value != 0; }
        check("hidden mode paints nothing", !painted);
    }
    private void hover()
    {
        Fixture f = new Fixture(); SquareHudFooterVisibility v = new SquareHudFooterVisibility();
        v.update(SquareHudFooterMode.MOUSEOVER_ONLY, true, false, false, 0);
        check("hover revealed", v.update(SquareHudFooterMode.MOUSEOVER_ONLY, true, false, false, 100));
        f.press(f.slider(65), 1, 0); f.drag(new Point(600, 600), InputEvent.BUTTON1_DOWN_MASK);
        check("hover stays during drag outside", v.update(SquareHudFooterMode.MOUSEOVER_ONLY, false, f.bar.isHolding(), false, 1000));
        check("cover overrides pinned bar", !v.update(SquareHudFooterMode.MOUSEOVER_ONLY, false, f.bar.isHolding(), true, 1100));
        f.bar.update(f.region, false, false); f.release(new Point(600, 600), 1, 0); f.tick();
        check("cover canceled pending gesture", f.audio.writes == 0);
    }
}
