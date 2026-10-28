package com.minimapfilter;

/** Small, clock-injected state machine so entering/leaving the bar does not flicker. */
final class SquareHudFooterVisibility
{
    static final long REVEAL_MS = 75;
    static final long HIDE_MS = 350;
    private boolean visible;
    private long entered = -1;
    private long left = -1;
    private SquareHudFooterMode previousMode;

    boolean update(SquareHudFooterMode mode, boolean hovered, boolean menuOpen,
        boolean covered, long now)
    {
        if (covered)
        {
            reset();
            return false;
        }
        if (mode != previousMode)
        {
            reset();
            previousMode = mode;
        }
        if (mode == SquareHudFooterMode.ALWAYS_VISIBLE)
        {
            visible = true;
            return true;
        }
        // Keep an already-visible bar behind an open native menu. Do not reveal a
        // hidden bar over a menu belonging to the scene or another interface.
        if (menuOpen)
        {
            entered = -1;
            left = -1;
            return visible;
        }
        if (hovered)
        {
            left = -1;
            if (entered < 0) { entered = now; }
            if (now - entered >= REVEAL_MS) { visible = true; }
        }
        else
        {
            entered = -1;
            if (left < 0) { left = now; }
            if (now - left >= HIDE_MS) { visible = false; }
        }
        return visible;
    }

    void reset()
    {
        visible = false;
        entered = -1;
        left = -1;
        previousMode = null;
    }
}
