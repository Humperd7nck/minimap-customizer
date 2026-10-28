package com.minimapfilter;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.components.ColorJButton;

/** Conditional state for this one native settings section, using public Swing APIs. */
final class LootingBagColorControls
{
    static final String TITLE = "Looting bag colors (ground item icon)";
    static final String TOGGLE = "Color Looting bag icons by value";
    private static final String TITLE_WRAPPED = "<html>Looting bag colors<br>(ground item icon)</html>";
    private static final String TOGGLE_WRAPPED = "<html>Color Looting bag icons<br>by value</html>";
    private static final String[] COLOR_LABELS = {
        "0–999 gp", "1k–9,999 gp", "10k–99,999 gp", "100k–999,999 gp",
        "1m–9,999,999 gp", "10m+ gp"
    };
    private static final Color GRAY_TEXT = new Color(128, 128, 128);
    private static final Color GRAY_SWATCH = new Color(70, 70, 70);
    private final MinimapFilterConfig config;
    private final Map<JComponent, Saved> disabled = new IdentityHashMap<>();
    private final Map<JLabel, String> wrapped = new IdentityHashMap<>();

    LootingBagColorControls(MinimapFilterConfig config)
    {
        this.config = config;
    }

    void update(List<? extends Container> roots, boolean bagSelected)
    {
        requireEdt();
        Set<JComponent> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JLabel> labels = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container root : roots) { collect(root, bagSelected, wanted, labels, 0); }
        for (JComponent component : new ArrayList<>(disabled.keySet()))
        {
            if (!wanted.contains(component)) { restore(component); }
        }
        for (JLabel label : new ArrayList<>(wrapped.keySet()))
        {
            if (!labels.contains(label)) { label.setText(wrapped.remove(label)); }
        }
        for (JComponent component : wanted)
        {
            disabled.computeIfAbsent(component, Saved::new);
            component.setEnabled(false);
            // ColorJButton paints its own swatch even when disabled. Its setColor
            // changes only the local button view, not the persisted configuration.
            if (component instanceof ColorJButton)
            {
                ((ColorJButton) component).setColor(GRAY_SWATCH);
            }
            if (component instanceof JLabel || component instanceof AbstractButton)
            {
                component.setForeground(GRAY_TEXT);
            }
        }
    }

    private void collect(Component component, boolean selected, Set<JComponent> wanted,
        Set<JLabel> labels, int depth)
    {
        if (component == null || depth > 40) { return; }
        if (component instanceof JLabel)
        {
            JLabel title = (JLabel) component;
            if (TITLE.equals(title.getText()) || TITLE_WRAPPED.equals(title.getText()))
            {
                Container header = title.getParent();
                Container section = header == null ? null : header.getParent();
                // Match the complete, distinctive section structure. Never disable
                // another plugin's controls merely because one label is similar.
                if (validSection(section, header))
                {
                    wrap(title, TITLE_WRAPPED, labels);
                    wrapToggle(section, labels);
                    if (!selected) { disableTree(section, wanted, 0); }
                    return;
                }
            }
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                collect(child, selected, wanted, labels, depth + 1);
            }
        }
    }

    private boolean validSection(Container section, Container header)
    {
        if (section == null || section.getComponentCount() != 2 || header == null) { return false; }
        Container contents = null;
        for (Component child : section.getComponents())
        {
            if (child != header && child instanceof Container) { contents = (Container) child; }
        }
        if (contents == null || contents.getComponentCount() != 7) { return false; }
        boolean toggle = false;
        boolean[] colors = new boolean[COLOR_LABELS.length];
        for (Component child : contents.getComponents())
        {
            if (!(child instanceof Container)) { return false; }
            String label = rowLabel((Container) child);
            if (TOGGLE.equals(label) || TOGGLE_WRAPPED.equals(label))
            {
                toggle = hasChild((Container) child, JCheckBox.class);
            }
            for (int i = 0; i < COLOR_LABELS.length; i++)
            {
                if (COLOR_LABELS[i].equals(label))
                {
                    colors[i] = hasChild((Container) child, ColorJButton.class);
                }
            }
        }
        if (!toggle) { return false; }
        for (boolean color : colors) { if (!color) { return false; } }
        return true;
    }

    private static boolean hasChild(Container container, Class<?> type)
    {
        for (Component child : container.getComponents()) { if (type.isInstance(child)) { return true; } }
        return false;
    }

    private static String rowLabel(Container row)
    {
        if (row != null)
        {
            for (Component child : row.getComponents())
            {
                if (child instanceof JLabel) { return ((JLabel) child).getText(); }
            }
        }
        return "";
    }

    private void wrapToggle(Container section, Set<JLabel> labels)
    {
        for (Component child : section.getComponents())
        {
            if (child instanceof JLabel)
            {
                JLabel label = (JLabel) child;
                if (TOGGLE.equals(label.getText()) || TOGGLE_WRAPPED.equals(label.getText()))
                {
                    wrap(label, TOGGLE_WRAPPED, labels);
                }
            }
            else if (child instanceof Container) { wrapToggle((Container) child, labels); }
        }
    }

    private void wrap(JLabel label, String text, Set<JLabel> labels)
    {
        labels.add(label);
        if (!wrapped.containsKey(label)) { wrapped.put(label, label.getText()); }
        if (!text.equals(label.getText())) { label.setText(text); }
    }

    private static void disableTree(Component component, Set<JComponent> wanted, int depth)
    {
        if (component == null || depth > 20) { return; }
        if (component instanceof JComponent) { wanted.add((JComponent) component); }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents()) { disableTree(child, wanted, depth + 1); }
        }
    }

    void restoreAll()
    {
        requireEdt();
        for (JComponent component : new ArrayList<>(disabled.keySet())) { restore(component); }
        for (Map.Entry<JLabel, String> entry : wrapped.entrySet()) { entry.getKey().setText(entry.getValue()); }
        wrapped.clear();
    }

    private void restore(JComponent component)
    {
        Saved old = disabled.remove(component);
        if (old == null) { return; }
        component.setEnabled(old.enabled);
        component.setForeground(old.foreground);
        if (component instanceof ColorJButton)
        {
            Color color = currentColor(rowLabel(component.getParent()));
            ((ColorJButton) component).setColor(color == null ? old.color : color);
        }
        for (MouseListener listener : old.listeners)
        {
            boolean present = false;
            for (MouseListener now : component.getMouseListeners()) { present |= listener == now; }
            if (!present) { component.addMouseListener(listener); }
        }
    }

    private Color currentColor(String label)
    {
        for (int i = 0; i < COLOR_LABELS.length; i++)
        {
            if (COLOR_LABELS[i].equals(label)) { return LootingBagValueTier.values()[i].selectedColor(config); }
        }
        return null;
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Use the Swing thread"); }
    }

    private static final class Saved
    {
        final boolean enabled;
        final Color foreground;
        final Color color;
        final List<MouseListener> listeners = new ArrayList<>();

        Saved(JComponent component)
        {
            enabled = component.isEnabled();
            foreground = component.getForeground();
            color = component instanceof ColorJButton ? ((ColorJButton) component).getColor() : null;
            // Native section headers, reset labels and color buttons have explicit
            // mouse listeners that ignore isEnabled(). Suspend those as well.
            for (MouseListener listener : component.getMouseListeners())
            {
                if (!(listener instanceof ToolTipManager))
                {
                    listeners.add(listener);
                    component.removeMouseListener(listener);
                }
            }
            if (component instanceof AbstractButton)
            {
                ((AbstractButton) component).getModel().setArmed(false);
                ((AbstractButton) component).getModel().setPressed(false);
                ((AbstractButton) component).getModel().setRollover(false);
            }
        }
    }
}
