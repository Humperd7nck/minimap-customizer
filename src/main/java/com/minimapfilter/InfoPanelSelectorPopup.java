package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Compact canvas-anchored selector for Info/Performance modes only. */
final class InfoPanelSelectorPopup
{
    private static final Color BACKGROUND = new Color(35, 35, 35);
    private static final Color ROW = new Color(48, 46, 40);
    private static final Color HOVER = new Color(74, 67, 48);
    private static final Color TEXT = new Color(238, 230, 209);
    private static final Color ACCENT = new Color(184, 153, 88);
    private static final Color SELECTED = new Color(244, 208, 102);

    private final Supplier<InfoPanelText> current;
    private final Consumer<InfoPanelText> select;
    private JDialog dialog;
    private Consumer<Point> positionChanged = point -> { };

    InfoPanelSelectorPopup(Supplier<InfoPanelText> current, Consumer<InfoPanelText> select)
    {
        this.current = Objects.requireNonNull(current);
        this.select = Objects.requireNonNull(select);
    }

    boolean isShowing() { requireEdt(); return dialog != null && dialog.isShowing(); }

    void toggle(Component source, Point anchor)
    {
        Rectangle area = anchor == null ? null : new Rectangle(anchor.x, anchor.y, 1, 1);
        toggle(source, area, null, point -> { });
    }

    void toggle(Component source, Rectangle defaultAnchor, Point remembered, Consumer<Point> moved)
    {
        requireEdt();
        if (isShowing()) { close(); return; }
        open(source, defaultAnchor, remembered, moved);
    }

    void open(Component source, Point anchor)
    {
        Rectangle area = anchor == null ? null : new Rectangle(anchor.x, anchor.y, 1, 1);
        open(source, area, null, point -> { });
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
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-info-selector");
        opened.getRootPane().getActionMap().put("close-info-selector", new AbstractAction()
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
        JPanel root = new JPanel(new BorderLayout());
        root.setName("info-selector-root");
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        JPanel header = new JPanel(new BorderLayout());
        header.setName("info-selector-header");
        header.setOpaque(false);
        JLabel title = new JLabel("Info panel");
        title.setName("info-selector-title");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
        installHeaderDrag(header, title);
        header.add(title, BorderLayout.CENTER);
        JButton close = new JButton("X");
        close.setName("info-selector-close");
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
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setOpaque(false);
        addGroup(list, "Info", InfoPanelDropdown.INFO_OPTIONS);
        addGroup(list, "Performance", InfoPanelDropdown.PERFORMANCE_OPTIONS);
        root.add(list, BorderLayout.CENTER);
        return root;
    }

    private void addGroup(JPanel list, String title, InfoPanelText[] options)
    {
        JLabel heading = new JLabel(title);
        heading.setName("info-selector-heading-" + title.toLowerCase());
        heading.setForeground(SELECTED);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setBorder(BorderFactory.createEmptyBorder(3, 5, 1, 5));
        list.add(heading);
        InfoPanelText selected = current.get();
        for (InfoPanelText option : options)
        {
            JButton button = new JButton(option.toString());
            button.setName("info-selector-" + option.name().toLowerCase());
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFocusable(true);
            button.setForeground(option == selected ? SELECTED : TEXT);
            button.setBackground(ROW);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(option == selected ? ACCENT : ROW),
                BorderFactory.createEmptyBorder(2, 10, 2, 8)));
            button.addChangeListener(event -> button.setBackground(button.getModel().isRollover() ? HOVER : ROW));
            button.addActionListener(event ->
            {
                select.accept(option);
                close();
            });
            list.add(button);
        }
    }

    void close()
    {
        requireEdt();
        JDialog old = dialog;
        dialog = null;
        positionChanged = point -> { };
        if (old != null) { old.dispose(); }
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
        for (JComponent handle : new JComponent[] {header, title})
        {
            handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            handle.setToolTipText("Drag to move");
            handle.addMouseListener(drag);
            handle.addMouseMotionListener(drag);
        }
    }

    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Info selector outside Swing thread"); }
    }
}
