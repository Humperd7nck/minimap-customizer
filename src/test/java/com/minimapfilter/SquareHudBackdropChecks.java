package com.minimapfilter;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Headless checks for the backing only; does not emulate native map rendering. */
public final class SquareHudBackdropChecks
{
    private static final int SENTINEL = 0xff9147b8;
    private int cases;

    public static void main(String[] args)
    {
        System.out.println("Square HUD backdrop checks passed: " + runAll());
    }

    public static int runAll()
    {
        SquareHudBackdropChecks tests = new SquareHudBackdropChecks();
        tests.run();
        return tests.cases;
    }

    private void check(String name, boolean result)
    {
        cases++;
        if (!result) { throw new AssertionError(name); }
    }

    private void run()
    {
        AtomicReference<SquareHudController.Snapshot> current = new AtomicReference<>();
        SquareHudBackdropOverlay overlay = new SquareHudBackdropOverlay(current::get);
        check("beneath native widgets", overlay.getLayer() == OverlayLayer.UNDER_WIDGETS);
        check("canvas coordinates", overlay.getPosition() == OverlayPosition.DYNAMIC);
        check("not movable or snappable", !overlay.isMovable() && !overlay.isSnappable());
        check("not resizable", !overlay.isResizable());

        BufferedImage image = canvas();
        Graphics2D g = image.createGraphics();
        check("inactive has no overlay bounds", overlay.render(g) == null);
        g.dispose();
        check("inactive leaves canvas untouched", matches(image, null));

        SquareHudGeometry geometry = new SquareHudGeometry(15, 20);
        current.set(view(geometry, false));
        g = image.createGraphics();
        overlay.render(g);
        g.dispose();
        check("suspended painting leaves canvas untouched", matches(image, null));

        current.set(view(geometry, true));
        image = canvas();
        g = image.createGraphics();
        g.setColor(Color.CYAN);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        Color color = g.getColor();
        Composite composite = g.getComposite();
        AffineTransform transform = g.getTransform();
        Rectangle clip = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        g.setClip(clip);
        check("active has no overlay bounds", overlay.render(g) == null);
        check("caller graphics state preserved", color.equals(g.getColor())
            && composite.equals(g.getComposite()) && transform.equals(g.getTransform())
            && clip.equals(g.getClipBounds()));
        g.dispose();
        check("exact map region is opaque black", matches(image, geometry.map()));

        image = canvas();
        g = image.createGraphics();
        Rectangle partial = new Rectangle(geometry.map().x + 10, geometry.map().y + 12, 50, 60);
        g.setClip(partial);
        overlay.render(g);
        g.dispose();
        check("respects caller clipping", matches(image, partial));

        image = canvas();
        g = image.createGraphics();
        g.scale(2.0, 2.0);
        overlay.render(g);
        g.dispose();
        Rectangle r = geometry.map();
        check("honors graphics scaling", matches(image, new Rectangle(r.x * 2, r.y * 2, r.width * 2, r.height * 2)));

        current.set(view(new SquareHudGeometry(90, 70), true));
        image = canvas();
        g = image.createGraphics();
        overlay.render(g);
        g.dispose();
        check("follows moved HUD geometry", matches(image, current.get().geometry.map()));

        current.set(null);
        image = canvas();
        g = image.createGraphics();
        overlay.render(g);
        g.dispose();
        check("return to Classic stops backing", matches(image, null));
    }

    private static SquareHudController.Snapshot view(SquareHudGeometry geometry, boolean paint)
    {
        return new SquareHudController.Snapshot(geometry, new int[4], new int[4],
            new BufferedImage[4], new boolean[4], new boolean[4], 0,
            false, false, 0.0, paint, paint);
    }

    private static BufferedImage canvas()
    {
        BufferedImage image = new BufferedImage(600, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(SENTINEL, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        return image;
    }

    private static boolean matches(BufferedImage image, Rectangle black)
    {
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int expected = black != null && black.contains(x, y) ? 0xff000000 : SENTINEL;
                if (image.getRGB(x, y) != expected) { return false; }
            }
        }
        return true;
    }
}
