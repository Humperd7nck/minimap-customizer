package com.minimapfilter;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * A separate entry-state lease for the native Wiki and World Map controls.
 *
 * RuneLite's interface scripts can update a widget while Square HUD is active.
 * The generic edit tracker intentionally adopts such intermediate values; that
 * is useful for most HUD fields but can leave these two native buttons hidden
 * or displaced after returning to Classic. Capture their state once, before
 * moving/hiding them, and restore it after the general HUD edits roll back.
 */
final class SquareHudControlLease
{
    private static final int[] IDS = {
        InterfaceID.Orbs.WIKI,
        InterfaceID.Orbs.ORB_WORLDMAP,
        InterfaceID.Orbs.WORLDMAP
    };

    private static final class State
    {
        final Widget widget;
        final int x, y, xMode, yMode, widthMode, heightMode, opacity;
        final boolean hidden, noClick;

        State(Widget widget)
        {
            this.widget = widget;
            x = widget.getOriginalX();
            y = widget.getOriginalY();
            xMode = widget.getXPositionMode();
            yMode = widget.getYPositionMode();
            widthMode = widget.getWidthMode();
            heightMode = widget.getHeightMode();
            opacity = widget.getOpacity();
            hidden = widget.isSelfHidden();
            noClick = widget.getNoClickThrough();
        }

        void restore()
        {
            widget.setXPositionMode(xMode);
            widget.setYPositionMode(yMode);
            widget.setWidthMode(widthMode);
            widget.setHeightMode(heightMode);
            widget.setOriginalX(x);
            widget.setOriginalY(y);
            // Dimensions are owned by the normal edit tracker, which respects
            // resize changes from game scripts while Square HUD is active.
            // Do not replay potentially stale entry dimensions here.
            widget.setOpacity(opacity);
            widget.setNoClickThrough(noClick);
            widget.setHidden(hidden);
            widget.revalidate();
        }
    }

    private final Map<Integer, State> saved = new LinkedHashMap<>();

    void capture(Client client)
    {
        // Do not replace the true Classic state on subsequent HUD frames.
        if (!saved.isEmpty())
        {
            return;
        }
        for (int id : IDS)
        {
            Widget widget = client.getWidget(id);
            if (widget != null)
            {
                saved.put(id, new State(widget));
            }
        }
    }

    void restore(Client client)
    {
        for (Map.Entry<Integer, State> entry : saved.entrySet())
        {
            // Never write into a widget that has been replaced by a new UI instance.
            if (client.getWidget(entry.getKey()) == entry.getValue().widget)
            {
                entry.getValue().restore();
            }
        }
        saved.clear();
    }

    boolean isEmpty()
    {
        return saved.isEmpty();
    }
}
