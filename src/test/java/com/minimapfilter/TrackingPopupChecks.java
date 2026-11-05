package com.minimapfilter;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Dependency-free state/structure checks for the Tracker popup. */
public final class TrackingPopupChecks
{
    private int cases;
    private void check(String name, boolean value) { cases++; if (!value) { throw new AssertionError(name); } }

    public static void main(String[] args) throws Exception
    {
        int count = runAll();
        System.out.println("Tracking popup checks passed: " + count);
    }

    public static int runAll() throws Exception
    {
        TrackingPopupChecks checks = new TrackingPopupChecks();
        checks.run();
        return checks.cases;
    }

    private void run() throws Exception
    {
        Fake model = new Fake();
        TrackingPopup popup = new TrackingPopup(model);
        final JPanel[] view = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> view[0] = popup.createView());
        JButton all = button(view[0], "tracking-all");
        JButton entities = button(view[0], "tracking-entities");
        JButton ground = button(view[0], "tracking-ground_items");
        JButton icons = button(view[0], "tracking-map_icons");
        JButton foliage = button(view[0], "tracking-foliage");
        check("All tracking row exists", all != null);
        check("Entities row exists", entities != null);
        check("Ground items row exists", ground != null);
        check("Map icons row exists", icons != null);
        check("Foliage row exists", foliage != null);
        check("category rows start enabled", entities.isEnabled() && ground.isEnabled() && icons.isEnabled() && foliage.isEnabled());
        check("open eye icon assigned", entities.getIcon() != null && all.getIcon() != null);

        SwingUtilities.invokeAndWait(ground::doClick);
        check("category toggle changes only category override", !model.stored(TrackingPopup.Category.GROUND_ITEMS)
            && model.stored(TrackingPopup.Category.ENTITIES) && model.all());
        SwingUtilities.invokeAndWait(all::doClick);
        check("All tracking turns master off", !model.all());
        check("category rows disable while master is off", !entities.isEnabled() && !ground.isEnabled() && !icons.isEnabled() && !foliage.isEnabled());
        check("All off preserves category memory", !model.stored(TrackingPopup.Category.GROUND_ITEMS)
            && model.stored(TrackingPopup.Category.ENTITIES));
        SwingUtilities.invokeAndWait(all::doClick);
        check("All tracking restores master", model.all());
        check("previous category state restored", !model.stored(TrackingPopup.Category.GROUND_ITEMS)
            && model.stored(TrackingPopup.Category.ENTITIES));
        check("category rows re-enable", entities.isEnabled() && ground.isEnabled());

        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try { InfoPanelEyeOrb.paintGlyph(g, new java.awt.Rectangle(5, 5, 22, 18), true, false, false); }
        finally { g.dispose(); }
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) != 0) { opaque++; }
        check("shared eye glyph paints visible pixels", opaque > 20);
    }

    private JButton button(Component root, String name)
    {
        Component found = find(root, name);
        check("find " + name, found instanceof JButton);
        return (JButton) found;
    }

    private static Component find(Component component, String name)
    {
        if (name.equals(component.getName())) { return component; }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                Component result = find(child, name);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static final class Fake implements TrackingPopup.Model
    {
        boolean all = true;
        final Map<TrackingPopup.Category, Boolean> states = new EnumMap<>(TrackingPopup.Category.class);
        Fake() { for (TrackingPopup.Category c : TrackingPopup.Category.values()) { states.put(c, true); } }
        @Override public boolean all() { return all; }
        @Override public boolean stored(TrackingPopup.Category category) { return states.get(category); }
        @Override public void toggleAll() { all = !all; }
        @Override public void toggle(TrackingPopup.Category category) { if (all) { states.put(category, !states.get(category)); } }
    }
}
