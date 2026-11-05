package com.minimapfilter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One-shot wall-clock alarm. The chosen instant/zone are frozen when armed. */
final class InfoPanelAlarm
{
    enum TimeZone { LOCAL("Local Time"), UTC("UTC");
        private final String label;
        TimeZone(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    static final int MAX_LABEL = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm z", Locale.ENGLISH);
    private final Clock clock;
    private Instant target;
    private ZoneId zone = ZoneOffset.UTC;
    private TimeZone choice = TimeZone.LOCAL;
    private String label = "";
    private int hour, minute;
    private boolean active = true, armed, fired, pending, notifications = true;
    private long revision;

    InfoPanelAlarm() { this(Clock.systemUTC()); }
    InfoPanelAlarm(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    static final class Snapshot
    {
        final Instant target;
        final ZoneId zone;
        final TimeZone choice;
        final String label;
        final int hour, minute;
        final boolean armed, fired, notifications, active;
        final long revision;
        Snapshot(Instant target, ZoneId zone, TimeZone choice, String label, int hour, int minute,
            boolean armed, boolean fired, boolean notifications, boolean active, long revision)
        {
            this.target=target; this.zone=zone; this.choice=choice; this.label=label;
            this.hour=hour; this.minute=minute; this.armed=armed; this.fired=fired; this.notifications=notifications;
            this.active=active; this.revision=revision;
        }
        String time() { return target == null ? "Off" : TIME.format(target.atZone(zone)) + (choice == TimeZone.UTC ? " UTC" : ""); }
        String date() { return target == null ? "Not set" : FULL.format(target.atZone(zone)); }
    }

    synchronized Snapshot snapshot()
    {
        update();
        return new Snapshot(target, zone, choice, label, hour, minute, armed, fired, notifications, active, revision);
    }

    synchronized void arm(int hour, int minute, TimeZone choice, ZoneId localZone, String label)
    {
        if (!active) { return; }
        if (label == null || label.length() > MAX_LABEL) { throw new IllegalArgumentException("Use a label of at most 60 characters."); }
        if (hour<0 || hour>23 || minute<0 || minute>59) { throw new IllegalArgumentException("Enter an hour from 0-23 and a minute from 0-59."); }
        LocalTime time = LocalTime.of(hour, minute); // validates before mutating anything
        ZoneId selectedZone = Objects.requireNonNull(choice) == TimeZone.UTC ? ZoneId.of("UTC") : Objects.requireNonNull(localZone);
        Instant next = nextOccurrence(clock.instant(), selectedZone, time);
        this.target = next; this.zone = selectedZone; this.choice = choice;
        this.hour=hour; this.minute=minute; this.label=label.trim();
        armed=true; fired=false; pending=false; revision++;
    }

    /** Pick the next occurrence, including the second occurrence of a DST overlap. */
    static Instant nextOccurrence(Instant now, ZoneId zone, LocalTime time)
    {
        LocalDate start = now.atZone(zone).toLocalDate();
        for (int day=0; day<3; day++)
        {
            LocalDateTime requested = start.plusDays(day).atTime(time);
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(requested);
            Instant best = null;
            if (offsets.isEmpty())
            {
                // Java shifts a nonexistent spring-forward time by the gap duration.
                Instant candidate = ZonedDateTime.ofLocal(requested, zone, null).toInstant();
                if (candidate.isAfter(now)) { best=candidate; }
            }
            else
            {
                for (ZoneOffset offset : offsets)
                {
                    Instant candidate=requested.toInstant(offset);
                    if (candidate.isAfter(now) && (best==null || candidate.isBefore(best))) { best=candidate; }
                }
            }
            if (best!=null) { return best; }
        }
        throw new IllegalArgumentException("Could not resolve the next alarm time.");
    }

    synchronized void cancel() { revision++; armed=false; fired=false; pending=false; target=null; }
    synchronized void setNotifications(boolean enabled) { notifications=enabled; }
    synchronized boolean pollNotification()
    {
        update();
        boolean result=pending && notifications && active;
        pending=false;
        return result;
    }
    synchronized void activate() { active=true; }
    synchronized void suspend() { active=false; cancel(); }
    synchronized void clear() { cancel(); label=""; hour=0; minute=0; choice=TimeZone.LOCAL; notifications=true; }
    private void update()
    {
        if (active && armed && !clock.instant().isBefore(target))
        { armed=false; fired=true; pending=true; revision++; }
    }
}
