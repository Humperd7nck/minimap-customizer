package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Stacks only our enum-backed Info Panel selector, retaining core's controls and listeners. */
final class InfoPanelSettingLayout
{
    private static final String LABEL = "Info panel text";
    private final Map<Container, SavedRow> changed = new IdentityHashMap<>();

    void update(Iterable<? extends Container> roots)
    {
        requireEdt();
        Set<Container> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container root : roots) { collect(root, found, 0); }
        for (Container row : new ArrayList<>(changed.keySet()))
        {
            if (!found.contains(row)) { restore(row); }
        }
    }

    private void collect(Component component, Set<Container> found, int depth)
    {
        if (component == null || depth > 40) { return; }
        if (component instanceof JComboBox<?>)
        {
            JComboBox<?> box = (JComboBox<?>) component;
            if (InfoPanelDropdown.isInfoPanelBox(box))
            {
                Container row = box.getParent();
                SavedRow existing = changed.get(row);
                if (existing != null && existing.matches(row, box))
                {
                    found.add(row);
                }
                else if (existing == null && row != null && row.getComponentCount() == 2
                    && row.getLayout() instanceof BorderLayout)
                {
                    BorderLayout original = (BorderLayout) row.getLayout();
                    Component centre = original.getLayoutComponent(BorderLayout.CENTER);
                    if (centre instanceof JLabel && LABEL.equals(((JLabel) centre).getText())
                        && original.getLayoutComponent(BorderLayout.EAST) == box)
                    {
                        SavedRow saved = new SavedRow(original, (JLabel) centre, box);
                        changed.put(row, saved);
                        saved.apply(row);
                        found.add(row);
                    }
                }
            }
            // Do not descend into popup/editor internals or other enum controls.
            return;
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                collect(child, found, depth + 1);
            }
        }
    }

    void restoreAll()
    {
        requireEdt();
        for (Container row : new ArrayList<>(changed.keySet())) { restore(row); }
    }

    private void restore(Container row)
    {
        SavedRow saved = changed.remove(row);
        if (saved == null) { return; }
        if (saved.grouped != null) { saved.grouped.restore(); }
        if (row.getLayout() == saved.stacked)
        {
            row.setLayout(saved.original);
        }
        if ((LABEL + ":").equals(saved.label.getText())) { saved.label.setText(saved.labelText); }
        row.revalidate();
        row.repaint();
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            throw new IllegalStateException("Settings layout must be updated on the Swing thread");
        }
    }

    private static final class SavedRow
    {
        final BorderLayout original;
        final BorderLayout stacked = new BorderLayout(0, 4);
        final JLabel label;
        final JComboBox<?> box;
        final String labelText;
        InfoPanelDropdown grouped;

        SavedRow(BorderLayout original, JLabel label, JComboBox<?> box)
        {
            this.original = original;
            this.label = label;
            this.box = box;
            this.labelText = label.getText();
        }

        boolean matches(Container row, JComboBox<?> current)
        {
            return current == box && label.getParent() == row && row.getComponentCount() == 2
                && row.getLayout() == stacked;
        }

        void apply(Container row)
        {
            // Keep the real controls and configuration listeners; group only their presentation.
            // The original BorderLayout still holds its exact constraints for restoration.
            stacked.addLayoutComponent(label, BorderLayout.NORTH);
            stacked.addLayoutComponent(box, BorderLayout.CENTER);
            row.setLayout(stacked);
            label.setText(LABEL + ":");
            grouped = new InfoPanelDropdown(box);
            row.revalidate();
            row.repaint();
        }
    }
}
