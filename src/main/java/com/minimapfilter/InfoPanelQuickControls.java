package com.minimapfilter;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Square transport controls for the selected drawer tool; uses the existing shared models. */
final class InfoPanelQuickControls
{
    static final int SIZE = 22, GAP = 3, TEXT_GAP = 6, MIN_TEXT_WIDTH = 40;
    enum Icon { PLAY, PAUSE, STOP, LAP, RESET, CANCEL }
    enum Command
    {
        TIMER_START(InfoPanelText.TIMER, Icon.PLAY, "Start timer"),
        TIMER_PAUSE(InfoPanelText.TIMER, Icon.PAUSE, "Pause timer"),
        TIMER_RESUME(InfoPanelText.TIMER, Icon.PLAY, "Resume timer"),
        TIMER_STOP(InfoPanelText.TIMER, Icon.STOP, "Stop timer"),
        STOPWATCH_START(InfoPanelText.STOPWATCH, Icon.PLAY, "Start stopwatch"),
        STOPWATCH_PAUSE(InfoPanelText.STOPWATCH, Icon.PAUSE, "Pause stopwatch"),
        STOPWATCH_RESUME(InfoPanelText.STOPWATCH, Icon.PLAY, "Resume stopwatch"),
        STOPWATCH_LAP(InfoPanelText.STOPWATCH, Icon.LAP, "Record lap"),
        STOPWATCH_RESET(InfoPanelText.STOPWATCH, Icon.RESET, "Reset stopwatch"),
        ALARM_CANCEL(InfoPanelText.ALARM, Icon.CANCEL, "Cancel alarm");
        final InfoPanelText tool;
        final Icon icon;
        final String tooltip;
        Command(InfoPanelText tool, Icon icon, String tooltip)
        { this.tool = tool; this.icon = icon; this.tooltip = tooltip; }
    }

    /** Bound to the model revision that was painted/pressed, never a blind toggle. */
    static final class Request
    {
        final Command command;
        final long revision;
        final boolean enabled;
        final String tooltip;
        Request(Command command, long revision, boolean enabled, String disabledTooltip)
        {
            this.command = command; this.revision = revision; this.enabled = enabled;
            this.tooltip = enabled ? command.tooltip : disabledTooltip;
        }
        boolean same(Request other)
        { return other != null && command == other.command && revision == other.revision && enabled == other.enabled; }
    }
    static final class Button
    {
        final Rectangle bounds;
        final Request request;
        Button(Rectangle bounds, Request request) { this.bounds = new Rectangle(bounds); this.request = request; }
    }
    static final class View
    {
        final List<Button> buttons;
        final Rectangle text;
        View(List<Button> buttons, Rectangle text)
        { this.buttons = Collections.unmodifiableList(new ArrayList<>(buttons)); this.text = new Rectangle(text); }
        static View none(Rectangle content) { return new View(Collections.emptyList(), content); }
    }

    private final InfoPanelTools tools;
    private final InfoPanelReminders reminders;
    InfoPanelQuickControls(InfoPanelTools tools, InfoPanelReminders reminders)
    { this.tools = tools; this.reminders = reminders; }

    View view(InfoPanelText mode, Rectangle content)
    {
        Request[] requests = requests(mode);
        int count = requests.length;
        int reserve = count * SIZE + Math.max(0, count - 1) * GAP + TEXT_GAP;
        if (count == 0 || content.height < SIZE || content.width < reserve + MIN_TEXT_WIDTH)
        { return View.none(content); }
        List<Button> buttons = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            buttons.add(new Button(new Rectangle(content.x + i * (SIZE + GAP),
                content.y + (content.height - SIZE) / 2, SIZE, SIZE), requests[i]));
        }
        return new View(buttons, new Rectangle(content.x + reserve, content.y,
            content.width - reserve, content.height));
    }

    private Request[] requests(InfoPanelText mode)
    {
        if (mode == InfoPanelText.ALARM)
        {
            InfoPanelAlarm.Snapshot a = reminders.alarm.snapshot();
            return new Request[]{new Request(Command.ALARM_CANCEL, a.revision,
                a.active && (a.armed || a.fired), "No alarm to cancel")};
        }
        if (mode != InfoPanelText.TIMER && mode != InfoPanelText.STOPWATCH) { return new Request[0]; }
        InfoPanelTools.Snapshot s = tools.snapshot();
        if (mode == InfoPanelText.TIMER)
        {
            Command toggle = s.timerRunning ? Command.TIMER_PAUSE : s.timerPaused
                ? Command.TIMER_RESUME : Command.TIMER_START;
            return new Request[]{new Request(toggle, s.timerRevision, s.active && s.timerDuration > 0,
                s.active ? "Set timer first" : "Timer controls unavailable"),
                new Request(Command.TIMER_STOP, s.timerRevision, s.active && s.timerDuration > 0
                    && (s.timerRunning || s.timerPaused || s.timerFinished), "Timer already stopped")};
        }
        Command toggle = s.stopwatchRunning ? Command.STOPWATCH_PAUSE : s.stopwatchStarted
            ? Command.STOPWATCH_RESUME : Command.STOPWATCH_START;
        return new Request[]{new Request(toggle, s.stopwatchRevision, s.active, "Stopwatch controls unavailable"),
            new Request(Command.STOPWATCH_LAP, s.stopwatchRevision,
                s.active && s.stopwatchRunning && s.laps.size() < InfoPanelTools.MAX_LAPS,
                s.laps.size() >= InfoPanelTools.MAX_LAPS ? "Lap limit reached" : "Start or resume stopwatch to record a lap"),
            new Request(Command.STOPWATCH_RESET, s.stopwatchRevision,
                s.active && (s.stopwatchStarted || !s.laps.isEmpty()), "Stopwatch already reset")};
    }

    /** Atomic with completion polling; edits, expiry, disable and profile changes invalidate old requests. */
    boolean execute(Request expected)
    {
        if (expected == null || !expected.enabled) { return false; }
        // Polling takes these locks in this order too: reminders, then the relevant clock.
        synchronized (reminders)
        {
            Object model = expected.command.tool == InfoPanelText.ALARM ? reminders.alarm : tools;
            synchronized (model)
            {
                boolean matches = false;
                for (Request current : requests(expected.command.tool))
                { if (current.enabled && current.same(expected)) { matches = true; break; } }
                if (!matches) { return false; }
                switch (expected.command)
                {
                    case TIMER_START:
                        tools.startTimer(tools.snapshot().timerDuration / 1000);
                        reminders.dismiss(InfoPanelText.TIMER);
                        break;
                    case TIMER_PAUSE: tools.pauseTimer(); break;
                    case TIMER_RESUME: tools.resumeTimer(); break;
                    case TIMER_STOP:
                        tools.resetTimer(); // retain configured duration, stop and clear pending completion
                        reminders.dismiss(InfoPanelText.TIMER);
                        break;
                    case STOPWATCH_START:
                    case STOPWATCH_RESUME: tools.startStopwatch(); break;
                    case STOPWATCH_PAUSE: tools.pauseStopwatch(); break;
                    case STOPWATCH_LAP: return tools.lap();
                    case STOPWATCH_RESET: tools.resetStopwatch(); break;
                    case ALARM_CANCEL:
                        reminders.alarm.cancel();
                        reminders.dismiss(InfoPanelText.ALARM);
                        break;
                    default: return false;
                }
                return true;
            }
        }
    }
}
