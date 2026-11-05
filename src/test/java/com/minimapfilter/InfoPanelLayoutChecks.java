package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Dependency-free tests for stacked settings and the shared HUD text/hit bounds. */
public final class InfoPanelLayoutChecks
{
    private int cases;
    public static void main(String[] args) throws Exception
    {
        System.out.println("Info Panel layout checks passed: " + runAll());
        if (args.length > 0)
        {
            SwingUtilities.invokeAndWait(() -> capture(args[0]));
        }
    }
    public static int runAll() throws Exception
    {
        InfoPanelLayoutChecks test = new InfoPanelLayoutChecks();
        SwingUtilities.invokeAndWait(test::settings);
        test.threadGuard();
        test.geometry();
        return test.cases;
    }
    private void check(String name, boolean value)
    {
        cases++;
        if (!value) { throw new AssertionError(name); }
    }
    private void settings()
    {
        InfoPanelSettingLayout layout = new InfoPanelSettingLayout();
        JPanel root = new JPanel();
        JPanel row = nativeRow();
        root.add(row);
        JComboBox<?> box = (JComboBox<?>) row.getComponent(1);
        JLabel label = (JLabel) row.getComponent(0);
        BorderLayout original = (BorderLayout) row.getLayout();
        String tooltip = "Choose the Info Panel content";
        label.setToolTipText(tooltip);
        MouseAdapter resetListener = new MouseAdapter() {};
        label.addMouseListener(resetListener);
        int[] selections = {0};
        box.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) { selections[0]++; } });
        Object model = box.getModel();
        Object renderer = box.getRenderer();
        Object selection = box.getSelectedItem();
        int listeners = box.getItemListeners().length;
        layout.update(Collections.singletonList(root));
        check("two-line label", "Info panel text:".equals(label.getText()));
        check("original parents retained", box.getParent() == row && label.getParent() == row);
        check("backing model retained", model == InfoPanelDropdown.backingModel(box));
        check("grouped renderer installed", renderer != box.getRenderer());
        check("selection retained", selection == box.getSelectedItem());
        check("no selection events from layout", selections[0] == 0);
        check("selection listeners retained", listeners == box.getItemListeners().length);
        check("label tooltip retained", tooltip.equals(label.getToolTipText()));
        check("reset listener retained", java.util.Arrays.asList(label.getMouseListeners()).contains(resetListener));
        Object stacked = row.getLayout();
        for (int width : new int[] {180, 203, 213, 225, 300})
        {
            row.setSize(width, row.getPreferredSize().height);
            row.doLayout();
            check("label above dropdown at " + width, label.getY() + label.getHeight() < box.getY());
            check("full-width label at " + width, label.getWidth() == width);
            check("full-width dropdown at " + width, box.getWidth() == width);
            check("label text untruncated at " + width,
                label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth());
            check("preferred combo height preserved at " + width, box.getHeight() >= box.getPreferredSize().height);
        }
        for (int i = 0; i < 100; i++) { layout.update(Collections.singletonList(root)); }
        check("refresh idempotent", row.getLayout() == stacked && row.getComponentCount() == 2);
        check("refresh emits no selection events", selections[0] == 0);
        box.setSelectedItem(InfoPanelText.SERVER_TIME);
        check("original selection listener works", selections[0] == 1);
        layout.restoreAll();
        check("restore original layout instance", row.getLayout() == original);
        check("restore original renderer", renderer == box.getRenderer());
        check("restore original model", model == box.getModel());
        check("restore original constraints", original.getLayoutComponent(BorderLayout.CENTER) == label
            && original.getLayoutComponent(BorderLayout.EAST) == box);
        check("restore label", "Info panel text".equals(label.getText()));
        check("restore does not undo user selection", box.getSelectedItem() == InfoPanelText.SERVER_TIME);
        check("restore doesn't write config", selections[0] == 1);
        layout.restoreAll();
        check("double restore harmless", row.getLayout() == original);

        label.setEnabled(false); box.setEnabled(false);
        layout.update(Collections.singletonList(root));
        check("pre-disabled controls stay disabled", !box.isEnabled() && !label.isEnabled());
        layout.update(Collections.emptyList());
        check("closed/rebuilt panel restored", row.getLayout() == original);
        check("restoring layout leaves enabled flags alone", !box.isEnabled() && !label.isEnabled());
        for (int i = 0; i < 20; i++)
        {
            JPanel replacement = nativeRow();
            root.removeAll(); root.add(replacement);
            layout.update(Collections.singletonList(root));
            check("new panel recognized " + i, replacement.getLayout() != original
                && "Info panel text:".equals(((JLabel) replacement.getComponent(0)).getText()));
        }
        layout.restoreAll();

        JPanel wrongEnum = new JPanel(new BorderLayout());
        JLabel sameName = new JLabel("Info panel text");
        wrongEnum.add(sameName, BorderLayout.CENTER);
        wrongEnum.add(new JComboBox<>(new String[]{"Zone", "Coords"}), BorderLayout.EAST);
        Object unrelatedLayout = wrongEnum.getLayout();
        layout.update(Collections.singletonList(wrongEnum));
        check("other plugin with same label untouched", wrongEnum.getLayout() == unrelatedLayout
            && "Info panel text".equals(sameName.getText()));
        JPanel extraChild = nativeRow();
        extraChild.add(new JLabel("Extra"), BorderLayout.SOUTH);
        Object extraLayout = extraChild.getLayout();
        layout.update(Collections.singletonList(extraChild));
        check("unknown row shape untouched", extraChild.getLayout() == extraLayout);
        JPanel unknownLayout = nativeRow(); unknownLayout.setLayout(new FlowLayout());
        Object flow = unknownLayout.getLayout();
        layout.update(Collections.singletonList(unknownLayout));
        check("unknown layout untouched", unknownLayout.getLayout() == flow);
        layout.restoreAll();
    }
    private void threadGuard()
    {
        try { new InfoPanelSettingLayout().update(Collections.emptyList()); }
        catch (IllegalStateException expected) { check("off-EDT update rejected", true); return; }
        throw new AssertionError("off-EDT update allowed");
    }
    private void geometry()
    {
        for (int offset : new int[]{0, 100, -100})
        {
            SquareHudGeometry g = new SquareHudGeometry(offset, 200);
            Rectangle footer = g.footer();
            check("null controls conservative", g.zoneText(null).equals(g.zoneText()));
            check("empty controls conservative", g.zoneText(new Rectangle[0]).equals(g.zoneText()));
            for (int xpWidth : new int[]{26, 30, 34, 40, 60})
            {
                for (int mapWidth : new int[]{30, 34, 40, 60})
                {
                    Rectangle xp = new Rectangle(footer.x + 6, footer.y + 2, xpWidth, 30);
                    Rectangle map = new Rectangle(footer.x + footer.width - 3 - mapWidth, footer.y + 2, mapWidth, 30);
                    Rectangle text = g.zoneText(new Rectangle[]{xp, map});
                    check("text within footer " + offset + "/" + xpWidth + "/" + mapWidth, footer.contains(text));
                    check("does not overlap XP", !text.intersects(xp));
                    check("does not overlap World Map", !text.intersects(map));
                    check("two-pixel gap at left", text.x >= xp.x + xp.width + 2);
                    check("two-pixel gap at right", text.x + text.width <= map.x - 2);
                    check("control order irrelevant", text.equals(g.zoneText(new Rectangle[]{map, xp})));
                    if (xpWidth <= 40 && mapWidth <= 40)
                    {
                        check("normal text wider than before", text.width > g.zoneText().width);
                    }
                }
            }
            Rectangle xp = new Rectangle(footer.x + 6, footer.y + 2, 34, 30);
            Rectangle map = new Rectangle(footer.x + footer.width - 37, footer.y + 2, 34, 30);
            Rectangle text = g.zoneText(new Rectangle[]{xp, map});
            check("typical native text width 132", text.width == 132);
            Rectangle crossed = new Rectangle(footer.x + 80, footer.y, 90, footer.height);
            check("cross-centre button leaves text empty", g.zoneText(new Rectangle[]{crossed}).isEmpty());
            check("irrelevant controls do not shrink real area", text.equals(g.zoneText(new Rectangle[]{xp, map,
                new Rectangle(footer.x, footer.y - 100, 213, 50), null})));
            check("new left text region interactive", text.contains(text.x + 1, text.y + 3)
                && !g.zoneText().contains(text.x + 1, text.y + 3));
            BufferedImage image = new BufferedImage(220, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try
            {
                // A reference small font, not a claim of real RuneLite font integration.
                graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
                for (String coords : new String[]{"3200, 3420 | P: 0", "16383, 16383 | P: 3"})
                {
                    check("coordinate reference font fits " + coords,
                        coords.equals(SquareHudText.fit(coords, graphics.getFontMetrics(), text.width - 4)));
                }
            }
            finally { graphics.dispose(); }
        }
    }
    private static JPanel nativeRow()
    {
        JPanel row = new JPanel(new BorderLayout());
        row.add(new JLabel("Info panel text"), BorderLayout.CENTER);
        JComboBox<InfoPanelText> box = new JComboBox<>(InfoPanelText.values());
        box.setSelectedItem(InfoPanelText.COORDS_AND_PLANE);
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));
        row.add(box, BorderLayout.EAST);
        return row;
    }
    private static void capture(String path)
    {
        JPanel row = nativeRow();
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        new InfoPanelSettingLayout().update(Collections.singletonList(row));
        row.setSize(225, row.getPreferredSize().height);
        layoutTree(row);
        BufferedImage image = new BufferedImage(row.getWidth(), row.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { row.paint(graphics); }
        finally { graphics.dispose(); }
        try
        {
            File file = new File(path); file.getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(image, "png", file);
        }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private static void layoutTree(Container container)
    {
        container.doLayout();
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof Container) { layoutTree((Container) child); }
        }
    }
}
