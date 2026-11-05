package com.minimapfilter;

/** Thread-safe view model. UI actions enqueue intent; only tick() touches native audio. */
final class InfoPanelVolume
{
    interface Backend
    {
        int read();
        void write(int percent);
        default String diagnostics() { return ""; }
    }
    static final class Snapshot
    {
        final boolean available, muted, pending;
        final int percent, selected;
        final String status, diagnostics;
        Snapshot(boolean available, int percent, int selected, boolean pending, String status, String diagnostics)
        {
            this.available = available; this.percent = percent; this.selected = selected;
            this.muted = available && percent == 0; this.pending = pending; this.status = status;
            this.diagnostics = diagnostics;
        }
        boolean canUnmute() { return available && selected > 0 && !pending; }
    }
    private int percent = -1, selected;
    private boolean available;
    private Integer pendingLevel;
    private boolean pendingToggle;
    private int expected;
    private long lastRead = Long.MIN_VALUE;
    private String status = "Select Volume while logged in.";
    private String diagnostics = "";

    synchronized Snapshot snapshot()
    {
        return new Snapshot(available, percent, selected, pendingLevel != null || pendingToggle, status, diagnostics);
    }

    synchronized void choose(int value)
    {
        if (value < 0 || value > 100) { throw new IllegalArgumentException("Volume must be 0-100."); }
        if (!available || pendingToggle) { return; }
        if (percent == 0)
        {
            selected = value;
            status = value == 0 ? "Choose a level above 0%, then Unmute." : "Unmute at " + value + "%.";
            return; // Adjusting a muted slider must never unexpectedly turn sound on.
        }
        expected = percent;
        pendingLevel = value;
    }

    synchronized void toggle()
    {
        if (!available || pendingLevel != null || pendingToggle || (percent == 0 && selected == 0)) { return; }
        expected = percent;
        pendingToggle = true;
    }

    synchronized void cancelPending() { pendingToggle = false; pendingLevel = null; }

    synchronized void suspend()
    {
        cancelPending(); available = false; percent = -1; lastRead = Long.MIN_VALUE;
        status = "Select Volume while logged in."; diagnostics = "";
    }

    synchronized void reset() { suspend(); selected = 0; }

    /** Called on the client thread; reads at most 4 times/sec and never changes audio on its own. */
    synchronized void tick(Backend backend, boolean active, boolean acceptsWrites, long now)
    {
        if (!active) { suspend(); return; }
        if (!acceptsWrites) { cancelPending(); }
        boolean pending = pendingLevel != null || pendingToggle;
        if (!pending && lastRead != Long.MIN_VALUE && now - lastRead < 250) { return; }
        lastRead = now;
        try
        {
            int actual = valid(backend.read());
            diagnostics = "";
            if (pending && (!available || actual != expected))
            {
                cancelPending();
                observe(actual);
                status = "Volume changed in-game; try again.";
                return;
            }
            if (pending)
            {
                int target = pendingToggle ? (actual == 0 ? selected : 0) : pendingLevel;
                cancelPending();
                if (target != actual) { backend.write(target); }
                actual = valid(backend.read());
                if (actual != target) { throw new IllegalStateException("Volume read-back did not match."); }
            }
            observe(actual);
        }
        catch (RuntimeException | LinkageError failure)
        {
            cancelPending(); available = false; percent = -1;
            status = failure.getMessage() == null ? "Native master volume is unavailable." : failure.getMessage();
            try
            {
                String detail = backend.diagnostics();
                diagnostics = detail == null ? "" : detail;
            }
            catch (RuntimeException | LinkageError ignored) { diagnostics = ""; }
        }
    }

    private void observe(int actual)
    {
        if (actual > 0) { selected = actual; }
        percent = actual; available = true;
        status = actual > 0 ? "OSRS game audio only."
            : selected > 0 ? "Muted. Unmute at " + selected + "%." : "Muted. Choose a level, then Unmute.";
    }

    private static int valid(int value)
    {
        if (value < 0 || value > 100) { throw new IllegalStateException("Native volume is unavailable."); }
        return value;
    }

    synchronized SquareHudInfoText.Content content()
    {
        return new SquareHudInfoText.Content(!available ? "Volume: —" : percent == 0 ? "Volume: Muted" : "Volume: " + percent + "%",
            available ? "Control volume" : status);
    }
}
