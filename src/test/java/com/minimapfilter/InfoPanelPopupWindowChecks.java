package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Optional real-window smoke test. Requires a display/Xvfb; never run in a game client. */
public final class InfoPanelPopupWindowChecks
{
    private static int assertions;
    private static void check(String label, boolean good)
    {
        assertions++;
        if (!good) { throw new AssertionError(label); }
    }
    public static void main(String[] args) throws Exception
    {
        File output = new File(args.length == 0 ? "build/popup-smoke" : args[0]);
        if (!output.isDirectory() && !output.mkdirs()) { throw new IllegalStateException("Cannot create capture directory"); }
        JFrame[] frame = new JFrame[1];
        Canvas canvas = new Canvas();
        AtomicInteger gameKeys = new AtomicInteger();
        AtomicInteger gameClicks = new AtomicInteger();
        canvas.addMouseListener(new MouseAdapter()
        {
            @Override public void mousePressed(MouseEvent e) { gameClicks.incrementAndGet(); }
        });
        canvas.addKeyListener(new KeyAdapter()
        {
            @Override public void keyPressed(KeyEvent e) { gameKeys.incrementAndGet(); }
        });
        InfoPanelTools tools = new InfoPanelTools();
        AtomicInteger closes = new AtomicInteger();
        InfoPanelPopup popup = new InfoPanelPopup(tools, closes::incrementAndGet, () -> true);
        Robot robot = new Robot();
        robot.setAutoDelay(60);
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0] = new JFrame("Standalone popup test - not RuneLite");
                frame[0].setBounds(100, 100, 900, 650);
                frame[0].add(canvas);
                frame[0].setVisible(true);
                popup.open(InfoPanelText.CALCULATOR, canvas, new Point(860, 450));
            });
            settle(robot); robot.delay(150);
            Component[] input = new Component[1];
            SwingUtilities.invokeAndWait(() -> input[0] = findDialogComponent("calculator-input"));
            check("calculator input exists in a real window", input[0] instanceof JTextField);
            Point[] position = new Point[1];
            SwingUtilities.invokeAndWait(() -> position[0] = input[0].getLocationOnScreen());
            robot.mouseMove(position[0].x + 15, position[0].y + 10);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            type(robot, KeyEvent.VK_1); type(robot, KeyEvent.VK_2);
            robot.keyPress(KeyEvent.VK_SHIFT); type(robot, KeyEvent.VK_EQUALS); robot.keyRelease(KeyEvent.VK_SHIFT);
            type(robot, KeyEvent.VK_3); type(robot, KeyEvent.VK_ENTER);
            settle(robot);
            check("real keyboard calculates 12+3", tools.snapshot().answer.intValueExact() == 15);
            check("calculator typing does not reach game-like canvas", gameKeys.get() == 0);
            dragHeader(robot, "Calculator", -90, -70);
            check("dragging doesn't alter calculator answer", tools.snapshot().answer.intValueExact() == 15);
            Rectangle beforeInputSelection = dialogBounds();
            mouseDrag(robot, "calculator-input", 70, 0, InputEvent.BUTTON1_DOWN_MASK);
            check("selecting calculator text doesn't move window", dialogBounds().equals(beforeInputSelection));
            type(robot, KeyEvent.VK_ENTER); settle(robot);
            check("Enter still works after dragging", tools.snapshot().answer.intValueExact() == 15);
            Rectangle beforeRightDrag = dialogBounds();
            mouseDrag(robot, "info-tool-title", 25, 10, InputEvent.BUTTON3_DOWN_MASK);
            check("right-button drag doesn't move window", dialogBounds().equals(beforeRightDrag));
            check("calculator header and field gestures stay out of game canvas", gameClicks.get() == 0);
            dragToScreenEdge(robot);
            click(robot, "calc-key-Clear");
            click(robot, "calc-key-7"); click(robot, "calc-key-+"); click(robot, "calc-key-8"); click(robot, "calc-key-=");
            check("colored calculator keypad works after moving", tools.snapshot().answer.intValueExact() == 15);
            SwingUtilities.invokeAndWait(() ->
            {
                check("no Ans control in the real popup", findDialogComponent("calc-key-Ans") == null);
                check("no old C control in the real popup", findDialogComponent("calc-key-C") == null);
                check("no separate Backspace control in real popup", findDialogComponent("calc-backspace") == null);
                for (String name : new String[] {"Clear", "Back"})
                {
                    javax.swing.JButton key = (javax.swing.JButton) findDialogComponent("calc-key-" + name);
                    check(name + " label fits its actual key", key.getFontMetrics(key.getFont()).stringWidth(name)
                        <= key.getWidth() - key.getInsets().left - key.getInsets().right);
                }
            });
            Rectangle beforeBack = dialogBounds();
            setExpression("123", 3, 3);
            click(robot, "calc-key-Back");
            check("real Back button deletes one digit", expressionText().equals("12"));
            check("Back does not move the popup", dialogBounds().equals(beforeBack));
            type(robot, KeyEvent.VK_BACK_SPACE); settle(robot);
            check("keyboard backspace still edits input after clicking Back", expressionText().equals("1"));
            type(robot, KeyEvent.VK_9); type(robot, KeyEvent.VK_ENTER); settle(robot);
            check("focus returns to input after Back", tools.snapshot().answer.intValueExact() == 19);
            setExpression("12345", 1, 4);
            click(robot, "calc-key-Back");
            check("real Back button deletes selection", expressionText().equals("15"));
            setExpression("12345", 2, 2);
            click(robot, "calc-key-Back");
            check("real Back edits at caret", expressionText().equals("1345"));
            setExpression("123", 0, 0);
            click(robot, "calc-key-Back");
            check("real Back at beginning is harmless", expressionText().equals("123"));
            setExpression("123", 0, 3);
            type(robot, KeyEvent.VK_BACK_SPACE); settle(robot);
            check("keyboard backspace deletes selected input", expressionText().isEmpty());
            click(robot, "calc-key-Back");
            check("real Back on empty input is harmless", expressionText().isEmpty());
            click(robot, "calc-key-Clear");
            type(robot, KeyEvent.VK_A); type(robot, KeyEvent.VK_N); type(robot, KeyEvent.VK_S);
            type(robot, KeyEvent.VK_ENTER); settle(robot);
            check("typed ans is rejected in the real window", !tools.snapshot().calculatorError.isEmpty());
            check("typed ans cannot recall any result", tools.snapshot().answer.signum() == 0);
            click(robot, "calc-key-Clear");
            check("Clear removes typed expression", expressionText().isEmpty());
            check("Clear resets result", tools.snapshot().answer.signum() == 0);
            check("Clear resets error", tools.snapshot().calculatorError.isEmpty());
            check("new keypad and keyboard edits stay out of game canvas", gameKeys.get() == 0 && gameClicks.get() == 0);
            click(robot, "calc-key-7"); click(robot, "calc-key-+"); click(robot, "calc-key-8"); click(robot, "calc-key-=");
            check("arithmetic recovers after removed Ans error", tools.snapshot().answer.intValueExact() == 15);
            capture(robot, output, "calculator-window.png");
            type(robot, KeyEvent.VK_ESCAPE); settle(robot);
            SwingUtilities.invokeAndWait(() -> check("Escape closes real popup", !popup.isShowing()));
            check("closing does not reset calculator answer", tools.snapshot().answer.intValueExact() == 15);
            check("Escape stays out of canvas", gameKeys.get() == 0);
            tools.startTimer(120);
            SwingUtilities.invokeAndWait(() -> popup.open(InfoPanelText.TIMER, canvas, new Point(860, 450)));
            settle(robot); robot.delay(120);
            dragHeader(robot, "Timer", -80, -50);
            check("countdown keeps running while dragged", tools.snapshot().timerRunning);
            click(robot, "timer-pause");
            check("Pause still works after dragging", !tools.snapshot().timerRunning);
            robot.delay(160);
            click(robot, "timer-pause");
            check("Resume still works after dragging", tools.snapshot().timerRunning);
            capture(robot, output, "timer-window.png");
            click(robot, "close-tool");
            SwingUtilities.invokeAndWait(() -> check("Close button works after dragging timer", !popup.isShowing()));
            check("countdown continues after closing the real window", tools.snapshot().timerRunning);
            tools.startStopwatch(); tools.lap();
            SwingUtilities.invokeAndWait(() -> popup.open(InfoPanelText.STOPWATCH, canvas, new Point(860, 450)));
            settle(robot); robot.delay(120);
            dragHeader(robot, "Stopwatch", -70, -50);
            check("stopwatch keeps running while dragged", tools.snapshot().stopwatchRunning);
            int oldLaps = tools.snapshot().laps.size();
            Rectangle beforeLap = dialogBounds();
            click(robot, "stopwatch-lap");
            check("Lap works after dragging", tools.snapshot().laps.size() == oldLaps + 1);
            check("buttons don't move popup", dialogBounds().equals(beforeLap));
            check("all popup clicks and drags stay out of game canvas", gameClicks.get() == 0);
            capture(robot, output, "stopwatch-window.png");
            SwingUtilities.invokeAndWait(popup::close);
            check("stopwatch continues after closing the real window", tools.snapshot().stopwatchRunning);
            check("close callback invoked once per actual window", closes.get() == 3);
            SwingUtilities.invokeAndWait(popup::close);
            check("repeated close is idempotent", closes.get() == 3);
            System.out.println("Info Panel real-window checks passed: " + assertions);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> { popup.close(); if (frame[0] != null) { frame[0].dispose(); } });
        }
    }
    private static String labelText(String name) throws Exception
    {
        String[] text = new String[1];
        SwingUtilities.invokeAndWait(() -> text[0] = ((javax.swing.JLabel) findDialogComponent(name)).getText());
        return text[0];
    }

    private static void setExpression(String text, int start, int end) throws Exception
    {
        // Let focus-gained processing finish before establishing a test selection.
        // Nimbus may otherwise move the caret after select() during Robot focus transfer.
        SwingUtilities.invokeAndWait(() -> findDialogComponent("calculator-input").requestFocusInWindow());
        Thread.sleep(120);
        SwingUtilities.invokeAndWait(() ->
        {
            JTextField field = (JTextField) findDialogComponent("calculator-input");
            field.setText(text);
            field.select(start, end);
        });
    }

    private static String expressionText() throws Exception
    {
        String[] text = new String[1];
        SwingUtilities.invokeAndWait(() -> text[0] = ((JTextField) findDialogComponent("calculator-input")).getText());
        return text[0];
    }

    private static Rectangle dialogBounds() throws Exception
    {
        Rectangle[] bounds = new Rectangle[1];
        SwingUtilities.invokeAndWait(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) { bounds[0] = window.getBounds(); }
            }
        });
        if (bounds[0] == null) { throw new AssertionError("Expected an open tool window"); }
        return bounds[0];
    }

    private static Point componentPoint(String name) throws Exception
    {
        Point[] point = new Point[1];
        SwingUtilities.invokeAndWait(() ->
        {
            Component component = findDialogComponent(name);
            if (component == null) { throw new AssertionError("Missing control: " + name); }
            point[0] = component.getLocationOnScreen();
            point[0].translate(Math.min(25, component.getWidth() / 2), component.getHeight() / 2);
        });
        return point[0];
    }

    private static void click(Robot robot, String name) throws Exception
    {
        Point point = componentPoint(name);
        robot.mouseMove(point.x, point.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle(robot);
    }

    private static void mouseDrag(Robot robot, String name, int dx, int dy, int button) throws Exception
    {
        Point point = componentPoint(name);
        robot.mouseMove(point.x, point.y);
        robot.mousePress(button);
        for (int step = 1; step <= 6; step++)
        {
            robot.mouseMove(point.x + dx * step / 6, point.y + dy * step / 6);
        }
        robot.mouseRelease(button);
        settle(robot);
    }

    private static void dragHeader(Robot robot, String tool, int dx, int dy) throws Exception
    {
        Rectangle before = dialogBounds();
        mouseDrag(robot, "info-tool-title", dx, dy, InputEvent.BUTTON1_DOWN_MASK);
        Rectangle after = dialogBounds();
        check(tool + " window moves by requested delta", after.x == before.x + dx && after.y == before.y + dy);
        check(tool + " drag doesn't resize window", after.getSize().equals(before.getSize()));
        Point inside = componentPoint("info-tool-title");
        robot.mouseMove(inside.x + 15, inside.y + 12);
        settle(robot);
        check(tool + " movement stops on release", dialogBounds().equals(after));
    }

    private static void dragToScreenEdge(Robot robot) throws Exception
    {
        Rectangle before = dialogBounds();
        Point point = componentPoint("info-tool-title");
        robot.mouseMove(point.x, point.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(1, 1);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle(robot);
        Rectangle after = dialogBounds();
        check("real drag clamps popup to display edge", after.x == 0 && after.y == 0);
        check("edge clamp doesn't resize popup", before.getSize().equals(after.getSize()));
        Point inside = componentPoint("info-tool-title");
        robot.mouseMove(inside.x + 20, inside.y + 20);
        settle(robot);
        check("edge drag stops on release", dialogBounds().equals(after));
        // Move away from the edge to show the whole popup in the capture.
        dragHeader(robot, "Calculator after edge clamp", 220, 180);
    }

    private static void settle(Robot robot) throws Exception
    {
        // Robot.waitForIdle() can stall inside XToolkit.syncNativeQueue after
        // moving undecorated windows under Xvfb. Flush the EDT instead; all
        // assertions still inspect real windows and actual Robot input.
        robot.delay(150);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void type(Robot robot, int key) { robot.keyPress(key); robot.keyRelease(key); }
    private static Component findDialogComponent(String name)
    {
        for (Window window : Window.getWindows())
        {
            if (window instanceof JDialog && window.isShowing())
            {
                Component found = InfoPanelV3Checks.find(window, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }
    private static void capture(Robot robot, File directory, String name) throws Exception
    {
        Rectangle[] bounds = new Rectangle[1];
        SwingUtilities.invokeAndWait(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing())
                {
                    bounds[0] = w.getBounds();
                    check("popup within test display", bounds[0].x >= 0 && bounds[0].y >= 0);
                    check("popup retains preferred dimensions", w.getSize().equals(w.getPreferredSize()));
                    check("popup components fit window", visibleChildrenFit((Container) w));
                }
            }
        });
        if (bounds[0] == null) { throw new AssertionError("No visible popup to capture"); }
        ImageIO.write(robot.createScreenCapture(bounds[0]), "png", new File(directory, name));
    }
    private static boolean visibleChildrenFit(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (!child.isVisible()) { continue; }
            if (child.getX() < 0 || child.getY() < 0 || child.getX() + child.getWidth() > parent.getWidth()
                || child.getY() + child.getHeight() > parent.getHeight()) { return false; }
            // Viewports intentionally clip their contents.
            if (child instanceof Container && !(child instanceof javax.swing.JViewport)
                && !visibleChildrenFit((Container) child)) { return false; }
        }
        return true;
    }
}
