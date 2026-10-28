package com.minimapfilter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;

/** Own only fields we change; never roll back a later change made by someone else. */
final class SquareHudWidgetEdits
{
    enum Field
    {
        X, Y, WIDTH, HEIGHT, X_MODE, Y_MODE, WIDTH_MODE, HEIGHT_MODE,
        SPRITE, CONTENT_TYPE, HIDDEN, OPACITY, TYPE, NO_CLICK
    }

    private static final class Edit
    {
        int original;
        int applied;

        Edit(int original, int applied)
        {
            this.original = original;
            this.applied = applied;
        }
    }

    private final Map<Widget, EnumMap<Field, Edit>> edits = new IdentityHashMap<>();

    boolean set(Widget widget, Field field, int value)
    {
        if (widget == null)
        {
            return false;
        }
        int current = read(widget, field);
        EnumMap<Field, Edit> fields = edits.get(widget);
        Edit edit = fields == null ? null : fields.get(field);
        if (edit != null && current != edit.applied)
        {
            // Layout scripts, hover art and resource packs can update a field in use.
            edit.original = current;
        }
        if (current == value)
        {
            return false;
        }
        if (fields == null)
        {
            fields = new EnumMap<>(Field.class);
            edits.put(widget, fields);
        }
        if (edit == null)
        {
            edit = new Edit(current, value);
            fields.put(field, edit);
        }
        else
        {
            edit.applied = value;
        }
        write(widget, field, value);
        return true;
    }

    int valueBeforeEdit(Widget widget, Field field)
    {
        int current = read(widget, field);
        EnumMap<Field, Edit> fields = edits.get(widget);
        Edit edit = fields == null ? null : fields.get(field);
        return edit != null && current == edit.applied ? edit.original : current;
    }

    boolean place(Widget widget, int x, int y, int width, int height)
    {
        boolean changed = set(widget, Field.X_MODE, WidgetPositionMode.ABSOLUTE_LEFT);
        changed |= set(widget, Field.Y_MODE, WidgetPositionMode.ABSOLUTE_TOP);
        changed |= set(widget, Field.WIDTH_MODE, WidgetSizeMode.ABSOLUTE);
        changed |= set(widget, Field.HEIGHT_MODE, WidgetSizeMode.ABSOLUTE);
        changed |= set(widget, Field.X, x);
        changed |= set(widget, Field.Y, y);
        changed |= set(widget, Field.WIDTH, width);
        changed |= set(widget, Field.HEIGHT, height);
        return changed;
    }

    void restore()
    {
        List<Widget> changed = new ArrayList<>(edits.keySet());
        for (Map.Entry<Widget, EnumMap<Field, Edit>> entry : edits.entrySet())
        {
            for (Map.Entry<Field, Edit> field : entry.getValue().entrySet())
            {
                Edit edit = field.getValue();
                if (read(entry.getKey(), field.getKey()) == edit.applied)
                {
                    write(entry.getKey(), field.getKey(), edit.original);
                }
            }
        }
        edits.clear();
        for (Widget widget : changed)
        {
            widget.revalidate();
        }
    }

    boolean isEmpty()
    {
        return edits.isEmpty();
    }

    private static int read(Widget w, Field f)
    {
        switch (f)
        {
            case X: return w.getOriginalX();
            case Y: return w.getOriginalY();
            case WIDTH: return w.getOriginalWidth();
            case HEIGHT: return w.getOriginalHeight();
            case X_MODE: return w.getXPositionMode();
            case Y_MODE: return w.getYPositionMode();
            case WIDTH_MODE: return w.getWidthMode();
            case HEIGHT_MODE: return w.getHeightMode();
            case SPRITE: return w.getSpriteId();
            case CONTENT_TYPE: return w.getContentType();
            case HIDDEN: return w.isSelfHidden() ? 1 : 0;
            case OPACITY: return w.getOpacity();
            case TYPE: return w.getType();
            case NO_CLICK: return w.getNoClickThrough() ? 1 : 0;
            default: throw new IllegalArgumentException("Unknown widget field");
        }
    }

    private static void write(Widget w, Field f, int value)
    {
        switch (f)
        {
            case X: w.setOriginalX(value); break;
            case Y: w.setOriginalY(value); break;
            case WIDTH: w.setOriginalWidth(value); break;
            case HEIGHT: w.setOriginalHeight(value); break;
            case X_MODE: w.setXPositionMode(value); break;
            case Y_MODE: w.setYPositionMode(value); break;
            case WIDTH_MODE: w.setWidthMode(value); break;
            case HEIGHT_MODE: w.setHeightMode(value); break;
            case SPRITE: w.setSpriteId(value); break;
            case CONTENT_TYPE: w.setContentType(value); break;
            case HIDDEN: w.setHidden(value != 0); break;
            case OPACITY: w.setOpacity(value); break;
            case TYPE: w.setType(value); break;
            case NO_CLICK: w.setNoClickThrough(value != 0); break;
            default: throw new IllegalArgumentException("Unknown widget field");
        }
    }
}
