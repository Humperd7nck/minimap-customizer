package com.minimapfilter;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/** A second view of the existing tools. No timer, alarm, notebook or volume state lives here. */
final class InfoPanelToolDrawer
{
    static final int HEIGHT = 34;
    static final int HAMMER_SIZE = 28;
    static final int EYE_SIZE = 28;
    static final int HAMMER_GAP = 3;

    static boolean isDirectTool(InfoPanelText mode)
    { return mode == InfoPanelText.CALCULATOR || mode == InfoPanelText.NOTES; }

    /** The Info Panel height is fixed; long readouts wrap inside the existing footer. */
    static int headerHeight(int freeWidth, int textWidth, int lineHeight)
    { return 0; }

    static int headerHeight(int freeWidth, int textWidth, int lineHeight, boolean toolsEnabled)
    { return 0; }
    static final InfoPanelText[] TOOLS = {InfoPanelText.TIMER, InfoPanelText.ALARM,
        InfoPanelText.STOPWATCH, InfoPanelText.CALCULATOR, InfoPanelText.NOTES, InfoPanelText.VOLUME};
    private static final int NONE = -1, HAMMER = 0, BACK = 1, TEXT = 2, EYE = 3, ICON = 10, CONTROL = 100;

    static final class State
    {
        final boolean expanded;
        final InfoPanelText selected;
        final long generation;
        State(boolean expanded, InfoPanelText selected, long generation)
        { this.expanded = expanded; this.selected = selected; this.generation = generation; }
    }

    /** All hit rectangles and rendering rectangles use the same native-canvas coordinates. */
    static final class Layout
    {
        final Rectangle footer, hammer, eye, primaryText, panel, back, content;
        final Rectangle[] icons = new Rectangle[TOOLS.length];
        final boolean stacked;
        Layout(Rectangle footer, Rectangle free, boolean expanded, boolean room, int textWidth)
        { this(footer, free, expanded, room, textWidth, 16, true); }

        Layout(Rectangle footer, Rectangle free, boolean expanded, boolean room, int textWidth, int lineHeight)
        { this(footer, free, expanded, room, textWidth, lineHeight, true); }

        Layout(Rectangle footer, Rectangle free, boolean expanded, boolean room, int textWidth, int lineHeight, boolean toolsEnabled)
        { this(footer, free, expanded, room, textWidth, lineHeight, toolsEnabled, true); }

        Layout(Rectangle footer, Rectangle free, boolean expanded, boolean room, int textWidth, int lineHeight,
            boolean toolsEnabled, boolean trackingEnabled)
        {
            this.footer = copy(footer);
            Rectangle available = copy(free);
            int required = 24 + (toolsEnabled ? HAMMER_SIZE + HAMMER_GAP : 0)
                + (trackingEnabled ? EYE_SIZE + HAMMER_GAP : 0);
            boolean enough = available.width >= required;
            boolean hammerUsable = toolsEnabled && enough && available.height >= HAMMER_SIZE;
            boolean eyeUsable = trackingEnabled && enough && available.height >= EYE_SIZE;
            // Footer height never changes. Enabled Tool/Tracking buttons stay centered on the
            // native control row while the readout uses the remaining width and the footer's
            // full vertical space so two RuneScape-font lines can fit.
            stacked = false;
            hammer = hammerUsable ? new Rectangle(available.x, available.y + (available.height - HAMMER_SIZE) / 2,
                HAMMER_SIZE, HAMMER_SIZE) : new Rectangle();
            eye = eyeUsable ? new Rectangle(available.x + available.width - EYE_SIZE,
                available.y + (available.height - EYE_SIZE) / 2, EYE_SIZE, EYE_SIZE) : new Rectangle();
            int leftReserve = hammerUsable ? HAMMER_SIZE + HAMMER_GAP : 0;
            int rightReserve = eyeUsable ? EYE_SIZE + HAMMER_GAP : 0;
            int textX = available.x + leftReserve;
            int textWidthAvailable = Math.max(0, available.width - leftReserve - rightReserve);
            primaryText = new Rectangle(textX, footer.y + 1, textWidthAvailable,
                Math.max(0, footer.height - 2));
            panel = expanded && room && hammerUsable
                ? new Rectangle(footer.x, footer.y + footer.height, footer.width, HEIGHT) : new Rectangle();
            back = panel.isEmpty() ? new Rectangle()
                : new Rectangle(panel.x + panel.width - 28, panel.y + 3, 25, HEIGHT - 6);
            content = panel.isEmpty() ? new Rectangle()
                : new Rectangle(panel.x + 5, panel.y + 3, panel.width - 36, HEIGHT - 6);
            for (int i = 0; i < icons.length; i++)
            {
                int width = Math.max(0, panel.width - 8);
                int left = i * width / icons.length, right = (i + 1) * width / icons.length;
                icons[i] = panel.isEmpty() ? new Rectangle()
                    : new Rectangle(panel.x + 4 + left, panel.y + 3, right - left, HEIGHT - 6);
            }
        }
        Rectangle reveal()
        {
            Rectangle area = new Rectangle(footer.x, footer.y - 2, footer.width, footer.height + 2);
            if (!panel.isEmpty()) { area.add(panel); }
            return area;
        }
        boolean same(Layout other)
        {
            return other != null && footer.equals(other.footer) && hammer.equals(other.hammer)
                && eye.equals(other.eye) && primaryText.equals(other.primaryText) && panel.equals(other.panel);
        }
        private static Rectangle copy(Rectangle r) { return r == null ? new Rectangle() : new Rectangle(r); }
    }

    static final class View
    {
        final State state;
        final Layout layout;
        final boolean visible;
        final InfoPanelQuickControls.View controls;
        View(State state, Layout layout, boolean visible)
        { this(state, layout, visible, InfoPanelQuickControls.View.none(layout.content)); }
        View(State state, Layout layout, boolean visible, InfoPanelQuickControls.View controls)
        { this.state = state; this.layout = layout; this.visible = visible; this.controls = controls; }
        boolean shows(InfoPanelText mode)
        { return state.expanded && !layout.panel.isEmpty() && state.selected == mode; }
    }

    static final class Action
    {
        final boolean openTool;
        final boolean toggleTool;
        final boolean toggleTracking;
        final InfoPanelText tool;
        final InfoPanelQuickControls.Request control;
        Action(boolean openTool, InfoPanelText tool) { this(openTool, tool, false); }
        Action(boolean openTool, InfoPanelText tool, boolean toggleTool)
        { this.openTool = openTool; this.tool = tool; this.toggleTool = toggleTool; this.toggleTracking = false; this.control = null; }
        Action(InfoPanelQuickControls.Request control)
        { this.openTool = false; this.toggleTool = false; this.toggleTracking = false; this.tool = control.command.tool; this.control = control; }
        private Action(boolean toggleTracking)
        { this.openTool = false; this.toggleTool = false; this.toggleTracking = toggleTracking; this.tool = null; this.control = null; }
        static Action tracking() { return new Action(true); }
    }

    private boolean expanded;
    private InfoPanelText selected;
    private long generation;
    private View view;
    private boolean interactive;
    private int downMask, clickMask, target = NONE;
    private long pressGeneration;
    private Point press;
    private Layout pressLayout;
    private InfoPanelQuickControls.Request pressControl;

    synchronized State state() { return new State(expanded, selected, generation); }
    synchronized boolean allows(InfoPanelText tool)
    { return expanded && tool != null && (selected == tool || selected == null && isDirectTool(tool)); }
    synchronized void reset()
    {
        expanded = false; selected = null; generation++;
        suspend();
    }
    synchronized void suspend()
    { view = null; interactive = false; cancel(); }
    synchronized void publish(View next, boolean inputAllowed)
    {
        boolean accepts = next != null && next.visible && next.state.generation == generation && inputAllowed;
        if (!accepts || view == null || !next.layout.same(view.layout)
            || next.state.generation != view.state.generation) { cancel(); }
        view = next; interactive = accepts;
    }
    synchronized boolean isHolding() { return interactive && downMask != 0; }
    synchronized boolean owns(Point point)
    {
        return interactive && point != null && (view.layout.hammer.contains(point)
            || view.layout.eye.contains(point) || view.layout.panel.contains(point));
    }
    synchronized boolean mousePressed(MouseEvent e)
    {
        int bit = bit(e.getButton());
        if (bit == 0) { return false; }
        if (downMask == 0) { clickMask = 0; }
        if (downMask != 0)
        {
            downMask |= bit; clickMask |= bit; cancel(); e.consume(); return true;
        }
        if (e.isConsumed() || !owns(e.getPoint())) { return false; }
        downMask = bit; clickMask = bit;
        target = e.getButton() == MouseEvent.BUTTON1 && !modified(e) ? target(e.getPoint()) : NONE;
        press = e.getPoint(); pressLayout = view.layout; pressGeneration = generation;
        pressControl = target >= CONTROL ? view.controls.buttons.get(target - CONTROL).request : null;
        if (pressControl != null && !pressControl.enabled) { target = NONE; pressControl = null; }
        e.consume(); return true;
    }
    synchronized boolean mouseDragged(MouseEvent e)
    {
        if (downMask == 0) { return false; }
        if (e.isConsumed() || modified(e) || press == null || press.distance(e.getPoint()) > 5) { cancel(); }
        e.consume(); return true;
    }
    synchronized Action mouseReleased(MouseEvent e)
    {
        int bit = bit(e.getButton());
        if ((downMask & bit) == 0) { return null; }
        Action result = null;
        if (!e.isConsumed() && !modified(e) && e.getButton() == MouseEvent.BUTTON1 && interactive
            && target != NONE && pressGeneration == generation && view.layout.same(pressLayout)
            && target(e.getPoint()) == target && press != null && press.distance(e.getPoint()) <= 5)
        {
            if (target == HAMMER)
            { expanded = !expanded; selected = null; generation++; result = new Action(false, null); }
            else if (target == EYE)
            { result = Action.tracking(); }
            else if (target == BACK)
            { selected = null; generation++; result = new Action(false, null); }
            else if (target >= CONTROL)
            {
                InfoPanelQuickControls.Request current = view.controls.buttons.get(target - CONTROL).request;
                if (current.enabled && current.same(pressControl)) { result = new Action(pressControl); }
            }
            else if (target >= ICON)
            {
                InfoPanelText tool = TOOLS[target - ICON];
                if (isDirectTool(tool))
                {
                    // These are launchers, not readouts. Keep all icons visible so the
                    // same icon can close its window or switch to the other editor.
                    result = new Action(true, tool, true);
                }
                else { selected = tool; generation++; result = new Action(false, selected); }
            }
            else if (target == TEXT)
            { result = new Action(true, selected, selected == InfoPanelText.TIMER
                || selected == InfoPanelText.ALARM || selected == InfoPanelText.STOPWATCH); }
        }
        downMask &= ~bit; cancel(); e.consume(); return result;
    }
    synchronized boolean mouseClicked(MouseEvent e)
    {
        int bit = bit(e.getButton());
        if ((clickMask & bit) == 0) { return false; }
        clickMask &= ~bit; e.consume(); return true;
    }
    synchronized void mouseMoved(MouseEvent e)
    {
        if ((e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK)) == 0 && downMask != 0)
        { cancel(); downMask = 0; clickMask = 0; }
    }
    private int target(Point point)
    {
        if (view.layout.hammer.contains(point)) { return HAMMER; }
        if (view.layout.eye.contains(point)) { return EYE; }
        if (!view.state.expanded || !view.layout.panel.contains(point)) { return NONE; }
        if (view.state.selected == null)
        {
            for (int i = 0; i < TOOLS.length; i++) { if (view.layout.icons[i].contains(point)) { return ICON + i; } }
        }
        else
        {
            if (view.layout.back.contains(point)) { return BACK; }
            for (int i = 0; i < view.controls.buttons.size(); i++)
            { if (view.controls.buttons.get(i).bounds.contains(point)) { return CONTROL + i; } }
            if (view.state.selected.isTool() && view.controls.text.contains(point)) { return TEXT; }
        }
        return NONE;
    }
    private void cancel() { target = NONE; press = null; pressLayout = null; pressControl = null; }
    private static int bit(int button) { return button >= 1 && button <= 5 ? 1 << button : 0; }
    private static boolean modified(MouseEvent e)
    { return e.isAltDown() || e.isControlDown() || e.isShiftDown() || e.isMetaDown(); }
}
