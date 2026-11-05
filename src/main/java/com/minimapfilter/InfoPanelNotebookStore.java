package com.minimapfilter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Local-only, bounded, atomic notebook storage. All disk work uses one owned worker. */
final class InfoPanelNotebookStore implements AutoCloseable
{
    private static final long MAX_BYTES=512_000;
    private final Path directory;
    private final ScheduledExecutorService io;
    private static ScheduledExecutorService worker()
    {
        ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(1,r ->
        { Thread t=new Thread(r,"minimap-customizer-notes"); t.setDaemon(true); return t; });
        executor.setKeepAliveTime(5,TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
    private final Set<Session> sessions=new HashSet<>();
    private boolean closed;

    InfoPanelNotebookStore(Path directory) { this.directory=directory; this.io=worker(); }
    final class Session
    {
        final InfoPanelNotebook notebook=new InfoPanelNotebook();
        final Path file;
        private ScheduledFuture<?> pending;
        private boolean released;
        Session(long profile)
        {
            file=directory.resolve("notes-"+Long.toUnsignedString(profile)+".properties");
            notebook.onChange(this::schedule);
            notebook.onRetry(this::retry);
        }
        private void load()
        {
            try
            {
                if(Files.exists(file))
                {
                    if(Files.size(file)>MAX_BYTES) { throw new IOException("Notebook too large"); }
                    InfoPanelNotebook.Snapshot s=decode(Files.readString(file,StandardCharsets.UTF_8));
                    notebook.load(s.notes,s.tasks);
                }
                else { notebook.load("",java.util.Collections.emptyList()); }
            }
            catch(IOException | RuntimeException ex)
            { notebook.failure("Could not load notes. Original file kept; use Retry."); }
        }
        private void schedule()
        {
            synchronized(InfoPanelNotebookStore.this)
            {
                if(closed || released) { return; }
                if(pending!=null) { pending.cancel(false); }
                pending=io.schedule(this::save,400,TimeUnit.MILLISECONDS);
            }
        }
        private void retry()
        {
            synchronized(InfoPanelNotebookStore.this)
            {
                if(closed || released) { return; }
                if(pending!=null) { pending.cancel(false); }
                io.execute(() -> { if(notebook.snapshot().loaded) { save(); } else { load(); } });
            }
        }
        private void save()
        {
            InfoPanelNotebook.Snapshot snapshot=notebook.snapshot();
            if(!snapshot.loaded || !snapshot.dirty) { return; }
            Path tmp=null;
            try
            {
                Files.createDirectories(directory);
                String encoded=encode(snapshot);
                tmp=Files.createTempFile(directory,"notes-",".tmp");
                Files.writeString(tmp,encoded,StandardCharsets.UTF_8);
                try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
                notebook.saved(snapshot.revision);
            }
            catch(IOException | RuntimeException ex) { notebook.failure("Save failed; notes remain in memory. Use Retry."); }
            finally { if(tmp!=null) { try { Files.deleteIfExists(tmp); } catch(IOException ignored) { } } }
        }
    }
    synchronized Session open(long profile)
    {
        if(closed) { throw new IllegalStateException("Notebook store closed"); }
        Session s=new Session(profile); sessions.add(s); io.execute(s::load); return s;
    }
    synchronized void release(Session s)
    {
        if(s==null || s.released) { return; }
        s.notebook.deactivate(); s.released=true;
        if(s.pending!=null) { s.pending.cancel(false); }
        if(!closed) { io.execute(s::save); }
        sessions.remove(s);
    }
    @Override public synchronized void close()
    {
        if(closed) { return; }
        for(Session s:new ArrayList<>(sessions)) { release(s); }
        closed=true;
        io.shutdown(); // queued final writes retain the old profile's captured path
    }
    boolean awaitClosed(long milliseconds) throws InterruptedException { return io.awaitTermination(milliseconds,TimeUnit.MILLISECONDS); }

    static String encode(InfoPanelNotebook.Snapshot snapshot) throws IOException
    {
        InfoPanelNotebook.validate(snapshot.notes,snapshot.tasks);
        Properties p=new Properties(); p.setProperty("version","1"); p.setProperty("notes",snapshot.notes);
        p.setProperty("tasks",Integer.toString(snapshot.tasks.size()));
        for(int i=0;i<snapshot.tasks.size();i++)
        { p.setProperty("task."+i+".text",snapshot.tasks.get(i).text); p.setProperty("task."+i+".done",Boolean.toString(snapshot.tasks.get(i).done)); }
        StringWriter out=new StringWriter(); p.store(out,"Minimap Customizer notebook - local profile data");
        return out.toString();
    }
    static InfoPanelNotebook.Snapshot decode(String data) throws IOException
    {
        if(data==null || data.length()>MAX_BYTES) { throw new IOException("Notebook too large"); }
        Properties p=new Properties(); p.load(new StringReader(data));
        if(!"1".equals(p.getProperty("version"))) { throw new IOException("Unknown notebook format"); }
        int count;
        try { count=Integer.parseInt(p.getProperty("tasks","-1")); }
        catch(NumberFormatException ex) { throw new IOException("Invalid task count",ex); }
        if(count<0 || count>InfoPanelNotebook.MAX_TASKS) { throw new IOException("Invalid task count"); }
        String notes=p.getProperty("notes");
        List<InfoPanelNotebook.Task> tasks=new ArrayList<>();
        for(int i=0;i<count;i++)
        {
            String done=p.getProperty("task."+i+".done");
            if(!"true".equals(done)&&!"false".equals(done)) { throw new IOException("Invalid task state"); }
            tasks.add(new InfoPanelNotebook.Task(p.getProperty("task."+i+".text"),Boolean.parseBoolean(done)));
        }
        InfoPanelNotebook.validate(notes,tasks);
        return new InfoPanelNotebook.Snapshot(notes,tasks,0,true,true,false,"Saved locally for this profile");
    }
}
