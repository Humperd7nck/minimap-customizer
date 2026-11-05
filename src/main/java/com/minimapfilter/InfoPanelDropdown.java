package com.minimapfilter;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.accessibility.Accessible;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;

/** A grouped presentation of the original enum model. Headings never reach configuration listeners. */
final class InfoPanelDropdown
{
    static final InfoPanelText[] INFO_OPTIONS = {InfoPanelText.SYSTEM_TIME, InfoPanelText.DAY_DATE, InfoPanelText.SERVER_TIME,
        InfoPanelText.ZONE, InfoPanelText.COORDS, InfoPanelText.COORDS_AND_PLANE, InfoPanelText.CURRENT_WORLD,
        InfoPanelText.FRIENDS_ONLINE, InfoPanelText.CLANMATES_ONLINE, InfoPanelText.FRIENDS_AND_CLANMATES,
        InfoPanelText.INVENTORY, InfoPanelText.TOTAL_XP, InfoPanelText.TOTAL_LEVEL, InfoPanelText.NOTHING};
    static final InfoPanelText[] PERFORMANCE_OPTIONS = {InfoPanelText.FPS, InfoPanelText.PING, InfoPanelText.FPS_AND_PING};
    private static final InfoPanelText[][] GROUPS = {INFO_OPTIONS, PERFORMANCE_OPTIONS};
    private static final String[] TITLES = {"Info", "Performance"};

    private final JComboBox<Object> box;
    private final ComboBoxModel<Object> originalModel;
    private final ListCellRenderer<? super Object> originalRenderer;
    private final JComboBox.KeySelectionManager originalKeys;
    private final InputMap originalInput;
    private final ActionMap originalActions;
    private final int originalRowCount;
    private final GroupedModel model;
    private final ListCellRenderer<Object> renderer = new Renderer();
    private final InputMap input = new InputMap();
    private final ActionMap actions = new ActionMap();
    private JList<?> guardedList;
    private MouseListener[] listMouseListeners;
    private MouseListener mouseGuard;
    private boolean headerPress;
    private String keyPrefix = "";
    private long lastKey;
    private final JComboBox.KeySelectionManager keys = this::keySelection;

    private final PopupMenuListener popupListener = new PopupMenuListener()
    {
        @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { guardHeadingClicks(); }
        @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { restoreListMouse(); }
        @Override public void popupMenuCanceled(PopupMenuEvent event) { restoreListMouse(); }
    };

    @SuppressWarnings("unchecked")
    InfoPanelDropdown(JComboBox<?> target)
    {
        requireEdt();
        box = (JComboBox<Object>) target;
        originalModel = box.getModel();
        originalRenderer = box.getRenderer();
        originalKeys = box.getKeySelectionManager();
        originalInput = box.getInputMap(JComponent.WHEN_FOCUSED);
        originalActions = box.getActionMap();
        originalRowCount = box.getMaximumRowCount();
        model = new GroupedModel(originalModel);
        if (box.isPopupVisible()) { box.setPopupVisible(false); }
        box.setModel(model);
        box.setRenderer(renderer);
        box.setKeySelectionManager(keys);
        box.setMaximumRowCount(model.getSize());
        input.setParent(originalInput);
        actions.setParent(originalActions);
        bind(KeyEvent.VK_DOWN, "next", () -> navigate(1, false));
        bind(KeyEvent.VK_UP, "previous", () -> navigate(-1, false));
        bind(KeyEvent.VK_HOME, "first", () -> navigate(1, true));
        bind(KeyEvent.VK_END, "last", () -> navigate(-1, true));
        bind(KeyEvent.VK_PAGE_DOWN, "pageNext", () -> page(1));
        bind(KeyEvent.VK_PAGE_UP, "pagePrevious", () -> page(-1));
        bind(KeyEvent.VK_ENTER, "accept", this::accept);
        bind(KeyEvent.VK_ESCAPE, "cancel", () -> box.setPopupVisible(false));
        box.setInputMap(JComponent.WHEN_FOCUSED, input);
        box.setActionMap(actions);
        box.addPopupMenuListener(popupListener);
        box.setToolTipText(String.valueOf(box.getSelectedItem()));
    }

    static boolean isInfoPanelBox(JComboBox<?> box)
    {
        if (box.getModel() instanceof GroupedModel) { return true; }
        return box.getItemCount() > 0 && box.getItemAt(0) instanceof InfoPanelText;
    }

    static Object backingModel(JComboBox<?> box)
    {
        return box.getModel() instanceof GroupedModel ? ((GroupedModel) box.getModel()).backing : box.getModel();
    }

    void restore()
    {
        requireEdt();
        box.setPopupVisible(false);
        restoreListMouse();
        box.removePopupMenuListener(popupListener);
        if (box.getInputMap(JComponent.WHEN_FOCUSED) == input) { box.setInputMap(JComponent.WHEN_FOCUSED, originalInput); }
        if (box.getActionMap() == actions) { box.setActionMap(originalActions); }
        if (box.getKeySelectionManager() == keys) { box.setKeySelectionManager(originalKeys); }
        if (box.getRenderer() == renderer) { box.setRenderer(originalRenderer); }
        if (box.getModel() == model) { box.setModel(originalModel); }
        box.setMaximumRowCount(originalRowCount);
        model.detach();
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Dropdown update outside Swing thread"); }
    }

    private void bind(int key, String name, Runnable action)
    {
        String id = "minimap.info." + name;
        input.put(KeyStroke.getKeyStroke(key, 0), id);
        actions.put(id, new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent event)
            {
                if (box.isEnabled() && box.getModel() == model) { action.run(); }
            }
        });
    }

    private JList<?> popupList()
    {
        if (box.getUI() == null) { return null; }
        Accessible child = box.getUI().getAccessibleChild(box, 0);
        return child instanceof ComboPopup ? ((ComboPopup) child).getList() : null;
    }

    private int highlightedIndex()
    {
        JList<?> list = box.isPopupVisible() ? popupList() : null;
        return list != null && list.getSelectedIndex() >= 0
            ? list.getSelectedIndex() : model.indexOf(box.getSelectedItem());
    }

    private void navigate(int direction, boolean edge)
    {
        int index = edge ? (direction > 0 ? -1 : model.getSize()) : highlightedIndex();
        if (index < 0 && direction < 0) { index = model.getSize(); }
        int next = model.next(index, direction);
        if (next < 0) { return; }
        selectNavigation(next);
    }

    private void selectNavigation(int index)
    {
        JList<?> list = box.isPopupVisible() ? popupList() : null;
        if (list != null)
        {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        }
        else { box.setSelectedIndex(index); }
    }

    private void page(int direction)
    {
        int index = highlightedIndex();
        for (int i = 0; i < 5; i++)
        {
            int next = model.next(index, direction);
            if (next < 0) { break; }
            index = next;
        }
        if (index >= 0 && model.getElementAt(index) instanceof InfoPanelText) { selectNavigation(index); }
    }

    private void accept()
    {
        if (!box.isPopupVisible()) { box.setPopupVisible(true); return; }
        JList<?> list = popupList();
        Object value = list == null ? null : list.getSelectedValue();
        if (value instanceof InfoPanelText) { box.setSelectedItem(value); }
        box.setPopupVisible(false);
    }

    private int keySelection(char key, ComboBoxModel<?> ignored)
    {
        if (Character.isISOControl(key)) { return -1; }
        long now = System.nanoTime();
        String letter = String.valueOf(key).toLowerCase(Locale.ROOT);
        boolean repeated = keyPrefix.equals(letter);
        if (now - lastKey > 1_000_000_000L || repeated) { keyPrefix = letter; }
        else { keyPrefix += letter; }
        lastKey = now;
        int start = model.indexOf(box.getSelectedItem());
        // A continued prefix may match the current item; repeated letters cycle.
        int from = keyPrefix.length() > 1 ? 0 : 1;
        for (int offset = from; offset < model.getSize() + from; offset++)
        {
            int i = Math.floorMod(start + offset, model.getSize());
            Object value = model.getElementAt(i);
            if (value instanceof InfoPanelText && value.toString().toLowerCase(Locale.ROOT).startsWith(keyPrefix)) { return i; }
        }
        keyPrefix = letter;
        for (int offset = 1; offset <= model.getSize(); offset++)
        {
            int i = Math.floorMod(start + offset, model.getSize());
            Object value = model.getElementAt(i);
            if (value instanceof InfoPanelText && value.toString().toLowerCase(Locale.ROOT).startsWith(letter)) { return i; }
        }
        return -1;
    }

    private boolean headingAt(MouseEvent event)
    {
        if (guardedList == null) { return false; }
        int index = guardedList.locationToIndex(event.getPoint());
        Rectangle bounds = index < 0 ? null : guardedList.getCellBounds(index, index);
        return bounds != null && bounds.contains(event.getPoint()) && model.getElementAt(index) instanceof Heading;
    }

    private void guardHeadingClicks()
    {
        restoreListMouse();
        guardedList = popupList();
        if (guardedList == null) { return; } // Model guard still prevents invalid selections on an unfamiliar UI.
        listMouseListeners = guardedList.getMouseListeners();
        for (MouseListener listener : listMouseListeners) { guardedList.removeMouseListener(listener); }
        mouseGuard = new MouseListener()
        {
            private void dispatch(MouseEvent event, int kind)
            {
                MouseListener[] delegates = listMouseListeners;
                if (kind == MouseEvent.MOUSE_PRESSED) { headerPress = headingAt(event); }
                if ((kind == MouseEvent.MOUSE_PRESSED || kind == MouseEvent.MOUSE_RELEASED || kind == MouseEvent.MOUSE_CLICKED)
                    && (headerPress || headingAt(event)))
                {
                    event.consume();
                    if (kind == MouseEvent.MOUSE_CLICKED) { headerPress = false; }
                    return;
                }
                if (delegates == null) { return; }
                for (MouseListener listener : delegates)
                {
                    switch (kind)
                    {
                        case MouseEvent.MOUSE_PRESSED: listener.mousePressed(event); break;
                        case MouseEvent.MOUSE_RELEASED: listener.mouseReleased(event); break;
                        case MouseEvent.MOUSE_CLICKED: listener.mouseClicked(event); break;
                        case MouseEvent.MOUSE_ENTERED: listener.mouseEntered(event); break;
                        case MouseEvent.MOUSE_EXITED: listener.mouseExited(event); break;
                        default: break;
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e) { dispatch(e, MouseEvent.MOUSE_PRESSED); }
            @Override public void mouseReleased(MouseEvent e) { dispatch(e, MouseEvent.MOUSE_RELEASED); }
            @Override public void mouseClicked(MouseEvent e) { dispatch(e, MouseEvent.MOUSE_CLICKED); }
            @Override public void mouseEntered(MouseEvent e) { dispatch(e, MouseEvent.MOUSE_ENTERED); }
            @Override public void mouseExited(MouseEvent e) { dispatch(e, MouseEvent.MOUSE_EXITED); }
        };
        guardedList.addMouseListener(mouseGuard);
    }

    private void restoreListMouse()
    {
        if (guardedList != null && listMouseListeners != null)
        {
            if (mouseGuard != null) { guardedList.removeMouseListener(mouseGuard); }
            for (MouseListener listener : listMouseListeners) { guardedList.addMouseListener(listener); }
        }
        guardedList = null;
        listMouseListeners = null;
        mouseGuard = null;
        headerPress = false;
    }

    private static final class Heading
    {
        final String title;
        Heading(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private static final class GroupedModel extends AbstractListModel<Object> implements ComboBoxModel<Object>, ListDataListener
    {
        final ComboBoxModel<Object> backing;
        final List<Object> rows = new ArrayList<>();
        GroupedModel(ComboBoxModel<Object> backing)
        {
            this.backing = backing;
            for (int group = 0; group < GROUPS.length; group++)
            {
                rows.add(new Heading(TITLES[group]));
                for (InfoPanelText option : GROUPS[group]) { if (contains(option)) { rows.add(option); } }
            }
            backing.addListDataListener(this);
        }
        private boolean contains(Object option)
        {
            for (int i = 0; i < backing.getSize(); i++) { if (backing.getElementAt(i) == option) { return true; } }
            return false;
        }
        @Override public int getSize() { return rows.size(); }
        @Override public Object getElementAt(int index) { return rows.get(index); }
        @Override public Object getSelectedItem() { return backing.getSelectedItem(); }
        @Override public void setSelectedItem(Object item)
        {
            if (item instanceof InfoPanelText && contains(item)) { backing.setSelectedItem(item); }
        }
        int indexOf(Object option) { return rows.indexOf(option); }
        int next(int index, int direction)
        {
            for (int i = index + direction; i >= 0 && i < rows.size(); i += direction)
            {
                if (rows.get(i) instanceof InfoPanelText) { return i; }
            }
            return -1;
        }
        @Override public void contentsChanged(ListDataEvent event) { fireContentsChanged(this, -1, -1); }
        @Override public void intervalAdded(ListDataEvent event) { contentsChanged(event); }
        @Override public void intervalRemoved(ListDataEvent event) { contentsChanged(event); }
        void detach() { backing.removeListDataListener(this); }
    }

    private static final class Renderer extends DefaultListCellRenderer
    {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
        {
            boolean heading = value instanceof Heading;
            super.getListCellRendererComponent(list, value, index, !heading && selected, !heading && focus);
            setText(value == null ? "" : value.toString());
            setFont(list.getFont().deriveFont(heading ? Font.BOLD : Font.PLAIN));
            setBorder(BorderFactory.createEmptyBorder(heading ? 3 : 2, heading || index < 0 ? 5 : 13, heading ? 3 : 2, 5));
            if (heading)
            {
                setBackground(new Color(58, 58, 58));
                setForeground(new Color(235, 235, 235));
            }
            getAccessibleContext().setAccessibleName(heading ? value + " section heading" : getText());
            return this;
        }
    }
}
