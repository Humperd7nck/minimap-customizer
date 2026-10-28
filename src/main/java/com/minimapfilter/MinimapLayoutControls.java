package com.minimapfilter;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import org.slf4j.LoggerFactory;

/**
 * Core ConfigItem has no conditional enabled state. This small Swing adapter
 * recognizes our enum models and Looting bag section, using public component APIs. No private
 * ConfigPanel fields, config rewrites or replacement settings window are used.
 */
@Singleton
final class MinimapLayoutControls
{
    private static final Color DISABLED = new Color(128, 128, 128);
    private final MinimapFilterConfig config;
    private final LootingBagColorControls lootingBagControls;
    private final Map<JComponent, SavedState> changed = new IdentityHashMap<>();
    private Timer timer;
    private boolean running;

    @Inject
    MinimapLayoutControls(MinimapFilterConfig config)
    {
        this.config = config;
        this.lootingBagControls = new LootingBagColorControls(config);
    }

    void start()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (running) { return; }
            running = true;
            timer = new Timer(200, event -> refresh());
            timer.setCoalesce(true);
            timer.start();
            refresh();
        });
    }

    void settingsChanged()
    {
        SwingUtilities.invokeLater(() -> { if (running) { refresh(); } });
    }

    void stop()
    {
        SwingUtilities.invokeLater(() ->
        {
            running = false;
            if (timer != null) { timer.stop(); timer = null; }
            restoreAll();
        });
    }

    private void refresh()
    {
        try
        {
            Set<JComponent> disabled = Collections.newSetFromMap(new IdentityHashMap<>());
            java.util.List<Container> roots = new ArrayList<>();
            for (Window window : Window.getWindows())
            {
                if (window.isShowing())
                {
                    roots.add(window);
                    if (!config.enableSquareMinimap()) { collect(window, disabled, 0); }
                    if (!config.showPlayers()) { collectWildernessRow(window, disabled, 0); }
                }
            }
            apply(disabled);
            lootingBagControls.update(roots, config.groundItemMarkerStyle() == GroundItemMarkerStyle.LOOTING_BAG);
        }
        catch (RuntimeException ex)
        {
            // A future UI redesign must not break the plugin or leave controls stuck.
            if (timer != null) { timer.stop(); }
            restoreAll();
            running = false;
            LoggerFactory.getLogger(MinimapLayoutControls.class)
                .warn("Conditional setting styling was disabled after a settings UI error", ex);
        }
    }

    // Also used by headless Swing tests with the same enum-backed controls as core.
    void updateTree(Container root, boolean squareEnabled)
    {
        requireEdt();
        Set<JComponent> disabled = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!squareEnabled && root != null) { collect(root, disabled, 0); }
        if (!config.showPlayers() && root != null) { collectWildernessRow(root, disabled, 0); }
        apply(disabled);
        lootingBagControls.update(root == null ? Collections.emptyList() : Collections.singletonList(root),
            config.groundItemMarkerStyle() == GroundItemMarkerStyle.LOOTING_BAG);
    }

    private void collect(Component component, Set<JComponent> disabled, int depth)
    {
        if (component == null || depth > 40) { return; }
        if (component instanceof JComboBox<?>)
        {
            JComboBox<?> box = (JComboBox<?>) component;
            Object value = box.getItemCount() == 0 ? null : box.getItemAt(0);
            String label = value instanceof SquareHudFooterMode ? "Info panel view"
                : value instanceof InfoPanelText ? "Info panel text" : null;
            if (label != null)
            {
                disabled.add(box);
                Container row = box.getParent();
                if (row != null)
                {
                    for (Component sibling : row.getComponents())
                    {
                        if (sibling instanceof JLabel && label.equals(((JLabel) sibling).getText()))
                        {
                            disabled.add((JLabel) sibling);
                        }
                    }
                }
            }
            // A combobox's popup/list editor does not contain another config row.
            return;
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                collect(child, disabled, depth + 1);
            }
        }
    }

    private void collectWildernessRow(Component component, Set<JComponent> disabled, int depth)
    {
        if (component == null || depth > 40) { return; }
        if (component instanceof JLabel)
        {
            JLabel label = (JLabel) component;
            if ("Wilderness player skulls".equals(label.getText()) && label.getParent() != null
                && label.getToolTipText() != null
                && label.getToolTipText().contains("Requires Other players"))
            {
                for (Component sibling : label.getParent().getComponents())
                {
                    if (sibling instanceof JCheckBox)
                    {
                        disabled.add(label);
                        disabled.add((JCheckBox) sibling);
                    }
                }
            }
        }
        if (component instanceof Container && !(component instanceof JComboBox<?>))
        {
            for (Component child : ((Container) component).getComponents())
            {
                collectWildernessRow(child, disabled, depth + 1);
            }
        }
    }

    private void apply(Set<JComponent> disabled)
    {
        requireEdt();
        for (JComponent component : new ArrayList<>(changed.keySet()))
        {
            if (!disabled.contains(component)) { restore(component); }
        }
        for (JComponent component : disabled)
        {
            if (!changed.containsKey(component))
            {
                changed.put(component, new SavedState(component));
            }
            if (component instanceof JComboBox<?>)
            {
                JComboBox<?> box = (JComboBox<?>) component;
                if (box.isPopupVisible()) { box.setPopupVisible(false); }
            }
            component.setEnabled(false);
            if (component instanceof JLabel) { component.setForeground(DISABLED); }
        }
    }

    void restoreAll()
    {
        requireEdt();
        for (JComponent component : new ArrayList<>(changed.keySet())) { restore(component); }
        lootingBagControls.restoreAll();
    }

    private void restore(JComponent component)
    {
        SavedState old = changed.remove(component);
        if (old == null) { return; }
        component.setEnabled(old.enabled);
        if (component instanceof JLabel)
        {
            component.setForeground(old.foreground);
            for (MouseListener listener : old.removed)
            {
                boolean present = false;
                for (MouseListener current : component.getMouseListeners()) { present |= current == listener; }
                if (!present) { component.addMouseListener(listener); }
            }
        }
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            throw new IllegalStateException("Settings controls must be updated on the Swing thread");
        }
    }

    private static final class SavedState
    {
        final boolean enabled;
        final Color foreground;
        final java.util.List<MouseListener> removed = new ArrayList<>();

        SavedState(JComponent component)
        {
            enabled = component.isEnabled();
            foreground = component.getForeground();
            if (component instanceof JLabel)
            {
                // Core's clickable label opens a reset menu even when disabled.
                // Suspend that interaction too, but leave tooltip handling alone.
                for (MouseListener listener : component.getMouseListeners())
                {
                    if (!(listener instanceof ToolTipManager))
                    {
                        removed.add(listener);
                        component.removeMouseListener(listener);
                    }
                }
            }
        }
    }
}
