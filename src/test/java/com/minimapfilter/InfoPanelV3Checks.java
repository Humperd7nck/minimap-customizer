package com.minimapfilter;

import java.awt.Component;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Runs with only a JDK, and is also called by the full Gradle/JUnit suite. */
public final class InfoPanelV3Checks
{
    private int cases;
    public static void main(String[] args) throws Exception
    {
        System.out.println("Info Panel v3 checks passed: " + runAll());
    }
    public static int runAll() throws Exception
    {
        InfoPanelV3Checks checks = new InfoPanelV3Checks();
        checks.formats(); checks.dev2Formats(); checks.dev3Tooltips(); checks.timer(); checks.stopwatch(); checks.calculator();
        checks.social(); checks.ping(); checks.placement(); checks.dragPlacement(); checks.positionMemory(); checks.suspension();
        SwingUtilities.invokeAndWait(checks::swing);
        return checks.cases;
    }
    private void check(String name, boolean ok)
    {
        cases++;
        if (!ok) { throw new AssertionError(name); }
    }
    private void equal(String name, Object expected, Object actual)
    {
        check(name + ": expected [" + expected + "] got [" + actual + "]", expected.equals(actual));
    }
    private void rejects(String name, Runnable operation)
    {
        boolean failed = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { failed = true; }
        check(name, failed);
    }
    private void formats()
    {
        equal("23 choices", 23, InfoPanelText.values().length);
        equal("Zone remains first", InfoPanelText.ZONE, InfoPanelText.values()[0]);
        equal("System time key preserved", InfoPanelText.SYSTEM_TIME, InfoPanelText.valueOf("SYSTEM_TIME"));
        equal("Coords key preserved", InfoPanelText.COORDS, InfoPanelText.valueOf("COORDS"));
        int interactive = 0, latency = 0;
        for (InfoPanelText choice : InfoPanelText.values())
        {
            if (choice.isTool()) { interactive++; }
            if (choice.usesPing()) { latency++; }
        }
        equal("five popup tools", 5, interactive);
        check("Volume is inline, not a popup", !InfoPanelText.VOLUME.isTool());
        equal("only two ping options", 2, latency);
        InfoPanelMetrics m = new InfoPanelMetrics(330, 8, 24, 144, 17, 125483920, 2184);
        InfoPanelText[] modes = {InfoPanelText.CURRENT_WORLD, InfoPanelText.FRIENDS_ONLINE,
            InfoPanelText.CLANMATES_ONLINE, InfoPanelText.FRIENDS_AND_CLANMATES, InfoPanelText.FPS,
            InfoPanelText.PING, InfoPanelText.FPS_AND_PING, InfoPanelText.INVENTORY, InfoPanelText.TOTAL_XP,
            InfoPanelText.TOTAL_LEVEL};
        String[] labels = {"World 330", "Friends: 8", "Clanmates: 24", "F: 8 · CC: 24", "FPS: 144",
            "Ping: 32 ms", "144 FPS · 32 ms", "Inventory: 17/28", "XP: 125,483,920", "Total Lvl: 2,184"};
        for (int i = 0; i < modes.length; i++)
        {
            SquareHudInfoText.Content text = InfoPanelFormatting.metric(modes[i], m, 32);
            equal("requested format " + modes[i], labels[i], text.text);
            check("has a tooltip " + modes[i], !text.tooltip.isEmpty());
            check("unavailable displayed honestly " + modes[i],
                InfoPanelFormatting.metric(modes[i], null, -1).text.contains("—"));
        }
        equal("XP narrow fallback", "XP: 125.48M", InfoPanelFormatting.metric(InfoPanelText.TOTAL_XP, m, -1).compactText);
        equal("zero friends is valid", "Friends: 0", InfoPanelFormatting.metric(InfoPanelText.FRIENDS_ONLINE,
            new InfoPanelMetrics(330, 0, 0, 0, 0, 0, 1), -1).text);
        equal("XP greater than int", "XP: 5,400,000,000", InfoPanelFormatting.metric(InfoPanelText.TOTAL_XP,
            new InfoPanelMetrics(330, -1, -1, -1, -1, 5400000000L, -1), -1).text);
        equal("latency unavailable isn't zero", "144 FPS · — ms", InfoPanelFormatting.metric(InfoPanelText.FPS_AND_PING, m, -1).text);
        equal("inventory tooltip", "Filled inventory slots", InfoPanelFormatting.metric(InfoPanelText.INVENTORY, m, -1).tooltip);
        equal("countdown ceiling", "00:01", InfoPanelFormatting.duration(1, true, false));
        equal("countdown exact boundary", "01:00", InfoPanelFormatting.duration(60000, true, false));
        equal("countdown before boundary", "01:01", InfoPanelFormatting.duration(60001, true, false));
        equal("stopwatch floor", "00:00", InfoPanelFormatting.duration(999, false, false));
        equal("hundredths", "01:01.23", InfoPanelFormatting.duration(61234, false, true));
        equal("hour format", "1:01:01", InfoPanelFormatting.duration(3661000, false, false));
        equal("no negative timer", "00:00", InfoPanelFormatting.duration(-1, true, false));
        equal("large duration not overflow", "2562047788015:12:55", InfoPanelFormatting.duration(Long.MAX_VALUE, false, false));
        equal("abbreviation no round-up", "999.99K", InfoPanelFormatting.abbreviate(999999));
    }
    private void dev3Tooltips()
    {
        InfoPanelMetrics sample = new InfoPanelMetrics(330, 8, 24, 144, 17, 125483920, 2184);
        InfoPanelText[] modes = {InfoPanelText.FRIENDS_ONLINE, InfoPanelText.CLANMATES_ONLINE,
            InfoPanelText.FRIENDS_AND_CLANMATES, InfoPanelText.INVENTORY, InfoPanelText.TOTAL_LEVEL};
        String[] tooltips = {"8 friends online", "24 clanmates online", "8 friends and 24 clanmates online",
            "Filled inventory slots", "Total Level: 2184"};
        for (int i = 0; i < modes.length; i++)
        {
            equal("exact requested tooltip " + modes[i], tooltips[i], InfoPanelFormatting.metric(modes[i], sample, -1).tooltip);
        }
        InfoPanelMetrics zero = new InfoPanelMetrics(330, 0, 0, 0, 0, 0, 0);
        equal("zero combined social tooltip", "0 friends and 0 clanmates online",
            InfoPanelFormatting.metric(InfoPanelText.FRIENDS_AND_CLANMATES, zero, -1).tooltip);
        equal("unknown friends remain explicit", "Friends list unavailable",
            InfoPanelFormatting.metric(InfoPanelText.FRIENDS_ONLINE, null, -1).tooltip);
        equal("unknown clan remains explicit", "Primary clan channel or roster unavailable",
            InfoPanelFormatting.metric(InfoPanelText.CLANMATES_ONLINE, null, -1).tooltip);
        equal("unknown inventory remains explicit", "Inventory unavailable",
            InfoPanelFormatting.metric(InfoPanelText.INVENTORY, null, -1).tooltip);
        InfoPanelMetrics partial = new InfoPanelMetrics(330, -1, 24, 0, 0, 0, 0);
        equal("partial counts do not invent zero", "Friends list unavailable | 24 clanmates online",
            InfoPanelFormatting.metric(InfoPanelText.FRIENDS_AND_CLANMATES, partial, -1).tooltip);
        long[] now = {0};
        InfoPanelTools tools = new InfoPanelTools(() -> now[0]);
        for (int state = 0; state < 4; state++)
        {
            equal("stopwatch hover simple at state " + state, "Open stopwatch",
                InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).tooltip);
            if (state == 0) { tools.startStopwatch(); now[0] = 65432; }
            else if (state == 1) { tools.pauseStopwatch(); }
            else { tools.resetStopwatch(); }
        }
    }
    private void dev2Formats()
    {
        equal("Coords + Plane selector label", "Coords + Plane", InfoPanelText.COORDS_AND_PLANE.toString());
        check("new coordinates option isn't a tool or network sample",
            !InfoPanelText.COORDS_AND_PLANE.isTool() && !InfoPanelText.COORDS_AND_PLANE.usesPing());
        for (int plane = 0; plane <= 3; plane++)
        {
            SquareHudInfoText.Content value = SquareHudInfoText.coordinatesWithPlane(3200, 3420, plane);
            equal("coords plus plane exact format " + plane, "3200, 3420 | P: " + plane, value.text);
            equal("coordinate compact format keeps plane " + plane, value.text, value.compactText);
            equal("coordinate tooltip still explicit " + plane, "World tile: 3200, 3420 | Plane: " + plane, value.tooltip);
            equal("existing Coords stays unchanged " + plane, "3200, 3420",
                SquareHudInfoText.coordinates(3200, 3420, plane).text);
        }
        equal("coordinate origin and plane zero valid", "0, 0 | P: 0",
            SquareHudInfoText.coordinatesWithPlane(0, 0, 0).text);
        int[][] invalid = {{-1, 2, 0}, {2, -1, 0}, {2, 3, -1}, {2, 3, 4}};
        for (int[] coordinates : invalid)
        {
            check("invalid combined coordinates stay blank " + Arrays.toString(coordinates),
                SquareHudInfoText.coordinatesWithPlane(coordinates[0], coordinates[1], coordinates[2]).text.isEmpty());
        }
        equal("five-digit coordinate format", "12345, 6789 | P: 2",
            SquareHudInfoText.coordinatesWithPlane(12345, 6789, 2).text);
        InfoPanelMetrics empty = InfoPanelMetrics.EMPTY;
        InfoPanelMetrics zero = new InfoPanelMetrics(330, 0, 0, 0, 0, 0, 1);
        equal("missing friends punctuation", "Friends: —", InfoPanelFormatting.metric(InfoPanelText.FRIENDS_ONLINE, empty, -1).text);
        equal("missing clan punctuation", "Clanmates: —", InfoPanelFormatting.metric(InfoPanelText.CLANMATES_ONLINE, empty, -1).text);
        equal("missing social punctuation", "F: — · CC: —", InfoPanelFormatting.metric(InfoPanelText.FRIENDS_AND_CLANMATES, empty, -1).text);
        equal("empty inventory punctuation", "Inventory: 0/28", InfoPanelFormatting.metric(InfoPanelText.INVENTORY, zero, -1).text);
        equal("missing inventory punctuation", "Inventory: —/28", InfoPanelFormatting.metric(InfoPanelText.INVENTORY, empty, -1).text);
        equal("zero social counts punctuation", "F: 0 · CC: 0", InfoPanelFormatting.metric(InfoPanelText.FRIENDS_AND_CLANMATES, zero, -1).text);

        long[] clock = {0};
        InfoPanelTools tools = new InfoPanelTools(() -> clock[0]);
        equal("full stopwatch label at rest", "Stopwatch 00:00", InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).text);
        tools.startStopwatch(); clock[0] = 65099;
        equal("full stopwatch label while running", "Stopwatch 01:05", InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).text);
        tools.pauseStopwatch(); clock[0] += 5000;
        equal("full stopwatch label while paused", "Stopwatch 01:05", InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).text);
        tools.startStopwatch(); clock[0] += 3600000;
        equal("hour-long stopwatch retains label", "Stopwatch 1:01:05", InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).text);
        tools.resetStopwatch();
        equal("reset stopwatch label", "Stopwatch 00:00", InfoPanelFormatting.tool(InfoPanelText.STOPWATCH, tools.snapshot()).text);
        for (String calculation : new String[] {"0", "12345 * 6", "1 / 0", "1e50", "-2", "0.1 + 0.2"})
        {
            tools.calculate(calculation);
            SquareHudInfoText.Content content = InfoPanelFormatting.tool(InfoPanelText.CALCULATOR, tools.snapshot());
            equal("calculator label fixed for " + calculation, "Calculator", content.text);
            equal("calculator compact label fixed for " + calculation, "Calculator", content.compactText);
            equal("calculator hover is launch instruction for " + calculation, "Click to open the calculator", content.tooltip);
        }
    }

    private void timer()
    {
        long[] clock = {1000};
        InfoPanelTools tools = new InfoPanelTools(() -> clock[0]);
        equal("fresh timer", 0L, tools.snapshot().timerRemaining);
        equal("fresh timer text", "Set timer", InfoPanelFormatting.tool(InfoPanelText.TIMER, tools.snapshot()).text);
        rejects("reject zero timer", () -> tools.startTimer(0));
        rejects("reject negative timer", () -> tools.startTimer(-1));
        rejects("reject over maximum timer", () -> tools.startTimer(InfoPanelTools.MAX_TIMER_SECONDS + 1));
        tools.startTimer(10);
        clock[0] += 2501;
        equal("uses elapsed milliseconds, not frames", 7499L, tools.snapshot().timerRemaining);
        tools.pauseTimer();
        clock[0] += 123456;
        equal("pause freezes timer", 7499L, tools.snapshot().timerRemaining);
        check("pause not running", !tools.snapshot().timerRunning);
        tools.resumeTimer();
        tools.resumeTimer();
        clock[0] += 7498;
        equal("last millisecond retained", 1L, tools.snapshot().timerRemaining);
        check("no early notification", !tools.pollNotification());
        clock[0]++;
        equal("expires exactly at zero", 0L, tools.snapshot().timerRemaining);
        check("finished state", tools.snapshot().timerFinished && !tools.snapshot().timerRunning);
        check("exactly one notification", tools.pollNotification());
        check("no duplicate notification", !tools.pollNotification());
        tools.resumeTimer();
        check("cannot resume expired timer", !tools.snapshot().timerRunning);
        tools.resetTimer();
        equal("reset retains chosen duration", 10000L, tools.snapshot().timerRemaining);
        check("reset doesn't restart", !tools.snapshot().timerRunning && !tools.snapshot().timerFinished);
        tools.resumeTimer();
        clock[0] += 12000;
        tools.setNotifications(false);
        check("optional notification off", !tools.pollNotification());
        tools.setNotifications(true);
        check("no delayed notification after re-enabling", !tools.pollNotification());
        tools.startTimer(5);
        clock[0] += 1000;
        tools.pauseAll();
        clock[0] += 10000;
        equal("disable pauses countdown", 4000L, tools.snapshot().timerRemaining);
        check("disable suppresses notifications", !tools.pollNotification());
        tools.resumeTimer();
        clock[0] += 4000;
        tools.pauseAll();
        check("disable at deadline suppresses pending alert", !tools.pollNotification());
        tools.startTimer(1);
        clock[0] += 1000;
        tools.snapshot();
        tools.startTimer(2);
        check("restart cancels old pending notification", !tools.pollNotification());
        tools.startTimer(InfoPanelTools.MAX_TIMER_SECONDS);
        equal("maximum timer doesn't overflow", InfoPanelTools.MAX_TIMER_SECONDS * 1000, tools.snapshot().timerRemaining);
        clock[0] -= 100;
        equal("defensive backward-clock handling", InfoPanelTools.MAX_TIMER_SECONDS * 1000, tools.snapshot().timerRemaining);
        tools.clearAll();
        equal("profile clears timer", 0L, tools.snapshot().timerDuration);
        check("profile clears pending alert", !tools.pollNotification());
    }
    private void stopwatch()
    {
        long[] clock = {0};
        InfoPanelTools tools = new InfoPanelTools(() -> clock[0]);
        check("no idle lap", !tools.lap());
        tools.startStopwatch();
        clock[0] = 1234;
        tools.startStopwatch();
        equal("starting twice doesn't reset", 1234L, tools.snapshot().stopwatchElapsed);
        check("first lap", tools.lap());
        clock[0] = 5000;
        check("second lap", tools.lap());
        equal("cumulative lap values", Arrays.asList(1234L, 5000L), tools.snapshot().laps);
        List<Long> frozen = tools.snapshot().laps;
        tools.pauseStopwatch();
        clock[0] += 999999;
        equal("stopwatch pause freezes", 5000L, tools.snapshot().stopwatchElapsed);
        check("paused lap rejected", !tools.lap());
        tools.startStopwatch();
        clock[0] += 2750;
        equal("resume adds elapsed time", 7750L, tools.snapshot().stopwatchElapsed);
        check("new lap", tools.lap());
        equal("snapshot laps immutable copy", 2, frozen.size());
        boolean immutable = false;
        try { frozen.add(1L); } catch (UnsupportedOperationException ex) { immutable = true; }
        check("immutable lap list", immutable);
        for (int i = tools.snapshot().laps.size(); i < InfoPanelTools.MAX_LAPS; i++) { clock[0]++; tools.lap(); }
        equal("bounded lap memory", 100, tools.snapshot().laps.size());
        check("lap limit enforced", !tools.lap());
        tools.pauseAll();
        long saved = tools.snapshot().stopwatchElapsed;
        clock[0] += 100000;
        equal("disable pauses stopwatch", saved, tools.snapshot().stopwatchElapsed);
        tools.resetStopwatch();
        equal("reset clears elapsed", 0L, tools.snapshot().stopwatchElapsed);
        check("reset clears laps", tools.snapshot().laps.isEmpty());
        check("reset stops", !tools.snapshot().stopwatchRunning);
    }
    private void calculator()
    {
        String[][] expressions = {{"0.1+0.2", "0.3"}, {"2+3*4", "14"}, {"(2+3)*4", "20"},
            {"10/4", "2.5"}, {"-4*-2", "8"}, {"--3", "3"}, {".5 + .25", "0.75"},
            {"2 - -3", "5"}, {"2×3+8÷4", "8"}, {"1e3 + 4", "1004"},
            {"5,000 + 1", "INVALID"}, {"-0", "0"}, {"  ( 1 + 2 ) / 2  ", "1.5"}};
        for (String[] pair : expressions)
        {
            if (pair[1].equals("INVALID")) { rejects(pair[0], () -> InfoPanelCalculator.evaluate(pair[0])); }
            else { equal(pair[0], pair[1], InfoPanelCalculator.display(InfoPanelCalculator.evaluate(pair[0]))); }
        }
        String[] removedTokens = {"Ans", "ans", "ANS", "aNs", "Ans * 3", "1+Ans", "(ans)", "2*ANS", "answer"};
        for (String input : removedTokens)
        {
            rejects("answer token removed: " + input, () -> InfoPanelCalculator.evaluate(input));
        }
        equal("recurring decimal bounded", 34, InfoPanelCalculator.evaluate("1/3").precision());
        String[] bad = {"", " ", ".", "1/0", "2/(-0)", "1+", "(2+3", "2+3)", "2(3)", "nan", "Infinity",
            "Math.random()", "Runtime.getRuntime()", "1;2", "<html>", "1e", "1e999", "1e101", "1e-101",
            "99999999999999999999999999999999999999999", "((((((((((((((((((((1))))))))))))))))))))"};
        for (String input : bad) { rejects("invalid " + input, () -> InfoPanelCalculator.evaluate(input)); }
        rejects("null", () -> InfoPanelCalculator.evaluate(null));
        rejects("input limit", () -> InfoPanelCalculator.evaluate("1+".repeat(130) + "1"));
        Random rng = new Random(74410);
        for (int i = 0; i < 180; i++)
        {
            int a = rng.nextInt(20001) - 10000;
            int b = rng.nextInt(20001) - 10000;
            int c = rng.nextInt(20) + 1;
            String input = "(" + a + "+" + b + ")*" + c;
            equal("seeded arithmetic " + i, BigDecimal.valueOf(((long) a + b) * c).stripTrailingZeros(),
                InfoPanelCalculator.evaluate(input).stripTrailingZeros());
        }
        InfoPanelTools tools = new InfoPanelTools();
        check("calculate succeeds", tools.calculate("12.5*2"));
        equal("answer stored", "25", InfoPanelCalculator.display(tools.snapshot().answer));
        check("divide error reported", !tools.calculate("1/0"));
        equal("error preserves answer", "25", InfoPanelCalculator.display(tools.snapshot().answer));
        equal("calculator footer remains a fixed label on error", "Calculator", InfoPanelFormatting.tool(InfoPanelText.CALCULATOR, tools.snapshot()).text);
        check("Ans cannot recall a displayed result", !tools.calculate("Ans+1"));
        equal("removed token leaves displayed result intact", "25", InfoPanelCalculator.display(tools.snapshot().answer));
        check("removed token reports an error", !tools.snapshot().calculatorError.isEmpty());
        check("can recover with normal arithmetic", tools.calculate("25+1"));
        equal("normal arithmetic after error", "26", InfoPanelCalculator.display(tools.snapshot().answer));
        tools.clearCalculator();
        equal("clear answer", "0", InfoPanelCalculator.display(tools.snapshot().answer));
        check("clear error", tools.snapshot().calculatorError.isEmpty());
    }
    private void social()
    {
        equal("missing friend list unknown", -1, InfoPanelCounts.friends(null));
        equal("empty loaded friend list zero", 0, InfoPanelCounts.friends(Collections.emptyList()));
        List<InfoPanelCounts.Member> online = Arrays.asList(new InfoPanelCounts.Member("Friend One", 330),
            new InfoPanelCounts.Member("Friend_two", 301), new InfoPanelCounts.Member("friend\u00a0one", 330),
            new InfoPanelCounts.Member("Offline", 0), new InfoPanelCounts.Member("Unknown", -1), null);
        equal("friends online deduplicated across worlds", 2, InfoPanelCounts.friends(online));
        List<String> roster = Arrays.asList("Me", "Friend One", "Offline");
        List<InfoPanelCounts.Member> channel = Arrays.asList(new InfoPanelCounts.Member("me", 330),
            new InfoPanelCounts.Member("FRIEND_ONE", 301), new InfoPanelCounts.Member("Guest", 330),
            new InfoPanelCounts.Member("Offline", 0));
        equal("exclude self and visitors", 1, InfoPanelCounts.clanmates(roster, channel, "ME"));
        equal("no primary channel unknown", -1, InfoPanelCounts.clanmates(roster, null, "Me"));
        equal("no roster unknown", -1, InfoPanelCounts.clanmates(null, channel, "Me"));
        equal("self unknown doesn't inflate count", -1, InfoPanelCounts.clanmates(roster, channel, null));
        equal("all other clanmates offline zero", 0, InfoPanelCounts.clanmates(roster,
            Collections.singletonList(new InfoPanelCounts.Member("Me", 330)), "Me"));
    }
    private void ping()
    {
        InfoPanelPingState ping = new InfoPanelPingState();
        check("off schedules nothing", ping.begin(0) == null);
        equal("off unavailable", -1, ping.value(0));
        ping.target(330);
        InfoPanelPingState.Request a = ping.begin(0);
        check("first request immediate", a != null && a.world == 330);
        check("no overlapping request", ping.begin(6000) == null);
        equal("pending unknown", -1, ping.value(0));
        ping.complete(a, 32, 100);
        equal("fresh result", 32, ping.value(200));
        check("rate limited", ping.begin(4999) == null);
        InfoPanelPingState.Request b = ping.begin(5000);
        check("5-second refresh", b != null);
        ping.target(301);
        equal("world hop clears old latency", -1, ping.value(5100));
        ping.complete(b, 99, 5200);
        equal("old world result rejected", -1, ping.value(5300));
        InfoPanelPingState.Request c = ping.begin(5300);
        check("new world can refresh immediately", c != null && c.world == 301);
        ping.complete(c, 20, 5400);
        equal("new world's result", 20, ping.value(5500));
        equal("TTL fresh boundary", 20, ping.value(20400));
        equal("TTL discards stale latency", -1, ping.value(20401));
        InfoPanelPingState.Request d = ping.begin(21000);
        ping.target(0);
        ping.complete(d, 10, 21100);
        equal("disabled rejects in-flight result", -1, ping.value(21200));
        check("disabled no polling", ping.begin(30000) == null);
        ping.target(330);
        InfoPanelPingState.Request e = ping.begin(30000);
        ping.target(301); ping.target(330);
        ping.complete(e, 1, 30100);
        equal("leave-return to same world rejects stale generation", -1, ping.value(30200));
        InfoPanelPingState.Request f = ping.begin(30200);
        ping.complete(f, -1, 30300);
        equal("failure isn't zero", -1, ping.value(30400));
        InfoPanelPingState.Request g = ping.begin(35200);
        ping.reset(); ping.target(330);
        InfoPanelPingState.Request h = ping.begin(35300);
        check("reset releases cancelled queued work", h != null);
        ping.complete(g, 99, 35400);
        equal("cancelled old worker cannot complete new request", -1, ping.value(35500));
        ping.complete(h, 0, 35500);
        equal("genuine submillisecond result may be zero", 0, ping.value(35600));
        equal("defensive backwards clock invalidates result", -1, ping.value(35499));
    }
    private void placement()
    {
        Rectangle[] screens = {new Rectangle(0, 0, 1920, 1040), new Rectangle(-1920, 0, 1920, 1080),
            new Rectangle(0, -1080, 1920, 1080), new Rectangle(100, 100, 200, 150)};
        for (Rectangle screen : screens)
        {
            Point[] anchors = {new Point(screen.x, screen.y), new Point(screen.x + screen.width, screen.y + screen.height),
                new Point(screen.x + screen.width / 2, screen.y + screen.height / 2)};
            for (Point anchor : anchors)
            {
                Rectangle result = InfoPanelPopup.placement(screen, new Dimension(320, 350), anchor);
                check("popup remains on usable monitor " + anchor, screen.contains(result));
            }
        }
        Rectangle screen = new Rectangle(0, 0, 1920, 1040);
        Rectangle hud = new Rectangle(1600, 40, SquareHudGeometry.WIDTH, SquareHudGeometry.HEIGHT + SquareHudGeometry.FOOTER_HEIGHT);
        Rectangle below = InfoPanelPopup.placement(screen, new Dimension(320, 350), hud);
        equal("default popup appears directly below HUD", hud.y + hud.height + 6, below.y);
        equal("default popup is centred on HUD", hud.x + hud.width / 2, below.x + below.width / 2);
        Rectangle lowerHud = new Rectangle(1600, 760, SquareHudGeometry.WIDTH, SquareHudGeometry.HEIGHT + SquareHudGeometry.FOOTER_HEIGHT);
        Rectangle above = InfoPanelPopup.placement(screen, new Dimension(320, 350), lowerHud);
        check("popup falls above HUD only when below cannot fit", above.y + above.height <= lowerHud.y - 6);
    }
    private void dragPlacement()
    {
        Dimension size = new Dimension(320, 405);
        equal("drag retains requested position", new Point(180, 90),
            InfoPanelPopup.dragLocation(new Rectangle(0, 0, 1920, 1040), size, new Point(180, 90)));
        equal("drag clamps top-left", new Point(0, 0),
            InfoPanelPopup.dragLocation(new Rectangle(0, 0, 1920, 1040), size, new Point(-100, -500)));
        equal("drag clamps bottom-right", new Point(1600, 635),
            InfoPanelPopup.dragLocation(new Rectangle(0, 0, 1920, 1040), size, new Point(2000, 2000)));
        equal("drag on left monitor", new Point(-1920, 675),
            InfoPanelPopup.dragLocation(new Rectangle(-1920, 0, 1920, 1080), size, new Point(-3000, 2000)));
        equal("drag on upper monitor", new Point(1600, -1080),
            InfoPanelPopup.dragLocation(new Rectangle(0, -1080, 1920, 1080), size, new Point(3000, -2000)));
        equal("small display keeps header accessible without resizing", new Point(100, 100),
            InfoPanelPopup.dragLocation(new Rectangle(100, 100, 200, 150), size, new Point(2000, -2000)));
        equal("drag does not mutate requested size", new Dimension(320, 405), size);
        Random random = new Random(2002);
        Rectangle monitor = new Rectangle(-1920, -100, 1920, 1040);
        for (int i = 0; i < 60; i++)
        {
            Point requested = new Point(random.nextInt(8000) - 4000, random.nextInt(8000) - 4000);
            Point actual = InfoPanelPopup.dragLocation(monitor, size, requested);
            check("random drag stays inside usable monitor " + i, monitor.contains(new Rectangle(actual, size)));
        }
    }

    private void positionMemory()
    {
        Point original = new Point(1234, -567);
        equal("popup position round-trips", original, InfoPanelPopupPosition.decode(InfoPanelPopupPosition.encode(original)));
        equal("timer popup has stable hidden key", "popupPositionTimer", InfoPanelPopupPosition.key(InfoPanelText.TIMER));
        equal("selector popup has stable hidden key", "popupPositionInfoSelector", InfoPanelPopupPosition.SELECTOR_KEY);
        equal("tracking popup has stable hidden key", "popupPositionTracking", InfoPanelPopupPosition.TRACKING_KEY);
        check("selector hidden position key recognized", InfoPanelPopupPosition.isPositionKey(InfoPanelPopupPosition.SELECTOR_KEY));
        check("tracking hidden position key recognized", InfoPanelPopupPosition.isPositionKey(InfoPanelPopupPosition.TRACKING_KEY));
        check("tool hidden position key recognized", InfoPanelPopupPosition.isPositionKey(InfoPanelPopupPosition.key(InfoPanelText.TIMER)));
        check("ordinary config key is not a popup position", !InfoPanelPopupPosition.isPositionKey("infoPanelText"));
        check("null config key is not a popup position", !InfoPanelPopupPosition.isPositionKey(null));
        check("missing popup position ignored", InfoPanelPopupPosition.decode(null) == null);
        check("malformed popup position ignored", InfoPanelPopupPosition.decode("1,2,3") == null);
        check("non-numeric popup position ignored", InfoPanelPopupPosition.decode("left,top") == null);
    }

    private void suspension()
    {
        long[] now = {0};
        InfoPanelTools t = new InfoPanelTools(() -> now[0]);
        t.startTimer(90); t.startStopwatch(); now[0] = 1000;
        t.suspend();
        equal("disable freezes remaining countdown", 89000L, t.snapshot().timerRemaining);
        check("disable freezes stopwatch", !t.snapshot().stopwatchRunning);
        t.resumeTimer(); t.startTimer(12); t.startStopwatch(); now[0] += 9999;
        equal("queued timer start after disable rejected", 89000L, t.snapshot().timerRemaining);
        equal("queued stopwatch start after disable rejected", 1000L, t.snapshot().stopwatchElapsed);
        check("disabled timers do not notify", !t.pollNotification());
        t.activate(); t.resumeTimer(); t.startStopwatch(); now[0] += 1000;
        equal("explicit resume after enable works", 88000L, t.snapshot().timerRemaining);
        equal("stopwatch resume after enable works", 2000L, t.snapshot().stopwatchElapsed);
    }

    private void swing()
    {
        InfoPanelTools unavailableTools = new InfoPanelTools();
        InfoPanelPopup unavailablePopup = new InfoPanelPopup(unavailableTools, () -> { }, () -> false);
        InfoPanelPopup.View unavailableCalc = unavailablePopup.createView(InfoPanelText.CALCULATOR);
        JTextField rejectedInput = (JTextField) find(unavailableCalc.panel, "calculator-input");
        rejectedInput.setText("9*9"); rejectedInput.postActionEvent();
        equal("queued Enter after closing/profile change is ignored", BigDecimal.ZERO, unavailableTools.snapshot().answer);
        ((JButton) find(unavailableCalc.panel, "calc-key-=")).doClick();
        equal("queued button after closing/profile change is ignored", BigDecimal.ZERO, unavailableTools.snapshot().answer);

        long[] clock = {0};
        InfoPanelTools tools = new InfoPanelTools(() -> clock[0]);
        InfoPanelPopup popup = new InfoPanelPopup(tools, () -> { });
        InfoPanelPopup.View timer = popup.createView(InfoPanelText.TIMER);
        equal("timer popup width", 320, timer.panel.getPreferredSize().width);
        ((JSpinner) find(timer.panel, "timer-minutes")).setValue(1);
        ((JSpinner) find(timer.panel, "timer-seconds")).setValue(30);
        click(timer.panel, "timer-start"); timer.refresh.run();
        equal("timer Start reads fields", 90000L, tools.snapshot().timerRemaining);
        clock[0] += 30000;
        timer.refresh.run();
        equal("timer popup updates", "01:00", ((JLabel) find(timer.panel, "timer-display")).getText());
        click(timer.panel, "timer-pause"); clock[0] += 50000;
        equal("timer Pause button", 60000L, tools.snapshot().timerRemaining);
        click(timer.panel, "timer-pause"); clock[0] += 10000;
        equal("timer Resume button", 50000L, tools.snapshot().timerRemaining);
        click(timer.panel, "timer-reset");
        equal("timer Reset button", 90000L, tools.snapshot().timerRemaining);
        check("Reset doesn't auto-start", !tools.snapshot().timerRunning);
        ((JCheckBox) find(timer.panel, "timer-notify")).doClick();
        check("checkbox controls notifications", !tools.snapshot().notifications);
        ((JSpinner) find(timer.panel, "timer-minutes")).setValue(0);
        ((JSpinner) find(timer.panel, "timer-seconds")).setValue(0);
        click(timer.panel, "timer-start");
        check("invalid timer shows inline error", !((JLabel) find(timer.panel, "timer-error")).getText().trim().isEmpty());
        equal("invalid timer doesn't change prior duration", 90000L, tools.snapshot().timerDuration);
        InfoPanelPopup.View sw = popup.createView(InfoPanelText.STOPWATCH);
        click(sw.panel, "stopwatch-toggle"); clock[0] += 1234; sw.refresh.run();
        equal("stopwatch display", "00:01.23", ((JLabel) find(sw.panel, "stopwatch-display")).getText());
        click(sw.panel, "stopwatch-lap"); sw.refresh.run();
        equal("Lap updates real Swing list", 1, ((JList<?>) find(sw.panel, "stopwatch-laps")).getModel().getSize());
        click(sw.panel, "stopwatch-toggle"); clock[0] += 999; sw.refresh.run();
        equal("stopwatch pause via button", 1234L, tools.snapshot().stopwatchElapsed);
        click(sw.panel, "stopwatch-reset"); sw.refresh.run();
        equal("reset clears Swing lap list", 0, ((JList<?>) find(sw.panel, "stopwatch-laps")).getModel().getSize());
        InfoPanelPopup.View calc = popup.createView(InfoPanelText.CALCULATOR);
        JTextField input = (JTextField) find(calc.panel, "calculator-input");
        JTextField answer = (JTextField) find(calc.panel, "calculator-result");
        for (InfoPanelPopup.View view : new InfoPanelPopup.View[] {timer, sw, calc})
        {
            JComponent header = (JComponent) find(view.panel, "info-tool-header");
            JComponent title = (JComponent) find(view.panel, "info-tool-title");
            JButton close = (JButton) find(view.panel, "close-tool");
            equal("header has move cursor", Cursor.MOVE_CURSOR, header.getCursor().getType());
            equal("title has move cursor", Cursor.MOVE_CURSOR, title.getCursor().getType());
            equal("header explains dragging", "Drag to move", header.getToolTipText());
            equal("title explains dragging", "Drag to move", title.getToolTipText());
            check("header listens for dragging", header.getMouseMotionListeners().length > 0);
            check("title listens for dragging", title.getMouseMotionListeners().length > 0);
            equal("Close is not a drag handle", Cursor.DEFAULT_CURSOR, close.getCursor().getType());
            for (java.awt.event.MouseMotionListener listener : header.getMouseMotionListeners())
            {
                check("Close doesn't share the header drag listener", !Arrays.asList(close.getMouseMotionListeners()).contains(listener));
            }
        }
        check("result readout is larger than expression", answer.getFont().getSize() >= 26
            && answer.getFont().getSize() > input.getFont().getSize());
        check("result readout is bold", answer.getFont().isBold());
        check("result has a dedicated tall area", answer.getPreferredSize().height >= 56);
        check("result can be selected but not edited", !answer.isEditable() && answer.isFocusable());
        equal("result right aligned", javax.swing.SwingConstants.RIGHT, answer.getHorizontalAlignment());
        check("result has high contrast dark background", answer.getBackground().getRed() < 40
            && answer.getForeground().getRed() > 200);
        equal("result accessible label", "Calculator result", answer.getAccessibleContext().getAccessibleName());
        check("Ans key removed", find(calc.panel, "calc-key-Ans") == null);
        check("old C key removed", find(calc.panel, "calc-key-C") == null);
        check("bottom Backspace button removed", find(calc.panel, "calc-backspace") == null);
        check("expression tooltip no longer offers Ans", !input.getToolTipText().contains("Ans"));
        Container keypad = find(calc.panel, "calc-key-Back").getParent();
        equal("keypad retains twenty buttons", 20, keypad.getComponentCount());
        String[] expectedKeys = {"7", "8", "9", "/", "4", "5", "6", "*", "1", "2", "3", "-",
            "0", ".", "Back", "+", "(", ")", "Clear", "="};
        for (int i = 0; i < expectedKeys.length; i++)
        {
            equal("keypad label and position " + i, expectedKeys[i], ((JButton) keypad.getComponent(i)).getText());
        }
        java.awt.BorderLayout bodyLayout = (java.awt.BorderLayout) keypad.getParent().getLayout();
        check("calculator has no leftover bottom row", bodyLayout.getLayoutComponent(java.awt.BorderLayout.SOUTH) == null);
        equal("calculator body shrinks after removing the bottom row", 310, keypad.getParent().getPreferredSize().height);
        String[] coloredKeys = {"Clear", "Back", "=", "/", "*", "-", "+"};
        for (String key : coloredKeys)
        {
            JButton button = (JButton) find(calc.panel, "calc-key-" + key);
            Color expected = key.equals("Clear") || key.equals("Back") ? new Color(160, 53, 59)
                : key.equals("=") ? new Color(39, 120, 69) : new Color(39, 96, 156);
            equal("key color " + key, expected, button.getBackground());
            equal("readable white text " + key, Color.WHITE, button.getForeground());
            equal("painted key background " + key, expected.getRGB(), paintedPixel(button));
            button.getModel().setRollover(true);
            check("hover visibly changes " + key, paintedPixel(button) != expected.getRGB());
            button.getModel().setRollover(false);
            button.getModel().setArmed(true); button.getModel().setPressed(true);
            check("pressed visibly changes " + key, paintedPixel(button) != expected.getRGB());
            button.getModel().setArmed(false); button.getModel().setPressed(false);
            button.setEnabled(false);
            check("disabled visibly changes " + key, paintedPixel(button) != expected.getRGB());
            button.setEnabled(true);
            equal("normal color restored " + key, expected.getRGB(), paintedPixel(button));
        }
        input.setText("12.5 * 2"); input.postActionEvent();
        equal("Enter computes", "25", answer.getText());
        equal("result tooltip follows calculation", "Result: 25", answer.getToolTipText());
        input.setText("25 / 5"); click(calc.panel, "calc-key-=");
        equal("equals computes", "5", answer.getText());
        input.setText("2/0"); input.postActionEvent();
        check("division error shown inline", ((JLabel) find(calc.panel, "calculator-error")).getText().contains("zero"));
        equal("result preserved on bad input", "5", answer.getText());
        click(calc.panel, "calc-key-Clear");
        equal("clear input", "", input.getText());
        equal("clear result", "0", answer.getText());
        equal("clear updates result tooltip", "Result: 0", answer.getToolTipText());
        click(calc.panel, "calc-key-7"); click(calc.panel, "calc-key-+"); click(calc.panel, "calc-key-8");
        click(calc.panel, "calc-key-=");
        equal("actual keypad calculates", "15", answer.getText());
        input.setText("123"); input.setCaretPosition(3); click(calc.panel, "calc-key-Back");
        equal("Backspace deletes one digit", "12", input.getText());
        input.setText("12345"); input.select(1, 4); click(calc.panel, "calc-key-Back");
        equal("Back deletes selection", "15", input.getText());
        equal("Back preserves displayed result", "15", answer.getText());
        input.setText("12345"); input.setCaretPosition(2); click(calc.panel, "calc-key-Back");
        equal("Back deletes before mid-expression caret", "1345", input.getText());
        equal("Back leaves caret at edit", 1, input.getCaretPosition());
        input.setCaretPosition(0); click(calc.panel, "calc-key-Back");
        equal("Back at start leaves text unchanged", "1345", input.getText());
        input.selectAll(); click(calc.panel, "calc-key-Back");
        equal("Back deletes full selection", "", input.getText());
        click(calc.panel, "calc-key-Back");
        equal("Back on empty input is harmless", "", input.getText());
        input.setText("Ans+1"); click(calc.panel, "calc-key-=");
        check("typed Ans is rejected by the actual popup", !tools.snapshot().calculatorError.isEmpty());
        equal("typed Ans cannot reuse displayed result", "15", answer.getText());
        click(calc.panel, "calc-key-Clear");
        equal("Clear removes input after an error", "", input.getText());
        equal("Clear removes displayed result after error", "0", answer.getText());
        equal("Clear removes inline error", " ", ((JLabel) find(calc.panel, "calculator-error")).getText());
        equal("Clear removes error tooltip", "", ((JLabel) find(calc.panel, "calculator-error")).getToolTipText());
        input.setText("1"); input.setCaretPosition(1);
        input.replaceSelection("2".repeat(256));
        equal("oversized paste rejected entirely", "1", input.getText());
        input.setText("1 / 7"); input.postActionEvent();
        equal("long decimal result kept in full", InfoPanelCalculator.display(tools.snapshot().answer), answer.getText());
        equal("long decimal result tooltip kept in full", "Result: " + answer.getText(), answer.getToolTipText());
        equal("long result scroll caret is at end", answer.getText().length(), answer.getCaretPosition());
        popup.close(); popup.close();
        check("close is idempotent", !popup.isShowing());
    }
    private static int paintedPixel(JButton button)
    {
        button.setSize(64, 36);
        BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { button.paint(graphics); }
        finally { graphics.dispose(); }
        return image.getRGB(8, 18);
    }

    static Component find(Component component, String name)
    {
        if (name.equals(component.getName())) { return component; }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                Component match = find(child, name);
                if (match != null) { return match; }
            }
        }
        return null;
    }
    private void click(JComponent component, String name)
    {
        JButton button = (JButton) find(component, name);
        check("button exists and enabled " + name, button != null && button.isEnabled());
        button.doClick(0);
    }
}
