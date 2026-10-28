package com.minimapfilter;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Prepares private, tightly cropped Prayer/Run artwork without altering native sprites. */
final class SquareHudIconRenderer
{
    static final int ICON_SIZE = 20;
    private final BufferedImage[] sources = new BufferedImage[4];
    private final BufferedImage[] prepared = new BufferedImage[4];

    BufferedImage prepare(int slot, BufferedImage source)
    {
        if (slot < 0 || slot >= sources.length)
        {
            throw new IllegalArgumentException("Resource slot must be 0..3");
        }
        if (source == null)
        {
            sources[slot] = null;
            prepared[slot] = null;
            return null;
        }
        if (sources[slot] != source)
        {
            sources[slot] = source;
            prepared[slot] = fit(source, ICON_SIZE);
        }
        return prepared[slot];
    }

    static BufferedImage fit(BufferedImage source, int maximum)
    {
        if (source == null) { return null; }
        if (maximum <= 0) { throw new IllegalArgumentException("Icon size must be positive"); }
        Rectangle ink = alphaBounds(source);
        if (ink.isEmpty())
        {
            // An intentionally transparent replacement stays transparent, not a letter fallback.
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        // Remove transparent canvas margins without throwing away any visible pixel.
        BufferedImage cropped = new BufferedImage(ink.width, ink.height, BufferedImage.TYPE_INT_ARGB);
        cropped.setRGB(0, 0, ink.width, ink.height,
            source.getRGB(ink.x, ink.y, ink.width, ink.height, null, 0, ink.width), 0, ink.width);
        if (ink.width <= maximum && ink.height <= maximum) { return cropped; }

        double scale = maximum / (double) Math.max(ink.width, ink.height);
        int width = Math.max(1, (int) Math.round(ink.width * scale));
        int height = Math.max(1, (int) Math.round(ink.height * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try
        {
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cropped, 0, 0, width, height, null);
        }
        finally { g.dispose(); }
        return result;
    }

    private static Rectangle alphaBounds(BufferedImage source)
    {
        int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                if ((source.getRGB(x, y) >>> 24) != 0)
                {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return right < left ? new Rectangle() : new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    void draw(Graphics2D graphics, BufferedImage image, int centerX, int centerY)
    {
        if (image == null) { return; }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            // No second resize. Integer placement preserves a prepared native pixel grid.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, centerX - image.getWidth() / 2, centerY - image.getHeight() / 2, null);
        }
        finally { g.dispose(); }
    }
}
