package com.minimapfilter;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.gameval.SpriteID;

/** Headless tests of the approved state chart and indicator bounds. */
public final class SquareHudResourceStyleChecks
{
    private int checks;
    private void check(String name, boolean ok)
    {
        checks++;
        if (!ok) { throw new AssertionError(name); }
    }

    public static void main(String[] args)
    {
        System.out.println("Resource style and indicator geometry checks passed: " + runAll());
    }

    public static int runAll()
    {
        SquareHudResourceStyleChecks t = new SquareHudResourceStyleChecks();
        t.states(); t.tint(); t.indicators();
        return t.checks;
    }

    private void states()
    {
        check("walk brown", SquareHudResourceStyle.runIconSprite(false, false) == SpriteID.OrbIcon.WALK);
        check("run yellow", SquareHudResourceStyle.runIconSprite(true, false) == SpriteID.OrbIcon.RUN);
        check("stamina while walking orange", SquareHudResourceStyle.runIconSprite(false, true)
            == SpriteID.OrbIcon.RUN_ICON_SLOWED_DEPLETION);
        check("stamina while running orange", SquareHudResourceStyle.runIconSprite(true, true)
            == SpriteID.OrbIcon.RUN_ICON_SLOWED_DEPLETION);
        for (boolean stamina : new boolean[]{false, true})
        {
            check("off gray, stamina=" + stamina, SquareHudResourceStyle.runFill(false)
                .equals(SquareHudResourceStyle.INACTIVE_GRAY));
            check("on yellow, stamina=" + stamina, SquareHudResourceStyle.runFill(true)
                .equals(new Color(192, 155, 47)));
        }
        check("no spec gray", SquareHudResourceStyle.specialFill(false, false)
            .equals(SquareHudResourceStyle.INACTIVE_GRAY));
        check("selected spec original green", SquareHudResourceStyle.specialFill(true, true)
            .equals(new Color(57, 137, 79)));
        check("ready spec lighter green", SquareHudResourceStyle.specialFill(true, false)
            .equals(SquareHudResourceStyle.SPEC_READY_GREEN));
        check("stale selection cannot imply availability", SquareHudResourceStyle.specialFill(false, true)
            .equals(SquareHudResourceStyle.INACTIVE_GRAY));
        Color active = SquareHudResourceStyle.SPEC_GREEN, ready = SquareHudResourceStyle.SPEC_READY_GREEN;
        check("ready visibly lighter", ready.getRed() > active.getRed()
            && ready.getGreen() > active.getGreen() && ready.getBlue() > active.getBlue());
        check("inactive gray stays neutral", Math.abs(SquareHudResourceStyle.INACTIVE_GRAY.getRed()
            - SquareHudResourceStyle.INACTIVE_GRAY.getBlue()) <= 8);
        check("inactive gray darker than dev11", SquareHudResourceStyle.INACTIVE_GRAY.getRed() < 168
            && SquareHudResourceStyle.INACTIVE_GRAY.getGreen() < 170);
        check("gray highlight remains gray", SquareHudResourceStyle.fillHighlight(
            SquareHudResourceStyle.INACTIVE_GRAY).equals(new Color(160, 164, 168)));
        check("active yellow gradient unchanged", SquareHudResourceStyle.fillHighlight(
            SquareHudResourceStyle.RUN_YELLOW).equals(SquareHudResourceStyle.RUN_YELLOW.brighter()));
        check("active green gradient unchanged", SquareHudResourceStyle.fillHighlight(
            SquareHudResourceStyle.SPEC_GREEN).equals(SquareHudResourceStyle.SPEC_GREEN.brighter()));
    }

    private void tint()
    {
        BufferedImage source = new BufferedImage(6, 1, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = {0x00000000, 0xff101010, 0xffeeeeee, 0xff808080, 0x80c1a26a, 0xff448855};
        source.setRGB(0, 0, 6, 1, pixels, 0, 6);
        BufferedImage tinted = SquareHudResourceStyle.yellowIcon(source);
        check("new image", tinted != source);
        check("same size", tinted.getWidth() == 6 && tinted.getHeight() == 1);
        for (int x = 0; x < 6; x++)
        {
            check("source never mutated " + x, source.getRGB(x, 0) == pixels[x]);
            check("alpha retained " + x, (tinted.getRGB(x, 0) >>> 24) == (pixels[x] >>> 24));
        }
        check("dark outline retained", tinted.getRGB(1, 0) == pixels[1]);
        check("transparency retained", tinted.getRGB(0, 0) == pixels[0]);
        Color bright = new Color(tinted.getRGB(2, 0), true);
        Color shade = new Color(tinted.getRGB(3, 0), true);
        check("yellow tint", bright.getRed() > bright.getGreen() && bright.getGreen() > bright.getBlue());
        check("shading retained", bright.getRed() > shade.getRed());
        check("null safe", SquareHudResourceStyle.yellowIcon(null) == null);
    }

    private void indicators()
    {
        for (int offset : new int[]{0, 37, 1000})
        {
            SquareHudGeometry g = new SquareHudGeometry(offset, offset + 10);
            Rectangle footer = g.footer();
            Rectangle globe = new Rectangle(footer.x + footer.width - 35, footer.y + 4, 26, 26);
            for (int width : new int[]{78, 100, 120})
            {
                for (int height : new int[]{50, 73, 84, 115})
                {
                    Rectangle wild = g.wilderness(width, height);
                    Rectangle multi = g.multiCombat(globe, 16, 16, wild);
                    check("wilderness below footer", wild.y >= footer.y + footer.height + SquareHudGeometry.GAP);
                    check("wilderness right aligned", wild.x + wild.width == footer.x + footer.width - SquareHudGeometry.BORDER);
                    check("no footer overlap", !wild.intersects(footer) && !multi.intersects(footer));
                    check("swords below all native text", multi.y >= wild.y + wild.height + SquareHudGeometry.GAP);
                    check("indicators disjoint", !wild.intersects(multi));
                    check("swords beneath globe", multi.x + multi.width / 2 == globe.x + globe.width / 2);
                }
            }
            Rectangle multi = g.multiCombat(globe, 16, 16, null);
            check("no wilderness keeps dev9 location", multi.equals(g.multiCombat(globe, 16, 16)));
            check("single indicator sits beneath footer", multi.y == footer.y + footer.height + SquareHudGeometry.GAP);
        }
    }
}
