package com.minimapfilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Session-only tools. Shared by Swing, the client thread, and the completion task. */
final class InfoPanelTools
{
    static final long MAX_TIMER_SECONDS = 999L * 3600 + 59 * 60 + 59;
    static final int MAX_LAPS = 100;
    private final LongSupplier clock;
    private long timerDuration;
    private long timerRemaining;
    private long timerAnchor;
    private boolean timerRunning;
    private boolean timerFinished;
    private boolean timerPaused;
    private long timerRevision;
    private long stopwatchRevision;
    private boolean stopwatchStarted;
    private boolean completionPending;
    private boolean notifications = true;
    private boolean active = true;
    private long stopwatchElapsed;
    private long stopwatchAnchor;
    private boolean stopwatchRunning;
    private final List<Long> laps = new ArrayList<>();
    private BigDecimal answer = BigDecimal.ZERO;
    private String expression = "";
    private String calculatorError = "";

    InfoPanelTools() { this(() -> System.nanoTime() / 1_000_000L); }
    InfoPanelTools(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    static final class Snapshot
    {
        final long timerDuration, timerRemaining, stopwatchElapsed;
        final boolean timerRunning, timerFinished, stopwatchRunning, notifications;
        final boolean timerPaused, stopwatchStarted, active;
        final long timerRevision, stopwatchRevision;
        final List<Long> laps;
        final BigDecimal answer;
        final String expression, calculatorError;
        Snapshot(long duration, long remaining, boolean running, boolean finished,
            long elapsed, boolean stopwatchRunning, List<Long> laps, BigDecimal answer,
            String expression, String error, boolean notifications, boolean timerPaused, boolean stopwatchStarted,
            boolean active, long timerRevision, long stopwatchRevision)
        {
            this.timerDuration = duration;
            this.timerRemaining = remaining;
            this.timerRunning = running;
            this.timerFinished = finished;
            this.stopwatchElapsed = elapsed;
            this.stopwatchRunning = stopwatchRunning;
            this.laps = Collections.unmodifiableList(new ArrayList<>(laps));
            this.answer = answer;
            this.expression = expression;
            this.calculatorError = error;
            this.notifications = notifications;
            this.timerPaused = timerPaused;
            this.stopwatchStarted = stopwatchStarted;
            this.active = active;
            this.timerRevision = timerRevision;
            this.stopwatchRevision = stopwatchRevision;
        }
    }

    synchronized Snapshot snapshot()
    {
        long now = clock.getAsLong();
        updateTimer(now);
        return new Snapshot(timerDuration, remaining(now), timerRunning, timerFinished,
            elapsed(now), stopwatchRunning, laps, answer, expression, calculatorError, notifications,
            timerPaused, stopwatchStarted, active, timerRevision, stopwatchRevision);
    }

    synchronized void startTimer(long seconds)
    {
        if (!active) { return; }
        if (seconds < 1 || seconds > MAX_TIMER_SECONDS)
        {
            throw new IllegalArgumentException("Set a timer from 1 second to 999:59:59.");
        }
        timerDuration = seconds * 1000;
        timerRemaining = timerDuration;
        timerAnchor = clock.getAsLong();
        timerRunning = true;
        timerPaused = false;
        timerRevision++;
        timerFinished = false;
        completionPending = false;
    }

    synchronized void pauseTimer()
    {
        long now = clock.getAsLong();
        updateTimer(now);
        if (timerRunning) { timerPaused = true; timerRevision++; }
        timerRemaining = remaining(now);
        timerRunning = false;
    }

    synchronized void resumeTimer()
    {
        if (active && !timerRunning && !timerFinished && timerRemaining > 0)
        {
            timerAnchor = clock.getAsLong();
            timerRunning = true;
            timerPaused = false;
            timerRevision++;
        }
    }

    synchronized void resetTimer()
    {
        timerRevision++;
        timerPaused = false;
        timerRunning = false;
        timerFinished = false;
        timerRemaining = timerDuration;
        completionPending = false;
    }

    synchronized void setNotifications(boolean enabled) { notifications = enabled; }

    /** A completion is consumed exactly once, even if notifications are disabled. */
    synchronized boolean pollNotification()
    {
        updateTimer(clock.getAsLong());
        boolean notify = completionPending && notifications;
        completionPending = false;
        return notify;
    }

    synchronized void startStopwatch()
    {
        if (active && !stopwatchRunning)
        {
            stopwatchAnchor = clock.getAsLong();
            stopwatchRunning = true;
            stopwatchStarted = true;
            stopwatchRevision++;
        }
    }

    synchronized void pauseStopwatch()
    {
        if (stopwatchRunning) { stopwatchRevision++; }
        stopwatchElapsed = elapsed(clock.getAsLong());
        stopwatchRunning = false;
    }

    synchronized void resetStopwatch()
    {
        stopwatchRevision++;
        stopwatchStarted = false;
        stopwatchElapsed = 0;
        stopwatchRunning = false;
        laps.clear();
    }

    synchronized boolean lap()
    {
        if (!stopwatchRunning || laps.size() >= MAX_LAPS) { return false; }
        laps.add(elapsed(clock.getAsLong()));
        stopwatchRevision++;
        return true;
    }

    synchronized boolean calculate(String input)
    {
        // Preserve the last successful answer on input errors.
        expression = input == null ? "" : input.substring(0, Math.min(input.length(), InfoPanelCalculator.MAX_INPUT));
        try
        {
            answer = InfoPanelCalculator.evaluate(input);
            calculatorError = "";
            return true;
        }
        catch (IllegalArgumentException ex)
        {
            calculatorError = ex.getMessage();
            return false;
        }
    }

    synchronized void clearCalculator()
    {
        answer = BigDecimal.ZERO;
        expression = "";
        calculatorError = "";
    }

    /** Disabling the plugin pauses both clocks and suppresses pending alerts. */
    synchronized void pauseAll()
    {
        pauseTimer();
        pauseStopwatch();
        completionPending = false;
    }

    /** Reject a queued start/resume from a popup being disposed during shutdown. */
    synchronized void suspend()
    {
        active = false;
        timerRevision++;
        stopwatchRevision++;
        pauseAll();
    }

    synchronized void activate() { active = true; }

    /** A RuneLite profile change must not carry tools into another profile. */
    synchronized void clearAll()
    {
        timerDuration = 0;
        resetTimer();
        resetStopwatch();
        clearCalculator();
        notifications = true;
    }

    private void updateTimer(long now)
    {
        if (timerRunning && remaining(now) == 0)
        {
            timerRemaining = 0;
            timerRunning = false;
            timerFinished = true;
            timerPaused = false;
            timerRevision++;
            completionPending = true;
        }
    }

    private long remaining(long now)
    {
        return timerRunning ? Math.max(0, timerRemaining - Math.max(0, now - timerAnchor)) : timerRemaining;
    }

    private long elapsed(long now)
    {
        long delta = stopwatchRunning ? Math.max(0, now - stopwatchAnchor) : 0;
        return delta > Long.MAX_VALUE - stopwatchElapsed ? Long.MAX_VALUE : stopwatchElapsed + delta;
    }
}
