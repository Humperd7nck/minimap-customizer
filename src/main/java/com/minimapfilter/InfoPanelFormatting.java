package com.minimapfilter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import com.minimapfilter.SquareHudInfoText.Content;

/** Exact public labels, with explicit unavailable values and full-value tooltips. */
final class InfoPanelFormatting
{
    private static final String UNKNOWN = "\u2014";
    static Content metric(InfoPanelText mode, InfoPanelMetrics sample, int ping)
    {
        InfoPanelMetrics s = sample == null ? InfoPanelMetrics.EMPTY : sample;
        switch (mode)
        {
            case CURRENT_WORLD:
                return new Content("World " + number(s.world), "Current world: " + number(s.world));
            case FRIENDS_ONLINE:
                return new Content("Friends: " + number(s.friends), friendsTooltip(s.friends));
            case CLANMATES_ONLINE:
                return new Content("Clanmates: " + number(s.clanmates), clanTooltip(s.clanmates));
            case FRIENDS_AND_CLANMATES:
                return new Content("F: " + number(s.friends) + " \u00b7 CC: " + number(s.clanmates),
                    socialTooltip(s.friends, s.clanmates));
            case FPS:
                return new Content("FPS: " + number(s.fps), "Current frames per second: " + number(s.fps));
            case PING:
                return new Content("Ping: " + number(ping) + " ms", pingTooltip(ping));
            case FPS_AND_PING:
                return new Content(number(s.fps) + " FPS \u00b7 " + number(ping) + " ms",
                    "FPS: " + number(s.fps) + " | " + pingTooltip(ping));
            case INVENTORY:
                return new Content("Inventory: " + number(s.inventory) + "/28", s.inventory < 0
                    ? "Inventory unavailable" : "Filled inventory slots");
            case TOTAL_XP:
                String full = s.totalXp < 0 ? UNKNOWN : grouped(s.totalXp);
                return new Content("XP: " + full, "Total XP: " + full,
                    "XP: " + (s.totalXp < 0 ? UNKNOWN : abbreviate(s.totalXp)));
            case TOTAL_LEVEL:
                return new Content("Total Lvl: " + (s.totalLevel < 0 ? UNKNOWN : grouped(s.totalLevel)),
                    "Total Level: " + number(s.totalLevel));
            default:
                return Content.EMPTY;
        }
    }

    static Content tool(InfoPanelText mode, InfoPanelTools.Snapshot state)
    {
        switch (mode)
        {
            case TIMER:
                String timer = state.timerDuration == 0 ? "Set timer" : state.timerFinished ? "Timer finished"
                    : "Timer " + duration(state.timerRemaining, true, false);
                return new Content(timer, toolHover(mode),
                    state.timerDuration == 0 || state.timerFinished ? timer : duration(state.timerRemaining, true, false));
            case STOPWATCH:
                return new Content("Stopwatch " + duration(state.stopwatchElapsed, false, false),
                    toolHover(mode), duration(state.stopwatchElapsed, false, false));
            case CALCULATOR:
                return new Content("Calculator", "Click to open the calculator");
            default:
                return Content.EMPTY;
        }
    }

    /** Short, consistent launcher/readout hints; individual controls have their own action hints. */
    static String toolHover(InfoPanelText mode)
    {
        if (mode == InfoPanelText.TIMER) { return "Set timer"; }
        if (mode == InfoPanelText.ALARM) { return "Set alarm"; }
        if (mode == InfoPanelText.STOPWATCH) { return "Open stopwatch"; }
        if (mode == InfoPanelText.VOLUME) { return "Control volume"; }
        return "";
    }

    static String duration(long milliseconds, boolean countdown, boolean hundredths)
    {
        long ms = Math.max(0, milliseconds);
        long seconds = ms / 1000 + (countdown && ms % 1000 != 0 ? 1 : 0);
        long hours = seconds / 3600;
        long minutes = (seconds / 60) % 60;
        String result = hours > 0 ? hours + ":" + two(minutes) + ":" + two(seconds % 60)
            : two(minutes) + ":" + two(seconds % 60);
        return hundredths ? result + "." + two((ms % 1000) / 10) : result;
    }
    private static String two(long value) { return value < 10 ? "0" + value : Long.toString(value); }
    static String number(long value) { return value < 0 ? UNKNOWN : Long.toString(value); }
    static String grouped(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private static String friendsTooltip(int count)
    {
        return count < 0 ? "Friends list unavailable" : count + " friends online";
    }
    private static String clanTooltip(int count)
    {
        return count < 0 ? "Primary clan channel or roster unavailable"
            : count + " clanmates online";
    }
    private static String socialTooltip(int friends, int clanmates)
    {
        return friends >= 0 && clanmates >= 0
            ? friends + " friends and " + clanmates + " clanmates online"
            : friendsTooltip(friends) + " | " + clanTooltip(clanmates);
    }
    private static String pingTooltip(int value)
    {
        return value < 0 ? "Ping unavailable or awaiting a fresh measurement"
            : "Network latency to your current world: " + value + " ms (not server tick delay); sampled every 5 seconds";
    }
    static String abbreviate(long value)
    {
        if (value < 1_000) { return Long.toString(value); }
        long divisor = value >= 1_000_000_000 ? 1_000_000_000 : value >= 1_000_000 ? 1_000_000 : 1_000;
        String suffix = divisor == 1_000_000_000 ? "B" : divisor == 1_000_000 ? "M" : "K";
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString() + suffix;
    }
    private InfoPanelFormatting() { }
}
