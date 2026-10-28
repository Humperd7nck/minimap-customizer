package com.minimapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/** Deterministic clock/coordinate, migration and real Swing-component checks. */
public final class SquareHudInfoPanelChecks
{
    private int cases;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Info panel checks passed: " + runAll());
    }

    public static int runAll() throws Exception
    {
        SquareHudInfoPanelChecks c = new SquareHudInfoPanelChecks();
        c.defaults();
        c.clock();
        c.text();
        c.migrations();
        SwingUtilities.invokeAndWait(c::controls);
        return c.cases;
    }

    private void check(String name, boolean condition)
    {
        cases++;
        if (!condition) { throw new AssertionError(name); }
    }

    private void defaults() throws Exception
    {
        MinimapFilterConfig config = new MinimapFilterConfig() {};
        check("square off default", !config.enableSquareMinimap());
        check("compass on default", config.showCompass());
        check("Always view default", config.squareHudFooterMode() == SquareHudFooterMode.ALWAYS_VISIBLE);
        check("Zone text default", config.infoPanelText() == InfoPanelText.ZONE);
        check("Always label", SquareHudFooterMode.ALWAYS_VISIBLE.toString().equals("Always"));
        check("Mouseover label", SquareHudFooterMode.MOUSEOVER_ONLY.toString().equals("Mouseover only"));
        check("exactly three content choices", InfoPanelText.values().length == 3);
        check("System Time label", InfoPanelText.SYSTEM_TIME.toString().equals("System Time"));
        check("Coords label", InfoPanelText.COORDS.toString().equals("Coords"));
        String[] names = {"Show compass", "Enable square minimap", "Info panel view", "Info panel text"};
        int count = 0;
        for (Method method : MinimapFilterConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null) { continue; }
            check("no visible legacy appearance item", !item.keyName().equals("minimapAppearance"));
            if (MinimapFilterConfig.LAYOUT.equals(item.section()))
            {
                count++;
                check("layout order " + method.getName(), item.position() >= 0 && item.position() < names.length
                    && names[item.position()].equals(item.name()));
                check("layout row visible " + method.getName(), !item.hidden());
            }
        }
        check("exactly four Layout rows", count == 4);
        ConfigSection layout = MinimapFilterConfig.class.getField("LAYOUT").getAnnotation(ConfigSection.class);
        check("Layout renamed", layout.name().equals("Layout"));
        check("Layout starts expanded", !layout.closedByDefault());
        for (Field field : MinimapFilterConfig.class.getFields())
        {
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null && !field.getName().equals("LAYOUT"))
            {
                check("Layout first before " + field.getName(), layout.position() < section.position());
            }
        }
        ConfigItem view = MinimapFilterConfig.class.getMethod("squareHudFooterMode").getAnnotation(ConfigItem.class);
        check("view storage key preserved", view.keyName().equals("squareHudFooterMode"));
    }

    private void clock()
    {
        MutableClock time = new MutableClock("2026-10-21T01:25:59Z");
        ZoneId[] zone = {ZoneId.of("America/Chicago")};
        SquareHudInfoText info = new SquareHudInfoText(time, () -> zone[0]);
        SquareHudInfoText.Content a = info.systemTime();
        check("computer-local rather than UTC", a.text.equals("8:25 PM"));
        check("time zone in tooltip", a.tooltip.contains("America/Chicago"));
        check("same minute uses cached text", info.systemTime() == a);
        time.instant = Instant.parse("2026-10-21T01:26:00Z");
        check("minute boundary updates", info.systemTime().text.equals("8:26 PM"));
        time.instant = Instant.parse("2026-10-21T00:01:00Z");
        check("backward clock correction", info.systemTime().text.equals("7:01 PM"));
        zone[0] = ZoneOffset.UTC;
        check("zone change noticed within minute", info.systemTime().text.equals("12:01 AM"));
        time.instant = Instant.parse("2026-10-21T12:00:00Z");
        check("noon", info.systemTime().text.equals("12:00 PM"));
        time.instant = Instant.parse("2026-10-22T00:00:00Z");
        check("midnight", info.systemTime().text.equals("12:00 AM"));
        zone[0] = ZoneOffset.ofHoursMinutes(5, 30);
        check("fractional timezone", info.systemTime().text.equals("5:30 AM"));
        zone[0] = ZoneId.of("America/New_York");
        time.instant = Instant.parse("2026-11-01T05:59:59Z");
        check("before daylight fallback", info.systemTime().text.equals("1:59 AM"));
        time.instant = Instant.parse("2026-11-01T06:00:00Z");
        check("daylight fallback", info.systemTime().text.equals("1:00 AM"));
        check("no seconds or percent", info.systemTime().text.equals("1:00 AM"));
    }

    private void text()
    {
        SquareHudInfoText.Content point = SquareHudInfoText.coordinates(3200, 3420, 0);
        check("world XY in display", point.text.equals("3200, 3420"));
        check("plane in tooltip", point.tooltip.contains("Plane: 0"));
        for (int plane = 0; plane < 4; plane++)
        {
            SquareHudInfoText.Content c = SquareHudInfoText.coordinates(12345, 6789, plane);
            check("plane does not crowd display " + plane, c.text.equals("12345, 6789"));
            check("plane reported " + plane, c.tooltip.endsWith(Integer.toString(plane)));
        }
        check("zero valid", SquareHudInfoText.coordinates(0, 0, 0).text.equals("0, 0"));
        check("unknown coordinates blank", SquareHudInfoText.coordinates(-1, 3, 0).text.isEmpty());
        check("unknown Y blank", SquareHudInfoText.coordinates(3, -1, 0).text.isEmpty());
        check("invalid plane blank", SquareHudInfoText.coordinates(3, 4, 4).text.isEmpty());
        check("zone still displays name", SquareHudInfoText.zone("Varrock").text.equals("Varrock"));
        check("full zone available", SquareHudInfoText.zone("Grand Exchange").tooltip.equals("Grand Exchange"));
        check("unknown zone stays blank", SquareHudInfoText.zone("").text.isEmpty());
        check("null zone stays blank", SquareHudInfoText.zone(null).text.isEmpty());
    }

    private void migrations()
    {
        for (String value : new String[]{"SQUARE_HUD", "CLASSIC"})
        {
            Map<String, String> saved = new HashMap<>();
            saved.put(MinimapLayoutMigration.LEGACY, value);
            saved.put(MinimapLayoutMigration.ENABLED, "false"); // core-seeded default
            saved.put("squareHudFooterMode", "MOUSEOVER_ONLY");
            saved.put("infoPanelText", "COORDS");
            MinimapLayoutMigration.migrate(saved::get, saved::put, saved::remove);
            check(value + " migrated", saved.get(MinimapLayoutMigration.ENABLED).equals(
                Boolean.toString(value.equals("SQUARE_HUD"))));
            check(value + " old signal removed", !saved.containsKey(MinimapLayoutMigration.LEGACY));
            check(value + " hover choice preserved", saved.get("squareHudFooterMode").equals("MOUSEOVER_ONLY"));
            check(value + " text choice preserved", saved.get("infoPanelText").equals("COORDS"));
            saved.put(MinimapLayoutMigration.ENABLED, "true");
            MinimapLayoutMigration.migrate(saved::get, saved::put, saved::remove);
            check(value + " one-time only", saved.get(MinimapLayoutMigration.ENABLED).equals("true"));
        }
        Map<String, String> fresh = new HashMap<>();
        MinimapLayoutMigration.migrate(fresh::get, fresh::put, fresh::remove);
        check("fresh install has no migration writes", fresh.isEmpty());
        fresh.put(MinimapLayoutMigration.LEGACY, "unexpected");
        MinimapLayoutMigration.migrate(fresh::get, fresh::put, fresh::remove);
        check("unknown values not guessed", fresh.size() == 1);
        fresh.put(MinimapLayoutMigration.LEGACY, "SQUARE_HUD");
        try
        {
            MinimapLayoutMigration.migrate(fresh::get, (k, v) -> { throw new IllegalStateException("test"); }, fresh::remove);
            throw new AssertionError("Expected write failure");
        }
        catch (IllegalStateException expected)
        {
            check("failed write retains old preference", fresh.containsKey(MinimapLayoutMigration.LEGACY));
        }
    }

    private void controls()
    {
        MinimapLayoutControls controls = new MinimapLayoutControls(new MinimapFilterConfig() {});
        JPanel root = new JPanel();
        JComboBox<SquareHudFooterMode> view = new JComboBox<>(SquareHudFooterMode.values());
        view.setSelectedItem(SquareHudFooterMode.MOUSEOVER_ONLY);
        JLabel viewLabel = row(root, "Info panel view", view);
        JComboBox<InfoPanelText> text = new JComboBox<>(InfoPanelText.values());
        text.setSelectedItem(InfoPanelText.COORDS);
        JLabel textLabel = row(root, "Info panel text", text);
        JComboBox<String> unrelated = new JComboBox<>(new String[]{"Zone", "System Time", "Coords"});
        row(root, "Other plugin", unrelated);
        int[] resetClicks = {0};
        MouseAdapter reset = new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e) { resetClicks[0]++; }
        };
        textLabel.addMouseListener(reset);
        Color original = textLabel.getForeground();
        controls.updateTree(root, false);
        check("both dropdowns disabled", !view.isEnabled() && !text.isEnabled());
        check("both labels disabled", !viewLabel.isEnabled() && !textLabel.isEnabled());
        check("label grayed", textLabel.getForeground().equals(new Color(128, 128, 128)));
        check("similar unrelated choices untouched", unrelated.isEnabled());
        check("selection retained while disabled", view.getSelectedItem() == SquareHudFooterMode.MOUSEOVER_ONLY
            && text.getSelectedItem() == InfoPanelText.COORDS);
        for (java.awt.event.MouseListener listener : textLabel.getMouseListeners())
        {
            if (!(listener instanceof javax.swing.ToolTipManager)) { listener.mouseClicked(null); }
        }
        check("disabled label cannot reset value", resetClicks[0] == 0);
        for (int i = 0; i < 30; i++) { controls.updateTree(root, false); }
        controls.updateTree(root, true);
        check("both dropdowns re-enabled", view.isEnabled() && text.isEnabled());
        check("labels and colors restored", textLabel.isEnabled() && textLabel.getForeground().equals(original));
        check("no changes to saved selection", text.getSelectedItem() == InfoPanelText.COORDS);
        int listenerCount = 0;
        for (java.awt.event.MouseListener listener : textLabel.getMouseListeners()) { if (listener == reset) { listenerCount++; } }
        check("reset listener restored exactly once", listenerCount == 1);
        controls.updateTree(root, false);
        controls.updateTree(new JPanel(), false);
        check("closed/rebuilt panel releases old controls", view.isEnabled() && text.isEnabled());
        controls.updateTree(root, false);
        controls.restoreAll();
        check("stop restores controls", view.isEnabled() && text.isEnabled());
        text.setEnabled(false);
        controls.updateTree(root, false);
        controls.restoreAll();
        check("preexisting disabled state preserved", !text.isEnabled());
    }

    private JLabel row(JPanel root, String title, JComboBox<?> box)
    {
        JPanel row = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setForeground(Color.WHITE);
        row.add(label, BorderLayout.CENTER);
        row.add(box, BorderLayout.EAST);
        root.add(row);
        return label;
    }

    private static final class MutableClock extends Clock
    {
        Instant instant;
        MutableClock(String instant) { this.instant = Instant.parse(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
