package com.minimapfilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

        Content(String text, String tooltip)
        {
            this.text = text;
            this.tooltip = tooltip;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private final Clock clock;
    private final Supplier<ZoneId> zone;
    private long lastMinute = Long.MIN_VALUE;
    private ZoneId lastZone;
    private Content lastTime = Content.EMPTY;

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
            lastTime = new Content(time, "System time: " + time + " (" + currentZone.getId() + ")");
            lastMinute = minute;
            lastZone = currentZone;
        }
        return lastTime;
    }

    static Content zone(String name)
    {
        return name == null || name.isEmpty() ? Content.EMPTY : new Content(name, name);
    }

    static Content coordinates(int x, int y, int plane)
    {
        if (x < 0 || y < 0 || plane < 0 || plane > 3) { return Content.EMPTY; }
        String value = x + ", " + y;
        return new Content(value, "World tile: " + value + " | Plane: " + plane);
    }
}
