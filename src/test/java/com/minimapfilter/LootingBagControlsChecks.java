package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.components.ColorJButton;

/** Exercises the core-style section tree on the real Swing event thread. */
public final class LootingBagControlsChecks
{
    private int checks;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Looting bag settings checks passed: " + runAll());
    }

    static int runAll() throws Exception
    {
        LootingBagControlsChecks t = new LootingBagControlsChecks();
        SwingUtilities.invokeAndWait(t::controls);
        return t.checks;
    }

    private void check(String name, boolean ok)
    {
        checks++;
        if (!ok) { throw new AssertionError(name); }
    }

    private void controls()
    {
        LootingBagChecks.Settings config = new LootingBagChecks.Settings();
        LootingBagColorControls controls = new LootingBagColorControls(config);
        Fixture f = new Fixture(config);
        controls.update(Collections.singletonList(f.root), false);
        check("whole section disabled", allDisabled(f.section));
        check("heading gray", f.title.getForeground().equals(new Color(128, 128, 128)));
        check("heading wraps without losing words", f.title.getText().contains("Looting bag colors<br>(ground item icon)"));
        check("checkbox caption wraps", f.toggleLabel.getText().contains("icons<br>by value"));
        check("other section enabled", f.foreign.isEnabled());
        for (ColorJButton button : f.colors)
        {
            Color color = button.getColor();
            check("swatch grayed", color.getRed() == color.getGreen() && color.getGreen() == color.getBlue());
        }
        clickListeners(f.section);
        f.expand.doClick();
        f.enabled.doClick();
        check("heading/reset/picker clicks suspended", f.clicks == 0);
        check("collapse action blocked", f.expands == 0);
        check("feature action blocked", f.toggles == 0);
        check("saved checkbox retained", f.enabled.isSelected());
        check("collapsed state retained", !f.contents.isVisible());
        check("saved color not changed by graying", config.gray.equals(LootingBagValueTier.GRAY.defaultColor()));
        config.gray = Color.CYAN;
        for (int i = 0; i < 30; i++) { controls.update(Collections.singletonList(f.root), false); }
        controls.update(Collections.singletonList(f.root), true);
        check("header/button restored", f.title.isEnabled() && f.expand.isEnabled());
        check("all pickers restored", f.colors.stream().allMatch(Component::isEnabled));
        check("latest config color restored", f.colors.get(0).getColor().equals(Color.CYAN));
        check("original title color restored", f.title.getForeground().equals(Color.ORANGE));
        check("checkbox selection never changed", f.enabled.isSelected());
        check("expand remains collapsed", !f.contents.isVisible());
        int seen = 0;
        for (MouseListener l : f.title.getMouseListeners()) { if (l == f.listener) { seen++; } }
        check("listener restored once", seen == 1);
        f.expand.doClick();
        check("expanding works again", f.expands == 1 && f.contents.isVisible());
        controls.update(Collections.singletonList(f.root), false);
        check("expanded content also disabled", allDisabled(f.contents));
        check("disabling does not collapse saved state", f.contents.isVisible());
        controls.update(Collections.emptyList(), false);
        check("closing/rebuilding releases old components", f.enabled.isEnabled() && f.colors.get(0).isEnabled());
        check("closing restores original labels", f.title.getText().equals(LootingBagColorControls.TITLE));
        check("closing restores original toggle label", f.toggleLabel.getText().equals(LootingBagColorControls.TOGGLE));
        f.colors.get(1).setEnabled(false);
        controls.update(Collections.singletonList(f.root), false);
        controls.restoreAll();
        check("preexisting disabled control retained", !f.colors.get(1).isEnabled());
        check("stop restores others", f.colors.get(0).isEnabled() && f.title.isEnabled());
        Fixture invalid = new Fixture(config);
        invalid.contents.remove(6);
        controls.update(Collections.singletonList(invalid.root), false);
        check("incomplete/unrelated section untouched", invalid.title.isEnabled() && invalid.enabled.isEnabled());
        controls.restoreAll();
        Fixture fresh = new Fixture(config);
        MinimapLayoutControls combined = new MinimapLayoutControls(config);
        JComboBox<InfoPanelText> info = new JComboBox<>(InfoPanelText.values());
        JPanel infoRow = new JPanel(new BorderLayout());
        infoRow.add(new JLabel("Info panel text"), BorderLayout.CENTER);
        infoRow.add(info, BorderLayout.EAST);
        fresh.root.add(infoRow);
        config.style = GroundItemMarkerStyle.LOOTING_BAG;
        combined.updateTree(fresh.root, false);
        check("bag independent of square mode", fresh.enabled.isEnabled() && !info.isEnabled());
        config.style = GroundItemMarkerStyle.NATIVE_DOT;
        combined.updateTree(fresh.root, true);
        check("square independent of bag mode", !fresh.enabled.isEnabled() && info.isEnabled());
        config.style = GroundItemMarkerStyle.OFF;
        combined.updateTree(fresh.root, true);
        check("Off also disables entire section", allDisabled(fresh.section));
        config.style = GroundItemMarkerStyle.LOOTING_BAG;
        config.enabled = false;
        combined.updateTree(fresh.root, true);
        check("can configure colors before enabling", fresh.colors.stream().allMatch(Component::isEnabled));
        combined.restoreAll();
        check("shared helper stop restores both sections", info.isEnabled() && fresh.enabled.isEnabled());
    }

    private static boolean allDisabled(Component c)
    {
        if (c.isEnabled()) { return false; }
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents()) { if (!allDisabled(child)) { return false; } }
        }
        return true;
    }

    private static void clickListeners(Component c)
    {
        for (MouseListener l : c.getMouseListeners())
        {
            if (!(l instanceof ToolTipManager)) { l.mouseClicked(null); }
        }
        if (c instanceof Container) { for (Component child : ((Container) c).getComponents()) { clickListeners(child); } }
    }

    private static final class Fixture
    {
        final JPanel root = new JPanel();
        final JPanel section = new JPanel();
        final JPanel header = new JPanel(new BorderLayout());
        final JPanel contents = new JPanel();
        final JLabel title = new JLabel(LootingBagColorControls.TITLE);
        final JLabel toggleLabel = new JLabel(LootingBagColorControls.TOGGLE);
        final JButton expand = new JButton(">");
        final JCheckBox enabled = new JCheckBox();
        final JButton foreign = new JButton("Other settings");
        final List<ColorJButton> colors = new ArrayList<>();
        int clicks, expands, toggles;
        final MouseAdapter listener = new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e) { clicks++; }
        };

        Fixture(MinimapFilterConfig config)
        {
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            title.setForeground(Color.ORANGE);
            title.addMouseListener(listener);
            header.addMouseListener(listener);
            header.add(expand, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            section.add(header);
            contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
            contents.setVisible(false);
            section.add(contents);
            expand.addActionListener(e -> { expands++; contents.setVisible(!contents.isVisible()); });
            enabled.setSelected(true);
            enabled.addActionListener(e -> toggles++);
            toggleLabel.addMouseListener(listener);
            addRow(toggleLabel, enabled);
            String[] names = {"0–999 gp", "1k–9,999 gp", "10k–99,999 gp", "100k–999,999 gp", "1m–9,999,999 gp", "10m+ gp"};
            for (int i = 0; i < names.length; i++)
            {
                ColorJButton button = new ColorJButton("Pick", LootingBagValueTier.values()[i].selectedColor(config));
                button.addMouseListener(listener);
                JLabel label = new JLabel(names[i]);
                label.addMouseListener(listener);
                colors.add(button);
                addRow(label, button);
            }
            root.add(section);
            root.add(foreign);
        }

        private void addRow(JLabel name, Component control)
        {
            JPanel row = new JPanel(new BorderLayout());
            row.add(name, BorderLayout.CENTER);
            row.add(control, BorderLayout.EAST);
            contents.add(row);
        }
    }
}
