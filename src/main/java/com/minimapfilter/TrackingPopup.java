package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Compact draggable manager for non-destructive minimap visibility overrides. */
final class TrackingPopup
{
    enum Category
    {
        ENTITIES("Entities"),
        GROUND_ITEMS("Ground items"),
        MAP_ICONS("Map icons"),
        FOLIAGE("Foliage / Trees");

        final String label;
        Category(String label) { this.label = label; }
    }

    interface Model
    {
        boolean all();
        boolean stored(Category category);
        void toggleAll();
        void toggle(Category category);
    }

    private static final Color BACKGROUND = new Color(35, 35, 35);
    private static final Color ROW = new Color(48, 46, 40);
    private static final Color HOVER = new Color(74, 67, 48);
    private static final Color TEXT = new Color(238, 230, 209);
    private static final Color MUTED = new Color(139, 134, 121);
    private static final Color ACCENT = new Color(184, 153, 88);
    private static final Color SELECTED = new Color(244, 208, 102);

    private final Model model;
    private final Runnable closed;
    private final Map<Category, JButton> categoryButtons = new EnumMap<>(Category.class);
    private JButton allButton;
    private JDialog dialog;
    private Consumer<Point> positionChanged = point -> { };

    TrackingPopup(Model model) { this(model, () -> { }); }
    TrackingPopup(Model model, Runnable closed)
    {
        this.model = java.util.Objects.requireNonNull(model);
        this.closed = closed == null ? () -> { } : closed;
    }

    boolean isShowing() { requireEdt(); return dialog != null && dialog.isShowing(); }

    void toggle(Component source, Rectangle defaultAnchor, Point remembered, Consumer<Point> moved)
    {
        requireEdt();
        if (isShowing()) { close(); return; }
        open(source, defaultAnchor, remembered, moved);
    }

    void open(Component source, Rectangle defaultAnchor, Point remembered, Consumer<Point> moved)
    {
        requireEdt();
        close();
        if (GraphicsEnvironment.isHeadless() || source == null || !source.isShowing()) { return; }
        positionChanged = moved == null ? point -> { } : moved;
        Window owner = SwingUtilities.getWindowAncestor(source);
        JDialog opened = new JDialog(owner);
        dialog = opened;
        opened.setModal(false);
        opened.setUndecorated(true);
        opened.setResizable(false);
        opened.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        opened.setContentPane(createView());
        opened.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-tracking");
        opened.getRootPane().getActionMap().put("close-tracking", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent event) { close(); }
        });
        opened.addWindowListener(new WindowAdapter()
        {
            @Override public void windowClosed(WindowEvent event) { if (dialog == opened) { dialog = null; } }
        });
        opened.pack();
        Rectangle anchor = defaultAnchor == null || defaultAnchor.isEmpty()
            ? new Rectangle(source.getLocationOnScreen(), new Dimension(1, 1)) : new Rectangle(defaultAnchor);
        Point monitorPoint = remembered == null
            ? new Point(anchor.x + anchor.width / 2, anchor.y + anchor.height) : remembered;
        Rectangle screen = InfoPanelPopup.screenBounds(source, monitorPoint);
        Rectangle bounds;
        if (remembered != null)
        {
            Point location = InfoPanelPopup.dragLocation(screen, opened.getSize(), remembered);
            bounds = new Rectangle(location, opened.getSize());
        }
        else { bounds = InfoPanelPopup.placement(screen, opened.getSize(), anchor); }
        opened.setBounds(bounds);
        opened.setVisible(true);
    }

    JPanel createView()
    {
        categoryButtons.clear();
        JPanel root = new JPanel(new BorderLayout(0, 3));
        root.setName("tracking-root");
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        JPanel header = new JPanel(new BorderLayout());
        header.setName("tracking-header");
        header.setOpaque(false);
        JLabel title = new JLabel("Tracking");
        title.setName("tracking-title");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
        installHeaderDrag(header, title);
        header.add(title, BorderLayout.CENTER);
        JButton close = new JButton("X");
        close.setName("tracking-close");
        close.setFocusable(false);
        close.setForeground(TEXT);
        close.setBackground(ROW);
        close.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        close.setToolTipText("Close");
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addChangeListener(event -> close.setBackground(close.getModel().isRollover() ? HOVER : ROW));
        close.addActionListener(event -> close());
        header.add(close, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setName("tracking-list");
        list.setOpaque(false);
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        allButton = row("All tracking", true, null);
        list.add(allButton);
        JPanel divider = new JPanel();
        divider.setName("tracking-divider");
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(200, 1));
        divider.setBackground(new Color(91, 82, 63));
        list.add(javax.swing.Box.createVerticalStrut(3));
        list.add(divider);
        list.add(javax.swing.Box.createVerticalStrut(3));
        for (Category category : Category.values())
        {
            JButton button = row(category.label, false, category);
            categoryButtons.put(category, button);
            list.add(button);
            if (category != Category.FOLIAGE) { list.add(javax.swing.Box.createVerticalStrut(2)); }
        }
        root.add(list, BorderLayout.CENTER);
        refreshRows();
        return root;
    }

    private JButton row(String label, boolean master, Category category)
    {
        JButton button = new JButton(label);
        button.setName(master ? "tracking-all" : "tracking-" + category.name().toLowerCase(java.util.Locale.ROOT));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(8);
        button.setFocusable(true);
        button.setBackground(ROW);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ROW),
            BorderFactory.createEmptyBorder(4, 7, 4, 10)));
        button.setPreferredSize(new Dimension(205, 30));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.addChangeListener(event -> button.setBackground(button.getModel().isRollover() && button.isEnabled() ? HOVER : ROW));
        button.addActionListener(event ->
        {
            if (master) { model.toggleAll(); }
            else { model.toggle(category); }
            refreshRows();
        });
        return button;
    }

    private void refreshRows()
    {
        boolean master = model.all();
        if (allButton != null) { style(allButton, master, true, false); }
        for (Map.Entry<Category, JButton> entry : categoryButtons.entrySet())
        {
            boolean effective = master && model.stored(entry.getKey());
            style(entry.getValue(), effective, false, !master);
        }
    }

    private static void style(JButton button, boolean open, boolean master, boolean disabled)
    {
        button.setEnabled(!disabled || master);
        button.setForeground(disabled ? MUTED : open ? SELECTED : TEXT);
        button.setIcon(new EyeIcon(open, disabled));
        button.setToolTipText(master
            ? (open ? "Hide all tracked minimap content" : "Restore tracked minimap content")
            : disabled ? "Turn on All tracking to change this category"
                : (open ? "Hide without changing detailed settings" : "Show with previous detailed settings"));
    }

    void close()
    {
        requireEdt();
        JDialog old = dialog;
        dialog = null;
        positionChanged = point -> { };
        if (old != null) { old.dispose(); closed.run(); }
    }

    private void installHeaderDrag(JComponent header, JComponent title)
    {
        MouseAdapter drag = new MouseAdapter()
        {
            private Point offset;
            private Point startLocation;
            private Window target;

            @Override public void mousePressed(MouseEvent event)
            {
                offset = null; startLocation = null; target = null;
                if (!SwingUtilities.isLeftMouseButton(event)) { return; }
                Window window = SwingUtilities.getWindowAncestor(event.getComponent());
                if (window == null || window != dialog || !window.isShowing()) { return; }
                Point screen = event.getLocationOnScreen();
                offset = new Point(screen.x - window.getX(), screen.y - window.getY());
                startLocation = window.getLocation();
                target = window;
                event.consume();
            }

            @Override public void mouseDragged(MouseEvent event)
            {
                if (offset == null || target == null || target != dialog || !target.isShowing()) { return; }
                Point screen = event.getLocationOnScreen();
                Rectangle usable = InfoPanelPopup.screenBounds(target, screen);
                Point requested = new Point(screen.x - offset.x, screen.y - offset.y);
                target.setLocation(InfoPanelPopup.dragLocation(usable, target.getSize(), requested));
                event.consume();
            }

            @Override public void mouseReleased(MouseEvent event)
            {
                if (!SwingUtilities.isLeftMouseButton(event)) { return; }
                if (target != null)
                {
                    if (startLocation != null && !startLocation.equals(target.getLocation()))
                    { positionChanged.accept(new Point(target.getLocation())); }
                    event.consume();
                }
                offset = null; startLocation = null; target = null;
            }
        };
        for (JComponent handle : new JComponent[]{header, title})
        {
            handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            handle.setToolTipText("Drag to move");
            handle.addMouseListener(drag);
            handle.addMouseMotionListener(drag);
        }
    }

    private static final class EyeIcon implements Icon
    {
        private final boolean open;
        private final boolean muted;
        EyeIcon(boolean open, boolean muted) { this.open = open; this.muted = muted; }
        @Override public int getIconWidth() { return 22; }
        @Override public int getIconHeight() { return 18; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            InfoPanelEyeOrb.paintGlyph((Graphics2D) graphics, new Rectangle(x, y, getIconWidth(), getIconHeight()), open, false, muted);
        }
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Tracking popup outside Swing thread"); }
    }
}
