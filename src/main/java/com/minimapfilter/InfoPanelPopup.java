package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.time.ZoneId;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Focused Swing popup: keyboard input belongs to this window, not the game canvas. */
final class InfoPanelPopup
{
    private static final Color BACKGROUND = new Color(35, 35, 35);
    private static final Color FOREGROUND = new Color(238, 230, 209);
    private static final Color ACCENT = new Color(184, 153, 88);
    private static final Color ERROR = new Color(242, 134, 126);
    private static final Color CALCULATOR_RESULT_BACKGROUND = new Color(21, 23, 26);
    private static final Color CLEAR_KEY = new Color(160, 53, 59);
    private static final Color EQUALS_KEY = new Color(39, 120, 69);
    private static final Color OPERATOR_KEY = new Color(39, 96, 156);
    private final InfoPanelTools tools;
    private final InfoPanelReminders reminders;
    private final Supplier<InfoPanelNotebook> notebookSupplier;
    private final Runnable onClose;
    private final BooleanSupplier acceptsActions;
    private JDialog dialog;
    private Component returnFocus;
    private Timer refresh;
    private Consumer<Point> positionChanged = point -> { };

    InfoPanelPopup(InfoPanelTools tools, Runnable onClose)
    {
        this(tools, onClose, () -> true);
    }

    InfoPanelPopup(InfoPanelTools tools, Runnable onClose, BooleanSupplier acceptsActions)
    {
        this(tools, onClose, acceptsActions, new InfoPanelReminders(tools, message -> { }),
            new Supplier<InfoPanelNotebook>()
            {
                private final InfoPanelNotebook memory = InfoPanelNotebook.memory();
                @Override public InfoPanelNotebook get() { return memory; }
            });
    }

    InfoPanelPopup(InfoPanelTools tools, Runnable onClose, BooleanSupplier acceptsActions,
        InfoPanelReminders reminders, Supplier<InfoPanelNotebook> notebookSupplier)
    {
        this.acceptsActions = Objects.requireNonNull(acceptsActions);
        this.tools = Objects.requireNonNull(tools);
        this.onClose = Objects.requireNonNull(onClose);
        this.reminders = Objects.requireNonNull(reminders);
        this.notebookSupplier = Objects.requireNonNull(notebookSupplier);
    }

    void open(InfoPanelText mode, Component source, Point anchor)
    {
        Rectangle area = anchor == null ? null : new Rectangle(anchor.x, anchor.y, 1, 1);
        open(mode, source, area, null, point -> { });
    }

    void open(InfoPanelText mode, Component source, Rectangle defaultAnchor, Point remembered,
        Consumer<Point> moved)
    {
        requireEdt();
        close(false);
        if (mode == null || !mode.isTool() || GraphicsEnvironment.isHeadless()
            || source == null || !source.isShowing()) { return; }
        returnFocus = source;
        positionChanged = moved == null ? point -> { } : moved;
        Window owner = SwingUtilities.getWindowAncestor(source);
        JDialog opened = new JDialog(owner);
        dialog = opened;
        opened.setTitle("Minimap Customizer - " + mode);
        opened.setModal(false);
        opened.setUndecorated(true);
        opened.setResizable(false);
        opened.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        View view = createView(mode);
        opened.setContentPane(view.panel);
        opened.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-tool");
        opened.getRootPane().getActionMap().put("close-tool", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent e) { close(); }
        });
        opened.addWindowListener(new WindowAdapter()
        {
            @Override public void windowDeactivated(WindowEvent e)
            {
                // Returning to the canvas is handled by its press interceptor so the
                // dismissal click cannot also walk. Other focus transfers close normally.
                if (dialog == opened && e.getOppositeWindow() != owner) { close(false); }
            }
            @Override public void windowClosed(WindowEvent e)
            {
                if (dialog == opened) { close(); }
            }
        });
        opened.pack();
        Rectangle anchor = defaultAnchor == null || defaultAnchor.isEmpty()
            ? new Rectangle(source.getLocationOnScreen(), new Dimension(1, 1)) : new Rectangle(defaultAnchor);
        Point monitorPoint = remembered == null
            ? new Point(anchor.x + anchor.width / 2, anchor.y + anchor.height) : remembered;
        Rectangle usable = screenBounds(source, monitorPoint);
        Rectangle bounds;
        if (remembered != null)
        {
            Point location = dragLocation(usable, opened.getSize(), remembered);
            bounds = new Rectangle(location, opened.getSize());
        }
        else { bounds = placement(usable, opened.getSize(), anchor); }
        opened.setBounds(bounds);
        refresh = new Timer(100, e -> view.refresh.run());
        refresh.setCoalesce(true);
        refresh.start();
        opened.setVisible(true);
        view.focus.requestFocusInWindow();
    }

    boolean isShowing() { requireEdt(); return dialog != null && dialog.isShowing(); }

    void close() { close(true); }

    private void close(boolean restoreFocus)
    {
        requireEdt();
        if (refresh != null) { refresh.stop(); refresh = null; }
        JDialog old = dialog;
        Component source = returnFocus;
        boolean handBack = restoreFocus && source != null && source.isShowing()
            && old != null && old.isFocused();
        dialog = null;
        returnFocus = null;
        positionChanged = point -> { };
        if (old != null)
        {
            old.dispose();
            onClose.run();
            // Escape/Close should leave the canvas ready for the very next toolbar
            // click. Do not focus the game when a different application took focus.
            if (handBack) { source.requestFocusInWindow(); }
        }
    }

    static final class View
    {
        final JPanel panel;
        final Runnable refresh;
        final JComponent focus;
        View(JPanel panel, Runnable refresh, JComponent focus)
        {
            this.panel = panel; this.refresh = refresh; this.focus = focus;
        }
    }

    /** Shared with headless Swing tests; these are the actual controls, not a mockup. */
    View createView(InfoPanelText mode)
    {
        requireEdt();
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setName("info-tool-root");
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JPanel header = row();
        header.setName("info-tool-header");
        header.setLayout(new BorderLayout(6, 0));
        JLabel title = label(mode.toString());
        title.setName("info-tool-title");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        installHeaderDrag(header, title);
        header.add(title, BorderLayout.CENTER);
        header.add(button("Close", "close-tool", this::close), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        View body;
        switch (mode)
        {
            case TIMER: body = timerView(); break;
            case ALARM: body = alarmView(); break;
            case NOTES: body = notesView(); break;
            case STOPWATCH: body = stopwatchView(); break;
            case CALCULATOR: body = calculatorView(); break;
            default: throw new IllegalArgumentException("Not an interactive tool");
        }
        root.add(body.panel, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(Math.max(320, body.panel.getPreferredSize().width + 22), body.panel.getPreferredSize().height + header.getPreferredSize().height + 38));
        return new View(root, body.refresh, body.focus);
    }

    private View timerView()
    {
        InfoPanelTools.Snapshot initial = tools.snapshot();
        long seconds = initial.timerDuration / 1000;
        JPanel panel = column();
        JLabel display = readout("timer-display");
        JLabel status = label(" ");
        status.setName("timer-status");
        JPanel editors = new JPanel(new GridLayout(1, 3, 8, 0));
        editors.setOpaque(false);
        JSpinner hours = spinner((int) (seconds / 3600), 999, "timer-hours");
        JSpinner minutes = spinner((int) ((seconds / 60) % 60), 59, "timer-minutes");
        JSpinner secs = spinner((int) (seconds % 60), 59, "timer-seconds");
        editors.add(spinnerGroup("Hours", hours));
        editors.add(spinnerGroup("Minutes", minutes));
        editors.add(spinnerGroup("Seconds", secs));
        JLabel error = label(" ");
        error.setForeground(ERROR);
        error.setName("timer-error");
        JButton start = button("Start", "timer-start", () ->
        {
            try
            {
                hours.commitEdit(); minutes.commitEdit(); secs.commitEdit();
                long value = ((Number) hours.getValue()).longValue() * 3600
                    + ((Number) minutes.getValue()).longValue() * 60 + ((Number) secs.getValue()).longValue();
                tools.startTimer(value);
                reminders.dismiss(InfoPanelText.TIMER);
                error.setText(" ");
            }
            catch (ParseException | IllegalArgumentException ex)
            {
                error.setText("Enter a duration of at least 1 second.");
            }
        });
        JButton pause = button("Resume", "timer-pause", () ->
        {
            if (tools.snapshot().timerRunning) { tools.pauseTimer(); }
            else { tools.resumeTimer(); }
        });
        JButton reset = button("Reset", "timer-reset", () ->
        { tools.resetTimer(); reminders.dismiss(InfoPanelText.TIMER); });
        JCheckBox notify = new JCheckBox("Notify when finished", initial.notifications);
        notify.setName("timer-notify");
        notify.setOpaque(false);
        notify.setForeground(FOREGROUND);
        notify.setToolTipText("Uses RuneLite's notification settings, including its sound and focus preferences.");
        notify.addActionListener(e ->
        {
            if (acceptsActions.getAsBoolean()) { tools.setNotifications(notify.isSelected()); }
        });
        panel.add(display); panel.add(status); panel.add(editors);
        ReminderControls alerts = reminderControls(InfoPanelText.TIMER);
        panel.add(buttonRow(start, pause, reset)); panel.add(notify); panel.add(error);
        panel.add(alerts.panel); panel.add(notificationHelp());
        alignColumn(panel);
        panel.setPreferredSize(new Dimension(298, Math.max(295,panel.getPreferredSize().height+6)));
        Runnable repaint = () ->
        {
            InfoPanelTools.Snapshot state = tools.snapshot();
            display.setText(InfoPanelFormatting.duration(state.timerRemaining, true, false));
            status.setText(state.timerFinished ? "Finished" : state.timerRunning ? "Running"
                : state.timerDuration == 0 ? "Set a duration, then Start" : "Paused / ready");
            start.setText(state.timerDuration == 0 ? "Start" : "Restart");
            pause.setText(state.timerRunning ? "Pause" : "Resume");
            pause.setEnabled(state.timerRemaining > 0 && !state.timerFinished);
            reset.setEnabled(state.timerDuration > 0);
            alerts.refresh.run();
        };
        repaint.run();
        return new View(panel, repaint, hours);
    }


    private static final class ReminderControls
    {
        final JPanel panel;
        final Runnable refresh;
        ReminderControls(JPanel panel, Runnable refresh) { this.panel=panel; this.refresh=refresh; }
    }

    private ReminderControls reminderControls(InfoPanelText mode)
    {
        String prefix=mode==InfoPanelText.TIMER ? "timer" : "alarm";
        JPanel p=column();
        JLabel delivery=label(" "); delivery.setName(prefix+"-delivery");
        delivery.setFont(delivery.getFont().deriveFont(11f));
        p.add(delivery);
        Runnable update=() ->
        {
            InfoPanelReminders.Notice notice=reminders.notice(mode);
            delivery.setText(notice==null ? " " : notice.status.startsWith("RuneLite alert delivery failed")
                ? "System alert failed; see client.log" : "Alert sent; RuneLite settings still apply");
            delivery.setToolTipText(notice==null ? null : notice.status);
        };
        alignColumn(p);
        update.run();
        return new ReminderControls(p,update);
    }

    private static JLabel notificationHelp()
    {
        JLabel help=label("<html>Sound/system alerts: Settings &gt; Alarm/timer alerts.<br>Enable notifications when focused if needed.</html>");
        help.setFont(help.getFont().deriveFont(11f));
        help.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
        return help;
    }

    private View alarmView()
    {
        InfoPanelAlarm alarm=reminders.alarm;
        InfoPanelAlarm.Snapshot initial=alarm.snapshot();
        JPanel panel=column();
        JLabel display=readout("alarm-display");
        display.setFont(display.getFont().deriveFont(23f));
        JLabel status=label(" "); status.setName("alarm-status");
        JLabel next=label(" "); next.setName("alarm-next"); next.setFont(next.getFont().deriveFont(11f));
        JPanel editors=new JPanel(new GridLayout(1,3,6,0)); editors.setOpaque(false);
        JSpinner hours=spinner(initial.hour,23,"alarm-hours");
        JSpinner minutes=spinner(initial.minute,59,"alarm-minutes");
        hours.setEditor(new JSpinner.NumberEditor(hours,"00")); minutes.setEditor(new JSpinner.NumberEditor(minutes,"00"));
        JComboBox<InfoPanelAlarm.TimeZone> zone=new JComboBox<>(InfoPanelAlarm.TimeZone.values());
        zone.setName("alarm-zone"); zone.setSelectedItem(initial.choice);
        JPanel zoneGroup=column(); zoneGroup.add(label("Time zone")); zoneGroup.add(zone); alignColumn(zoneGroup);
        editors.add(spinnerGroup("Hour (0-23)",hours)); editors.add(spinnerGroup("Minute",minutes)); editors.add(zoneGroup);
        JTextField name=new JTextField(initial.label); name.setName("alarm-label");
        name.setToolTipText("Optional alarm label, up to 60 characters"); limit(name,InfoPanelAlarm.MAX_LABEL);
        JLabel error=label(" "); error.setName("alarm-error"); error.setForeground(ERROR);
        JButton set=button("Set alarm","alarm-set",() ->
        {
            try
            {
                hours.commitEdit(); minutes.commitEdit();
                alarm.arm(((Number)hours.getValue()).intValue(),((Number)minutes.getValue()).intValue(),
                    (InfoPanelAlarm.TimeZone)zone.getSelectedItem(),ZoneId.systemDefault(),name.getText());
                reminders.dismiss(InfoPanelText.ALARM); error.setText(" ");
            }
            catch(ParseException | IllegalArgumentException ex) { error.setText("Enter a valid hour and minute."); }
        });
        JButton cancel=button("Cancel alarm","alarm-cancel",() -> { alarm.cancel(); reminders.dismiss(InfoPanelText.ALARM); });
        JCheckBox notify=new JCheckBox("Notify when due",initial.notifications);
        notify.setName("alarm-notify"); notify.setOpaque(false); notify.setForeground(FOREGROUND);
        notify.addActionListener(e -> { if(acceptsActions.getAsBoolean()) { alarm.setNotifications(notify.isSelected()); } });
        ReminderControls alerts=reminderControls(InfoPanelText.ALARM);
        panel.add(display); panel.add(status); panel.add(next); panel.add(editors);
        panel.add(label("Label (optional)")); panel.add(name); panel.add(buttonRow(set,cancel));
        panel.add(notify); panel.add(error); panel.add(alerts.panel); panel.add(notificationHelp());
        JLabel limits=label("<html>One-shot; next occurrence of this time.<br>Runs only while this plugin is enabled.</html>");
        limits.setFont(limits.getFont().deriveFont(11f)); panel.add(limits);
        alignColumn(panel);
        panel.setPreferredSize(new Dimension(320,Math.max(365,panel.getPreferredSize().height+6)));
        Runnable update=() ->
        {
            InfoPanelAlarm.Snapshot a=alarm.snapshot();
            display.setText(a.fired ? "Alarm finished" : a.armed ? a.time() : "Not set");
            status.setText(a.fired ? "Finished" : a.armed ? "Armed" : "Choose a time, then Set alarm");
            next.setText(a.target==null ? " " : a.date()); next.setToolTipText(a.target==null ? null : a.date());
            cancel.setEnabled(a.armed || a.fired);
            alerts.refresh.run();
        };
        update.run(); return new View(panel,update,hours);
    }

    private View notesView()
    {
        // Capture the profile-bound instance. Profile switching deactivates this instance.
        InfoPanelNotebook notebook=notebookSupplier.get();
        JPanel panel=new JPanel(new BorderLayout(5,6)); panel.setOpaque(false);
        JTabbedPane tabs=new JTabbedPane(); tabs.setName("notes-tabs");
        tabs.setBackground(BACKGROUND); tabs.setForeground(FOREGROUND);
        JTextArea notes=new JTextArea(10,26); notes.setName("notes-text");
        notes.setLineWrap(true); notes.setWrapStyleWord(true);
        notes.setBackground(CALCULATOR_RESULT_BACKGROUND); notes.setForeground(FOREGROUND);
        notes.setCaretColor(FOREGROUND); notes.setMargin(new Insets(7,7,7,7));
        notes.setToolTipText("Autosaved locally for this RuneLite profile; up to 16,000 characters.");
        limit(notes,InfoPanelNotebook.MAX_NOTES);
        tabs.addTab("Notes",new JScrollPane(notes));
        JPanel checklist=new JPanel(new BorderLayout(4,5)); checklist.setBackground(BACKGROUND);
        DefaultListModel<InfoPanelNotebook.Task> model=new DefaultListModel<>();
        JList<InfoPanelNotebook.Task> list=new JList<>(model); list.setName("notes-tasks");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(26);
        list.setBackground(BACKGROUND); list.setForeground(FOREGROUND);
        list.setCellRenderer((items,value,index,selected,focus) ->
        {
            JCheckBox row=new JCheckBox(value.text,value.done);
            row.putClientProperty("html.disable",Boolean.TRUE);
            row.setOpaque(true); row.setForeground(value.done ? new Color(155,155,155) : FOREGROUND);
            row.setBackground(selected ? new Color(64,69,76) : BACKGROUND);
            row.setToolTipText(value.text); return row;
        });
        checklist.add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel editor=column();
        JTextField task=new JTextField(); task.setName("notes-task-input"); limit(task,InfoPanelNotebook.MAX_TASK);
        task.setToolTipText("Type a task and Add, or select one and Update.");
        JLabel error=label(" "); error.setName("notes-error"); error.setForeground(ERROR);
        Runnable add=() ->
        {
            if(!acceptsActions.getAsBoolean()) { return; }
            try { notebook.add(task.getText()); task.setText(""); error.setText(" "); }
            catch(IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        };
        JButton addButton=button("Add","notes-add",add);
        JButton edit=button("Update","notes-update",() ->
        {
            try { notebook.edit(list.getSelectedIndex(),task.getText()); error.setText(" "); }
            catch(IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        });
        JButton remove=button("Remove","notes-remove",() ->
        { notebook.remove(list.getSelectedIndex()); task.setText(""); error.setText(" "); });
        task.addActionListener(e -> add.run());
        editor.add(label("Task")); editor.add(task); editor.add(buttonRow(addButton,edit,remove)); editor.add(error);
        alignColumn(editor);
        checklist.add(editor,BorderLayout.SOUTH);
        tabs.addTab("Checklist",checklist);
        JLabel status=label("Loading..."); status.setName("notes-status"); status.setFont(status.getFont().deriveFont(11f));
        JButton retry=button("Retry","notes-retry",notebook::retry);
        JPanel footer=new JPanel(new BorderLayout(4,0)); footer.setOpaque(false);
        footer.add(status,BorderLayout.CENTER); footer.add(retry,BorderLayout.EAST);
        panel.add(tabs,BorderLayout.CENTER); panel.add(footer,BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(350,310));
        boolean[] initialized={false}, populating={false};
        long[] shownRevision={-1};
        notes.getDocument().addDocumentListener(new DocumentListener()
        {
            private void changed()
            {
                if(initialized[0]&&!populating[0]&&acceptsActions.getAsBoolean()) { notebook.setNotes(notes.getText()); }
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        list.addListSelectionListener(e ->
        {
            if(!e.getValueIsAdjusting()&&!populating[0]&&list.getSelectedValue()!=null)
            { task.setText(list.getSelectedValue().text); }
        });
        Runnable toggle=() -> { if(acceptsActions.getAsBoolean()) { notebook.toggle(list.getSelectedIndex()); } };
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"toggle-task");
        list.getActionMap().put("toggle-task",new AbstractAction()
        { @Override public void actionPerformed(ActionEvent e) { toggle.run(); } });
        list.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)
            {
                int index=list.locationToIndex(e.getPoint());
                if(SwingUtilities.isLeftMouseButton(e) && index>=0 && list.getCellBounds(index,index).contains(e.getPoint())
                    && (e.getX()<26 || e.getClickCount()==2)) { list.setSelectedIndex(index); toggle.run(); }
            }
        });
        Runnable update=() ->
        {
            InfoPanelNotebook.Snapshot n=notebook.snapshot();
            if(n.loaded && !initialized[0])
            {
                populating[0]=true; notes.setText(n.notes); notes.setCaretPosition(0); populating[0]=false; initialized[0]=true;
            }
            if(n.loaded && shownRevision[0]!=n.revision)
            {
                populating[0]=true;
                int selection=list.getSelectedIndex(); model.clear(); for(InfoPanelNotebook.Task t:n.tasks) { model.addElement(t); }
                if(selection>=0 && selection<model.size()) { list.setSelectedIndex(selection); }
                populating[0]=false; shownRevision[0]=n.revision;
            }
            notes.setEditable(n.editable); task.setEnabled(n.editable); list.setEnabled(n.editable);
            addButton.setEnabled(n.editable && n.tasks.size()<InfoPanelNotebook.MAX_TASKS);
            edit.setEnabled(n.editable && list.getSelectedIndex()>=0);
            remove.setEnabled(n.editable && list.getSelectedIndex()>=0);
            tabs.setTitleAt(1,"Checklist "+n.completed()+"/"+n.tasks.size());
            status.setText(n.status); status.setToolTipText(n.status);
            retry.setVisible(n.status.contains("failed") || n.status.contains("Could not"));
        };
        update.run(); return new View(panel,update,notes);
    }

    private static void limit(javax.swing.text.JTextComponent field,int maximum)
    {
        ((AbstractDocument)field.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override public void insertString(FilterBypass b,int offset,String text,AttributeSet a) throws BadLocationException
            { replace(b,offset,0,text,a); }
            @Override public void replace(FilterBypass b,int offset,int length,String text,AttributeSet a) throws BadLocationException
            {
                if(b.getDocument().getLength()-length+(text==null?0:text.length())<=maximum)
                { super.replace(b,offset,length,text,a); }
            }
        });
    }

    private View stopwatchView()
    {
        JPanel panel = column();
        JLabel display = readout("stopwatch-display");
        JButton toggle = button("Start", "stopwatch-toggle", () ->
        {
            if (tools.snapshot().stopwatchRunning) { tools.pauseStopwatch(); }
            else { tools.startStopwatch(); }
        });
        JButton lap = button("Lap", "stopwatch-lap", () -> tools.lap());
        JButton reset = button("Reset", "stopwatch-reset", tools::resetStopwatch);
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        list.setName("stopwatch-laps");
        list.setVisibleRowCount(4);
        list.setBackground(BACKGROUND);
        list.setForeground(FOREGROUND);
        JLabel hint = label("Laps: split time (total elapsed)");
        panel.add(display); panel.add(buttonRow(toggle, lap, reset));
        panel.add(hint); panel.add(new JScrollPane(list));
        panel.setPreferredSize(new Dimension(298, 190));
        int[] previousLaps = {-1};
        Runnable repaint = () ->
        {
            InfoPanelTools.Snapshot state = tools.snapshot();
            display.setText(InfoPanelFormatting.duration(state.stopwatchElapsed, false, true));
            toggle.setText(state.stopwatchRunning ? "Pause" : state.stopwatchElapsed == 0 ? "Start" : "Resume");
            lap.setEnabled(state.stopwatchRunning && state.laps.size() < InfoPanelTools.MAX_LAPS);
            lap.setToolTipText("Up to 100 laps per stopwatch run");
            if (previousLaps[0] != state.laps.size())
            {
                model.clear();
                for (int i = state.laps.size() - 1; i >= 0; i--)
                {
                    long total = state.laps.get(i);
                    long split = total - (i == 0 ? 0 : state.laps.get(i - 1));
                    model.addElement((i + 1) + ": " + InfoPanelFormatting.duration(split, false, true)
                        + " (" + InfoPanelFormatting.duration(total, false, true) + ")");
                }
                previousLaps[0] = state.laps.size();
            }
        };
        repaint.run();
        return new View(panel, repaint, toggle);
    }

    private View calculatorView()
    {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        InfoPanelTools.Snapshot initial = tools.snapshot();
        JTextField input = new JTextField(initial.expression);
        input.setName("calculator-input");
        input.setToolTipText("Numbers, decimals, + - * /, and parentheses. Enter evaluates; Backspace deletes; Escape closes.");
        JTextField result = new JTextField();
        result.setName("calculator-result");
        result.setEditable(false);
        result.setHorizontalAlignment(SwingConstants.RIGHT);
        result.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        result.setOpaque(true);
        result.setBackground(CALCULATOR_RESULT_BACKGROUND);
        result.setForeground(FOREGROUND);
        result.setSelectedTextColor(Color.WHITE);
        result.setSelectionColor(OPERATOR_KEY);
        result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        result.setPreferredSize(new Dimension(298, 60));
        result.getAccessibleContext().setAccessibleName("Calculator result");
        showCalculatorResult(result, initial.answer);
        JLabel status = label(initial.calculatorError.isEmpty() ? " " : initial.calculatorError);
        status.setName("calculator-error");
        status.setForeground(ERROR);
        status.setToolTipText(initial.calculatorError);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override public void insertString(FilterBypass b, int offset, String text, AttributeSet a) throws BadLocationException
            {
                replace(b, offset, 0, text, a);
            }
            @Override public void replace(FilterBypass b, int offset, int length, String text, AttributeSet a) throws BadLocationException
            {
                if (b.getDocument().getLength() - length + (text == null ? 0 : text.length()) <= InfoPanelCalculator.MAX_INPUT)
                {
                    super.replace(b, offset, length, text, a);
                }
                else { status.setText("Maximum 256 characters."); }
            }
        });
        Runnable evaluate = () ->
        {
            if (!acceptsActions.getAsBoolean()) { return; }
            tools.calculate(input.getText());
            InfoPanelTools.Snapshot state = tools.snapshot();
            showCalculatorResult(result, state.answer);
            status.setText(state.calculatorError.isEmpty() ? " " : state.calculatorError);
            status.setToolTipText(state.calculatorError);
            input.requestFocusInWindow();
        };
        input.addActionListener(e -> evaluate.run());
        top.add(input, BorderLayout.NORTH);
        top.add(result, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        JPanel keypad = new JPanel(new GridLayout(5, 4, 4, 4));
        keypad.setOpaque(false);
        String[] keys = {"7", "8", "9", "/", "4", "5", "6", "*", "1", "2", "3", "-",
            "0", ".", "Back", "+", "(", ")", "Clear", "="};
        for (String key : keys)
        {
            keypad.add(calculatorButton(key, () ->
            {
                if (key.equals("=")) { evaluate.run(); }
                else if (key.equals("Clear"))
                {
                    input.setText(""); tools.clearCalculator();
                    showCalculatorResult(result, tools.snapshot().answer);
                    status.setText(" "); status.setToolTipText("");
                }
                else if (key.equals("Back"))
                {
                    int start = input.getSelectionStart();
                    int end = input.getSelectionEnd();
                    if (start == end && start > 0) { start--; }
                    input.select(start, end);
                    input.replaceSelection("");
                }
                else { input.replaceSelection(key); }
                input.requestFocusInWindow();
            }));
        }
        panel.add(keypad, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(298, 310));
        return new View(panel, () -> { }, input);
    }

    private static void showCalculatorResult(JTextField result, java.math.BigDecimal value)
    {
        String text = InfoPanelCalculator.display(value);
        result.setText(text);
        result.setToolTipText("Result: " + text);
        // A read-only field remains selectable/copyable and scrolls for long results.
        result.setCaretPosition(text.length());
    }

    private JButton calculatorButton(String key, Runnable action)
    {
        Color color = key.equals("Clear") || key.equals("Back") ? CLEAR_KEY : key.equals("=") ? EQUALS_KEY
            : key.equals("/") || key.equals("*") || key.equals("-") || key.equals("+")
                ? OPERATOR_KEY : null;
        JButton button = color == null ? new JButton(key) : new CalculatorKeyButton(key, color);
        if (key.equals("Back")) { button.setToolTipText("Delete selection or the character before the cursor"); }
        else if (key.equals("Clear")) { button.setToolTipText("Clear the calculation and result"); }
        return wireButton(button, "calc-key-" + key, action);
    }

    /** Paint only these key backgrounds/text ourselves: client themes may ignore setBackground. */
    private static final class CalculatorKeyButton extends JButton
    {
        CalculatorKeyButton(String text, Color background)
        {
            super(text);
            setBackground(background);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        }

        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color fill = getBackground();
                if (!isEnabled()) { fill = new Color(70, 70, 70); }
                else if (getModel().isPressed() && getModel().isArmed()) { fill = fill.darker(); }
                else if (getModel().isRollover()) { fill = fill.brighter(); }
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
                g.setColor(fill.brighter());
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                if (hasFocus())
                {
                    g.setColor(getForeground());
                    g.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 5, 5);
                }
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(isEnabled() ? getForeground() : new Color(170, 170, 170));
                g.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2,
                    (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            finally { g.dispose(); }
        }
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
                offset = null;
                startLocation = null;
                target = null;
                if (!SwingUtilities.isLeftMouseButton(event) || !acceptsActions.getAsBoolean()) { return; }
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
                if (offset == null || target == null || target != dialog || !target.isShowing()
                    || !acceptsActions.getAsBoolean()) { return; }
                Point screen = event.getLocationOnScreen();
                Rectangle usable = screenBounds(target, screen);
                Point requested = new Point(screen.x - offset.x, screen.y - offset.y);
                target.setLocation(dragLocation(usable, target.getSize(), requested));
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
                offset = null;
                startLocation = null;
                target = null;
            }
        };
        // Only the title and header whitespace are drag handles. Inputs, keypad,
        // spinners, lap list and Close retain their normal mouse interactions.
        for (JComponent handle : new JComponent[] {header, title})
        {
            handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            handle.setToolTipText("Drag to move");
            handle.addMouseListener(drag);
            handle.addMouseMotionListener(drag);
        }
    }

    static Point dragLocation(Rectangle usable, Dimension size, Point requested)
    {
        // Never resize a tool during a drag. Keep it on the pointer's monitor;
        // for unusually small displays, at least its header stays reachable.
        int x = Math.max(usable.x, Math.min(requested.x,
            usable.x + Math.max(0, usable.width - size.width)));
        int y = Math.max(usable.y, Math.min(requested.y,
            usable.y + Math.max(0, usable.height - size.height)));
        return new Point(x, y);
    }

    static Rectangle placement(Rectangle usable, Dimension desired, Rectangle anchor)
    {
        Rectangle area = anchor == null ? new Rectangle(usable.x, usable.y, 1, 1) : anchor;
        int w = Math.max(1, Math.min(desired.width, usable.width));
        int h = Math.max(1, Math.min(desired.height, usable.height));
        int x = area.x + area.width / 2 - w / 2;
        x = Math.max(usable.x, Math.min(x, usable.x + usable.width - w));
        int y = area.y + area.height + 6;
        if ((long) y + h > (long) usable.y + usable.height) { y = area.y - h - 6; }
        y = Math.max(usable.y, Math.min(y, usable.y + usable.height - h));
        return new Rectangle(x, y, w, h);
    }

    static Rectangle placement(Rectangle usable, Dimension desired, Point anchor)
    {
        int w = Math.max(1, Math.min(desired.width, usable.width));
        int h = Math.max(1, Math.min(desired.height, usable.height));
        int x = Math.max(usable.x, Math.min(anchor.x - w + 12, usable.x + usable.width - w));
        int y = anchor.y + 12;
        if ((long) y + h > (long) usable.y + usable.height) { y = anchor.y - h - 12; }
        y = Math.max(usable.y, Math.min(y, usable.y + usable.height - h));
        return new Rectangle(x, y, w, h);
    }

    static Rectangle screenBounds(Component owner, Point point)
    {
        GraphicsConfiguration config = owner.getGraphicsConfiguration();
        for (java.awt.GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
        {
            GraphicsConfiguration candidate = device.getDefaultConfiguration();
            if (candidate.getBounds().contains(point)) { config = candidate; break; }
        }
        Rectangle bounds = new Rectangle(config.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom;
        return bounds;
    }
    private static void alignColumn(JPanel panel)
    {
        for(Component c:panel.getComponents())
        {
            if(c instanceof JComponent) { ((JComponent)c).setAlignmentX(Component.LEFT_ALIGNMENT); }
        }
    }
    private static JPanel row() { JPanel p = new JPanel(); p.setOpaque(false); return p; }
    private static JPanel column()
    {
        JPanel p = row(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p;
    }
    private static JLabel label(String text) { JLabel l = new JLabel(text); l.setForeground(FOREGROUND); return l; }
    private static JLabel readout(String name)
    {
        JLabel l = label("00:00"); l.setName(name); l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0)); return l;
    }
    private JButton button(String text, String name, Runnable action)
    {
        return wireButton(new JButton(text), name, action);
    }
    private JButton wireButton(JButton b, String name, Runnable action)
    {
        b.setName(name);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        b.addActionListener(e ->
        {
            if (name.equals("close-tool") || acceptsActions.getAsBoolean()) { action.run(); }
        });
        return b;
    }
    private static JPanel buttonRow(JButton... buttons)
    {
        JPanel p = new JPanel(new GridLayout(1, buttons.length, 6, 0)); p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        for (JButton b : buttons) { p.add(b); }
        return p;
    }
    private static JSpinner spinner(int value, int max, String name)
    {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, 0, max, 1)); s.setName(name);
        s.setEditor(new JSpinner.NumberEditor(s, "0")); return s;
    }
    private static JPanel spinnerGroup(String title, JSpinner spinner)
    {
        JPanel p = column(); p.add(label(title)); p.add(spinner); alignColumn(p); return p;
    }
    private static void requireEdt()
    {
        if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Tool windows belong to the Swing thread"); }
    }
}
