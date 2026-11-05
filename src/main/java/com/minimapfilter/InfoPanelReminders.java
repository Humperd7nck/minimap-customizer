package com.minimapfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Persistent in-client notices are independent of RuneLite's sound/focus policy. */
final class InfoPanelReminders
{
    static final class Notice
    {
        final InfoPanelText tool;
        final String message, status;
        Notice(InfoPanelText tool, String message, String status)
        { this.tool=tool; this.message=message; this.status=status; }
    }
    final InfoPanelAlarm alarm;
    private final InfoPanelTools tools;
    private final Consumer<String> notify;
    private final Map<InfoPanelText, Notice> notices = new LinkedHashMap<>();
    private boolean active=true;

    InfoPanelReminders(InfoPanelTools tools, Consumer<String> notify)
    { this(tools, new InfoPanelAlarm(), notify); }
    InfoPanelReminders(InfoPanelTools tools, InfoPanelAlarm alarm, Consumer<String> notify)
    { this.tools=Objects.requireNonNull(tools); this.alarm=Objects.requireNonNull(alarm); this.notify=Objects.requireNonNull(notify); }

    synchronized void poll()
    {
        if (!active) { return; }
        if (tools.pollNotification()) { emit(InfoPanelText.TIMER, "Timer finished"); }
        if (alarm.pollNotification())
        {
            String label=alarm.snapshot().label;
            emit(InfoPanelText.ALARM, label.isEmpty() ? "Alarm finished" : "Alarm: " + label);
        }
    }
    synchronized void test(InfoPanelText tool)
    {
        if (active && (tool==InfoPanelText.TIMER || tool==InfoPanelText.ALARM)) { emit(tool, tool + " test alert"); }
    }
    private void emit(InfoPanelText tool, String message)
    {
        String status="RuneLite alert requested; sound/tray/focus settings still apply.";
        try { notify.accept("Minimap Customizer: " + message + "."); }
        catch (RuntimeException failed) { status="RuneLite alert delivery failed. Check client.log and notification settings."; }
        notices.put(tool, new Notice(tool, message, status));
    }
    synchronized List<Notice> notices() { return Collections.unmodifiableList(new ArrayList<>(notices.values())); }
    synchronized Notice notice(InfoPanelText tool) { return notices.get(tool); }
    synchronized void dismiss(InfoPanelText tool) { notices.remove(tool); }
    /** A banner click must never dismiss a newer notice that replaced the one on screen. */
    synchronized void dismiss(Notice expected)
    {
        if (expected != null && notices.get(expected.tool) == expected) { notices.remove(expected.tool); }
    }
    synchronized void clear() { notices.clear(); alarm.clear(); }
    synchronized void activate() { active=true; alarm.activate(); }
    synchronized void suspend() { active=false; notices.clear(); alarm.suspend(); }
}
