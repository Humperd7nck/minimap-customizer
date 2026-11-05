package com.minimapfilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual service routing; compiled/executed by the user's full RuneLite Gradle build. */
public class InfoPanelReminderIntegrationTest
{
    private static Field field(String name) throws Exception
    { Field f=InfoPanelService.class.getDeclaredField(name); f.setAccessible(true); return f; }

    @Test public void serviceShowsNotesAndAlarmWithoutChangingOtherModes() throws Exception
    {
        InfoPanelService service=new InfoPanelService(null,new MinimapFilterConfig(){},null,null,null);
        InfoPanelNotebook notes=InfoPanelNotebook.memory(); notes.setNotes("Example"); notes.add("First"); notes.add("Second"); notes.toggle(0);
        field("notebook").set(service,notes);
        assertEquals("Tasks: 1/2",service.content(InfoPanelText.NOTES).text);
        assertEquals("Click for notes and checklist",service.content(InfoPanelText.NOTES).tooltip);
        InfoPanelTools tools=(InfoPanelTools)field("tools").get(service);
        InfoPanelAlarm alarm=new InfoPanelAlarm(Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"),ZoneOffset.UTC));
        InfoPanelReminders reminders=new InfoPanelReminders(tools,alarm,message -> {});
        field("reminders").set(service,reminders);
        assertEquals("Alarm: Off",service.content(InfoPanelText.ALARM).text);
        alarm.arm(12,1,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"");
        assertEquals("Alarm 12:01 UTC",service.content(InfoPanelText.ALARM).text);
        assertEquals("Set alarm",service.content(InfoPanelText.ALARM).tooltip);
        assertEquals("Calculator",service.content(InfoPanelText.CALCULATOR).text);
        assertEquals("Stopwatch 00:00",service.content(InfoPanelText.STOPWATCH).text);
    }

    @Test public void notificationPollDoesNotRequireTimerSelectedOrPopupOpen() throws Exception
    {
        InfoPanelService service=new InfoPanelService(null,new MinimapFilterConfig(){},null,null,null);
        field("running").setBoolean(service,true);
        long[] now={0}; InfoPanelTools tools=new InfoPanelTools(() -> now[0]);
        java.util.List<String> delivered=new java.util.ArrayList<>();
        InfoPanelReminders reminders=new InfoPanelReminders(tools,delivered::add);
        field("tools").set(service,tools); field("reminders").set(service,reminders);
        Method tick=InfoPanelService.class.getDeclaredMethod("notificationTick");tick.setAccessible(true);
        assertFalse(service.isPopupOpen());
        tools.startTimer(1); now[0]=1000; tick.invoke(service);
        assertEquals(1,delivered.size()); assertEquals(1,service.notices().size());
        tick.invoke(service); assertEquals(1,delivered.size());
        service.profileChanged(); assertTrue(service.notices().isEmpty());
        assertFalse(reminders.alarm.snapshot().armed);
        service.stop(); reminders.test(InfoPanelText.ALARM); assertTrue(service.notices().isEmpty());
    }

    @Test public void defaultsKeepSoundPreferencesSeparateFromVisualNotice()
    {
        MinimapFilterConfig config=new MinimapFilterConfig(){};
        assertTrue(config.toolCompletionBanner());
        assertTrue(config.toolNotifications().isEnabled());
        assertFalse(config.toolNotifications().isOverride());
    }
}
