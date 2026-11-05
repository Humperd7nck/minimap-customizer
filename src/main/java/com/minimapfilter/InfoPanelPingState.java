package com.minimapfilter;

/** Single-flight, world-bound ping cache. Contains no networking or RuneLite API calls. */
final class InfoPanelPingState
{
    static final long INTERVAL_MS = 5_000;
    static final long STALE_MS = 15_000;
    static final class Request
    {
        final int world;
        final long generation;
        Request(int world, long generation) { this.world = world; this.generation = generation; }
    }
    private int world;
    private long generation;
    private Request pending;
    private long lastRequest;
    private boolean requested;
    private int value = -1;
    private long measured;

    synchronized void target(int newWorld)
    {
        newWorld = Math.max(0, newWorld);
        if (world != newWorld)
        {
            world = newWorld;
            generation++;
            value = -1;
            requested = false;
            // A previous world's worker still owns the single flight until it finishes.
        }
    }

    synchronized Request begin(long now)
    {
        if (world == 0 || pending != null || (requested && now - lastRequest < INTERVAL_MS)) { return null; }
        pending = new Request(world, generation);
        requested = true;
        lastRequest = now;
        return pending;
    }

    synchronized void complete(Request request, int milliseconds, long now)
    {
        if (request == null || pending != request) { return; }
        pending = null;
        if (world != request.world || generation != request.generation) { return; }
        value = milliseconds >= 0 ? milliseconds : -1;
        measured = now;
    }

    synchronized int value(long now)
    {
        return world == 0 || value < 0 || now - measured > STALE_MS || now < measured ? -1 : value;
    }

    synchronized void reset()
    {
        invalidate();
        pending = null; // A queued task can be cancelled before its finally block executes.
    }

    synchronized void invalidate()
    {
        world = 0;
        generation++;
        value = -1;
        requested = false;
    }
}
