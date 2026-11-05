package com.minimapfilter;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual AWT/Robot input; simulated audio and native controls, not a live RuneLite test. */
public final class InfoPanelVolumeBarWindowChecks
{
    private static int checks;
    private static void check(String name, boolean ok) { checks++; if (!ok) { throw new AssertionError(name); } }
    public static void main(String[] args) throws Exception
    {
        File output = new File(args.length > 0 ? args[0] : "build/inline-volume-smoke");
        if (!output.isDirectory() && !output.mkdirs()) { throw new IllegalStateException("Cannot create captures"); }
        InfoPanelVolumeBarChecks.Fixture f = new InfoPanelVolumeBarChecks.Fixture();
        Rectangle footer = new Rectangle(1, 7, 213, 34);
        Rectangle xp = new Rectangle(4, 9, 34, 30), map = new Rectangle(177, 9, 34, 30);
        int[] scale = {1}, game = {0, 0, 0}, nativeClicks = {0, 0};
        boolean[] visible = {true}, allowed = {true};
        Canvas canvas = new Canvas()
        {
            @Override public void paint(Graphics graphics)
            {
                Graphics2D g = (Graphics2D) graphics.create();
                try
                {
                    g.setColor(new Color(36, 39, 32)); g.fillRect(0, 0, getWidth(), getHeight());
                    g.scale(scale[0], scale[0]);
                    g.setColor(new Color(75, 69, 51)); g.fillRect(footer.x, footer.y, footer.width, footer.height);
                    g.setColor(new Color(112, 101, 79)); g.drawRect(footer.x, footer.y, footer.width - 1, footer.height - 1);
                    g.setColor(new Color(255, 237, 173));
                    g.drawOval(xp.x + 3, xp.y + 2, 27, 26); g.drawString("XP", xp.x + 9, xp.y + 20);
                    g.drawOval(map.x + 3, map.y + 2, 27, 26); g.drawString("M", map.x + 12, map.y + 20);
                    f.bar.paint(g, null);
                }
                finally { g.dispose(); }
            }
            @Override public void update(Graphics g) { paint(g); }
        };
        MouseAdapter input = new MouseAdapter()
        {
            private MouseEvent translate(MouseEvent e)
            {
                return new MouseEvent(canvas, e.getID(), e.getWhen(), e.getModifiersEx(),
                    e.getX() / scale[0], e.getY() / scale[0], e.getClickCount(), e.isPopupTrigger(), e.getButton());
            }
            @Override public void mousePressed(MouseEvent e)
            {
                MouseEvent n = translate(e);
                if (f.bar.mousePressed(n)) { e.consume(); }
                else if (xp.contains(n.getPoint())) { nativeClicks[0]++; }
                else if (map.contains(n.getPoint())) { nativeClicks[1]++; }
                else { game[0]++; }
            }
            @Override public void mouseReleased(MouseEvent e) { if (f.bar.mouseReleased(translate(e))) { e.consume(); } else { game[1]++; } }
            @Override public void mouseClicked(MouseEvent e) { if (f.bar.mouseClicked(translate(e))) { e.consume(); } }
            @Override public void mouseDragged(MouseEvent e) { if (f.bar.mouseDragged(translate(e))) { e.consume(); } else { game[2]++; } }
            @Override public void mouseMoved(MouseEvent e) { f.bar.mouseMoved(translate(e)); }
            @Override public void mouseEntered(MouseEvent e) { f.bar.mouseMoved(translate(e)); }
        };
        JFrame[] frame = new JFrame[1]; Timer[] timer = new Timer[1];
        Robot robot = new Robot(); robot.setAutoDelay(45);
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                canvas.addMouseListener(input); canvas.addMouseMotionListener(input);
                frame[0] = new JFrame("Inline volume test - simulated audio, not RuneLite");
                frame[0].setBounds(100, 100, 650, 300); frame[0].add(canvas); frame[0].setVisible(true); canvas.requestFocusInWindow();
                timer[0] = new Timer(25, e ->
                {
                    f.bar.update(f.region, visible[0], allowed[0]);
                    f.model.tick(f.audio, visible[0], visible[0] && allowed[0], System.nanoTime() / 1_000_000);
                    f.bar.update(f.region, visible[0], allowed[0]); canvas.repaint();
                });
                timer[0].start();
            });
            settle(robot); check("read-only initial display", f.audio.actual == 65 && f.audio.writes == 0);
            tap(robot, canvas, f.speaker(), 1); settle(robot); check("real speaker click mutes", f.audio.actual == 0);
            tap(robot, canvas, f.slider(25), 1); settle(robot); check("real muted track click stays silent", f.audio.actual == 0 && f.model.snapshot().selected == 25);
            tap(robot, canvas, f.speaker(), 1); settle(robot); check("real unmute restores level", f.audio.actual == 25);
            int writes = f.audio.writes;
            move(robot, canvas, f.slider(25), 1); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            move(robot, canvas, f.slider(65), 1); settle(robot);
            check("real drag previews", Math.abs(f.bar.displayedPercent() - 65) <= 1);
            check("real drag holds footer", f.bar.isHolding());
            check("drag commits only on release", f.audio.writes == writes && f.audio.actual == 25);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); settle(robot);
            check("real release applies", Math.abs(f.audio.actual - 65) <= 1);
            check("release ends hold", !f.bar.isHolding());
            check("bar input isolated from game-like handler", game[0] == 0 && game[1] == 0 && game[2] == 0);
            move(robot, canvas, f.slider(f.audio.actual), 1); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            move(robot, canvas, new Point(480, 200), 1); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); settle(robot);
            check("outside drag clamps", f.audio.actual == 100);
            check("outside release isolated", game[0] == 0 && game[1] == 0 && game[2] == 0);
            tap(robot, canvas, new Point(xp.x + 12, xp.y + 12), 1); tap(robot, canvas, new Point(map.x + 12, map.y + 12), 1); settle(robot);
            check("native XP input unchanged", nativeClicks[0] == 1); check("native Map input unchanged", nativeClicks[1] == 1);
            int releases = game[1]; writes = f.audio.writes;
            move(robot, canvas, f.slider(100), 1); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); move(robot, canvas, f.slider(25), 1);
            SwingUtilities.invokeAndWait(() -> { allowed[0] = false; f.bar.update(f.region, true, false); });
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); settle(robot);
            check("context loss cancels", f.audio.writes == writes && f.audio.actual == 100);
            check("canceled release consumed", game[1] == releases);
            SwingUtilities.invokeAndWait(() -> { allowed[0] = true; visible[0] = false; f.bar.update(f.region, false, false); });
            tap(robot, canvas, f.slider(25), 1); settle(robot);
            check("other mode allows game input", game[0] == 1); check("other mode doesn't change audio", f.audio.actual == 100);
            SwingUtilities.invokeAndWait(() -> { visible[0] = true; scale[0] = 2; }); settle(robot);
            tap(robot, canvas, f.slider(50), 2); settle(robot);
            check("upstream 2x translation works", Math.abs(f.audio.actual - 50) <= 1);
            SwingUtilities.invokeAndWait(() -> f.audio.actual = 42); settle(robot);
            check("external native level reflected", f.bar.displayedPercent() == 42);
            int gameBefore = game[0]; move(robot, canvas, f.speaker(), 2);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK); settle(robot);
            check("right click no toggle", f.audio.actual == 42); check("right click isolated", game[0] == gameBefore);
            boolean dialog = false; for (Window w : Window.getWindows()) { dialog |= w instanceof JDialog && w.isShowing(); }
            check("no popup opened", !dialog);
            capture(canvas, footer, scale[0], output, "inline-volume.png");
            tap(robot, canvas, f.speaker(), 2); settle(robot); capture(canvas, footer, scale[0], output, "inline-muted.png");
            check("scaled speaker mutes", f.audio.actual == 0);
            SwingUtilities.invokeAndWait(() -> f.audio.error = true); settle(robot);
            int failures = f.audio.writes; tap(robot, canvas, f.speaker(), 2); tap(robot, canvas, f.slider(50), 2); settle(robot);
            check("failure blocks audio writes", f.audio.writes == failures);
            capture(canvas, footer, scale[0], output, "inline-unavailable.png");
            System.out.println("Inline Volume real-canvas checks passed: " + checks);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> { if (timer[0] != null) { timer[0].stop(); } f.bar.suspend(); if (frame[0] != null) { frame[0].dispose(); } });
        }
    }
    private static void move(Robot r, Canvas c, Point p, int scale) throws Exception
    {
        Point[] origin = new Point[1]; SwingUtilities.invokeAndWait(() -> origin[0] = c.getLocationOnScreen());
        r.mouseMove(origin[0].x + p.x * scale, origin[0].y + p.y * scale);
    }
    private static void tap(Robot r, Canvas c, Point p, int scale) throws Exception { move(r, c, p, scale); r.mousePress(InputEvent.BUTTON1_DOWN_MASK); r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
    private static void settle(Robot r) throws Exception { r.delay(400); SwingUtilities.invokeAndWait(() -> {}); }
    private static void capture(Canvas c, Rectangle rect, int scale, File output, String name) throws Exception
    {
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() ->
        {
            image[0] = new BufferedImage(rect.width * scale, rect.height * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image[0].createGraphics(); g.translate(-rect.x * scale, -rect.y * scale); c.paint(g); g.dispose();
        });
        ImageIO.write(image[0], "png", new File(output, name));
    }
}
