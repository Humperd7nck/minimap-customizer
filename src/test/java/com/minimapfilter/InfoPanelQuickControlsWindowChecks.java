package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** OS mouse input over the actual drawer and popup classes; not a live RuneLite session. */
public final class InfoPanelQuickControlsWindowChecks
{
    private static Fixture f;
    private static Robot robot;
    private static int checks;
    public static void main(String[] args) throws Exception
    {
        robot = new Robot(); robot.setAutoDelay(25);
        SwingUtilities.invokeAndWait(() -> { f = new Fixture(); f.frame.setVisible(true); f.canvas.requestFocus(); f.tick.start(); });
        try
        {
            await(() -> f.canvas.isFocusOwner(), "canvas focus");
            click(center(f.view.layout.hammer)); click(center(f.view.layout.icons[0]));
            check(f.drawer.state().selected == InfoPanelText.TIMER, "select Timer");
            check(f.view.controls.buttons.size() == 2, "two square timer buttons");
            click(control(0)); check(f.openMode == null, "unset Play does not open editor");
            check(f.tools.snapshot().timerDuration == 0, "unset Play is inert");
            check(f.content(InfoPanelText.TIMER).text.equals("Set timer"), "unset label");
            click(center(f.view.controls.text)); await(() -> f.openMode == InfoPanelText.TIMER, "timer settings open");
            SwingUtilities.invokeAndWait(() -> ((JSpinner) component("timer-seconds")).setValue(10));
            clickComponent("timer-start");
            check(f.tools.snapshot().timerRunning, "set duration in real popup");
            escape(); await(() -> f.canvas.isFocusOwner(), "return from popup");
            f.time.addAndGet(1250); pause(80); click(control(0));
            check(!f.tools.snapshot().timerRunning && f.tools.snapshot().timerRemaining == 8750, "direct Pause exact remaining");
            check(f.view.controls.buttons.get(0).request.tooltip.equals("Resume timer"), "Pause becomes Resume");
            f.time.addAndGet(1000); click(control(0));
            check(f.tools.snapshot().timerRunning, "direct Resume");
            capture(args, "timer-controls.png");
            click(control(1));
            check(!f.tools.snapshot().timerRunning && f.tools.snapshot().timerRemaining == 10000, "Stop retains duration");
            check(f.view.controls.buttons.get(0).request.tooltip.equals("Start timer"), "Stop restores Start");
            click(control(0)); check(f.tools.snapshot().timerRunning, "Start reuses previous timer");
            // Replace the timer while a Stop press is held: its release must not cancel the new one.
            move(control(1)); press(); f.tools.startTimer(20); pause(80); release(); pause(60);
            check(f.tools.snapshot().timerRunning && f.tools.snapshot().timerDuration == 20000, "stale Stop canceled");
            click(center(f.view.layout.back)); click(center(f.view.layout.icons[2]));
            check(f.view.controls.buttons.size() == 3, "three square stopwatch controls");
            click(control(0)); f.time.addAndGet(1250); click(control(1));
            check(f.tools.snapshot().laps.size() == 1 && f.tools.snapshot().laps.get(0) == 1250, "Lap records actual model");
            click(center(f.view.controls.text)); await(() -> f.openMode == InfoPanelText.STOPWATCH, "stopwatch popup");
            pause(200);
            check(listSize("stopwatch-laps") == 1, "popup shows toolbar lap");
            click(control(1)); pause(200);
            check(f.openMode != null, "toolbar action does not close matching popup");
            check(listSize("stopwatch-laps") == 2, "popup updates from second toolbar lap");
            clickComponent("close-tool"); await(() -> f.openMode == null && f.canvas.isFocusOwner(), "return from stopwatch popup");
            click(control(0)); f.time.addAndGet(1000);
            long elapsed = f.tools.snapshot().stopwatchElapsed;
            click(control(1));
            check(f.tools.snapshot().laps.size() == 2 && f.tools.snapshot().stopwatchElapsed == elapsed, "paused Lap is inert");
            capture(args, "stopwatch-controls.png");
            click(control(0)); check(f.tools.snapshot().stopwatchRunning, "Resume stopwatch");
            click(control(2));
            check(f.tools.snapshot().stopwatchElapsed == 0 && !f.tools.snapshot().stopwatchRunning && f.tools.snapshot().laps.isEmpty(), "Reset stops and clears laps");
            check(f.openMode == null, "stopwatch buttons do not open windows");
            click(center(f.view.layout.back)); click(center(f.view.layout.icons[1]));
            f.reminders.alarm.arm(12, 30, InfoPanelAlarm.TimeZone.UTC, ZoneOffset.UTC, "Example"); pause(80);
            check(f.view.controls.buttons.size() == 1 && f.view.controls.buttons.get(0).request.enabled, "armed alarm Cancel enabled");
            capture(args, "alarm-controls.png");
            click(control(0)); check(!f.reminders.alarm.snapshot().armed, "Cancel alarm");
            check(f.openMode == null, "Cancel does not open a window");
            check(!f.view.controls.buttons.get(0).request.enabled, "Cancel becomes disabled");
            // Shared hover area and press/release cancellation with a transport button.
            click(center(f.view.layout.back)); click(center(f.view.layout.icons[2])); click(control(0));
            SwingUtilities.invokeAndWait(() -> f.mode = SquareHudFooterMode.MOUSEOVER_ONLY);
            move(new Point(550, 60)); pause(600); check(!f.shown, "hover-only hides both rows");
            move(control(0)); pause(180); check(f.shown, "hover over toolbar shows both");
            press(); move(new Point(550, 60)); pause(600); check(f.shown, "captured press holds hover panel");
            release(); pause(600);
            check(!f.shown && f.tools.snapshot().stopwatchRunning, "release outside cancels Pause, resumes hide");
            check(f.gameReleases == 0 && f.gameClicks == 0, "toolbar gestures never reach game-like handler");
            move(control(0)); pause(180); click(control(0));
            check(!f.tools.snapshot().stopwatchRunning, "same selected tool reappears and works");
            check(f.mainText.equals("World 330"), "main Info Panel unchanged");
            check(f.tools.snapshot().timerRunning, "switching tools did not stop Timer");
            System.out.println("Square tool-control real-window checks passed: " + checks);
        }
        finally { SwingUtilities.invokeAndWait(() -> { f.tick.stop(); f.popup.close(); f.frame.dispose(); }); }
    }
    private static Point control(int index) { return center(f.view.controls.buttons.get(index).bounds); }
    private static Point center(Rectangle r) { return new Point(r.x+r.width/2, r.y+r.height/2); }
    private static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    private static void pause(int ms) throws Exception { Thread.sleep(ms); SwingUtilities.invokeAndWait(() -> {}); }
    private static void await(BooleanSupplier condition,String why) throws Exception
    {
        long end=System.nanoTime()+3_000_000_000L;
        while(!condition.getAsBoolean() && System.nanoTime()<end) { pause(15); }
        if(!condition.getAsBoolean()) throw new AssertionError("No event: "+why);
    }
    private static void move(Point point) throws Exception
    {
        Point origin=f.canvas.getLocationOnScreen(); robot.mouseMove(origin.x+point.x,origin.y+point.y);
        await(() -> point.equals(f.mouse), "move "+point); pause(50);
    }
    private static void press() throws Exception { int old=f.presses;robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);await(() -> f.presses>old,"press"); }
    private static void release() throws Exception { int old=f.releases;robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);await(() -> f.releases>old,"release"); }
    private static void click(Point point) throws Exception { move(point);press();release();pause(100); }
    private static void escape() throws Exception { robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);pause(150); }
    private static int listSize(String name) throws Exception
    {
        int[] value={0}; SwingUtilities.invokeAndWait(() -> value[0]=((JList<?>)component(name)).getModel().getSize());
        return value[0];
    }
    private static Component component(String name)
    {
        for(Window w:Window.getWindows()) if(w instanceof JDialog && w.isShowing())
        { Component c=find((Container)w,name);if(c!=null)return c; }
        throw new AssertionError("Missing component "+name);
    }
    private static Component find(Container root,String name)
    {
        for(Component c:root.getComponents())
        { if(name.equals(c.getName()))return c; if(c instanceof Container){Component r=find((Container)c,name);if(r!=null)return r;} }
        return null;
    }
    private static void clickComponent(String name) throws Exception
    {
        Component[] result={null};SwingUtilities.invokeAndWait(() -> result[0]=component(name));
        Component c=result[0];Point origin=c.getLocationOnScreen();
        robot.mouseMove(origin.x+c.getWidth()/2,origin.y+c.getHeight()/2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);pause(150);
    }
    private static void capture(String[] args,String name) throws Exception
    {
        if(args.length==0)return;
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                BufferedImage image=new BufferedImage(233,88,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();g.translate(-10,-70);f.canvas.paint(g);g.dispose();
                File dir=new File(args[0]);dir.mkdirs();ImageIO.write(image,"png",new File(dir,name));
            }
            catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static final class Fixture
    {
        final JFrame frame=new JFrame("Square tool controls — isolated test canvas");
        final AtomicLong time=new AtomicLong();
        final InfoPanelTools tools=new InfoPanelTools(time::get);
        final InfoPanelReminders reminders=new InfoPanelReminders(tools,
            new InfoPanelAlarm(Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"),ZoneOffset.UTC)),message -> {});
        final InfoPanelQuickControls quick=new InfoPanelQuickControls(tools,reminders);
        final InfoPanelToolDrawer drawer=new InfoPanelToolDrawer();
        final InfoPanelNotebook notebook=InfoPanelNotebook.memory();
        final InfoPanelPopup popup=new InfoPanelPopup(tools,() -> openMode=null,() -> drawer.allows(this.openMode),reminders,() -> notebook);
        final SquareHudFooterVisibility visibility=new SquareHudFooterVisibility();
        final String mainText="World 330";
        volatile InfoPanelToolDrawer.View view;
        volatile InfoPanelText openMode;
        volatile boolean shown;
        volatile Point mouse=new Point(-1,-1);
        volatile SquareHudFooterMode mode=SquareHudFooterMode.ALWAYS_VISIBLE;
        volatile int presses,releases,gameReleases,gameClicks;
        final Canvas canvas=new Canvas(){@Override public void paint(Graphics graphics)
        {
            Graphics2D g=(Graphics2D)graphics.create();
            try
            {
                g.setColor(new Color(24,27,24));g.fillRect(0,0,getWidth(),getHeight());
                g.setFont(new Font(Font.DIALOG,Font.PLAIN,11));
                if(shown)
                {
                    Rectangle r=view.layout.footer;g.setColor(new Color(77,71,52));g.fillRect(r.x,r.y,r.width,r.height);
                    g.setColor(new Color(112,101,79));g.drawRect(r.x,r.y,r.width-1,r.height-1);
                    g.setColor(new Color(255,237,173));g.drawString("XP",27,101);g.drawString("Map",207,101);
                    InfoPanelDrawerPainter.centered(g,view.layout.primaryText,mainText,new Color(255,237,173));
                    InfoPanelDrawerPainter.paint(g,view,mouse,null,Fixture.this::content,null,false,openMode);
                }
            }
            finally{g.dispose();}
        }};
        final Timer tick=new Timer(20,event -> update());
        Fixture()
        {
            canvas.setPreferredSize(new Dimension(600,650));frame.add(canvas);frame.pack();frame.setLocation(60,40);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            MouseAdapter listener=new MouseAdapter()
            {
                @Override public void mouseMoved(MouseEvent e){mouse=e.getPoint();drawer.mouseMoved(e);update();}
                @Override public void mouseExited(MouseEvent e){mouse=new Point(-1,-1);update();}
                @Override public void mousePressed(MouseEvent e){presses++;update();drawer.mousePressed(e);}
                @Override public void mouseDragged(MouseEvent e){mouse=e.getPoint();drawer.mouseDragged(e);update();}
                @Override public void mouseReleased(MouseEvent e)
                {
                    releases++;update();InfoPanelToolDrawer.Action a=drawer.mouseReleased(e);
                    if(a!=null)
                    {
                        if(a.control!=null){quick.execute(a.control);}
                        else if(a.toggleTool && popup.isShowing() && openMode==a.tool){popup.close();}
                        else if(a.openTool){popup.open(a.tool,canvas,e.getLocationOnScreen());openMode=popup.isShowing()?a.tool:null;}
                        else if(popup.isShowing()){popup.close();}
                    }
                    if(!e.isConsumed())gameReleases++;update();
                }
                @Override public void mouseClicked(MouseEvent e){drawer.mouseClicked(e);if(!e.isConsumed())gameClicks++;}
            };
            canvas.addMouseListener(listener);canvas.addMouseMotionListener(listener);update();
        }
        void update()
        {
            InfoPanelToolDrawer.State state=drawer.state();
            InfoPanelToolDrawer.Layout layout=new InfoPanelToolDrawer.Layout(new Rectangle(20,80,213,34),
                new Rectangle(60,83,132,28),state.expanded,true,60);
            shown=visibility.update(mode,layout.reveal().contains(mouse),drawer.isHolding(),false,System.nanoTime()/1_000_000L);
            view=new InfoPanelToolDrawer.View(state,layout,shown,quick.view(state.selected,layout.content));
            drawer.publish(view,canvas.isFocusOwner()||popup.isShowing());canvas.repaint();
        }
        SquareHudInfoText.Content content(InfoPanelText tool)
        {
            if(tool==InfoPanelText.ALARM)
            {
                InfoPanelAlarm.Snapshot a=reminders.alarm.snapshot();
                return new SquareHudInfoText.Content(a.armed?"Alarm "+a.time():"Alarm: Off","Set alarm");
            }
            return InfoPanelFormatting.tool(tool,tools.snapshot());
        }
    }
}
