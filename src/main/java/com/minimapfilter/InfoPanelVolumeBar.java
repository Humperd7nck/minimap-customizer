package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

/** Inline master-volume UI. Canvas coordinates in/out; no native audio calls or popup windows. */
final class InfoPanelVolumeBar
{
    private static final Color EDGE = new Color(30, 26, 21);
    private static final Color TRIM = new Color(112, 101, 79);
    private static final Color TRACK = new Color(43, 39, 31);
    private static final Color GOLD = new Color(232, 173, 65);
    private static final Color LIGHT = new Color(255, 224, 142);
    private static final Color GREEN = new Color(51, 157, 55);
    private static final Color MUTED = new Color(197, 94, 72);
    private static final Color DISABLED = new Color(115, 110, 98);
    private enum Gesture { NONE, SPEAKER, SLIDER }

    /** Drawing and hit testing use exactly the same layout, confined between the native buttons. */
    static final class Layout
    {
        final Rectangle bounds, speaker, slider;
        final int first, last, cy;
        Layout(Rectangle region)
        {
            bounds = region == null ? new Rectangle() : new Rectangle(region);
            if (bounds.width < 60 || bounds.height < 20) { bounds.setSize(0, 0); }
            int height = Math.min(26, bounds.height);
            int top = bounds.y + (bounds.height - height) / 2;
            speaker = new Rectangle(bounds.x, top, bounds.isEmpty() ? 0 : 24, height);
            slider = new Rectangle(bounds.x + 28, top,
                bounds.isEmpty() ? 0 : bounds.width - 28, height);
            first = slider.x + 5;
            last = slider.x + slider.width - 6;
            cy = top + height / 2;
        }
        boolean usable() { return !bounds.isEmpty(); }
        int value(int x)
        {
            if (!usable()) { return 0; }
            return (int) Math.round(Math.max(0.0, Math.min(1.0,
                ((double) x - first) / (last - first))) * 100.0);
        }
        int position(int value)
        {
            return first + (int) Math.round((last - first) * Math.max(0, Math.min(100, value)) / 100.0);
        }
        Rectangle thumb(int value) { return new Rectangle(position(value) - 4, cy - 7, 9, 15); }
    }

    private final InfoPanelVolume volume;
    private final boolean ownsPending;
    private Layout layout = new Layout(null);
    private boolean shown, acceptsInput;
    private Gesture gesture = Gesture.NONE;
    private int downMask, clickMask;
    private int preview = -1, dragOffset, initialPercent, initialLevel;
    private boolean thumbGrab;
    private Point press;

    InfoPanelVolumeBar(InfoPanelVolume volume) { this(volume, true); }
    InfoPanelVolumeBar(InfoPanelVolume volume, boolean ownsPending)
    { this.volume = Objects.requireNonNull(volume); this.ownsPending = ownsPending; }

    /** Client-thread context publication; the model and the gesture state are safe for AWT access. */
    synchronized void update(Rectangle region, boolean visible, boolean inputAllowed)
    {
        Layout next = new Layout(region);
        boolean nextShown = visible && next.usable();
        InfoPanelVolume.Snapshot state = volume.snapshot();
        if (!nextShown || !inputAllowed || !next.bounds.equals(layout.bounds)
            || !state.available || (gesture != Gesture.NONE && state.percent != initialPercent))
        {
            cancelGesture();
        }
        shown = nextShown;
        acceptsInput = nextShown && inputAllowed;
        layout = next;
        if (!acceptsInput && ownsPending) { volume.cancelPending(); }
        if (gesture == Gesture.NONE && !state.pending) { preview = -1; }
    }

    /** Keep release/click ownership after cancellation so a partial gesture cannot become a game click. */
    synchronized void suspend()
    {
        shown = false; acceptsInput = false;
        cancelGesture();
        if (ownsPending) { volume.cancelPending(); }
    }

    synchronized boolean isHolding() { return shown && acceptsInput && gesture != Gesture.NONE; }
    synchronized boolean isVisible() { return shown; }
    synchronized Layout layout() { return new Layout(layout.bounds); }
    synchronized int displayedPercent()
    {
        InfoPanelVolume.Snapshot state = volume.snapshot();
        return preview >= 0 && (gesture == Gesture.SLIDER || state.pending) ? preview
            : state.available ? (state.muted ? state.selected : state.percent) : 0;
    }

    synchronized boolean mousePressed(MouseEvent event)
    {
        int bit = buttonBit(event.getButton());
        if (bit == 0) { return false; }
        // A new press replaces any missing clicked event from the preceding drag.
        if (downMask == 0) { clickMask = 0; }
        if (downMask != 0)
        {
            // A second button during a drag cancels its action but retains both releases.
            downMask |= bit; clickMask |= bit; cancelGesture(); event.consume(); return true;
        }
        if (event.isConsumed() || !acceptsInput || !layout.bounds.contains(event.getPoint())) { return false; }
        downMask = bit; clickMask = bit;
        InfoPanelVolume.Snapshot state = volume.snapshot();
        if (event.getButton() == MouseEvent.BUTTON1 && !modified(event) && state.available && !state.pending)
        {
            initialPercent = state.percent;
            press = event.getPoint();
            if (layout.speaker.contains(press) && (!state.muted || state.canUnmute()))
            {
                gesture = Gesture.SPEAKER;
            }
            else if (layout.slider.contains(press))
            {
                gesture = Gesture.SLIDER;
                initialLevel = state.muted ? state.selected : state.percent;
                thumbGrab = layout.thumb(initialLevel).contains(press);
                dragOffset = thumbGrab ? press.x - layout.position(initialLevel) : 0;
                preview = dragValue(press.x);
            }
        }
        event.consume();
        return true;
    }

    synchronized boolean mouseDragged(MouseEvent event)
    {
        if (downMask == 0) { return false; }
        if (event.isConsumed() || modified(event) || !validGesture()) { cancelGesture(); }
        if (gesture == Gesture.SLIDER) { preview = dragValue(event.getX()); }
        if (gesture == Gesture.SPEAKER && press != null && press.distance(event.getPoint()) > 4) { cancelGesture(); }
        event.consume();
        return true;
    }

    synchronized boolean mouseReleased(MouseEvent event)
    {
        int bit = buttonBit(event.getButton());
        if ((downMask & bit) == 0) { return false; }
        boolean perform = !event.isConsumed() && event.getButton() == MouseEvent.BUTTON1
            && !modified(event) && validGesture();
        if (perform && gesture == Gesture.SLIDER)
        {
            preview = dragValue(event.getX());
            volume.choose(preview); // Queue only; the client thread applies and verifies after this event.
        }
        else if (perform && gesture == Gesture.SPEAKER && layout.speaker.contains(event.getPoint()))
        {
            volume.toggle();
        }
        gesture = Gesture.NONE;
        press = null;
        downMask &= ~bit;
        event.consume();
        return true;
    }

    synchronized boolean mouseClicked(MouseEvent event)
    {
        int bit = buttonBit(event.getButton());
        if ((clickMask & bit) == 0) { return false; }
        clickMask &= ~bit;
        event.consume();
        return true;
    }

    synchronized void mouseMoved(MouseEvent event)
    {
        // Focus loss or leaving the component can suppress a release event. Never keep a stale capture.
        if ((event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK)) == 0 && downMask != 0)
        {
            cancelGesture(); downMask = 0; clickMask = 0;
        }
    }

    private int dragValue(int x)
    {
        // Native percentages can be more precise than the short track's pixel grid.
        // Merely grabbing/releasing the thumb must not round a level to a different value.
        return thumbGrab && press != null && x == press.x ? initialLevel : layout.value(x - dragOffset);
    }

    private boolean validGesture()
    {
        InfoPanelVolume.Snapshot state = volume.snapshot();
        return shown && acceptsInput && state.available && !state.pending && state.percent == initialPercent;
    }
    private void cancelGesture() { gesture = Gesture.NONE; preview = -1; press = null; }
    private static int buttonBit(int button) { return button >= 1 && button <= 5 ? 1 << button : 0; }
    private static boolean modified(MouseEvent event)
    {
        return event.isAltDown() || event.isControlDown() || event.isShiftDown() || event.isMetaDown();
    }

    synchronized String tooltip(Point mouse)
    {
        if (!shown || mouse == null || !layout.bounds.contains(mouse)) { return ""; }
        InfoPanelVolume.Snapshot state = volume.snapshot();
        if (!state.available) { return state.status; }
        if (layout.speaker.contains(mouse))
        {
            if (state.muted && state.selected == 0) { return "Choose a level above 0%, then click to unmute"; }
            return state.muted ? "Unmute master volume" : "Mute master volume";
        }
        if (layout.slider.contains(mouse))
        {
            int value = displayedPercent();
            return state.muted ? "Master volume: 0% (muted) · Unmute at " + value + "%"
                : "Master volume: " + value + "%";
        }
        return "";
    }

    /** Original vector/pixel artwork, sized in native canvas pixels; no external sprites or assets. */
    synchronized void paint(Graphics2D graphics, Point mouse)
    {
        if (!shown) { return; }
        InfoPanelVolume.Snapshot state = volume.snapshot();
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(layout.bounds);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            boolean speakerHover = acceptsInput && mouse != null && layout.speaker.contains(mouse);
            boolean sliderHover = acceptsInput && mouse != null && layout.slider.contains(mouse);
            paintSpeaker(g, layout.speaker, state.available, state.muted, speakerHover);
            int x = layout.slider.x, y = layout.cy;
            int width = layout.slider.width;
            g.setColor(EDGE); g.fillRoundRect(x, y - 5, width, 11, 8, 8);
            g.setColor(sliderHover && state.available ? LIGHT : TRIM);
            g.drawRoundRect(x, y - 5, width - 1, 10, 8, 8);
            g.setColor(TRACK); g.fillRect(x + 4, y - 3, Math.max(0, width - 8), 7);
            g.setColor(new Color(143, 132, 105));
            for (int value = 0; value <= 100; value += 25)
            {
                int tick = layout.position(value);
                g.drawLine(tick, y - 1, tick, y + 1);
            }
            Rectangle thumb = layout.thumb(displayedPercent());
            g.setColor(EDGE); g.fillRect(thumb.x, thumb.y, thumb.width, thumb.height);
            g.setColor(state.available ? (state.muted ? TRIM : GREEN) : DISABLED);
            g.fillRect(thumb.x + 2, thumb.y + 2, thumb.width - 4, thumb.height - 4);
            g.setColor(sliderHover || gesture == Gesture.SLIDER ? LIGHT : new Color(172, 171, 141));
            g.drawRect(thumb.x + 1, thumb.y + 1, thumb.width - 3, thumb.height - 3);
            if (state.available && !state.muted)
            {
                g.setColor(new Color(163, 229, 139));
                g.drawLine(thumb.x + 3, thumb.y + 3, thumb.x + 5, thumb.y + 3);
            }
        }
        finally { g.dispose(); }
    }

    static void paintSpeaker(Graphics2D g, Rectangle rect, boolean available, boolean muted, boolean hover)
    {
        int x = rect.x + 2, y = rect.y + rect.height / 2;
        Polygon body = new Polygon(new int[]{x, x + 5, x + 11, x + 11, x + 5, x},
            new int[]{y - 4, y - 4, y - 9, y + 9, y + 4, y + 4}, 6);
        g.setColor(EDGE); g.fillPolygon(body);
        g.setColor(available ? hover ? LIGHT : GOLD : DISABLED); g.drawPolygon(body);
        g.setColor(new Color(97, 87, 65)); g.fillRect(x + 2, y - 2, 3, 5);
        g.drawLine(x + 8, y - 5, x + 8, y + 5);
        g.setStroke(new BasicStroke(1));
        if (muted)
        {
            g.setColor(MUTED);
            g.drawLine(x + 14, y - 4, x + 20, y + 4);
            g.drawLine(x + 20, y - 4, x + 14, y + 4);
        }
        else if (available)
        {
            g.setColor(hover ? LIGHT : GOLD);
            g.drawArc(x + 10, y - 5, 7, 10, -65, 130);
            g.drawArc(x + 10, y - 8, 12, 16, -65, 130);
        }
        else
        {
            g.setColor(DISABLED); g.drawLine(x + 15, y, x + 20, y);
        }
    }
}
