package com.minimapfilter;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Real-window checks for below-HUD defaults, draggable selector, and reopen-at-last-position behavior. */
public final class InfoPanelPopupPlacementWindowChecks
{
    private int cases;
    private JFrame frame;
    private JPanel canvas;
    private Robot robot;

    private void check(String name, boolean value)
    {
        cases++;
        if (!value) { throw new AssertionError(name); }
    }

    public static void main(String[] args) throws Exception
    {
        InfoPanelPopupPlacementWindowChecks c = new InfoPanelPopupPlacementWindowChecks();
        try { c.run(); System.out.println("Popup placement real-window checks passed: " + c.cases); }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                for (Window w : Window.getWindows()) if (w instanceof JDialog) { w.dispose(); }
                if (c.frame != null) { c.frame.dispose(); }
            });
        }
    }

    private void run() throws Exception
    {
        robot = new Robot(); robot.setAutoDelay(30);
        SwingUtilities.invokeAndWait(() ->
        {
            frame = new JFrame("Popup placement test");
            canvas = new JPanel(); canvas.setPreferredSize(new Dimension(900, 700));
            frame.add(canvas); frame.pack(); frame.setLocation(50, 40); frame.setVisible(true); frame.toFront();
        });
        settle();
        Point canvasOrigin = read(canvas::getLocationOnScreen);
        Rectangle hud = new Rectangle(canvasOrigin.x + 620, canvasOrigin.y + 35,
            SquareHudGeometry.WIDTH, SquareHudGeometry.HEIGHT + SquareHudGeometry.FOOTER_HEIGHT + InfoPanelToolDrawer.HEIGHT);

        InfoPanelTools tools = new InfoPanelTools(); tools.activate();
        InfoPanelPopup tool = new InfoPanelPopup(tools, () -> { }, () -> true);
        AtomicReference<Point> toolSaved = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> tool.open(InfoPanelText.CALCULATOR, canvas, hud, null,
            point -> toolSaved.set(new Point(point))));
        settle();
        Rectangle first = dialogBounds("info-tool-root");
        check("tool defaults immediately below HUD", first.y == hud.y + hud.height + 6);
        check("tool default is centered under HUD", first.x + first.width / 2 == hud.x + hud.width / 2);
        drag("info-tool-title", -110, 70); settle();
        Point movedTool = toolSaved.get();
        check("tool drag reports remembered position", movedTool != null && dialogBounds("info-tool-root").getLocation().equals(movedTool));
        SwingUtilities.invokeAndWait(tool::close); settle();
        Rectangle otherDefault = new Rectangle(hud.x - 300, hud.y + 40, hud.width, hud.height);
        SwingUtilities.invokeAndWait(() -> tool.open(InfoPanelText.CALCULATOR, canvas, otherDefault, movedTool,
            point -> toolSaved.set(new Point(point))));
        settle();
        check("tool reopens at its remembered position", dialogBounds("info-tool-root").getLocation().equals(movedTool));
        SwingUtilities.invokeAndWait(tool::close); settle();

        AtomicReference<Point> selectorSaved = new AtomicReference<>();
        final InfoPanelText[] selected = {InfoPanelText.ZONE};
        InfoPanelSelectorPopup selector = new InfoPanelSelectorPopup(() -> selected[0], value -> selected[0] = value);
        SwingUtilities.invokeAndWait(() -> selector.open(canvas, hud, null,
            point -> selectorSaved.set(new Point(point))));
        settle();
        Rectangle selectorFirst = dialogBounds("info-selector-root");
        check("selector defaults immediately below HUD", selectorFirst.y == hud.y + hud.height + 6);
        check("selector has draggable title", find("info-selector-title") != null);
        drag("info-selector-title", -85, 55); settle();
        Point movedSelector = selectorSaved.get();
        check("selector drag reports remembered position", movedSelector != null
            && dialogBounds("info-selector-root").getLocation().equals(movedSelector));
        check("selector remains open after drag", read(selector::isShowing));
        SwingUtilities.invokeAndWait(() -> { frame.toFront(); canvas.requestFocusInWindow(); }); settle();
        check("canvas focus does not auto-dismiss selector", read(selector::isShowing));
        SwingUtilities.invokeAndWait(selector::close); settle();
        SwingUtilities.invokeAndWait(() -> selector.open(canvas, otherDefault, movedSelector,
            point -> selectorSaved.set(new Point(point))));
        settle();
        check("selector reopens at its remembered position", dialogBounds("info-selector-root").getLocation().equals(movedSelector));
        SwingUtilities.invokeAndWait(() -> selector.toggle(canvas, otherDefault, movedSelector,
            point -> selectorSaved.set(new Point(point))));
        settle();
        check("second Info-text toggle closes selector", !read(selector::isShowing));
        SwingUtilities.invokeAndWait(() -> selector.toggle(canvas, otherDefault, movedSelector,
            point -> selectorSaved.set(new Point(point))));
        settle();
        check("next Info-text toggle reopens selector", read(selector::isShowing));
        click("info-selector-close"); settle();
        check("selector X closes popup", !read(selector::isShowing));

        TrackingPopup.Model trackingModel = new TrackingPopup.Model()
        {
            private boolean all = true;
            private final java.util.EnumMap<TrackingPopup.Category, Boolean> values =
                new java.util.EnumMap<>(TrackingPopup.Category.class);
            { for (TrackingPopup.Category category : TrackingPopup.Category.values()) { values.put(category, true); } }
            public boolean all() { return all; }
            public boolean stored(TrackingPopup.Category category) { return values.get(category); }
            public void toggleAll() { all = !all; }
            public void toggle(TrackingPopup.Category category)
            { if (all) { values.put(category, !values.get(category)); } }
        };
        TrackingPopup tracking = new TrackingPopup(trackingModel);
        AtomicReference<Point> trackingSaved = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> tracking.open(canvas, hud, null,
            point -> trackingSaved.set(new Point(point))));
        settle();
        Rectangle trackingFirst = dialogBounds("tracking-root");
        check("tracking defaults immediately below HUD", trackingFirst.y == hud.y + hud.height + 6);
        check("tracking defaults centered under HUD", trackingFirst.x + trackingFirst.width / 2 == hud.x + hud.width / 2);
        drag("tracking-title", -70, 65); settle();
        Point movedTracking = trackingSaved.get();
        check("tracking drag reports remembered position", movedTracking != null
            && dialogBounds("tracking-root").getLocation().equals(movedTracking));
        check("tracking remains open after drag", read(tracking::isShowing));
        SwingUtilities.invokeAndWait(tracking::close); settle();
        SwingUtilities.invokeAndWait(() -> tracking.toggle(canvas, otherDefault, movedTracking,
            point -> trackingSaved.set(new Point(point))));
        settle();
        check("tracking reopens at remembered position", dialogBounds("tracking-root").getLocation().equals(movedTracking));
        SwingUtilities.invokeAndWait(() -> tracking.toggle(canvas, otherDefault, movedTracking,
            point -> trackingSaved.set(new Point(point))));
        settle();
        check("second Tracking-button toggle closes popup", !read(tracking::isShowing));
        SwingUtilities.invokeAndWait(() -> tracking.toggle(canvas, otherDefault, movedTracking,
            point -> trackingSaved.set(new Point(point))));
        settle();
        click("tracking-close"); settle();
        check("tracking X closes popup", !read(tracking::isShowing));
    }


    private void click(String name) throws Exception
    {
        Component c = find(name);
        if (c == null) { throw new AssertionError("Missing click target " + name); }
        Point p = read(c::getLocationOnScreen);
        int x = p.x + Math.max(2, c.getWidth() / 2), y = p.y + Math.max(2, c.getHeight() / 2);
        robot.mouseMove(x, y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private void drag(String name, int dx, int dy) throws Exception
    {
        Component c = find(name);
        if (c == null) { throw new AssertionError("Missing drag handle " + name); }
        Point p = read(c::getLocationOnScreen);
        int x = p.x + Math.max(3, c.getWidth() / 2), y = p.y + Math.max(3, c.getHeight() / 2);
        robot.mouseMove(x, y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 6; i++) { robot.mouseMove(x + dx * i / 6, y + dy * i / 6); }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private Rectangle dialogBounds(String rootName) throws Exception
    {
        return read(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing() && find(w, rootName) != null) { return w.getBounds(); }
            }
            throw new AssertionError("No visible dialog for " + rootName);
        });
    }

    private Component find(String name) throws Exception
    {
        return read(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing())
                {
                    Component found = find(w, name);
                    if (found != null) { return found; }
                }
            }
            return null;
        });
    }

    private static Component find(Component c, String name)
    {
        if (name.equals(c.getName())) { return c; }
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents())
            {
                Component found = find(child, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private void settle() throws Exception { robot.delay(140); SwingUtilities.invokeAndWait(() -> { }); }
    private static <T> T read(java.util.function.Supplier<T> supplier) throws Exception
    {
        final Object[] value = new Object[1];
        SwingUtilities.invokeAndWait(() -> value[0] = supplier.get());
        @SuppressWarnings("unchecked") T result = (T) value[0]; return result;
    }
}
