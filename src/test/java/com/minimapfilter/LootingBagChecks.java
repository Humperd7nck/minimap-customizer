package com.minimapfilter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.TileItem;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/** Price and image checks with no live client, price downloads or shared sprite edits. */
public final class LootingBagChecks
{
    private int checks;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Looting bag value/image checks passed: " + runAll());
    }

    static int runAll() throws Exception
    {
        LootingBagChecks t = new LootingBagChecks();
        t.boundaries();
        t.valuesAndFallback();
        t.icons();
        t.defaults();
        return t.checks;
    }

    private void check(String name, boolean ok)
    {
        checks++;
        if (!ok) { throw new AssertionError(name); }
    }

    private void boundaries()
    {
        long[] lower = {0, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
        long[] upper = {999, 9_999, 99_999, 999_999, 9_999_999, Long.MAX_VALUE};
        for (int i = 0; i < lower.length; i++)
        {
            LootingBagValueTier tier = LootingBagValueTier.values()[i];
            check("inclusive lower " + i, LootingBagValueTier.forValue(lower[i]) == tier);
            check("inclusive upper " + i, LootingBagValueTier.forValue(upper[i]) == tier);
            if (i > 0) { check("previous tier below " + i, LootingBagValueTier.forValue(lower[i] - 1) == LootingBagValueTier.values()[i - 1]); }
        }
        check("negative is unknown", LootingBagValueTier.forValue(-1) == null);
        check("lowest long unknown", LootingBagValueTier.forValue(Long.MIN_VALUE) == null);
        check("quantity multiplication", LootingBagValueTier.addStack(500, 200, 5) == 1_500);
        check("int overflow avoided", LootingBagValueTier.addStack(0, 2_000_000_000L, 2_000_000_000) == 4_000_000_000_000_000_000L);
        check("multiplication saturates", LootingBagValueTier.addStack(0, Long.MAX_VALUE, 2) == Long.MAX_VALUE);
        check("addition saturates", LootingBagValueTier.addStack(Long.MAX_VALUE - 50, 30, 2) == Long.MAX_VALUE);
        check("exact max", LootingBagValueTier.addStack(Long.MAX_VALUE - 50, 25, 2) == Long.MAX_VALUE);
        check("unknown after saturation", LootingBagValueTier.addStack(Long.MAX_VALUE, 0, 1) == -1);
        check("zero quantity not invented", LootingBagValueTier.addStack(0, 10, 0) == -1);
        check("negative quantity", LootingBagValueTier.addStack(0, 10, -1) == -1);
        check("unknown previous total", LootingBagValueTier.addStack(-1, 10, 1) == -1);
    }

    private void valuesAndFallback()
    {
        Settings c = new Settings();
        Map<Integer, Long> prices = new HashMap<>();
        prices.put(1, 60_000L);
        prices.put(2, 300L);
        prices.put(995, 1L);
        int[] calls = {0};
        LootingBagMarkers bags = new LootingBagMarkers(c, id -> { calls[0]++; return prices.getOrDefault(id, 0L); });
        bags.beginFrame();
        check("combined not max", bags.combinedValue(Arrays.asList(item(1, 1), item(2, 200))) == 120_000L);
        check("combined crosses blue threshold", LootingBagValueTier.forValue(
            bags.combinedValue(Arrays.asList(item(1, 1), item(2, 200)))) == LootingBagValueTier.BLUE);
        check("each ID read once per pass", calls[0] == 2);
        check("duplicate item nodes summed", bags.combinedValue(Arrays.asList(item(2, 2), item(2, 3))) == 1_500L);
        check("coins quantity", bags.combinedValue(Collections.singletonList(item(995, 100_000))) == 100_000L);
        check("empty unknown", bags.combinedValue(Collections.emptyList()) == -1L);
        check("null list unknown", bags.combinedValue(null) == -1L);
        check("null item unknown", bags.combinedValue(Arrays.asList(item(1, 1), null)) == -1L);
        check("missing price unknown", bags.combinedValue(Collections.singletonList(item(99, 1))) == -1L);
        check("mixed pile unknown", bags.combinedValue(Arrays.asList(item(1, 1000), item(99, 1))) == -1L);
        check("invalid ID unknown", bags.combinedValue(Collections.singletonList(item(-1, 2))) == -1L);
        check("bad quantity unknown", bags.combinedValue(Collections.singletonList(item(1, 0))) == -1L);
        check("fallback uses original", bags.imageFor(Collections.singletonList(item(99, 1))) == LootingBagIcons.original());
        c.brown = Color.MAGENTA;
        check("unknown ignores custom brown", bags.imageFor(Collections.singletonList(item(99, 1))) == LootingBagIcons.original());
        BufferedImage customized = bags.imageFor(Collections.singletonList(item(2, 4)));
        check("custom brown tier applied", customized != LootingBagIcons.original());
        c.brown = LootingBagValueTier.BROWN.defaultColor();
        check("brown reset returns original", bags.imageFor(Collections.singletonList(item(2, 4))) == LootingBagIcons.original());
        BufferedImage first = bags.imageFor(Collections.singletonList(item(2, 1)));
        check("gray tier cached", first == bags.imageFor(Collections.singletonList(item(2, 1))));
        c.gray = Color.CYAN;
        check("picker immediately changes icon", bags.imageFor(Collections.singletonList(item(2, 1))) != first);
        prices.put(99, 100_000L);
        bags.beginFrame();
        check("new price recovers next pass", bags.combinedValue(Collections.singletonList(item(99, 1))) == 100_000L);
        prices.put(1, Long.MAX_VALUE);
        bags.beginFrame();
        check("large combined total saturates", bags.combinedValue(Arrays.asList(item(1, 2), item(2, 3))) == Long.MAX_VALUE);
        check("unknown after huge known pile", bags.combinedValue(Arrays.asList(item(1, 2), item(1234, 1))) == -1L);
        for (GroundItemMarkerStyle style : GroundItemMarkerStyle.values())
        {
            c.style = style;
            c.enabled = false;
            bags.beginFrame();
            int before = calls[0];
            check("disabled original " + style, bags.imageFor(Collections.singletonList(item(1, 1))) == LootingBagIcons.original());
            check("disabled no pricing " + style, calls[0] == before);
        }
        c.enabled = true;
        for (GroundItemMarkerStyle style : new GroundItemMarkerStyle[]{GroundItemMarkerStyle.NATIVE_DOT, GroundItemMarkerStyle.OFF})
        {
            c.style = style;
            bags.beginFrame();
            int before = calls[0];
            check("dormant preference ignored " + style, bags.imageFor(Collections.singletonList(item(1, 1))) == LootingBagIcons.original());
            check("nonbag no pricing " + style, calls[0] == before);
        }
        c.style = GroundItemMarkerStyle.LOOTING_BAG;
        bags.beginFrame();
        check("coloring restores on reselection", bags.imageFor(Collections.singletonList(item(1, 1))) != LootingBagIcons.original());
        LootingBagMarkers unavailable = new LootingBagMarkers(c, id -> { throw new IllegalStateException("price unavailable"); });
        unavailable.beginFrame();
        check("price exception safe fallback", unavailable.imageFor(Collections.singletonList(item(1, 1))) == LootingBagIcons.original());
        LootingBagMarkers negative = new LootingBagMarkers(c, id -> -4L);
        negative.beginFrame();
        check("negative price fallback", negative.imageFor(Collections.singletonList(item(1, 1))) == LootingBagIcons.original());
    }

    private void icons()
    {
        LootingBagIcons images = new LootingBagIcons();
        BufferedImage original = LootingBagIcons.original();
        check("original 13x13", original.getWidth() == 13 && original.getHeight() == 13);
        check("default brown pixel exact", samePixels(original, legacyOriginal()));
        check("brown identity", images.forTier(LootingBagValueTier.BROWN, LootingBagValueTier.BROWN.defaultColor()) == original);
        int[] snapshot = original.getRGB(0, 0, 13, 13, null, 0, 13);
        for (LootingBagValueTier tier : LootingBagValueTier.values())
        {
            BufferedImage colored = images.forTier(tier, tier.defaultColor());
            check("dimensions " + tier, colored.getWidth() == 13 && colored.getHeight() == 13);
            check("cached " + tier, colored == images.forTier(tier, tier.defaultColor()));
            check("coin unchanged " + tier, colored.getRGB(8, 8) == original.getRGB(8, 8));
            check("tie unchanged " + tier, colored.getRGB(6, 4) == original.getRGB(6, 4));
            check("outline unchanged " + tier, colored.getRGB(2, 9) == original.getRGB(2, 9));
            boolean sameAlpha = true;
            for (int y = 0; y < 13; y++)
            {
                for (int x = 0; x < 13; x++)
                {
                    sameAlpha &= (original.getRGB(x, y) >>> 24) == (colored.getRGB(x, y) >>> 24);
                }
            }
            check("alpha/silhouette preserved " + tier, sameAlpha);
            if (tier != LootingBagValueTier.BROWN) { check("cloth differs " + tier, !samePixels(original, colored)); }
        }
        check("no original mutation", Arrays.equals(snapshot, original.getRGB(0, 0, 13, 13, null, 0, 13)));
        BufferedImage blue = images.forTier(LootingBagValueTier.BLUE, Color.BLUE);
        for (int i = 0; i < 200; i++) { images.forTier(LootingBagValueTier.GRAY, new Color(i, i, i)); }
        check("picker changes do not evict other tiers", blue == images.forTier(LootingBagValueTier.BLUE, Color.BLUE));
        check("null custom color uses default", samePixels(images.forTier(LootingBagValueTier.RED, null),
            new LootingBagIcons().forTier(LootingBagValueTier.RED, LootingBagValueTier.RED.defaultColor())));
    }

    private void defaults() throws Exception
    {
        MinimapFilterConfig c = new MinimapFilterConfig() {};
        check("feature off by default", !c.colorLootingBagIconsByValue());
        check("native dot default retained", c.groundItemMarkerStyle() == GroundItemMarkerStyle.NATIVE_DOT);
        check("native dot red unchanged", c.groundItemDotColor().equals(new Color(0xFF0000)));
        ConfigSection section = MinimapFilterConfig.class.getField("LOOTING_BAG_COLORS").getAnnotation(ConfigSection.class);
        check("section full title", section.name().equals(LootingBagColorControls.TITLE));
        check("section collapsed", section.closedByDefault());
        int colors = 0, switches = 0;
        for (java.lang.reflect.Method method : MinimapFilterConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null || !MinimapFilterConfig.LOOTING_BAG_COLORS.equals(item.section())) { continue; }
            if (method.getReturnType() == Color.class) { colors++; }
            if (method.getReturnType() == boolean.class) { switches++; }
        }
        check("all six pickers present", colors == 6);
        check("one enable checkbox", switches == 1);
        for (LootingBagValueTier tier : LootingBagValueTier.values())
        {
            check("default color " + tier, tier.selectedColor(c).equals(tier.defaultColor()));
        }
    }

    static TileItem item(int id, int quantity)
    {
        return (TileItem) Proxy.newProxyInstance(TileItem.class.getClassLoader(), new Class<?>[]{TileItem.class},
            (proxy, method, args) ->
            {
                if (method.getName().equals("getId")) { return id; }
                if (method.getName().equals("getQuantity")) { return quantity; }
                if (method.getReturnType() == boolean.class) { return false; }
                if (method.getReturnType() == int.class) { return 0; }
                if (method.getReturnType() == long.class) { return 0L; }
                return null;
            });
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b)
    {
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()
            && Arrays.equals(a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth()),
                b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()));
    }

    static final class Settings implements MinimapFilterConfig
    {
        boolean enabled = true;
        GroundItemMarkerStyle style = GroundItemMarkerStyle.LOOTING_BAG;
        Color brown = LootingBagValueTier.BROWN.defaultColor();
        Color gray = LootingBagValueTier.GRAY.defaultColor();
        @Override public GroundItemMarkerStyle groundItemMarkerStyle() { return style; }
        @Override public boolean colorLootingBagIconsByValue() { return enabled; }
        @Override public Color lootingBagColor1k() { return brown; }
        @Override public Color lootingBagColorUnder1k() { return gray; }
    }

    // Frozen pre-dev.13 procedural artwork. Guards the off/missing/default-brown path.
    private static BufferedImage legacyOriginal()
    {
        BufferedImage image = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Color outline = new Color(67, 43, 24, 245);
            Color sack = new Color(198, 147, 73, 255);
            Color sackLight = new Color(236, 191, 105, 255);
            Color tie = new Color(117, 72, 33, 255);
            Color coin = new Color(255, 213, 71, 255);
            g.setColor(outline); g.fillRoundRect(4, 1, 5, 4, 2, 2);
            g.setColor(sackLight); g.fillRoundRect(5, 1, 3, 3, 2, 2);
            g.setColor(tie); g.fillRect(4, 4, 5, 2);
            Polygon body = new Polygon(new int[]{4,9,11,11,9,4,2,2}, new int[]{4,4,7,10,12,12,10,7}, 8);
            g.setColor(outline); g.fillPolygon(body);
            Polygon inner = new Polygon(new int[]{5,8,10,10,8,4,3,3}, new int[]{5,5,7,9,11,11,9,7}, 8);
            g.setColor(sack); g.fillPolygon(inner);
            g.setColor(sackLight); g.drawLine(4, 7, 5, 10);
            g.setColor(coin); g.fillOval(7, 7, 2, 2);
        }
        finally { g.dispose(); }
        return image;
    }
}
