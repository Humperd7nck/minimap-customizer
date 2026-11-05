package com.minimapfilter;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Actual new models, atomic disk store and Swing controls. No RuneLite API stubs. */
public final class InfoPanelReminderChecks
{
    private int checks;
    public static void main(String[] args) throws Exception
    { System.out.println("Notes/alarm/notification checks passed: "+runAll()); }
    public static int runAll() throws Exception
    {
        InfoPanelReminderChecks t=new InfoPanelReminderChecks();
        t.alarm(); t.notifications(); t.notebook(); t.storage();
        SwingUtilities.invokeAndWait(t::controls);
        return t.checks;
    }
    private void check(String message,boolean condition) { checks++; if(!condition) { throw new AssertionError(message); } }
    private void eq(String message,Object want,Object got) { check(message+" expected "+want+" got "+got,want.equals(got)); }
    private void rejects(String message,Runnable action)
    { boolean failed=false; try { action.run(); } catch(IllegalArgumentException ex) { failed=true; } check(message,failed); }
    static final class MutableClock extends Clock
    {
        Instant now=Instant.parse("2026-10-29T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now,zone); }
        @Override public Instant instant() { return now; }
    }
    private void alarm()
    {
        MutableClock clock=new MutableClock(); InfoPanelAlarm a=new InfoPanelAlarm(clock);
        check("fresh alarm disarmed",!a.snapshot().armed && !a.pollNotification());
        a.arm(12,1,InfoPanelAlarm.TimeZone.UTC,ZoneId.of("America/Chicago"),"Meeting");
        eq("UTC ignores local offset",Instant.parse("2026-10-29T12:01:00Z"),a.snapshot().target);
        eq("display", "12:01 UTC", a.snapshot().time());
        clock.now=Instant.parse("2026-10-29T12:00:59.999Z"); check("not early",!a.pollNotification());
        clock.now=Instant.parse("2026-10-29T12:01:00Z"); check("fires at target",a.pollNotification());
        for(int i=0;i<20;i++) { check("one shot "+i,!a.pollNotification()); }
        check("fired not armed",a.snapshot().fired && !a.snapshot().armed);
        a.arm(12,0,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"");
        eq("past schedules tomorrow",Instant.parse("2026-10-30T12:00:00Z"),a.snapshot().target);
        a.cancel(); check("cancel clears target",a.snapshot().target==null && !a.snapshot().fired);
        clock.now=Instant.parse("2026-10-29T12:00:00Z");
        a.arm(7,15,InfoPanelAlarm.TimeZone.LOCAL,ZoneId.of("America/Chicago"),"Local");
        eq("local conversion",Instant.parse("2026-10-29T12:15:00Z"),a.snapshot().target);
        clock.now=Instant.parse("2026-10-29T12:16:00Z"); check("resume after missed poll fires",a.pollNotification());
        a.arm(7,16,InfoPanelAlarm.TimeZone.LOCAL,ZoneId.of("America/Chicago"),"");
        eq("setting exactly current minute means tomorrow",Instant.parse("2026-10-30T12:16:00Z"),a.snapshot().target);
        a.suspend(); clock.now=Instant.parse("2026-11-01T12:00:00Z"); check("disabled does not fire",!a.pollNotification());
        a.activate(); check("re-enable does not rearm",!a.snapshot().armed);
        a.arm(12,1,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"");
        a.setNotifications(false); clock.now=clock.now.plusSeconds(60); check("notify checkbox suppresses external event",!a.pollNotification());
        a.setNotifications(true); check("enabling not retroactive",!a.pollNotification());
        a.arm(12,2,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"");
        Instant preserved=a.snapshot().target;
        rejects("hour bounded",() -> a.arm(24,0,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,""));
        rejects("minute bounded",() -> a.arm(0,60,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,""));
        rejects("label bounded",() -> a.arm(0,0,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"x".repeat(61)));
        eq("invalid edits preserve alarm",preserved,a.snapshot().target);
        a.clear(); check("profile clear disarms",!a.snapshot().armed && a.snapshot().label.isEmpty());
        ZoneId ny=ZoneId.of("America/New_York");
        eq("DST gap resolves and shows real target",Instant.parse("2026-03-08T07:30:00Z"),
            InfoPanelAlarm.nextOccurrence(Instant.parse("2026-03-08T06:00:00Z"),ny,LocalTime.of(2,30)));
        eq("DST overlap first occurrence",Instant.parse("2026-11-01T05:30:00Z"),
            InfoPanelAlarm.nextOccurrence(Instant.parse("2026-11-01T04:00:00Z"),ny,LocalTime.of(1,30)));
        eq("DST overlap second occurrence",Instant.parse("2026-11-01T06:30:00Z"),
            InfoPanelAlarm.nextOccurrence(Instant.parse("2026-11-01T05:45:00Z"),ny,LocalTime.of(1,30)));
        for(String zone:new String[]{"UTC","America/Chicago","Asia/Kolkata","Pacific/Auckland"})
        {
            Instant now=Instant.parse("2026-12-31T23:59:50Z");
            Instant next=InfoPanelAlarm.nextOccurrence(now,ZoneId.of(zone),LocalTime.of(0,0));
            check("future across year "+zone,next.isAfter(now));
            eq("midnight local "+zone,LocalTime.MIDNIGHT,next.atZone(ZoneId.of(zone)).toLocalTime());
        }
    }
    private void notifications()
    {
        long[] time={0}; InfoPanelTools tools=new InfoPanelTools(() -> time[0]);
        MutableClock wall=new MutableClock(); InfoPanelAlarm alarm=new InfoPanelAlarm(wall);
        List<String> messages=new ArrayList<>(); InfoPanelReminders r=new InfoPanelReminders(tools,alarm,messages::add);
        tools.startTimer(1); time[0]=999; r.poll(); check("no premature reminder",messages.isEmpty()&&r.notices().isEmpty());
        time[0]=1000; r.poll(); check("timer completion emits",messages.size()==1&&r.notice(InfoPanelText.TIMER)!=null);
        check("footer finished label",InfoPanelFormatting.tool(InfoPanelText.TIMER,tools.snapshot()).text.equals("Timer finished"));
        for(int i=0;i<20;i++) { r.poll(); }
        check("no repeat",messages.size()==1);
        r.dismiss(InfoPanelText.TIMER); check("dismiss clears banner",r.notices().isEmpty());
        r.test(InfoPanelText.TIMER); check("test uses same route",messages.size()==2&&r.notice(InfoPanelText.TIMER)!=null);
        check("test doesn't restart timer",tools.snapshot().timerFinished);
        r.alarm.arm(12,1,InfoPanelAlarm.TimeZone.UTC,ZoneOffset.UTC,"Clan event");
        wall.now=wall.now.plusSeconds(60); r.poll();
        check("two notices coexist",r.notices().size()==2);
        check("alarm label included",messages.get(2).contains("Clan event"));
        r.dismiss(InfoPanelText.TIMER); check("separate dismiss retains alarm",r.notices().size()==1&&r.notice(InfoPanelText.ALARM)!=null);
        r.test(InfoPanelText.NOTES); check("notes cannot test alerts",messages.size()==3);
        r.suspend(); r.test(InfoPanelText.TIMER); check("shutdown guards test",messages.size()==3&&r.notices().isEmpty());
        r.activate(); r.test(InfoPanelText.ALARM); check("test works after restart",messages.size()==4);
        r.clear(); check("profile clear resets both notices",r.notices().isEmpty());
        InfoPanelReminders failure=new InfoPanelReminders(tools,message -> { throw new IllegalStateException("simulated notifier failure"); });
        failure.test(InfoPanelText.TIMER); check("external failure still visible",failure.notice(InfoPanelText.TIMER).status.contains("failed"));
        InfoPanelReminders suppressed=new InfoPanelReminders(tools,message -> { });
        suppressed.test(InfoPanelText.TIMER); check("focus/global suppression still has banner",!suppressed.notices().isEmpty());
        BufferedImage image=new BufferedImage(500,180,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
        InfoPanelReminderBanner.paint(g,500,suppressed.notices()); g.dispose();
        check("banner produces pixels",(image.getRGB(250,14)>>>24)!=0);
        tools.startTimer(1); tools.setNotifications(false); time[0]+=1000; r.poll(); check("per-tool alert off respected",r.notices().isEmpty());
    }
    private void notebook() throws Exception
    {
        InfoPanelNotebook n=InfoPanelNotebook.memory(); AtomicInteger changes=new AtomicInteger(); n.onChange(changes::incrementAndGet);
        n.setNotes("Hello\nworld 📝 <html> plain text"); n.add("Buy supplies"); n.add("Repair equipment"); n.toggle(0);
        eq("completed",1,n.snapshot().completed()); check("dirty",n.snapshot().dirty);
        n.edit(1,"Repair gear"); n.edit(-1,"must not add"); eq("no-selection edit doesn't add",2,n.snapshot().tasks.size());
        n.remove(0); eq("remaining label","Repair gear",n.snapshot().tasks.get(0).text);
        String encoded=InfoPanelNotebookStore.encode(n.snapshot()); InfoPanelNotebook.Snapshot decoded=InfoPanelNotebookStore.decode(encoded);
        eq("unicode newlines retained",n.snapshot().notes,decoded.notes); eq("tasks retained",1,decoded.tasks.size());
        n.saved(n.snapshot().revision); check("saved revision",!n.snapshot().dirty);
        int previous=changes.get(); n.setNotes(n.snapshot().notes); eq("unchanged text no autosave",previous,changes.get());
        rejects("blank task rejected",() -> n.add("  "));
        rejects("long task rejected",() -> n.add("x".repeat(201)));
        rejects("notes limit",() -> n.setNotes("x".repeat(16001)));
        for(int i=1;i<100;i++) { n.add("Task "+i); }
        rejects("task count capped",() -> n.add("overflow"));
        n.deactivate(); previous=changes.get(); n.setNotes("stale"); n.add("late"); n.toggle(0); n.remove(0); eq("old profile cannot mutate",previous,changes.get());
        InfoPanelNotebook waiting=new InfoPanelNotebook(); waiting.setNotes("stale while loading"); waiting.add("late");
        check("unloaded notebook disabled",!waiting.snapshot().editable&&waiting.snapshot().notes.isEmpty());
        for(String bad:new String[]{"", "version=2\nnotes=hi\ntasks=0", "version=1\nnotes=hi\ntasks=101",
            "version=1\nnotes=hi\ntasks=1\ntask.0.text=test\ntask.0.done=oops", "version=1\ntasks=0"})
        {
            boolean rejected=false; try{InfoPanelNotebookStore.decode(bad);}catch(Exception ex){rejected=true;}
            check("malformed data rejected",rejected);
        }
    }
    private void storage() throws Exception
    {
        Path dir=Files.createTempDirectory("minimap-notebook-test");
        try
        {
            InfoPanelNotebookStore store=new InfoPanelNotebookStore(dir);
            InfoPanelNotebookStore.Session a=store.open(11); waitFor(() -> a.notebook.snapshot().loaded);
            a.notebook.setNotes("profile eleven"); a.notebook.add("First task"); a.notebook.toggle(0);
            waitFor(() -> !a.notebook.snapshot().dirty); check("debounced autosave creates file",Files.exists(a.file));
            a.notebook.setNotes("final eleven"); store.release(a);
            InfoPanelNotebookStore.Session b=store.open(22); waitFor(() -> b.notebook.snapshot().loaded);
            check("other profile starts empty",b.notebook.snapshot().notes.isEmpty());
            a.notebook.setNotes("stale old popup"); b.notebook.setNotes("profile twenty-two");
            store.release(b); InfoPanelNotebookStore.Session a2=store.open(11); waitFor(() -> a2.notebook.snapshot().loaded);
            eq("old profile flushed before reload","final eleven",a2.notebook.snapshot().notes);
            check("checked state persisted",a2.notebook.snapshot().tasks.get(0).done);
            a2.notebook.setNotes("shutdown flush"); store.close(); check("worker exits cleanly",store.awaitClosed(3000));
            eq("shutdown saves final edit","shutdown flush",InfoPanelNotebookStore.decode(Files.readString(a.file)).notes);
            eq("profile isolation","profile twenty-two",InfoPanelNotebookStore.decode(Files.readString(b.file)).notes);
            Files.writeString(dir.resolve("notes-33.properties"),"broken-data",StandardCharsets.UTF_8);
            InfoPanelNotebookStore second=new InfoPanelNotebookStore(dir); InfoPanelNotebookStore.Session corrupt=second.open(33);
            waitFor(() -> corrupt.notebook.snapshot().status.contains("Could not"));
            check("corrupt file noneditable",!corrupt.notebook.snapshot().editable);
            corrupt.notebook.setNotes("do not overwrite"); second.close(); second.awaitClosed(3000);
            eq("corrupt original retained","broken-data",Files.readString(corrupt.file));
            Path blocker=dir.resolve("not-a-directory"); Files.writeString(blocker,"keep");
            InfoPanelNotebookStore badStore=new InfoPanelNotebookStore(blocker); InfoPanelNotebookStore.Session unsaved=badStore.open(4);
            waitFor(() -> unsaved.notebook.snapshot().loaded);
            unsaved.notebook.setNotes("recover me"); waitFor(() -> unsaved.notebook.snapshot().status.contains("failed"));
            eq("failed save retains memory","recover me",unsaved.notebook.snapshot().notes);
            Files.delete(blocker); unsaved.notebook.retry(); waitFor(() -> !unsaved.notebook.snapshot().dirty);
            check("retry saves after disk recovery",Files.exists(unsaved.file)); badStore.close();badStore.awaitClosed(3000);
        }
        finally
        {
            try(java.util.stream.Stream<Path> paths=Files.walk(dir))
            { for(Path f:(Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator) { Files.deleteIfExists(f); } }
        }
    }
    static void waitFor(BooleanSupplier condition) throws Exception
    {
        long end=System.nanoTime()+TimeUnitHelper.SECONDS_5;
        while(!condition.getAsBoolean()&&System.nanoTime()<end) { Thread.sleep(15); }
        if(!condition.getAsBoolean()) { throw new AssertionError("Timed out waiting for asynchronous disk result"); }
    }
    private static class TimeUnitHelper { static final long SECONDS_5=5_000_000_000L; }
    private void controls()
    {
        InfoPanelTools tools=new InfoPanelTools(); List<String> messages=new ArrayList<>();
        InfoPanelReminders reminders=new InfoPanelReminders(tools,messages::add);
        InfoPanelNotebook notebook=InfoPanelNotebook.memory(); boolean[] enabled={true};
        InfoPanelPopup popup=new InfoPanelPopup(tools,() -> {},() -> enabled[0],reminders,() -> notebook);
        InfoPanelPopup.View timer=popup.createView(InfoPanelText.TIMER);
        check("Timer Test alert button removed",find(timer.panel,"timer-test")==null);
        check("Timer Dismiss alert button removed",find(timer.panel,"timer-dismiss")==null);
        InfoPanelPopup.View alarm=popup.createView(InfoPanelText.ALARM);
        check("Alarm Test alert button removed",find(alarm.panel,"alarm-test")==null);
        check("Alarm Dismiss alert button removed",find(alarm.panel,"alarm-dismiss")==null);
        ((JSpinner)find(alarm.panel,"alarm-hours")).setValue(23);
        ((JSpinner)find(alarm.panel,"alarm-minutes")).setValue(59);
        ((JComboBox<?>)find(alarm.panel,"alarm-zone")).setSelectedItem(InfoPanelAlarm.TimeZone.UTC);
        ((JTextField)find(alarm.panel,"alarm-label")).setText("Clan event");
        button(alarm,"alarm-set").doClick(); alarm.refresh.run(); check("actual Set arms",reminders.alarm.snapshot().armed);
        check("next occurrence shown",((JLabel)find(alarm.panel,"alarm-next")).getText().contains("23:59"));
        button(alarm,"alarm-cancel").doClick(); check("actual Cancel",!reminders.alarm.snapshot().armed);
        ((JCheckBox)find(alarm.panel,"alarm-notify")).doClick(); check("alarm notification toggle",!reminders.alarm.snapshot().notifications);
        InfoPanelPopup.View notes=popup.createView(InfoPanelText.NOTES);
        ((JTextArea)find(notes.panel,"notes-text")).setText("Autosave content"); eq("actual notes document updates model","Autosave content",notebook.snapshot().notes);
        ((JTextField)find(notes.panel,"notes-task-input")).setText("Buy potions"); button(notes,"notes-add").doClick(); notes.refresh.run();
        JList<?> list=(JList<?>)find(notes.panel,"notes-tasks"); list.setSelectedIndex(0); notes.refresh.run();
        list.getActionMap().get("toggle-task").actionPerformed(new java.awt.event.ActionEvent(list,0,"")); notes.refresh.run();
        eq("space toggles task",1,notebook.snapshot().completed());
        ((JTextField)find(notes.panel,"notes-task-input")).setText("Buy supplies"); button(notes,"notes-update").doClick(); notes.refresh.run();
        eq("actual Update edits","Buy supplies",notebook.snapshot().tasks.get(0).text);
        enabled[0]=false; button(notes,"notes-remove").doClick(); check("stale popup cannot remove",notebook.snapshot().tasks.size()==1);
        enabled[0]=true; button(notes,"notes-remove").doClick(); notes.refresh.run(); check("Remove works",notebook.snapshot().tasks.isEmpty());
        check("both new popup modes",InfoPanelText.ALARM.isTool()&&InfoPanelText.NOTES.isTool());
        check("saved enum order retained",InfoPanelText.values()[18]==InfoPanelText.SERVER_TIME);
    }
    static Component find(Container root,String name)
    {
        if(name.equals(root.getName())) { return root; }
        for(Component c:root.getComponents())
        {
            if(name.equals(c.getName())) { return c; }
            if(c instanceof Container) { Component found=find((Container)c,name); if(found!=null) { return found; } }
        }
        return null;
    }
    static JButton button(InfoPanelPopup.View view,String name) { return (JButton)find(view.panel,name); }
}
