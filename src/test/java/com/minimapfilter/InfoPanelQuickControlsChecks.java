package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** Exercises actual models, drawer gestures and rendering; no RuneLite/API test doubles. */
public final class InfoPanelQuickControlsChecks
{
    private static int checks;
    private static final Rectangle CONTENT = new Rectangle(25, 217, 177, 28);
    public static void main(String[] args) { System.out.println("Square tool-control checks passed: " + runAll()); }
    static int runAll()
    {
        checks = 0;
        labels(); timer(); stopwatch(); alarm(); revisions(); gestures(); layoutAndPainting();
        return checks;
    }
    private static void labels()
    {
        Fixture f = new Fixture();
        equal("fresh timer readout", "Set timer", f.content(InfoPanelText.TIMER).text);
        equal("timer tooltip", "Set timer", f.content(InfoPanelText.TIMER).tooltip);
        equal("stopwatch tooltip", "Open stopwatch", f.content(InfoPanelText.STOPWATCH).tooltip);
        equal("alarm tooltip", "Set alarm", InfoPanelFormatting.toolHover(InfoPanelText.ALARM));
        equal("volume tooltip", "Control volume", InfoPanelFormatting.toolHover(InfoPanelText.VOLUME));
        f.open();
        for (int i : new int[]{0, 1, 2, 5})
        {
            String expected = new String[]{"Set timer", "Set alarm", "Open stopwatch", "", "", "Control volume"}[i];
            equal("launcher hint " + i, expected, f.paint(center(f.view.layout.icons[i])));
        }
        f.choose(0);
        equal("timer readout hover", "Set timer", f.paint(center(f.view.controls.text)));
        InfoPanelToolDrawer.Action timerText = f.tap(center(f.view.controls.text));
        check(timerText.openTool && timerText.toggleTool, "timer readout toggles editor");
    }
    private static void timer()
    {
        Fixture f = new Fixture(); f.open(); f.choose(0);
        check(f.view.controls.buttons.size() == 2, "timer has two controls");
        check(!f.request(0).enabled && !f.request(1).enabled, "unset timer buttons disabled");
        check(f.click(0) == null, "disabled Start consumes without opening popup");
        equal("unset remains unset", 0L, f.tools.snapshot().timerDuration);
        f.tools.startTimer(10); f.frame();
        command("running timer", f.request(0), InfoPanelQuickControls.Command.TIMER_PAUSE);
        f.time.addAndGet(1250); f.click(0);
        equal("pause exact remaining", 8750L, f.tools.snapshot().timerRemaining);
        check(!f.tools.snapshot().timerRunning && f.tools.snapshot().timerPaused, "timer paused");
        command("paused timer", f.request(0), InfoPanelQuickControls.Command.TIMER_RESUME);
        f.time.addAndGet(10000); equal("paused time frozen", 8750L, f.tools.snapshot().timerRemaining);
        f.click(0); f.time.addAndGet(1000); equal("resume elapsed", 7750L, f.tools.snapshot().timerRemaining);
        f.reminders.test(InfoPanelText.TIMER); f.reminders.test(InfoPanelText.ALARM);
        f.click(1);
        check(!f.tools.snapshot().timerRunning && !f.tools.snapshot().timerPaused, "Stop stops instead of pausing");
        equal("Stop retains preset", 10000L, f.tools.snapshot().timerDuration);
        equal("Stop resets remaining for next Start", 10000L, f.tools.snapshot().timerRemaining);
        check(!f.tools.pollNotification(), "Stop clears pending completion");
        check(f.reminders.notice(InfoPanelText.TIMER) == null, "Stop clears timer notice");
        check(f.reminders.notice(InfoPanelText.ALARM) != null, "Stop leaves alarm notice");
        command("after Stop", f.request(0), InfoPanelQuickControls.Command.TIMER_START);
        check(!f.request(1).enabled, "Stop disabled when already stopped");
        f.click(0); check(f.tools.snapshot().timerRunning, "Start reuses preset without popup");
        f.time.addAndGet(10000); f.frame(); f.reminders.poll();
        check(f.tools.snapshot().timerFinished, "timer naturally finishes");
        equal("finished readout", "Timer finished", f.content(InfoPanelText.TIMER).text);
        check(f.reminders.notice(InfoPanelText.TIMER) != null, "normal completion banner preserved");
        f.click(0); check(f.tools.snapshot().timerRunning, "Start restarts completed countdown");
        check(f.reminders.notice(InfoPanelText.TIMER) == null, "restart dismisses only old timer banner");
        f.click(0); command("paused immediately at zero elapsed", f.request(0), InfoPanelQuickControls.Command.TIMER_RESUME);
        equal("no extra popup opens from controls", 0, f.opens);
    }
    private static void stopwatch()
    {
        Fixture f = new Fixture(); f.open(); f.choose(2);
        check(f.view.controls.buttons.size() == 3, "stopwatch has three controls");
        command("idle stopwatch", f.request(0), InfoPanelQuickControls.Command.STOPWATCH_START);
        check(!f.request(1).enabled && !f.request(2).enabled, "idle lap/reset disabled");
        f.click(0); f.time.addAndGet(1250); f.click(1);
        equal("lap recorded", 1250L, f.tools.snapshot().laps.get(0));
        check(f.tools.snapshot().stopwatchRunning, "Lap does not pause");
        f.click(0); long paused = f.tools.snapshot().stopwatchElapsed;
        f.time.addAndGet(12345);
        equal("paused stopwatch freezes", paused, f.tools.snapshot().stopwatchElapsed);
        check(!f.request(1).enabled && f.click(1) == null, "paused Lap disabled and inert");
        command("paused stopwatch", f.request(0), InfoPanelQuickControls.Command.STOPWATCH_RESUME);
        f.click(0); f.time.addAndGet(500); f.click(1);
        equal("resume keeps previous elapsed", 1750L, f.tools.snapshot().laps.get(1));
        for (int i = 2; i < InfoPanelTools.MAX_LAPS; i++) { f.click(1); }
        equal("lap cap", InfoPanelTools.MAX_LAPS, f.tools.snapshot().laps.size());
        check(!f.request(1).enabled && f.request(1).tooltip.equals("Lap limit reached"), "lap cap has clear disabled hint");
        f.click(2);
        equal("reset elapsed", 0L, f.tools.snapshot().stopwatchElapsed);
        check(!f.tools.snapshot().stopwatchRunning && f.tools.snapshot().laps.isEmpty(), "Reset stops and clears laps");
        f.click(0); f.click(0);
        command("zero-time pause still resumes", f.request(0), InfoPanelQuickControls.Command.STOPWATCH_RESUME);
        equal("stopwatch controls no popup", 0, f.opens);
    }
    private static void alarm()
    {
        Fixture f = new Fixture(); f.open(); f.choose(1);
        check(f.view.controls.buttons.size() == 1 && !f.request(0).enabled, "unset alarm cancel disabled");
        f.arm(); f.frame(); check(f.request(0).enabled, "armed cancel enabled");
        f.reminders.test(InfoPanelText.ALARM); f.reminders.test(InfoPanelText.TIMER);
        f.click(0);
        check(!f.alarm.snapshot().armed && f.alarm.snapshot().target == null, "Cancel disarms same alarm model");
        check(f.reminders.notice(InfoPanelText.ALARM) == null, "Cancel clears alarm notice");
        check(f.reminders.notice(InfoPanelText.TIMER) != null, "Cancel leaves timer notice");
        f.arm(); f.time.addAndGet(60000); f.reminders.poll(); f.frame();
        check(f.alarm.snapshot().fired && f.request(0).enabled, "fired alarm can be cleared");
        f.click(0); check(!f.alarm.snapshot().fired, "Cancel clears fired status");
        check(!f.alarm.pollNotification(), "Cancel suppresses pending alarm alert");
        equal("alarm controls no popup", 0, f.opens);
    }
    private static void revisions()
    {
        Fixture f = new Fixture();
        f.tools.startTimer(1);
        InfoPanelQuickControls.Request pause = f.quick.view(InfoPanelText.TIMER, CONTENT).buttons.get(0).request;
        f.time.addAndGet(1000);
        check(!f.quick.execute(pause), "expired timer cannot interpret old Pause as Start");
        check(f.tools.snapshot().timerFinished && f.tools.pollNotification(), "expiry remains a completion");
        f.tools.startTimer(60);
        InfoPanelQuickControls.Request stop = f.quick.view(InfoPanelText.TIMER, CONTENT).buttons.get(1).request;
        f.tools.startTimer(60);
        check(!f.quick.execute(stop) && f.tools.snapshot().timerRunning, "same-duration replacement invalidates old Stop");
        f.tools.startStopwatch();
        InfoPanelQuickControls.Request reset = f.quick.view(InfoPanelText.STOPWATCH, CONTENT).buttons.get(2).request;
        f.tools.resetStopwatch(); f.tools.startStopwatch();
        check(!f.quick.execute(reset), "reset and restart invalidates old Reset");
        f.arm(); InfoPanelQuickControls.Request cancel = f.quick.view(InfoPanelText.ALARM, CONTENT).buttons.get(0).request;
        f.arm(); check(!f.quick.execute(cancel), "rearm same target invalidates old Cancel");
        InfoPanelQuickControls.Request lap = f.quick.view(InfoPanelText.STOPWATCH, CONTENT).buttons.get(1).request;
        check(f.quick.execute(lap), "fresh Lap executes");
        check(!f.quick.execute(lap) && f.tools.snapshot().laps.size() == 1, "same request is not replayable");
        InfoPanelQuickControls.Request start = new Fixture().quick.view(InfoPanelText.STOPWATCH, CONTENT).buttons.get(0).request;
        Fixture inactive = new Fixture(); inactive.tools.suspend(); inactive.reminders.suspend();
        check(!inactive.quick.execute(start), "disabled model rejects command");
        f.tools.clearAll(); f.reminders.clear();
        check(!f.quick.execute(reset) && !f.quick.execute(cancel) && !f.quick.execute(stop), "profile clear rejects all old commands");
    }
    private static void gestures()
    {
        for (int index : new int[]{0, 1, 2})
        for (String reason : new String[]{"right", "middle", "shift", "consumed", "outside", "move", "releaseOutside", "focus", "hidden", "layout", "replacement", "multi"})
        {
            Fixture f = new Fixture(); f.tools.startTimer(60); f.tools.startStopwatch(); f.arm(); f.open(); f.choose(index);
            Point point = center(f.view.controls.buttons.get(0).bounds);
            int button = reason.equals("right") ? 3 : reason.equals("middle") ? 2 : 1;
            int mods = reason.equals("shift") ? InputEvent.SHIFT_DOWN_MASK : 0;
            MouseEvent down = f.event(MouseEvent.MOUSE_PRESSED, reason.equals("outside") ? new Point(0, 0) : point, button, mods);
            if (reason.equals("consumed")) { down.consume(); }
            boolean owned = f.drawer.mousePressed(down);
            if (reason.equals("move")) { f.drawer.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED, new Point(point.x + 15, point.y), 0, InputEvent.BUTTON1_DOWN_MASK)); }
            if (reason.equals("focus") || reason.equals("hidden")) { f.frame(!reason.equals("hidden"), !reason.equals("focus")); }
            if (reason.equals("layout")) { f.offset = 4; f.frame(); }
            if (reason.equals("replacement"))
            {
                if (index == 0) { f.tools.startTimer(60); }
                else if (index == 1) { f.arm(); }
                else { f.tools.resetStopwatch(); f.tools.startStopwatch(); }
                f.frame();
            }
            if (reason.equals("multi")) { f.drawer.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, point, 3, 0)); }
            MouseEvent up = f.event(MouseEvent.MOUSE_RELEASED, reason.equals("releaseOutside") ? new Point(0, 0) : point, button, mods);
            check(f.drawer.mouseReleased(up) == null, index + ": " + reason + " produces no command");
            check(up.isConsumed() == owned, index + ": " + reason + " owns release exactly when expected");
            check(f.drawer.mouseClicked(f.event(MouseEvent.MOUSE_CLICKED, point, button, 0)) == owned, "clicked ownership");
            check(f.tools.snapshot().timerRunning && f.tools.snapshot().stopwatchRunning && f.alarm.snapshot().armed, "models unchanged by rejected gestures");
        }
        Fixture f = new Fixture(); f.tools.startTimer(1); f.open(); f.choose(0);
        Point point = center(f.view.controls.buttons.get(0).bounds);
        f.drawer.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, point, 1, 0));
        f.time.addAndGet(1000); f.frame();
        check(f.drawer.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED, point, 1, 0)) == null, "expired button changes cancel held action");
        check(f.tools.snapshot().timerFinished, "timer not restarted by old click");
        f = new Fixture(); f.open(); f.choose(2);
        InfoPanelToolDrawer.Action action = f.click(0);
        check(action != null && action.control != null && !action.openTool && !action.toggleTool, "control is not a popup or navigation action");
        check(f.drawer.state().selected == InfoPanelText.STOPWATCH && f.drawer.state().expanded, "button does not switch drawer or top display");
    }
    private static void layoutAndPainting()
    {
        Fixture f = new Fixture();
        for (InfoPanelText tool : new InfoPanelText[]{InfoPanelText.TIMER, InfoPanelText.ALARM, InfoPanelText.STOPWATCH})
        for (int width : new int[]{0, 60, 100, 160, 177, 320})
        {
            Rectangle bounds = new Rectangle(500, 300, width, 28);
            InfoPanelQuickControls.View controls = f.quick.view(tool, bounds);
            check(bounds.equals(controls.text) || bounds.contains(controls.text), "text bounds stay inside content");
            for (int i = 0; i < controls.buttons.size(); i++)
            {
                Rectangle b = controls.buttons.get(i).bounds;
                check(b.width == 22 && b.height == 22, "action hitbox is square");
                check(bounds.contains(b) && !b.intersects(controls.text), "button stays clear of text/back");
                if (i > 0) { check(!b.intersects(controls.buttons.get(i - 1).bounds), "buttons disjoint"); }
            }
        }
        check(f.quick.view(InfoPanelText.STOPWATCH, new Rectangle(0, 0, 200, 10)).buttons.isEmpty(), "short layout has no invisible controls");
        for (InfoPanelText tool : new InfoPanelText[]{InfoPanelText.VOLUME, InfoPanelText.NOTES, InfoPanelText.CALCULATOR, InfoPanelText.ZONE})
        { check(f.quick.view(tool, CONTENT).buttons.isEmpty(), "non-transport tools unchanged"); }
        for (int index = 0; index < 3; index++)
        {
            f = new Fixture(); f.tools.startTimer(60); f.tools.startStopwatch(); f.arm(); f.open(); f.choose(index);
            for (InfoPanelQuickControls.Button b : f.view.controls.buttons)
            {
                equal("button hint", b.request.tooltip, f.paint(center(b.bounds)));
                BufferedImage image = f.image();
                // All four corners are opaque: no circle/invisible padding masquerading as a square.
                int x = b.bounds.x, y = b.bounds.y, d = b.bounds.width - 1;
                check(image.getRGB(x,y) == image.getRGB(x+d,y+d) && (image.getRGB(x,y) >>> 24) == 255, "square corners visibly painted");
            }
            equal("selected readout hint", InfoPanelFormatting.toolHover(InfoPanelToolDrawer.TOOLS[index]), f.paint(center(f.view.controls.text)));
            check(!f.view.controls.text.intersects(f.view.layout.back), "readout keeps back button clear");
        }
    }
    private static void command(String name, InfoPanelQuickControls.Request r, InfoPanelQuickControls.Command expected)
    { check(r.command == expected, name + " command is " + expected); }
    private static void equal(String name, Object expected, Object actual)
    { check(expected.equals(actual), name + ": expected " + expected + ", got " + actual); }
    private static void check(boolean ok, String what) { checks++; if (!ok) { throw new AssertionError(what); } }
    private static Point center(Rectangle r) { return new Point(r.x+r.width/2, r.y+r.height/2); }

    static final class Fixture
    {
        final AtomicLong time = new AtomicLong();
        final InfoPanelTools tools = new InfoPanelTools(time::get);
        final Clock clock = new Clock()
        {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.parse("2026-01-01T12:00:00Z").plusMillis(time.get()); }
        };
        final InfoPanelAlarm alarm = new InfoPanelAlarm(clock);
        final InfoPanelReminders reminders = new InfoPanelReminders(tools, alarm, message -> {});
        final InfoPanelQuickControls quick = new InfoPanelQuickControls(tools, reminders);
        final InfoPanelToolDrawer drawer = new InfoPanelToolDrawer();
        final Canvas canvas = new Canvas();
        InfoPanelToolDrawer.View view;
        int opens, offset;
        Fixture() { frame(); }
        void arm() { alarm.arm(12, 1, InfoPanelAlarm.TimeZone.UTC, ZoneOffset.UTC, "Test"); }
        void frame() { frame(true, true); }
        void frame(boolean visible, boolean focused)
        {
            InfoPanelToolDrawer.State state = drawer.state();
            InfoPanelToolDrawer.Layout layout = new InfoPanelToolDrawer.Layout(new Rectangle(20+offset,180,213,34),
                new Rectangle(60+offset,183,132,28),state.expanded,true,40);
            view = new InfoPanelToolDrawer.View(state,layout,visible,quick.view(state.selected,layout.content));
            drawer.publish(view,focused);
        }
        void open() { tap(center(view.layout.hammer)); frame(); }
        void choose(int index) { tap(center(view.layout.icons[index])); frame(); }
        InfoPanelQuickControls.Request request(int index) { return view.controls.buttons.get(index).request; }
        InfoPanelToolDrawer.Action click(int index) { InfoPanelToolDrawer.Action a = tap(center(view.controls.buttons.get(index).bounds)); frame(); return a; }
        InfoPanelToolDrawer.Action tap(Point point)
        {
            drawer.mousePressed(event(MouseEvent.MOUSE_PRESSED,point,1,0));
            InfoPanelToolDrawer.Action a = drawer.mouseReleased(event(MouseEvent.MOUSE_RELEASED,point,1,0));
            drawer.mouseClicked(event(MouseEvent.MOUSE_CLICKED,point,1,0));
            if (a != null && a.control != null) { check(quick.execute(a.control), "fresh action executes"); }
            else if (a != null && a.openTool) { opens++; }
            frame(); return a;
        }
        MouseEvent event(int id,Point p,int button,int modifiers)
        { return new MouseEvent(canvas,id,0,modifiers,p.x,p.y,p.x,p.y,1,false,button); }
        SquareHudInfoText.Content content(InfoPanelText mode)
        {
            if(mode == InfoPanelText.ALARM) { return new SquareHudInfoText.Content("Alarm 12:01",InfoPanelFormatting.toolHover(mode)); }
            return InfoPanelFormatting.tool(mode, tools.snapshot());
        }
        String paint(Point mouse)
        {
            BufferedImage image = new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
            try { g.setFont(new Font(Font.DIALOG,Font.PLAIN,11)); return InfoPanelDrawerPainter.paint(g,view,mouse,null,this::content,null,false); }
            finally { g.dispose(); }
        }
        BufferedImage image()
        {
            BufferedImage image = new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
            try { g.setFont(new Font(Font.DIALOG,Font.PLAIN,11)); InfoPanelDrawerPainter.paint(g,view,null,null,this::content,null,false); }
            finally { g.dispose(); }
            return image;
        }
    }
}
