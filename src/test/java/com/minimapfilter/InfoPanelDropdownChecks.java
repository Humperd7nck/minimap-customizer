package com.minimapfilter;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Grouped view/enum persistence and deterministic local/UTC clock checks, without RuneLite dependencies. */
public final class InfoPanelDropdownChecks
{
    static final String[] ROWS = {
        "Info", "Local Time", "Day + Date", "Server Time", "Zone", "Coords", "Coords + Plane", "Current World",
        "Friends Online", "Clanmates Online", "Friends + Clanmates Online", "Inventory", "Total XP", "Total Lvl",
        "-Nothing-", "Performance", "FPS", "Ping", "FPS + Ping"
    };
    private static final EnumSet<InfoPanelText> SELECTABLE = EnumSet.of(
        InfoPanelText.SYSTEM_TIME, InfoPanelText.DAY_DATE, InfoPanelText.SERVER_TIME, InfoPanelText.ZONE, InfoPanelText.COORDS,
        InfoPanelText.COORDS_AND_PLANE, InfoPanelText.CURRENT_WORLD, InfoPanelText.FRIENDS_ONLINE,
        InfoPanelText.CLANMATES_ONLINE, InfoPanelText.FRIENDS_AND_CLANMATES, InfoPanelText.INVENTORY,
        InfoPanelText.TOTAL_XP, InfoPanelText.TOTAL_LEVEL, InfoPanelText.NOTHING, InfoPanelText.FPS, InfoPanelText.PING, InfoPanelText.FPS_AND_PING);
    private int checks;
    public static void main(String[] args) throws Exception
    {
        System.out.println("Grouped dropdown and clock checks passed: " + runAll());
    }
    public static int runAll() throws Exception
    {
        InfoPanelDropdownChecks c = new InfoPanelDropdownChecks();
        c.clocks();
        SwingUtilities.invokeAndWait(c::dropdown);
        SwingUtilities.invokeAndWait(c::canvasSelector);
        return c.checks;
    }
    private void check(String name, boolean value)
    {
        checks++;
        if (!value) { throw new AssertionError(name); }
    }
    private void clocks()
    {
        check("System Time key still loads", InfoPanelText.valueOf("SYSTEM_TIME") == InfoPanelText.SYSTEM_TIME);
        check("System Time visible name changed", InfoPanelText.SYSTEM_TIME.toString().equals("Local Time"));
        String[] oldKeys = {"ZONE", "SYSTEM_TIME", "COORDS", "COORDS_AND_PLANE", "CURRENT_WORLD", "FRIENDS_ONLINE",
            "CLANMATES_ONLINE", "FRIENDS_AND_CLANMATES", "FPS", "PING", "FPS_AND_PING", "INVENTORY", "TOTAL_XP",
            "TOTAL_LEVEL", "TIMER", "STOPWATCH", "CALCULATOR", "VOLUME"};
        for (int i = 0; i < oldKeys.length; i++)
        {
            check("existing enum ordinal and key retained " + oldKeys[i], InfoPanelText.values()[i].name().equals(oldKeys[i]));
        }
        check("server time appended", InfoPanelText.values()[18] == InfoPanelText.SERVER_TIME);
        check("Nothing retained without shifting prior values", InfoPanelText.values()[21] == InfoPanelText.NOTHING
            && InfoPanelText.NOTHING.toString().equals("-Nothing-"));
        check("Day + Date appended without shifting prior values", InfoPanelText.values()[22] == InfoPanelText.DAY_DATE
            && InfoPanelText.DAY_DATE.toString().equals("Day + Date"));
        check("new time not interactive/networked", !InfoPanelText.SERVER_TIME.isTool() && !InfoPanelText.SERVER_TIME.usesPing()
            && !InfoPanelText.SERVER_TIME.usesFriends() && !InfoPanelText.SERVER_TIME.usesClan());
        MutableClock clock = new MutableClock("2026-10-29T23:47:59Z");
        ZoneId[] zone = {ZoneId.of("America/Chicago")};
        SquareHudInfoText text = new SquareHudInfoText(clock, () -> zone[0]);
        check("local time retains twelve-hour format", text.systemTime().text.equals("6:47 PM"));
        check("local tooltip renamed", text.systemTime().tooltip.equals("Local time: 6:47 PM (America/Chicago)"));
        clock.now = Instant.parse("2026-10-30T17:00:00Z");
        check("day and date exact format", text.dayDate().text.equals("Fri · Oct 30"));
        check("day and date provenance", text.dayDate().tooltip.equals("Day and date: Fri · Oct 30 (America/Chicago)"));
        clock.now = Instant.parse("2026-10-29T23:47:59Z");
        check("UTC in 24-hour format", text.serverTime().text.equals("23:47 UTC"));
        check("clock provenance explicit", text.serverTime().tooltip.equals("Server time (UTC): 23:47<br>Based on your computer clock"));
        SquareHudInfoText.Content local = text.systemTime(), utc = text.serverTime();
        check("local minute cache retained", local == text.systemTime());
        check("UTC minute cache", utc == text.serverTime());
        zone[0] = ZoneId.of("Asia/Kolkata");
        check("local follows zone changes", text.systemTime().text.equals("5:17 AM"));
        check("UTC unaffected by local zone", text.serverTime() == utc);
        String[] instants = {"2026-10-30T00:00:00Z", "2027-01-01T00:00:00Z", "2028-02-29T23:59:59Z",
            "2028-03-01T00:00:00Z", "2026-06-01T12:00:00Z", "2026-11-01T05:59:59Z", "2026-11-01T06:00:00Z",
            "2026-03-08T06:59:59Z", "2026-03-08T07:00:00Z", "1969-12-31T23:59:59Z"};
        String[] expected = {"00:00 UTC", "00:00 UTC", "23:59 UTC", "00:00 UTC", "12:00 UTC", "05:59 UTC", "06:00 UTC",
            "06:59 UTC", "07:00 UTC", "23:59 UTC"};
        for (int i = 0; i < instants.length; i++)
        {
            clock.now = Instant.parse(instants[i]);
            for (String id : new String[]{"UTC", "America/Chicago", "Europe/London", "Pacific/Auckland", "Asia/Kolkata"})
            {
                zone[0] = ZoneId.of(id);
                check("UTC fixed offset " + id + " " + instants[i], text.serverTime().text.equals(expected[i]));
                String expectedLocal = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(clock.now.atZone(zone[0]));
                check("local independent cache " + id + " " + instants[i], text.systemTime().text.equals(expectedLocal));
            }
        }
        clock.now = Instant.parse("2026-10-30T10:10:10Z"); utc = text.serverTime();
        clock.now = Instant.parse("2026-10-30T10:09:59Z");
        check("backward clock correction refreshes UTC", text.serverTime() != utc && text.serverTime().text.equals("10:09 UTC"));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dropdown()
    {
        for (InfoPanelText saved : InfoPanelText.values())
        {
            JComboBox<Object> box = new JComboBox<>(InfoPanelText.values());
            box.setSelectedItem(saved);
            ComboBoxModel<Object> original = box.getModel();
            Object renderer = box.getRenderer();
            Object manager = box.getKeySelectionManager();
            int popupListeners = box.getPopupMenuListeners().length;
            int dataListeners = ((AbstractListModel<?>) original).getListDataListeners().length;
            InputMap input = box.getInputMap(); ActionMap actions = box.getActionMap();
            int[] changed = {0};
            box.addItemListener(e ->
            {
                if (e.getStateChange() == ItemEvent.SELECTED)
                {
                    // Mirrors core ConfigPanel's enum cast. Any leaked header/null would fail here.
                    String key = ((Enum<?>) box.getSelectedItem()).name();
                    if (key.isEmpty()) { throw new AssertionError("invalid config value"); }
                    changed[0]++;
                }
            });
            int itemListeners = box.getItemListeners().length;
            InfoPanelDropdown grouped = new InfoPanelDropdown(box);
            check("saved selection retained " + saved, box.getSelectedItem() == saved && changed[0] == 0);
            check("native backing retained " + saved, InfoPanelDropdown.backingModel(box) == original);
            check("native listeners retained " + saved, box.getItemListeners().length == itemListeners);
            check("rows count " + saved, box.getItemCount() == ROWS.length);
            check("recognized after grouping " + saved, InfoPanelDropdown.isInfoPanelBox(box));
            EnumSet<InfoPanelText> values = EnumSet.noneOf(InfoPanelText.class);
            for (int i = 0; i < ROWS.length; i++)
            {
                Object row = box.getItemAt(i);
                check("display order " + i + " " + saved, row.toString().equals(ROWS[i]));
                if (row instanceof InfoPanelText)
                {
                    check("no duplicate option " + saved + "/" + i, values.add((InfoPanelText) row));
                }
                else
                {
                    int before = changed[0];
                    Object selected = box.getSelectedItem();
                    box.setSelectedIndex(i);
                    check("heading cannot be selected " + saved + "/" + i, box.getSelectedItem() == selected && changed[0] == before);
                    box.getModel().setSelectedItem(row);
                    check("model rejects heading " + saved + "/" + i, box.getSelectedItem() == selected);
                }
            }
            check("only Info and Performance represented " + saved, values.equals(SELECTABLE));
            check("Tools omitted from settings menu " + saved, !values.contains(InfoPanelText.TIMER)
                && !values.contains(InfoPanelText.ALARM) && !values.contains(InfoPanelText.STOPWATCH)
                && !values.contains(InfoPanelText.CALCULATOR) && !values.contains(InfoPanelText.NOTES)
                && !values.contains(InfoPanelText.VOLUME));
            box.getModel().setSelectedItem(null);
            box.getModel().setSelectedItem("Performance");
            check("invalid selection blocked " + saved, box.getSelectedItem() == saved);
            box.setSelectedItem(InfoPanelText.SYSTEM_TIME);
            int before = changed[0];
            key(box, KeyEvent.VK_UP);
            check("Info first stays within bounds " + saved, box.getSelectedItem() == InfoPanelText.SYSTEM_TIME);
            box.setSelectedItem(InfoPanelText.TOTAL_LEVEL); key(box, KeyEvent.VK_DOWN);
            check("Down reaches Nothing option " + saved, box.getSelectedItem() == InfoPanelText.NOTHING);
            key(box, KeyEvent.VK_DOWN);
            check("Down skips Performance heading " + saved, box.getSelectedItem() == InfoPanelText.FPS);
            key(box, KeyEvent.VK_HOME);
            check("Home chooses first selectable " + saved, box.getSelectedItem() == InfoPanelText.SYSTEM_TIME);
            key(box, KeyEvent.VK_UP);
            check("Up stays within bounds " + saved, box.getSelectedItem() == InfoPanelText.SYSTEM_TIME);
            key(box, KeyEvent.VK_END);
            check("End chooses last selectable " + saved, box.getSelectedItem() == InfoPanelText.FPS_AND_PING);
            key(box, KeyEvent.VK_DOWN);
            check("Down stays within bounds " + saved, box.getSelectedItem() == InfoPanelText.FPS_AND_PING);
            before = changed[0];
            key(box, KeyEvent.VK_PAGE_UP);
            check("Page Up emits one config change " + saved, changed[0] == before + 1 && box.getSelectedItem() instanceof InfoPanelText);
            before = changed[0];
            key(box, KeyEvent.VK_PAGE_DOWN);
            check("Page Down emits one config change " + saved, changed[0] == before + 1 && box.getSelectedItem() == InfoPanelText.FPS_AND_PING);
            check("type ahead Local Time " + saved, box.selectWithKeyChar('l') && box.getSelectedItem() == InfoPanelText.SYSTEM_TIME);
            box.setEnabled(false); key(box, KeyEvent.VK_END);
            check("disabled keyboard does not change selection " + saved, box.getSelectedItem() == InfoPanelText.SYSTEM_TIME);
            box.setEnabled(true);
            original.setSelectedItem(InfoPanelText.SERVER_TIME);
            check("native reset/model change reflected " + saved, box.getSelectedItem() == InfoPanelText.SERVER_TIME);
            before = changed[0];
            grouped.restore();
            check("restore native model " + saved, box.getModel() == original);
            check("restore native renderer " + saved, box.getRenderer() == renderer);
            check("restore native key map " + saved, box.getInputMap() == input && box.getActionMap() == actions);
            check("restore native key manager " + saved, box.getKeySelectionManager() == manager);
            check("restore retains latest selection " + saved, box.getSelectedItem() == InfoPanelText.SERVER_TIME && changed[0] == before);
            check("no popup listener leak " + saved, box.getPopupMenuListeners().length == popupListeners);
            check("no backing listener leak " + saved, ((AbstractListModel<?>) original).getListDataListeners().length == dataListeners);
            grouped.restore();
            check("double restore harmless " + saved, box.getModel() == original);
        }
        JComboBox<Object> box = new JComboBox<>(InfoPanelText.values());
        InfoPanelDropdown grouped = new InfoPanelDropdown(box);
        JList<Object> list = new JList<>();
        JLabel heading = (JLabel) box.getRenderer().getListCellRendererComponent(list, box.getItemAt(0), 0, true, true);
        check("heading bold", heading.getFont().isBold());
        check("heading background", heading.getBackground().equals(new Color(58,58,58)));
        JLabel item = (JLabel) box.getRenderer().getListCellRendererComponent(list, InfoPanelText.SYSTEM_TIME, 1, false, false);
        check("local time rendered not enum key", item.getText().equals("Local Time"));
        check("option indented", item.getInsets().left == 13);
        JLabel closed = (JLabel) box.getRenderer().getListCellRendererComponent(list, InfoPanelText.SERVER_TIME, -1, false, false);
        check("closed shows option only", closed.getText().equals("Server Time") && closed.getInsets().left == 5);
        grouped.restore();
    }

    private void canvasSelector()
    {
        final InfoPanelText[] selected = {InfoPanelText.COORDS};
        InfoPanelSelectorPopup popup = new InfoPanelSelectorPopup(() -> selected[0], value -> selected[0] = value);
        JPanel root = popup.createView();
        java.util.List<JButton> buttons = new java.util.ArrayList<>();
        collectButtons(root, buttons);
        JButton close = buttons.stream().filter(b -> "info-selector-close".equals(b.getName())).findFirst().orElse(null);
        check("canvas selector has explicit X closeout", close != null && close.getText().equals("X")
            && "Close".equals(close.getToolTipText()));
        java.util.List<JButton> options = new java.util.ArrayList<>();
        for (JButton button : buttons)
        {
            if (button != close) { options.add(button); }
        }
        check("canvas selector has only Info/Performance choices", options.size() == 17);
        check("canvas selector exposes Nothing", options.stream().anyMatch(b -> b.getText().equals("-Nothing-")));
        check("canvas selector exposes Day + Date", options.stream().anyMatch(b -> b.getText().equals("Day + Date")));
        for (JButton button : options)
        {
            check("canvas selector has no tool labels " + button.getText(), !button.getText().equals("Timer")
                && !button.getText().equals("Alarm") && !button.getText().equals("Stopwatch")
                && !button.getText().equals("Calculator") && !button.getText().equals("Notes / Checklist")
                && !button.getText().equals("Volume"));
        }
        JButton nothing = options.stream().filter(b -> b.getText().equals("-Nothing-")).findFirst().orElseThrow();
        nothing.doClick();
        check("canvas selector writes Nothing enum", selected[0] == InfoPanelText.NOTHING);
        JButton ping = options.stream().filter(b -> b.getText().equals("Ping")).findFirst().orElseThrow();
        ping.doClick();
        check("canvas selector writes selected enum", selected[0] == InfoPanelText.PING);
    }

    private static void collectButtons(java.awt.Container container, java.util.List<JButton> out)
    {
        for (java.awt.Component component : container.getComponents())
        {
            if (component instanceof JButton) { out.add((JButton) component); }
            if (component instanceof java.awt.Container) { collectButtons((java.awt.Container) component, out); }
        }
    }
    static void key(JComboBox<?> box, int key)
    {
        Object id = box.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        Action action = box.getActionMap().get(id);
        if (action == null) { throw new AssertionError("no key binding " + key); }
        action.actionPerformed(new ActionEvent(box, ActionEvent.ACTION_PERFORMED, String.valueOf(id)));
    }
    private static final class MutableClock extends Clock
    {
        Instant now;
        MutableClock(String now) { this.now = Instant.parse(now); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
