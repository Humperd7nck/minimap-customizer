package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.ComboPopup;

/** Real OS mouse/keyboard tests on an isolated test display; not a live RuneLite test. */
public final class InfoPanelDropdownWindowChecks
{
    private JFrame frame;
    private JPanel row;
    private JComboBox<InfoPanelText> box;
    private InfoPanelSettingLayout layout;
    private Robot robot;
    private int changes;
    private int cases;
    private String persisted = "ZONE";
    private void check(String name, boolean value)
    {
        cases++;
        if (!value) { throw new AssertionError(name); }
    }
    public static void main(String[] args) throws Exception
    {
        InfoPanelDropdownWindowChecks c = new InfoPanelDropdownWindowChecks();
        try
        {
            c.run(args.length == 0 ? "build/dropdown-smoke" : args[0]);
            System.out.println("Grouped dropdown real-window checks passed: " + c.cases);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (c.layout != null) { c.layout.restoreAll(); }
                if (c.frame != null) { c.frame.dispose(); }
            });
        }
    }
    private void run(String output) throws Exception
    {
        robot = new Robot(); robot.setAutoDelay(25);
        SwingUtilities.invokeAndWait(() ->
        {
            frame = new JFrame("Info Panel settings test");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout());
            root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            row = new JPanel(new BorderLayout());
            row.add(new JLabel("Info panel text"), BorderLayout.CENTER);
            box = new JComboBox<>(InfoPanelText.values());
            box.setSelectedItem(InfoPanelText.ZONE);
            box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));
            box.addItemListener(e ->
            {
                if (e.getStateChange() == ItemEvent.SELECTED)
                {
                    persisted = ((Enum<?>) box.getSelectedItem()).name();
                    changes++;
                }
            });
            row.add(box, BorderLayout.EAST); root.add(row, BorderLayout.NORTH);
            layout = new InfoPanelSettingLayout(); layout.update(Collections.singletonList(root));
            frame.setContentPane(root); frame.setSize(245,130); frame.setLocation(140,60);
            frame.setVisible(true); frame.toFront(); box.requestFocusInWindow();
        });
        settle();
        check("stacked label visible", read(() -> row.getComponent(0).getBounds().y + row.getComponent(0).getHeight() < box.getY()));
        click(box); settle();
        check("mouse opens native dropdown", read(box::isPopupVisible));
        check("all rows available", read(() -> list().getModel().getSize() == 19));
        check("first group caption", read(() -> list().getModel().getElementAt(0).toString().equals("Info")));
        capture(output + "/grouped-dropdown.png");
        for (int heading : new int[]{0,15})
        {
            int before = changes;
            clickRow(heading); settle();
            check("heading click leaves popup open " + heading, read(box::isPopupVisible));
            check("heading click does not persist " + heading, changes == before && persisted.equals("ZONE"));
        }
        clickRow(3); settle();
        check("server time click selected", persisted.equals("SERVER_TIME"));
        check("option click closes popup", !read(box::isPopupVisible));
        check("exactly one config selection", changes == 1);
        SwingUtilities.invokeAndWait(() -> { box.setSelectedItem(InfoPanelText.COORDS); box.requestFocusInWindow(); });
        settle();
        press(KeyEvent.VK_ENTER); settle();
        check("Enter opens grouped dropdown", read(box::isPopupVisible));
        int before = changes;
        press(KeyEvent.VK_HOME); settle();
        check("keyboard skips Info heading", read(() -> list().getSelectedValue() == InfoPanelText.SYSTEM_TIME));
        check("popup navigation not committed", changes == before);
        press(KeyEvent.VK_ENTER); settle();
        check("Enter commits Local Time legacy key", persisted.equals("SYSTEM_TIME"));
        check("closed Local Time display", read(() -> box.getSelectedItem().toString().equals("Local Time")));
        press(KeyEvent.VK_ENTER); settle();
        before = changes;
        press(KeyEvent.VK_END); settle();
        check("End highlights final option", read(() -> list().getSelectedValue() == InfoPanelText.FPS_AND_PING));
        press(KeyEvent.VK_HOME); settle();
        check("Home skips first heading", read(() -> list().getSelectedValue() == InfoPanelText.SYSTEM_TIME));
        press(KeyEvent.VK_ESCAPE); settle();
        check("Escape dismisses", !read(box::isPopupVisible));
        check("Escape keeps original selection", persisted.equals("SYSTEM_TIME") && changes == before);
        SwingUtilities.invokeAndWait(() -> box.setSelectedItem(InfoPanelText.TOTAL_LEVEL));
        press(KeyEvent.VK_DOWN); settle();
        check("closed Down reaches Nothing", persisted.equals("NOTHING"));
        press(KeyEvent.VK_DOWN); settle();
        check("closed Down skips Performance", persisted.equals("FPS"));
        press(KeyEvent.VK_UP); settle();
        check("closed Up skips Performance", persisted.equals("NOTHING"));
        press(KeyEvent.VK_UP); settle();
        check("closed Up reaches Total Level", persisted.equals("TOTAL_LEVEL"));
        press(KeyEvent.VK_L); settle();
        check("actual type ahead uses Local Time", persisted.equals("SYSTEM_TIME"));
        click(box); settle();
        before = changes;
        clickScreen(50,50); settle();
        check("outside click dismisses", !read(box::isPopupVisible));
        check("outside dismissal no config change", before == changes);
        for (int i = 0; i < 4; i++)
        {
            click(box); settle();
            check("repeat popup opens " + i, read(box::isPopupVisible));
            clickRow(15); settle();
            check("repeat heading remains nonselectable " + i, read(box::isPopupVisible) && persisted.equals("SYSTEM_TIME"));
            press(KeyEvent.VK_ESCAPE); settle();
        }
        SwingUtilities.invokeAndWait(() -> box.setEnabled(false));
        click(box); settle();
        check("disabled control cannot open", !read(box::isPopupVisible));
        SwingUtilities.invokeAndWait(() -> box.setEnabled(true));
        click(box); settle();
        SwingUtilities.invokeAndWait(layout::restoreAll); settle();
        check("stop closes open popup", !read(box::isPopupVisible));
        check("stop restores real enum model", read(() -> box.getItemAt(0) == InfoPanelText.ZONE && box.getItemCount() == 23));
        check("stop retains selection", persisted.equals("SYSTEM_TIME"));
        SwingUtilities.invokeAndWait(() -> layout.update(Collections.singletonList(frame)));
        click(box); settle();
        check("re-enable regroups", read(() -> box.isPopupVisible() && list().getModel().getSize() == 19));
        check("Tools are absent from grouped menu", read(() -> {
            for (int i = 0; i < list().getModel().getSize(); i++)
            { if (list().getModel().getElementAt(i) == InfoPanelText.VOLUME) return false; }
            return true;
        }));
        press(KeyEvent.VK_ESCAPE); settle();
        // Reposition near the lower screen edge to exercise native popup placement.
        SwingUtilities.invokeAndWait(() -> frame.setLocation(140,650)); settle();
        click(box); settle();
        check("lower-screen popup fits screen", read(() -> {
            Rectangle p = new Rectangle(list().getLocationOnScreen(), list().getSize());
            return frame.getGraphicsConfiguration().getBounds().contains(p);
        }));
        clickRow(18); settle();
        check("last option clickable near screen edge", persisted.equals("FPS_AND_PING"));
    }
    private JList<?> list() { return ((ComboPopup) box.getUI().getAccessibleChild(box, 0)).getList(); }
    private void capture(String path) throws Exception
    {
        Rectangle area = read(() -> {
            Rectangle l = new Rectangle(list().getLocationOnScreen(), list().getSize());
            Rectangle r = new Rectangle(row.getLocationOnScreen(), row.getSize());
            Rectangle both = r.union(l); both.grow(4,4); return both;
        });
        File file = new File(path); file.getParentFile().mkdirs();
        ImageIO.write(robot.createScreenCapture(area), "png", file);
    }
    private void click(Component c) throws Exception
    {
        Point p = read(() -> { Point origin = c.getLocationOnScreen(); return new Point(origin.x+c.getWidth()-10, origin.y+c.getHeight()/2); });
        clickScreen(p.x,p.y);
    }
    private void clickRow(int index) throws Exception
    {
        Point p = read(() -> { JList<?> l = list(); l.ensureIndexIsVisible(index); Rectangle cell = l.getCellBounds(index,index);
            Point origin = l.getLocationOnScreen(); return new Point(origin.x+30,origin.y+cell.y+cell.height/2); });
        clickScreen(p.x,p.y);
    }
    private void clickScreen(int x,int y)
    {
        robot.mouseMove(x,y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
    private void press(int key) { robot.keyPress(key); robot.keyRelease(key); }
    private void settle() throws Exception { robot.delay(120); SwingUtilities.invokeAndWait(() -> {}); }
    private static <T> T read(java.util.function.Supplier<T> task) throws Exception
    {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(task.get())); return result.get();
    }
}
