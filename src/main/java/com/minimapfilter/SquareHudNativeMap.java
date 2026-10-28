package com.minimapfilter;

import java.util.Arrays;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;

/**
 * Owns one native minimap child without copying the stock widget's click-shape state.
 * Native-child setup follows the Compact Orbs approach; see THIRD-PARTY-NOTICES.
 */
final class SquareHudNativeMap
{
    static final int CONTENT_TYPE = 1338;
    private Widget parent;
    private Widget child;

    Widget ensure(Widget target, int width, int height, int spriteId)
    {
        if (target == null || width <= 0 || height <= 0 || spriteId < 0)
        {
            throw new IllegalArgumentException("Invalid native minimap geometry or sprite");
        }
        if (target != parent || !isAttached())
        {
            remove();
            parent = target;
            child = parent.createChild(-1, WidgetType.GRAPHIC);
            if (child == null)
            {
                parent = null;
                return null;
            }
            // Content type 1338 is handled by the game's own minimap renderer and
            // input code. No onClick listener or replacement walking action is used.
            child.setType(WidgetType.GRAPHIC);
            child.setContentType(CONTENT_TYPE);
            child.setSpriteId(spriteId);
            child.setOriginalX(0);
            child.setOriginalY(0);
            child.setOriginalWidth(width);
            child.setOriginalHeight(height);
            child.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            child.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            child.setWidthMode(WidgetSizeMode.ABSOLUTE);
            child.setHeightMode(WidgetSizeMode.ABSOLUTE);
            child.setHasListener(true);
            child.setHidden(false);
            child.setOpacity(0);
            child.setNoClickThrough(true);
            child.revalidate();
        }
        return child;
    }

    private boolean isAttached()
    {
        if (parent == null || child == null)
        {
            return false;
        }
        Widget[] children = parent.getChildren();
        int index = child.getIndex();
        return children != null && index >= 0 && index < children.length && children[index] == child;
    }

    void remove()
    {
        if (isAttached())
        {
            Widget[] children = parent.getChildren();
            int index = child.getIndex();
            // Preserve all foreign siblings, including ones added after our child.
            // Trim only our own trailing slot so repeated mode switches do not grow
            // the child array when no other plugin has appended to it.
            Widget[] next = Arrays.copyOf(children, index == children.length - 1 ? index : children.length);
            if (index < next.length)
            {
                next[index] = null;
            }
            child.setHidden(true);
            child.setContentType(0);
            parent.setChildren(next);
            parent.revalidate();
        }
        child = null;
        parent = null;
    }
}
