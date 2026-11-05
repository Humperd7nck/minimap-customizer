package com.minimapfilter;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Captures only close-button gestures, in already translated native canvas coordinates. */
final class InfoPanelReminderInput
{
    private final Consumer<InfoPanelReminders.Notice> dismiss;
    private List<InfoPanelReminderBanner.Entry> entries = Collections.emptyList();
    private List<InfoPanelReminders.Notice> current = Collections.emptyList();
    private int paintedWidth, paintedHeight;
    private boolean interactive;
    private int ownedButtons, swallowedClicks;
    private InfoPanelReminders.Notice pressed;
    private Rectangle pressedBounds;

    InfoPanelReminderInput(Consumer<InfoPanelReminders.Notice> dismiss)
    { this.dismiss = Objects.requireNonNull(dismiss); }

    synchronized void context(int width, int height, boolean visible, boolean interactive,
        List<InfoPanelReminders.Notice> notices)
    {
        this.interactive = visible && interactive;
        current = new ArrayList<>(notices);
        if (!visible || width != paintedWidth || height != paintedHeight) { entries = Collections.emptyList(); }
        cancelStalePress();
    }

    synchronized void painted(int width, int height, List<InfoPanelReminderBanner.Entry> entries)
    {
        paintedWidth = width;
        paintedHeight = height;
        this.entries = new ArrayList<>(entries);
        cancelStalePress();
    }

    synchronized void suspend()
    {
        interactive = false;
        current = Collections.emptyList();
        entries = Collections.emptyList();
        cancelPress();
        // Retain ownership of an already consumed gesture's release/click, even after hiding.
    }

    synchronized void reset()
    { suspend(); ownedButtons = 0; swallowedClicks = 0; }

    synchronized boolean mousePressed(MouseEvent event)
    {
        int button = bit(event.getButton());
        if (ownedButtons == 0) { swallowedClicks = 0; }
        if (event.isConsumed() || button == 0) { return false; }
        if (ownedButtons != 0)
        {
            ownedButtons |= button;
            cancelPress(); // A chord is not a close click.
            event.consume();
            return true;
        }
        InfoPanelReminderBanner.Entry entry = hit(event);
        if (entry == null) { return false; }
        ownedButtons = button;
        if (event.getButton() == MouseEvent.BUTTON1 && !modified(event))
        {
            pressed = entry.notice;
            pressedBounds = entry.closeBounds();
        }
        else { cancelPress(); }
        // Secondary/middle/modifier clicks on the X are inert, not game or camera actions.
        event.consume();
        return true;
    }

    boolean mouseReleased(MouseEvent event)
    {
        InfoPanelReminders.Notice close = null;
        synchronized (this)
        {
            int button = bit(event.getButton());
            if (button == 0 || (ownedButtons & button) == 0) { return false; }
            InfoPanelReminderBanner.Entry entry = hit(event);
            if (!event.isConsumed() && event.getButton() == MouseEvent.BUTTON1 && !modified(event)
                && pressed != null && entry != null && entry.notice == pressed
                && pressedBounds.equals(entry.closeBounds()))
            { close = pressed; }
            ownedButtons &= ~button;
            swallowedClicks |= button;
            cancelPress();
            event.consume();
        }
        // Do not acquire the service/model lock while holding this input lock.
        if (close != null) { dismiss.accept(close); }
        return true;
    }

    synchronized boolean mouseClicked(MouseEvent event)
    {
        int button = bit(event.getButton());
        if (button == 0 || (swallowedClicks & button) == 0) { return false; }
        swallowedClicks &= ~button;
        event.consume();
        return true;
    }

    synchronized boolean mouseDragged(MouseEvent event)
    {
        if (ownedButtons == 0) { return false; }
        event.consume();
        return true;
    }

    private InfoPanelReminderBanner.Entry hit(MouseEvent event)
    {
        if (!interactive) { return null; }
        for (InfoPanelReminderBanner.Entry entry : entries)
        {
            if (current.contains(entry.notice) && entry.closeBounds().contains(event.getPoint())) { return entry; }
        }
        return null;
    }

    private void cancelStalePress()
    {
        if (pressed == null) { return; }
        if (interactive && current.contains(pressed))
        {
            for (InfoPanelReminderBanner.Entry entry : entries)
            {
                if (entry.notice == pressed && pressedBounds.equals(entry.closeBounds())) { return; }
            }
        }
        cancelPress();
    }

    private void cancelPress() { pressed = null; pressedBounds = null; }
    private static boolean modified(MouseEvent e)
    { return e.isAltDown() || e.isControlDown() || e.isShiftDown() || e.isMetaDown(); }
    private static int bit(int button)
    { return button >= MouseEvent.BUTTON1 && button <= MouseEvent.BUTTON3 ? 1 << button : 0; }
}
