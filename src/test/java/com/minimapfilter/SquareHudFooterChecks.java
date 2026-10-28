package com.minimapfilter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;

public final class SquareHudFooterChecks
{
    private int cases;
    private void check(String name, boolean ok)
    {
        cases++;
        if (!ok) { throw new AssertionError(name); }
    }
    public static void main(String[] args) throws Exception
    {
        System.out.println("Footer logic checks passed: " + runAll());
    }
    public static int runAll() throws Exception
    {
        SquareHudFooterChecks t = new SquareHudFooterChecks();
        t.visibility(); t.geometry(); t.names(); t.text();
        return t.cases;
    }
    private void visibility()
    {
        SquareHudFooterVisibility v = new SquareHudFooterVisibility();
        SquareHudFooterMode hover = SquareHudFooterMode.MOUSEOVER_ONLY;
        check("hidden initially", !v.update(hover, false, false, false, 0));
        check("brief crossing does not reveal", !v.update(hover, true, false, false, 100));
        check("reveal delay", !v.update(hover, true, false, false, 174));
        check("reveals at threshold", v.update(hover, true, false, false, 175));
        check("stays while hovered", v.update(hover, true, false, false, 500));
        check("leave delay begins", v.update(hover, false, false, false, 501));
        check("leave hysteresis", v.update(hover, false, false, false, 850));
        check("hides at threshold", !v.update(hover, false, false, false, 851));
        v.update(hover, true, false, false, 900);
        check("reveals again", v.update(hover, true, false, false, 975));
        check("native menu pins", v.update(hover, false, true, false, 2000));
        check("menu can stay open", v.update(hover, false, true, false, 9000));
        check("menu close allows exit delay", v.update(hover, false, false, false, 9001));
        check("hides after menu", !v.update(hover, false, false, false, 9351));
        check("hidden does not appear over menu", !v.update(hover, true, true, false, 10000));
        check("always on", v.update(SquareHudFooterMode.ALWAYS_VISIBLE, false, false, false, 11000));
        check("cover suppresses always", !v.update(SquareHudFooterMode.ALWAYS_VISIBLE, true, false, true, 11001));
        check("uncovered restores always", v.update(SquareHudFooterMode.ALWAYS_VISIBLE, false, false, false, 11002));
        check("switching to hover outside hides", !v.update(hover, false, false, false, 11003));
        v.reset();
        check("reset has no visible tail", !v.update(hover, false, false, false, 12000));
        check("default always", new MinimapFilterConfig() {}.squareHudFooterMode() == SquareHudFooterMode.ALWAYS_VISIBLE);
        check("zone selected", new MinimapFilterConfig() {}.infoPanelText() == InfoPanelText.ZONE);
    }
    private void geometry()
    {
        SquareHudGeometry g = new SquareHudGeometry(100, 200);
        Rectangle f = g.footer();
        check("footer full width", f.width == g.panel().width && f.x == g.panel().x);
        check("footer below panel", f.y == g.panel().y + g.panel().height);
        check("map still 152 square", g.map().width == 152 && g.map().height == 152);
        check("footer 34 high", f.height == 34);
        check("text between controls", f.contains(g.zoneText()) && g.zoneText().width > 0);
        check("reveal strip over bottom border", g.footerReveal().contains(f.x + 20, f.y - 1));
        check("reveal not on map", !g.footerReveal().contains(g.map().x + 20, g.map().y + 20));
    }
    private void names() throws Exception
    {
        check("names bundled", SquareHudZones.regionCount() > 200);
        check("Varrock", SquareHudZones.lookup((50 << 8) | 53).equals("Varrock"));
        check("Grand Exchange", SquareHudZones.lookup((49 << 8) | 54).equals("Grand Exchange"));
        check("Falador", SquareHudZones.lookup((46 << 8) | 52).equals("Falador"));
        check("Lumbridge", SquareHudZones.lookup((50 << 8) | 50).equals("Lumbridge"));
        check("unknown blank", SquareHudZones.lookup(0).isEmpty());
        check("underground not surface", SquareHudZones.lookup((50 << 8) | 154).equals("Varrock Sewers"));
        Map<Integer, String> map = SquareHudZones.parse(new BufferedReader(new StringReader(
            "A\t1,2 3,4\nB\t1,2\nA\t3,4\n")));
        check("ambiguous omitted", !map.containsKey((1 << 8) | 2));
        check("same-name duplicates fine", map.size() == 1 && map.get((3 << 8) | 4).equals("A"));
        boolean rejected = false;
        try { SquareHudZones.parse(new BufferedReader(new StringReader("A\t256,2"))); }
        catch (java.io.IOException expected) { rejected = true; }
        check("invalid data rejected", rejected);
    }
    private void text()
    {
        Graphics2D g = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            check("short name unchanged", SquareHudText.fit("Varrock", g.getFontMetrics(), 180).equals("Varrock"));
            String longName = "This location name is much longer than the footer";
            String fit = SquareHudText.fit(longName, g.getFontMetrics(), 85);
            check("long name shortened", fit.endsWith("...") && g.getFontMetrics().stringWidth(fit) <= 85);
            check("tiny width blank", SquareHudText.fit(longName, g.getFontMetrics(), 1).isEmpty());
            check("null safe", SquareHudText.fit(null, g.getFontMetrics(), 100).isEmpty());
        }
        finally { g.dispose(); }
    }
}
