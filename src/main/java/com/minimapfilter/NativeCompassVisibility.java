package com.minimapfilter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Reversible compass visibility for the unmodified game layouts. */
final class NativeCompassVisibility
{
    private final Client client;
    private final SquareHudWidgetEdits edits = new SquareHudWidgetEdits();
    private Widget activeRoot;

    NativeCompassVisibility(Client client)
    {
        this.client = client;
    }

    void update(boolean show)
    {
        if (show) { restore(); return; }
        int top = client.getTopLevelInterfaceId();
        int rootId;
        int clickId;
        if (top == InterfaceID.TOPLEVEL_PRE_EOC)
        {
            rootId = InterfaceID.ToplevelPreEoc.MAP_MINIMAP;
            clickId = InterfaceID.ToplevelPreEoc.COMPASSCLICK;
        }
        else if (top == InterfaceID.TOPLEVEL_OSRS_STRETCH)
        {
            rootId = InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP;
            clickId = InterfaceID.ToplevelOsrsStretch.COMPASSCLICK;
        }
        else if (top == InterfaceID.TOPLEVEL)
        {
            rootId = InterfaceID.Toplevel.MAPCONTAINER;
            clickId = InterfaceID.Toplevel.COMPASSCLICK;
        }
        else
        {
            restore();
            return;
        }
        Widget root = client.getWidget(rootId);
        if (activeRoot != root) { restore(); }
        if (root == null) { return; }
        activeRoot = root;
        edits.set(client.getWidget(clickId), SquareHudWidgetEdits.Field.HIDDEN, 1);
        // The visual compass uses native content type 1339. Find it inside only
        // this minimap branch, without assuming the fixed layout's graphic index.
        hideDrawing(root, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private void hideDrawing(Widget widget, Set<Widget> seen, int depth)
    {
        if (widget == null || depth > 16 || !seen.add(widget)) { return; }
        if (widget.getContentType() == 1339)
        {
            edits.set(widget, SquareHudWidgetEdits.Field.HIDDEN, 1);
        }
        Widget[][] groups = {widget.getStaticChildren(), widget.getDynamicChildren()};
        for (Widget[] children : groups)
        {
            if (children == null) { continue; }
            for (Widget child : children) { hideDrawing(child, seen, depth + 1); }
        }
    }

    void restore()
    {
        if (!edits.isEmpty()) { edits.restore(); }
        activeRoot = null;
    }
}
