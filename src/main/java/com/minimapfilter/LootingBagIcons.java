package com.minimapfilter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Same 13px bag silhouette, outline, tie and gold coin; only the cloth changes. */
final class LootingBagIcons
{
    private static final BufferedImage ORIGINAL = draw(LootingBagValueTier.BROWN.defaultColor());
    private final BufferedImage[] images = new BufferedImage[LootingBagValueTier.values().length];
    private final int[] colors = new int[images.length];

    static BufferedImage original()
    {
        return ORIGINAL;
    }

    BufferedImage forTier(LootingBagValueTier tier, Color selected)
    {
        if (tier == null) { return ORIGINAL; }
        Color color = selected == null ? tier.defaultColor() : selected;
        int rgb = color.getRGB() & 0xFFFFFF;
        // The default brown/gold swatch is the original artwork, pixel for pixel.
        if (rgb == (LootingBagValueTier.BROWN.defaultColor().getRGB() & 0xFFFFFF))
        {
            return ORIGINAL;
        }
        int slot = tier.ordinal();
        if (images[slot] == null || colors[slot] != rgb)
        {
            colors[slot] = rgb;
            images[slot] = draw(new Color(rgb));
            // Keep even the coin's antialiased edge pixels identical to the
            // original instead of blending them with a different cloth color.
            images[slot].setRGB(7, 7, 2, 2, ORIGINAL.getRGB(7, 7, 2, 2, null, 0, 2), 0, 2);
        }
        return images[slot];
    }

    private static Color highlight(Color color)
    {
        return new Color(color.getRed() + (255 - color.getRed()) / 3,
            color.getGreen() + (255 - color.getGreen()) / 3,
            color.getBlue() + (255 - color.getBlue()) / 3);
    }

    private static BufferedImage draw(Color selected)
    {
        BufferedImage image = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Color outline = new Color(67, 43, 24, 245);
            Color sack = new Color(selected.getRGB() & 0xFFFFFF);
            Color sackLight = selected.getRGB() == LootingBagValueTier.BROWN.defaultColor().getRGB()
                ? new Color(236, 191, 105, 255) : highlight(sack);
            Color tie = new Color(117, 72, 33, 255);
            Color coin = new Color(255, 213, 71, 255);

            // Tied neck.
            g.setColor(outline);
            g.fillRoundRect(4, 1, 5, 4, 2, 2);
            g.setColor(sackLight);
            g.fillRoundRect(5, 1, 3, 3, 2, 2);
            g.setColor(tie);
            g.fillRect(4, 4, 5, 2);

            // Full bag body.
            Polygon body = new Polygon(
                new int[] {4, 9, 11, 11, 9, 4, 2, 2},
                new int[] {4, 4, 7, 10, 12, 12, 10, 7},
                8);
            g.setColor(outline);
            g.fillPolygon(body);

            Polygon inner = new Polygon(
                new int[] {5, 8, 10, 10, 8, 4, 3, 3},
                new int[] {5, 5, 7, 9, 11, 11, 9, 7},
                8);
            g.setColor(sack);
            g.fillPolygon(inner);

            g.setColor(sackLight);
            g.drawLine(4, 7, 5, 10);
            g.setColor(coin);
            g.fillOval(7, 7, 2, 2);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }
}
