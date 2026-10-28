package com.minimapfilter;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;

/** Also runnable directly: checks do not need a logged-in client. */
public final class SquareHudChecks
{
    private int cases;

    public static void main(String[] args)
    {
        System.out.println("Square HUD checks passed: " + runAll());
    }

    public static int runAll()
    {
        SquareHudChecks checks = new SquareHudChecks();
        checks.geometry();
        checks.values();
        checks.widgets();
        checks.nativeControls();
        checks.sprites();
        return checks.cases;
    }

    private void check(String name, boolean result)
    {
        cases++;
        if (!result)
        {
            throw new AssertionError(name);
        }
    }

    private void geometry()
    {
        SquareHudGeometry g = new SquareHudGeometry(100, 200);
        Rectangle map = g.map();
        check("Native map size", map.width == 152 && map.height == 152);
        check("Column on the left", g.block(0).x + g.block(0).width < map.x);
        check("Top edges align", g.block(0).y == map.y);
        check("Bottom edges align", g.block(3).y + g.block(3).height == map.y + map.height);
        check("N is entirely inside map", map.contains(g.compass()));
        check("Frame contains map", g.panel().contains(map));
        check("Frame contains all status blocks", g.panel().contains(g.block(0)) && g.panel().contains(g.block(3)));
        int height = 0;
        int min = Integer.MAX_VALUE, max = 0;
        boolean separated = true;
        for (int i = 0; i < 4; i++)
        {
            Rectangle r = g.block(i);
            height += r.height;
            min = Math.min(min, r.height);
            max = Math.max(max, r.height);
            if (i > 0)
            {
                Rectangle previous = g.block(i - 1);
                separated &= r.y - previous.y - previous.height == SquareHudGeometry.GAP;
            }
        }
        check("Blocks fill map height exactly", height + 3 * SquareHudGeometry.GAP == map.height);
        check("Blocks have balanced heights", max - min <= 1);
        check("Gaps are consistent", separated);
        SquareHudGeometry shifted = new SquareHudGeometry(-37, 15);
        Rectangle r = shifted.compass();
        r.translate(137, 185);
        check("Compasses translate with panel", r.equals(g.compass()));
        boolean rejected = false;
        try { g.block(4); } catch (IllegalArgumentException expected) { rejected = true; }
        check("Invalid block rejected", rejected);
    }

    private void values()
    {
        check("Run is converted from hundredths", SquareHudValues.runValue(8499) == 84);
        check("Full run displays 100", SquareHudValues.runValue(10000) == 100);
        check("Run input clamped", SquareHudValues.runValue(-1) == 0 && SquareHudValues.runValue(20000) == 100);
        check("Special energy is converted from tenths", SquareHudValues.specialValue(840) == 84);
        check("Full special displays 100", SquareHudValues.specialValue(1000) == 100);
        check("Special input clamped", SquareHudValues.specialValue(-1) == 0 && SquareHudValues.specialValue(2000) == 100);
        check("HP uses actual maximum", Math.abs(SquareHudValues.fraction(73, 99) - 73.0 / 99) < 0.000001);
        check("Boosted HP fill capped", SquareHudValues.fraction(115, 99) == 1.0);
        check("Empty resource has no fill", SquareHudValues.fraction(0, 99) == 0.0);
        check("Invalid maximum guarded", SquareHudValues.fraction(10, 0) == 0.0);
        check("Fill height correct", SquareHudValues.fillHeight(34, 0.5) == 17);
        check("Fill cannot overflow", SquareHudValues.fillHeight(34, 8.0) == 34);
        check("NaN fill guarded", SquareHudValues.fillHeight(34, Double.NaN) == 0);
        check("Negative fill guarded", SquareHudValues.fillHeight(34, -1) == 0);
        boolean monotonic = true;
        int previous = -1;
        for (int i = 0; i <= 100; i++)
        {
            int fill = SquareHudValues.fillHeight(34, SquareHudValues.fraction(i, 100));
            monotonic &= fill >= previous && fill <= 34;
            previous = fill;
        }
        check("Fill is monotonic across full range", monotonic);
    }

    private static final class FakeWidget
    {
        final Map<String, Object> data = new HashMap<>();
        final Widget widget;

        FakeWidget()
        {
            data.put("OriginalX", 7);
            data.put("OriginalY", 8);
            data.put("OriginalWidth", 57);
            data.put("OriginalHeight", 34);
            data.put("XPositionMode", 2);
            data.put("YPositionMode", 1);
            data.put("WidthMode", 1);
            data.put("HeightMode", 1);
            data.put("SpriteId", 1178);
            data.put("Opacity", 0);
            data.put("Hidden", false);
            data.put("NoClickThrough", false);
            data.put("Type", 5);
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class}, (proxy, method, args) ->
            {
                String name = method.getName();
                if (name.equals("equals")) { return proxy == args[0]; }
                if (name.equals("hashCode")) { return System.identityHashCode(proxy); }
                if (name.equals("toString")) { return "Test widget"; }
                if (name.equals("revalidate")) { return null; }
                if (name.equals("isSelfHidden")) { return data.get("Hidden"); }
                if (name.startsWith("set"))
                {
                    data.put(name.substring(3), args[0]);
                    return method.getReturnType() == Widget.class ? proxy : null;
                }
                if (name.startsWith("get"))
                {
                    Object value = data.get(name.substring(3));
                    if (value != null) { return value; }
                }
                if (method.getReturnType() == boolean.class) { return false; }
                if (method.getReturnType() == int.class) { return 0; }
                return null;
            });
        }
    }

    private void widgets()
    {
        FakeWidget f = new FakeWidget();
        SquareHudWidgetEdits e = new SquareHudWidgetEdits();
        e.place(f.widget, 0, 0, 52, 36);
        e.set(f.widget, SquareHudWidgetEdits.Field.TYPE, 3);
        e.set(f.widget, SquareHudWidgetEdits.Field.OPACITY, 255);
        e.set(f.widget, SquareHudWidgetEdits.Field.HIDDEN, 1);
        e.set(f.widget, SquareHudWidgetEdits.Field.NO_CLICK, 1);
        check("Edits applied", f.widget.getOriginalWidth() == 52 && f.widget.getType() == 3 && f.widget.getOpacity() == 255);
        e.restore();
        check("Original geometry restored", f.widget.getOriginalX() == 7 && f.widget.getOriginalY() == 8
            && f.widget.getOriginalWidth() == 57 && f.widget.getOriginalHeight() == 34);
        check("Original modes restored", f.widget.getXPositionMode() == 2 && f.widget.getYPositionMode() == 1
            && f.widget.getWidthMode() == 1 && f.widget.getHeightMode() == 1);
        check("Original visual fields restored", f.widget.getType() == 5 && f.widget.getOpacity() == 0 && !f.widget.isSelfHidden());
        check("Original click blocking restored", !f.widget.getNoClickThrough());
        check("Rollback records cleared", e.isEmpty());
        e.set(f.widget, SquareHudWidgetEdits.Field.OPACITY, 255);
        f.widget.setOpacity(73);
        e.restore();
        check("Later external changes survive restore", f.widget.getOpacity() == 73);
        e.set(f.widget, SquareHudWidgetEdits.Field.X, 10);
        f.widget.setOriginalX(15);
        e.set(f.widget, SquareHudWidgetEdits.Field.X, 10);
        e.restore();
        check("New game-script baseline retained", f.widget.getOriginalX() == 15);
        e.set(f.widget, SquareHudWidgetEdits.Field.OPACITY, 73);
        check("Unchanged fields not claimed", e.isEmpty());
        e.set(f.widget, SquareHudWidgetEdits.Field.X, 10);
        f.widget.setSpriteId(2154);
        e.restore();
        check("Unedited live sprite untouched", f.widget.getSpriteId() == 2154);
        FakeWidget other = new FakeWidget();
        e.set(f.widget, SquareHudWidgetEdits.Field.X, 90);
        e.set(other.widget, SquareHudWidgetEdits.Field.X, 40);
        e.restore();
        check("Different widget instances restore independently", f.widget.getOriginalX() == 15 && other.widget.getOriginalX() == 7);
        for (int i = 0; i < 30; i++)
        {
            e.place(f.widget, 1, 2, 52, 36);
            e.restore();
        }
        check("Repeated toggles do not drift", f.widget.getOriginalX() == 15 && f.widget.getOriginalWidth() == 57);
    }

    private void nativeControls()
    {
        FakeWidget wiki = new FakeWidget();
        FakeWidget worldMapButton = new FakeWidget();
        FakeWidget worldMapGraphic = new FakeWidget();
        Map<Integer, Widget> controls = new HashMap<>();
        controls.put(net.runelite.api.gameval.InterfaceID.Orbs.WIKI, wiki.widget);
        controls.put(net.runelite.api.gameval.InterfaceID.Orbs.ORB_WORLDMAP, worldMapButton.widget);
        controls.put(net.runelite.api.gameval.InterfaceID.Orbs.WORLDMAP, worldMapGraphic.widget);
        net.runelite.api.Client client = (net.runelite.api.Client) Proxy.newProxyInstance(
            net.runelite.api.Client.class.getClassLoader(), new Class<?>[]{net.runelite.api.Client.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getWidget") && args.length == 1 && args[0] instanceof Integer)
                {
                    return controls.get((Integer) args[0]);
                }
                if (method.getReturnType() == boolean.class) { return false; }
                if (method.getReturnType() == int.class) { return 0; }
                return null;
            });
        SquareHudControlLease lease = new SquareHudControlLease();
        lease.capture(client);
        check("Original native controls captured", !lease.isEmpty());
        wiki.widget.setHidden(true);
        worldMapButton.widget.setOriginalX(180);
        worldMapButton.widget.setOriginalY(150);
        worldMapButton.widget.setOpacity(255);
        worldMapGraphic.widget.setNoClickThrough(true);
        // A native interface resize can occur while the Square HUD is active.
        worldMapButton.widget.setOriginalHeight(48);
        // An intermediate script refresh must not become the restored baseline.
        wiki.widget.setHidden(true);
        worldMapButton.widget.setOriginalX(170);
        lease.capture(client);
        lease.restore(client);
        check("Wiki restored after HUD", !wiki.widget.isSelfHidden());
        check("World Map placement restored after intermediate edits",
            worldMapButton.widget.getOriginalX() == 7 && worldMapButton.widget.getOriginalY() == 8);
        check("World Map opacity restored", worldMapButton.widget.getOpacity() == 0);
        check("Native World Map resize preserved", worldMapButton.widget.getOriginalHeight() == 48);
        check("World Map click behavior restored", !worldMapGraphic.widget.getNoClickThrough());
        check("Native control lease cleared", lease.isEmpty());
        for (int i = 0; i < 25; i++)
        {
            lease.capture(client);
            wiki.widget.setHidden(true);
            worldMapButton.widget.setOriginalX(100 + i);
            lease.restore(client);
        }
        check("Repeated Square to Classic transitions restore controls",
            !wiki.widget.isSelfHidden() && worldMapButton.widget.getOriginalX() == 7);
    }

    private static SpritePixels sprite()
    {
        return (SpritePixels) Proxy.newProxyInstance(SpritePixels.class.getClassLoader(), new Class<?>[]{SpritePixels.class},
            (proxy, method, args) ->
            {
                if (method.getName().equals("equals")) { return proxy == args[0]; }
                if (method.getName().equals("hashCode")) { return System.identityHashCode(proxy); }
                if (method.getName().equals("toString")) { return "Test sprite"; }
                if (method.getReturnType() == int.class) { return 0; }
                return null;
            });
    }

    private void sprites()
    {
        SquareHudSpriteLease lease = new SquareHudSpriteLease();
        Map<Integer, SpritePixels> table = new HashMap<>();
        SpritePixels original = sprite(), replacement = sprite(), external = sprite();
        table.put(1178, original);
        check("Override installs", lease.put(table, 1178, replacement) && table.get(1178) == replacement);
        check("Identical override causes no cache reset", !lease.put(table, 1178, replacement));
        lease.restore(table);
        check("Existing override restored", table.get(1178) == original);
        lease.put(table, 819200, replacement);
        lease.restore(table);
        check("Private override removed", !table.containsKey(819200));
        lease.put(table, 1178, replacement);
        table.put(1178, external);
        lease.restore(table);
        check("Later pack override preserved", table.get(1178) == external);
        table.put(1178, original);
        lease.put(table, 1178, replacement);
        table.put(1178, external);
        lease.put(table, 1178, replacement);
        lease.restore(table);
        check("Refreshed pack becomes restored baseline", table.get(1178) == external);
        lease.put(table, 1178, replacement);
        lease.restore(null);
        check("Unavailable table does not retain stale leases", !lease.restore(table));
    }
}
