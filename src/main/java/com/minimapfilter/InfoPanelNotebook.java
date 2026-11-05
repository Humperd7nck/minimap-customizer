package com.minimapfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A profile-bound notebook; an old popup never writes to a new profile's model. */
final class InfoPanelNotebook
{
    static final int MAX_NOTES=16000, MAX_TASKS=100, MAX_TASK=200;
    static final class Task
    {
        final String text;
        final boolean done;
        Task(String text, boolean done) { this.text=text; this.done=done; }
    }
    static final class Snapshot
    {
        final String notes, status;
        final List<Task> tasks;
        final long revision;
        final boolean editable, loaded, dirty;
        Snapshot(String notes, List<Task> tasks, long revision, boolean editable, boolean loaded, boolean dirty, String status)
        {
            this.notes=notes; this.tasks=Collections.unmodifiableList(new ArrayList<>(tasks)); this.revision=revision;
            this.editable=editable; this.loaded=loaded; this.dirty=dirty; this.status=status;
        }
        int completed() { int n=0; for (Task t : tasks) { if(t.done) { n++; } } return n; }
    }
    private String notes="", error="";
    private final List<Task> tasks=new ArrayList<>();
    private long revision, savedRevision;
    private boolean active=true, loaded;
    private Runnable changed=() -> {};
    private Runnable retry=() -> {};

    static InfoPanelNotebook memory()
    { InfoPanelNotebook n=new InfoPanelNotebook(); n.load("", Collections.emptyList()); return n; }
    synchronized void onChange(Runnable callback) { changed=Objects.requireNonNull(callback); }
    synchronized void onRetry(Runnable callback) { retry=Objects.requireNonNull(callback); }
    void retry() { Runnable action; synchronized(this) { action=retry; } action.run(); }
    synchronized void load(String notes, List<Task> tasks)
    {
        validate(notes,tasks);
        this.notes=notes; this.tasks.clear(); this.tasks.addAll(tasks);
        revision=0; savedRevision=0; loaded=true; error="";
    }
    synchronized void failure(String message) { error=message; }
    synchronized void saved(long revision)
    { savedRevision=Math.max(savedRevision, revision); error=""; }
    synchronized Snapshot snapshot()
    {
        String status=!error.isEmpty() ? error : !loaded ? "Loading..." : revision>savedRevision ? "Saving..." : "Saved locally for this profile";
        return new Snapshot(notes,tasks,revision,active&&loaded,loaded,revision>savedRevision,status);
    }
    synchronized void deactivate() { active=false; }
    void setNotes(String value)
    {
        Runnable callback;
        synchronized(this)
        {
            if (!active || !loaded) { return; }
            if(value==null || value.length()>MAX_NOTES) { throw new IllegalArgumentException("Notes are limited to 16,000 characters."); }
            if(notes.equals(value)) { return; }
            notes=value; revision++; error=""; callback=changed;
        }
        callback.run();
    }
    void add(String value) { mutate(-1, value, false, false); }
    void edit(int index,String value) { if(index>=0) { mutate(index,value,false,false); } }
    void remove(int index) { mutate(index,null,true,false); }
    void toggle(int index) { mutate(index,null,false,true); }
    private void mutate(int index,String value,boolean remove,boolean toggle)
    {
        Runnable callback;
        synchronized(this)
        {
            if(!active || !loaded) { return; }
            if(index < -1 || index>=tasks.size()) { return; }
            if(index==-1 && (remove||toggle)) { return; }
            if(!remove&&!toggle)
            {
                value=value==null ? "" : value.trim();
                if(value.isEmpty() || value.length()>MAX_TASK) { throw new IllegalArgumentException("Enter a task of 1-200 characters."); }
            }
            if(index==-1)
            {
                if(tasks.size()>=MAX_TASKS) { throw new IllegalArgumentException("Up to 100 tasks per profile."); }
                tasks.add(new Task(value,false));
            }
            else if(remove) { tasks.remove(index); }
            else if(toggle) { Task t=tasks.get(index); tasks.set(index,new Task(t.text,!t.done)); }
            else { Task t=tasks.get(index); tasks.set(index,new Task(value,t.done)); }
            revision++; error=""; callback=changed;
        }
        callback.run();
    }
    static void validate(String notes,List<Task> tasks)
    {
        if(notes==null || notes.length()>MAX_NOTES || tasks==null || tasks.size()>MAX_TASKS)
        { throw new IllegalArgumentException("Notebook exceeds supported limits."); }
        for(Task t:tasks)
        { if(t==null || t.text==null || t.text.trim().isEmpty() || t.text.length()>MAX_TASK) { throw new IllegalArgumentException("Invalid checklist entry."); } }
    }
}
