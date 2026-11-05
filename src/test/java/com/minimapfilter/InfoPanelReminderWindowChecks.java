package com.minimapfilter;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** OS-window tests with real mouse/keyboard input; notifications/audio are simulated. */
public final class InfoPanelReminderWindowChecks
{
    private final long[] elapsed={0};
    private final InfoPanelTools tools=new InfoPanelTools(() -> elapsed[0]);
    private final InfoPanelReminderChecks.MutableClock wall=new InfoPanelReminderChecks.MutableClock();
    private final AtomicInteger deliveries=new AtomicInteger(),canvasKeys=new AtomicInteger();
    private final InfoPanelReminders reminders=new InfoPanelReminders(tools,new InfoPanelAlarm(wall),s -> deliveries.incrementAndGet());
    private JFrame frame;
    private JPanel canvas;
    private InfoPanelPopup popup;
    private Robot robot;
    private InfoPanelNotebookStore store;
    private InfoPanelNotebookStore.Session session;
    private int checks;
    private Path output;

    public static void main(String[] args) throws Exception
    {
        InfoPanelReminderWindowChecks t=new InfoPanelReminderWindowChecks();
        t.run(Path.of(args.length==0 ? "build/reminder-smoke" : args[0]));
        System.out.println("Reminder real-window checks passed: "+t.checks);
    }
    private void check(String message,boolean ok) { checks++; if(!ok) { throw new AssertionError(message); } }
    private void run(Path destination) throws Exception
    {
        output=destination; Files.createDirectories(output); Path dir=Files.createTempDirectory("minimap-notes-window");
        store=new InfoPanelNotebookStore(dir); session=store.open(1);
        InfoPanelReminderChecks.waitFor(() -> session.notebook.snapshot().loaded);
        robot=new Robot(); robot.setAutoDelay(35);
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame=new JFrame("Standalone Minimap Customizer test canvas - simulated notifications");
                canvas=new JPanel()
                {
                    @Override protected void paintComponent(Graphics g)
                    { super.paintComponent(g); InfoPanelReminderBanner.paint((Graphics2D)g,getWidth(),reminders.notices()); }
                };
                canvas.setFocusable(true); canvas.setBackground(new java.awt.Color(43,46,49));
                canvas.addKeyListener(new KeyAdapter() { @Override public void keyTyped(KeyEvent e) { canvasKeys.incrementAndGet(); } });
                frame.setContentPane(canvas); frame.setSize(760,610); frame.setLocation(40,30); frame.setVisible(true);
                popup=new InfoPanelPopup(tools,() -> {},() -> true,reminders,() -> session.notebook);
            });
            settle(); open(InfoPanelText.NOTES);
            check("Notes window opens",read(() -> popup.isShowing()));
            click(component("notes-text")); type("hello notes"); settle();
            check("real typed notes captured",session.notebook.snapshot().notes.equals("hello notes"));
            check("typing did not reach canvas",canvasKeys.get()==0);
            capture("notes-window.png");
            JTabbedPane tabs=(JTabbedPane)component("notes-tabs");
            Point tab=read(() -> { Point p=tabs.getLocationOnScreen(); Rectangle r=tabs.getBoundsAt(1); return new Point(p.x+r.x+r.width/2,p.y+r.y+r.height/2); });
            click(tab); settle(); click(component("notes-task-input")); type("buy supplies"); press(KeyEvent.VK_ENTER); settle();
            check("Enter adds checklist task",session.notebook.snapshot().tasks.size()==1);
            JList<?> list=(JList<?>)component("notes-tasks");
            Point box=read(() -> { Point p=list.getLocationOnScreen(); return new Point(p.x+12,p.y+13); });
            click(box); settle(); check("checkbox click toggles completed",session.notebook.snapshot().completed()==1);
            press(KeyEvent.VK_SPACE); settle(); check("Space toggles checklist",session.notebook.snapshot().completed()==0);
            click(box); settle(); capture("checklist-window.png");
            drag("Notes");
            click(component("close-tool")); settle(); check("Close disposes Notes",!read(popup::isShowing));
            store.release(session); session=store.open(1); InfoPanelReminderChecks.waitFor(() -> session.notebook.snapshot().loaded);
            check("notes persisted on disk",session.notebook.snapshot().notes.equals("hello notes"));
            check("checked task persisted on disk",session.notebook.snapshot().completed()==1);
            open(InfoPanelText.NOTES); check("reopen restores text",read(() -> ((JTextArea)componentUnchecked("notes-text")).getText().equals("hello notes")));
            press(KeyEvent.VK_ESCAPE); settle(); check("Notes Escape closes",!read(popup::isShowing));
            open(InfoPanelText.ALARM);
            SwingUtilities.invokeAndWait(() ->
            {
                ((JSpinner)componentUnchecked("alarm-hours")).setValue(12);
                ((JSpinner)componentUnchecked("alarm-minutes")).setValue(1);
                ((JComboBox<?>)componentUnchecked("alarm-zone")).setSelectedItem(InfoPanelAlarm.TimeZone.UTC);
            });
            click(component("alarm-label")); type("meeting"); click(component("alarm-set")); settle();
            check("real Set alarm button arms",reminders.alarm.snapshot().armed);
            check("UTC target correct",reminders.alarm.snapshot().target.equals(java.time.Instant.parse("2026-10-29T12:01:00Z")));
            check("next firing date visible",read(() -> ((JLabel)componentUnchecked("alarm-next")).getText().contains("2026")));
            capture("alarm-window.png"); drag("Alarm");
            click(component("close-tool")); settle(); check("closing alarm keeps it armed",reminders.alarm.snapshot().armed);
            wall.now=wall.now.plusSeconds(60); reminders.poll(); SwingUtilities.invokeAndWait(canvas::repaint); settle();
            check("closed alarm delivered once",deliveries.get()==1&&reminders.notice(InfoPanelText.ALARM)!=null);
            reminders.poll(); check("no duplicate while closed",deliveries.get()==1);
            captureCanvas("completion-banner.png"); open(InfoPanelText.ALARM);
            check("finished readout visible",read(() -> ((JLabel)componentUnchecked("alarm-display")).getText().equals("Alarm finished")));
            check("Alarm Test alert button absent",read(() -> !componentExistsUnchecked("alarm-test")));
            check("Alarm Dismiss alert button absent",read(() -> !componentExistsUnchecked("alarm-dismiss")));
            reminders.dismiss(InfoPanelText.ALARM); settle(); check("alarm notice still dismissible through banner/model",reminders.notices().isEmpty());
            click(component("alarm-cancel")); settle(); check("Cancel clears alarm and banner",reminders.notices().isEmpty()&&reminders.alarm.snapshot().target==null);
            press(KeyEvent.VK_ESCAPE); settle(); open(InfoPanelText.TIMER);
            SwingUtilities.invokeAndWait(() -> ((JSpinner)componentUnchecked("timer-seconds")).setValue(1));
            click(component("timer-start")); settle(); elapsed[0]=1000; reminders.poll(); settle();
            check("timer completion route delivered",deliveries.get()==2&&reminders.notice(InfoPanelText.TIMER)!=null);
            check("Timer finished status visible",read(() -> ((JLabel)componentUnchecked("timer-status")).getText().equals("Finished")));
            check("Timer Test alert button absent",read(() -> !componentExistsUnchecked("timer-test")));
            check("Timer Dismiss alert button absent",read(() -> !componentExistsUnchecked("timer-dismiss")));
            capture("timer-alert-window.png"); reminders.dismiss(InfoPanelText.TIMER); settle();
            check("Timer notice remains dismissible through banner/model",reminders.notices().isEmpty());
            check("no keyboard input reached game-like canvas",canvasKeys.get()==0);
            checkBounds(); press(KeyEvent.VK_ESCAPE); settle();
            check("Timer closes with Escape",!read(popup::isShowing));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> { if(popup!=null) { popup.close(); } if(frame!=null) { frame.dispose(); } });
            store.close(); check("notebook worker shuts down",store.awaitClosed(3000));
            try(java.util.stream.Stream<Path> paths=Files.walk(dir))
            { for(Path f:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator) { Files.deleteIfExists(f); } }
        }
    }
    private void open(InfoPanelText mode) throws Exception
    { SwingUtilities.invokeAndWait(() -> popup.open(mode,canvas,new Point(690,170))); settle(); }
    private Component component(String name) throws Exception { return read(() -> componentUnchecked(name)); }
    private Component componentUnchecked(String name)
    {
        for(Window w:Window.getWindows())
        { if(w instanceof JDialog && w.isShowing()) { Component c=InfoPanelReminderChecks.find((Container)w,name); if(c!=null) { return c; } } }
        throw new AssertionError("Missing component "+name);
    }
    private boolean componentExistsUnchecked(String name)
    {
        for(Window w:Window.getWindows())
        { if(w instanceof JDialog && w.isShowing() && InfoPanelReminderChecks.find((Container)w,name)!=null) { return true; } }
        return false;
    }
    private JDialog dialog() { return (JDialog)SwingUtilities.getWindowAncestor(componentUnchecked("info-tool-root")); }
    private void click(Component c) throws Exception
    { click(read(() -> { Point p=c.getLocationOnScreen(); return new Point(p.x+c.getWidth()/2,p.y+c.getHeight()/2); })); }
    private void click(Point p) throws Exception { robot.mouseMove(p.x,p.y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); settle(); }
    private void press(int key) { robot.keyPress(key); robot.keyRelease(key); }
    private void type(String text) { for(char c:text.toCharArray()) { int key=KeyEvent.getExtendedKeyCodeForChar(c); press(key); } }
    private void settle() throws Exception { Thread.sleep(180); SwingUtilities.invokeAndWait(() -> {}); }
    private <T>T read(Callable<T> call) throws Exception
    { final Object[] out={null}; final Exception[] error={null}; SwingUtilities.invokeAndWait(() -> {try{out[0]=call.call();}catch(Exception ex){error[0]=ex;}}); if(error[0]!=null){throw error[0];} @SuppressWarnings("unchecked") T value=(T)out[0]; return value; }
    private void drag(String tool) throws Exception
    {
        Point before=read(() -> dialog().getLocation()); Component title=component("info-tool-title");
        Point from=read(() -> {Point p=title.getLocationOnScreen();return new Point(p.x+25,p.y+8);});
        robot.mouseMove(from.x,from.y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(from.x+48,from.y+32); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); settle();
        Point after=read(() -> dialog().getLocation()); check(tool+" header drag moves window",after.x==before.x+48&&after.y==before.y+32);
        checkBounds();
    }
    private void checkBounds() throws Exception
    {
        check("visible controls within popup",read(() -> inside(dialog().getContentPane(),dialog().getContentPane())));
    }
    private static boolean inside(Container root,Container parent)
    {
        for(Component c:parent.getComponents())
        {
            if(!c.isShowing()) { continue; }
            // Scroll-view content can deliberately be larger than its viewport.
            if(parent instanceof javax.swing.JViewport) { continue; }
            Rectangle b=SwingUtilities.convertRectangle(parent,c.getBounds(),root);
            if(b.x<0||b.y<0||b.x+b.width>root.getWidth()+1||b.y+b.height>root.getHeight()+1) { System.err.println("Clipped "+c.getName()+" "+c.getClass().getSimpleName()+" "+b+" root="+root.getSize()); return false; }
            if(c instanceof Container&&!inside(root,(Container)c)) { return false; }
        }
        return true;
    }
    private void capture(String name) throws Exception
    { Rectangle b=read(() -> dialog().getBounds()); ImageIO.write(robot.createScreenCapture(b),"png",output.resolve(name).toFile()); }
    private void captureCanvas(String name) throws Exception
    { Rectangle b=read(() -> {Point p=canvas.getLocationOnScreen();return new Rectangle(p.x,p.y,canvas.getWidth(),150);}); ImageIO.write(robot.createScreenCapture(b),"png",output.resolve(name).toFile()); }
}
