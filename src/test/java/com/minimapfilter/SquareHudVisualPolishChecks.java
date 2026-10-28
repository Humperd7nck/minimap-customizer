package com.minimapfilter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.Point;

/** Pixel and geometry checks for compact Info panels and native resource artwork. */
public final class SquareHudVisualPolishChecks
{
    private int checks;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Visual polish checks passed: " + runAll());
    }

    public static int runAll() throws Exception
    {
        SquareHudVisualPolishChecks t = new SquareHudVisualPolishChecks();
        t.footer();
        t.nativePixels();
        t.scalingAndCache();
        t.drawing();
        t.grayRendering();
        return t.checks;
    }

    private void check(String name, boolean result)
    {
        checks++;
        if (!result) { throw new AssertionError(name); }
    }

    private void footer()
    {
        SquareHudGeometry standard = new SquareHudGeometry(0, 0);
        check("shorter Info panel", standard.footer().height == 34);
        check("native 27px XP and 30px globe fit", SquareHudGeometry.footerHeightForControls(27, 30) == 34);
        check("34px containers receive padding", SquareHudGeometry.footerHeightForControls(34, 34) == 38);
        check("larger control is not forced into short panel", SquareHudGeometry.footerHeightForControls(44, 30) == 48);
        check("missing controls retain minimum", SquareHudGeometry.footerHeightForControls(0, 0) == 34);
        for (int height : new int[] {34, 38, 48, 84})
        {
            SquareHudGeometry g = new SquareHudGeometry(10, 20, height);
            SquareHudGeometry original = new SquareHudGeometry(10, 20);
            check("fixed upper panel " + height, original.panel().equals(g.panel()));
            check("map unchanged " + height, original.map().equals(g.map()));
            check("compass unchanged " + height, original.compass().equals(g.compass()));
            check("text inside shorter panel " + height, g.footer().contains(g.zoneText()));
            Rectangle wild = g.wilderness(78, 73);
            Rectangle multi = g.multiCombat(null, 16, 16, wild);
            check("indicators below panel " + height,
                wild.y >= g.footer().getMaxY() + SquareHudGeometry.GAP && !multi.intersects(wild));
        }
    }

    private BufferedImage pattern(int width, int height, int inset, Color color)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = inset; y < height - inset; y++)
        {
            for (int x = inset; x < width - inset; x++)
            {
                int rgb = x == inset || y == inset || x == width - inset - 1 || y == height - inset - 1
                    ? 0xff171717 : color.getRGB();
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private void nativePixels()
    {
        BufferedImage source = pattern(26, 26, 3, new Color(145, 192, 229));
        source.setRGB(8, 8, 0x809ac5e1);
        source.setRGB(9, 8, 0);
        int[] before = source.getRGB(0, 0, 26, 26, null, 0, 26);
        BufferedImage icon = SquareHudIconRenderer.fit(source, 20);
        check("crop transparent margin", icon.getWidth() == 20 && icon.getHeight() == 20);
        check("private copy", icon != source);
        boolean exact = true;
        for (int y = 0; y < 20; y++)
        {
            for (int x = 0; x < 20; x++) { exact &= icon.getRGB(x, y) == source.getRGB(x + 3, y + 3); }
        }
        check("all native ink and outlines preserved one-to-one", exact);
        check("interior transparency preserved", icon.getRGB(6, 5) == 0);
        check("semi-transparent native pixel preserved", icon.getRGB(5, 5) == 0x809ac5e1);
        check("original sprite unmodified", Arrays.equals(before, source.getRGB(0, 0, 26, 26, null, 0, 26)));
        BufferedImage boot = pattern(16, 18, 0, new Color(147, 106, 58));
        BufferedImage preparedBoot = SquareHudIconRenderer.fit(boot, 20);
        check("small native boot not enlarged", preparedBoot.getWidth() == 16 && preparedBoot.getHeight() == 18);
        check("boot colors preserved", Arrays.equals(boot.getRGB(0, 0, 16, 18, null, 0, 16),
            preparedBoot.getRGB(0, 0, 16, 18, null, 0, 16)));
        BufferedImage empty = SquareHudIconRenderer.fit(new BufferedImage(26, 26, BufferedImage.TYPE_INT_ARGB), 20);
        check("blank replacement stays blank", empty.getWidth() == 1 && empty.getRGB(0, 0) == 0);
        check("null safe", SquareHudIconRenderer.fit(null, 20) == null);
    }

    private void scalingAndCache()
    {
        BufferedImage larger = pattern(40, 60, 0, Color.ORANGE);
        BufferedImage scaled = SquareHudIconRenderer.fit(larger, 20);
        check("larger artwork fits slot", scaled.getWidth() == 13 && scaled.getHeight() == 20);
        check("larger source not modified", larger.getWidth() == 40 && larger.getRGB(10, 10) == Color.ORANGE.getRGB());
        SquareHudIconRenderer cache = new SquareHudIconRenderer();
        BufferedImage walk = pattern(16, 18, 0, new Color(147, 106, 58));
        BufferedImage run = pattern(16, 18, 0, Color.YELLOW);
        BufferedImage stamina = pattern(16, 18, 0, Color.ORANGE);
        BufferedImage first = cache.prepare(2, walk);
        check("cached for stable frames", cache.prepare(2, walk) == first);
        check("run switch invalidates cache", cache.prepare(2, run) != first);
        check("run state stays yellow", cache.prepare(2, run).getRGB(5, 5) == Color.YELLOW.getRGB());
        check("stamina state stays orange", cache.prepare(2, stamina).getRGB(5, 5) == Color.ORANGE.getRGB());
        BufferedImage prayer = cache.prepare(1, larger);
        check("other slot cannot overwrite prayer cache", cache.prepare(2, walk) != prayer && cache.prepare(1, larger) == prayer);
        check("null clears slot", cache.prepare(2, null) == null);
        check("source reload refreshes slot", cache.prepare(2, walk) != first);
        boolean badSize = false, badSlot = false;
        try { SquareHudIconRenderer.fit(walk, 0); } catch (IllegalArgumentException expected) { badSize = true; }
        try { cache.prepare(-1, walk); } catch (IllegalArgumentException expected) { badSlot = true; }
        check("invalid size rejected", badSize);
        check("invalid slot rejected", badSlot);
    }

    private void drawing()
    {
        BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_ARGB);
        BufferedImage icon = pattern(20, 20, 0, Color.CYAN);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.RED);
            AffineTransform before = g.getTransform();
            new SquareHudIconRenderer().draw(g, icon, 13, 20);
            check("integer one-to-one blit", image.getRGB(4, 11) == icon.getRGB(1, 1));
            check("retains outline", image.getRGB(3, 10) == 0xff171717);
            check("no bleed into numeric column", image.getRGB(23, 20) == 0);
            check("caller interpolation unchanged", g.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
                == RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            check("caller transform unchanged", before.equals(g.getTransform()));
            check("caller paint unchanged", g.getColor().equals(Color.RED));
        }
        finally { g.dispose(); }
    }

    private void grayRendering() throws Exception
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> method.getName().equals("getMouseCanvasPosition") ? new Point(-1, -1)
                : method.getReturnType() == boolean.class ? false : null);
        SquareHudOverlay overlay = new SquareHudOverlay(client, null, null);
        Method draw = SquareHudOverlay.class.getDeclaredMethod("paintBlock", Graphics2D.class, Rectangle.class,
            int.class, SquareHudController.Snapshot.class);
        draw.setAccessible(true);
        SquareHudGeometry geometry = new SquareHudGeometry(0, 0);
        SquareHudController.Snapshot view = new SquareHudController.Snapshot(geometry,
            new int[] {99, 99, 100, 100}, new int[] {99, 99, 100, 100}, new BufferedImage[4],
            new boolean[] {true, true, true, false}, new boolean[4], 0, false, false, 0, true, true);
        BufferedImage image = new BufferedImage(213, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            for (int slot : new int[] {2, 3})
            {
                Rectangle rect = geometry.block(slot);
                draw.invoke(overlay, g, rect, slot, view);
                Color left = new Color(image.getRGB(rect.x + 2, rect.y + 2), true);
                Color right = new Color(image.getRGB(rect.x + rect.width - 3, rect.y + 2), true);
                check("gray no longer near-white at left " + slot,
                    left.getRed() < 180 && left.getGreen() < 180 && left.getBlue() < 180);
                check("gray retains restrained shading " + slot, left.getRed() > right.getRed());
                check("full resource fill remains opaque " + slot, left.getAlpha() == 255 && right.getAlpha() == 255);
            }
        }
        finally { g.dispose(); }
    }
}
