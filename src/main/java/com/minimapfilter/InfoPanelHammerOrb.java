package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/** A native-size HUD orb with original hammer artwork drawn 1:1, never downsampled. */
final class InfoPanelHammerOrb
{
    private static final int SIZE = InfoPanelToolDrawer.HAMMER_SIZE;
    private static final BufferedImage[] FRAMES = frames();
    // This is the permanent glyph, not an interim thumbnail while an inventory sprite loads.
    // Its silhouette, outline and highlights were designed for this exact 20x20 pixel grid.
    private static final BufferedImage HAMMER = createHammer();

    static void paint(Graphics2D graphics, Rectangle cell, boolean hovered, boolean expanded)
    {
        if (cell == null || cell.width < SIZE || cell.height < SIZE) { return; }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(cell);
            int x = cell.x + (cell.width - SIZE) / 2;
            int y = cell.y + (cell.height - SIZE) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(FRAMES[(hovered ? 1 : 0) | (expanded ? 2 : 0)], x, y, null);
            // Integer placement and the unscaled overload preserve every designed icon pixel.
            // Client-wide stretching may still scale the final canvas, as it does other HUD icons.
            g.drawImage(HAMMER, x + (SIZE - HAMMER.getWidth()) / 2,
                y + (SIZE - HAMMER.getHeight()) / 2, null);
        }
        finally { g.dispose(); }
    }

    private static BufferedImage[] frames()
    {
        BufferedImage[] frames = new BufferedImage[4];
        for (int state = 0; state < frames.length; state++)
        {
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try
            {
                boolean hover = (state & 1) != 0, selected = (state & 2) != 0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(25, 23, 17));
                g.fillOval(0, 0, SIZE - 1, SIZE - 1);
                g.setColor(selected ? new Color(193, 159, 78) : new Color(107, 100, 77));
                g.fillOval(1, 1, SIZE - 3, SIZE - 3);
                g.setColor(hover ? new Color(209, 193, 143) : new Color(152, 140, 106));
                g.drawArc(2, 2, SIZE - 5, SIZE - 5, 40, 180);
                g.setColor(new Color(54, 49, 37));
                g.drawArc(2, 2, SIZE - 5, SIZE - 5, 220, 180);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(10, 8), 20,
                    new float[]{0, 0.65f, 1}, new Color[]{
                        hover ? new Color(126, 116, 84) : new Color(99, 91, 68),
                        new Color(75, 69, 50), new Color(43, 40, 29)}));
                g.fillOval(4, 4, SIZE - 9, SIZE - 9);
                g.setColor(new Color(45, 41, 30));
                g.drawOval(3, 3, SIZE - 7, SIZE - 7);
                if (selected)
                {
                    g.setColor(hover ? new Color(255, 231, 165) : new Color(232, 196, 106));
                    g.drawOval(1, 1, SIZE - 3, SIZE - 3);
                }
            }
            finally { g.dispose(); }
            frames[state] = image;
        }
        return frames;
    }

    /** Original high-contrast steel head and wooden grip, rasterized once at their final size. */
    private static BufferedImage createHammer()
    {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(27, 23, 17));
            g.fillPolygon(new Polygon(new int[]{2, 5, 14, 11}, new int[]{16, 19, 10, 7}, 4));
            g.setColor(new Color(164, 105, 51));
            g.drawLine(4, 16, 12, 8); g.drawLine(5, 16, 12, 9);
            g.setColor(new Color(216, 164, 87)); g.drawLine(4, 15, 10, 9);
            g.setColor(new Color(25, 25, 21));
            g.fillPolygon(new Polygon(new int[]{7, 10, 18, 18, 15}, new int[]{4, 1, 9, 12, 12}, 5));
            g.setColor(new Color(164, 167, 151));
            g.fillPolygon(new Polygon(new int[]{9, 11, 17, 17, 15}, new int[]{4, 3, 9, 10, 10}, 5));
            g.setColor(new Color(231, 230, 207)); g.drawLine(9, 4, 11, 2); g.drawLine(11, 3, 16, 8);
            g.setColor(new Color(88, 90, 81)); g.drawLine(10, 6, 15, 11);
        }
        finally { g.dispose(); }
        return image;
    }
    private InfoPanelHammerOrb() { }
}
