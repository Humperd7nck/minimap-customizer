package com.minimapfilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Text only: no drawing, input handling or network access. */
final class SquareHudInfoText
{
    static final class Content
    {
        static final Content EMPTY = new Content("", "");
        final String text;
        final String tooltip;
        final String compactText;

        Content(String text, String tooltip)
        {
            this(text, tooltip, text);
        }

        Content(String text, String tooltip, String compactText)
        {
            this.text = text;
            this.tooltip = tooltip;
            this.compactText = compactText;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_DATE = DateTimeFormatter.ofPattern("EEE · MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private final Clock clock;
    private final Supplier<ZoneId> zone;
    private long lastMinute = Long.MIN_VALUE;
    private ZoneId lastZone;
    private Content lastTime = Content.EMPTY;
    private long lastLocalDay = Long.MIN_VALUE;
    private ZoneId lastDateZone;
    private Content lastDayDate = Content.EMPTY;
    private long lastUtcMinute = Long.MIN_VALUE;
    private Content lastUtcTime = Content.EMPTY;

    SquareHudInfoText()
    {
        this(Clock.systemUTC(), ZoneId::systemDefault);
    }

    SquareHudInfoText(Clock clock, Supplier<ZoneId> zone)
    {
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
    }

    Content systemTime()
    {
        Instant now = clock.instant();
        ZoneId currentZone = zone.get();
        long minute = Math.floorDiv(now.getEpochSecond(), 60L);
        // Also notice a clock correction or a changed system time zone. The hover
        // timer uses a separate monotonic clock and is never affected by this.
        if (minute != lastMinute || !currentZone.equals(lastZone))
        {
            String time = TIME.format(now.atZone(currentZone));
            lastTime = new Content(time, "Local time: " + time + " (" + currentZone.getId() + ")");
            lastMinute = minute;
            lastZone = currentZone;
        }
        return lastTime;
    }

    Content dayDate()
    {
        Instant now = clock.instant();
        ZoneId currentZone = zone.get();
        long localDay = now.atZone(currentZone).toLocalDate().toEpochDay();
        if (localDay != lastLocalDay || !currentZone.equals(lastDateZone))
        {
            String value = DAY_DATE.format(now.atZone(currentZone));
            lastDayDate = new Content(value, "Day and date: " + value + " (" + currentZone.getId() + ")");
            lastLocalDay = localDay;
            lastDateZone = currentZone;
        }
        return lastDayDate;
    }

    /** UTC game-time convention, derived from the computer clock, not a server synchronization. */
    Content serverTime()
    {
        Instant now = clock.instant();
        long minute = Math.floorDiv(now.getEpochSecond(), 60L);
        if (minute != lastUtcMinute)
        {
            String time = UTC_TIME.format(now.atOffset(ZoneOffset.UTC));
            lastUtcTime = new Content(time + " UTC", "Server time (UTC): " + time
                + "<br>Based on your computer clock");
            lastUtcMinute = minute;
        }
        return lastUtcTime;
    }

    static Content zone(String name)
    {
        return name == null || name.isEmpty() ? Content.EMPTY : new Content(name, name);
    }

    static Content coordinates(int x, int y, int plane)
    {
        return coordinates(x, y, plane, false);
    }

    static Content coordinatesWithPlane(int x, int y, int plane)
    {
        return coordinates(x, y, plane, true);
    }

    private static Content coordinates(int x, int y, int plane, boolean showPlane)
    {
        if (x < 0 || y < 0 || plane < 0 || plane > 3) { return Content.EMPTY; }
        String value = x + ", " + y;
        return new Content(showPlane ? value + " | P: " + plane : value,
            "World tile: " + value + " | Plane: " + plane);
    }
}
