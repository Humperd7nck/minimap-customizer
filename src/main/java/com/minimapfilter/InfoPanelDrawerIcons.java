package com.minimapfilter;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

/** Fetch native inventory sprites lazily on the client thread. Never mutate the shared item-image cache. */
@Singleton
final class InfoPanelDrawerIcons implements InfoPanelDrawerPainter.Images
{
    private final ItemManager items;
    private final EnumMap<InfoPanelText, BufferedImage> images = new EnumMap<>(InfoPanelText.class);
    private long nextAttempt;

    @Inject InfoPanelDrawerIcons(ItemManager items) { this.items = items; }

    void prepare(boolean expanded)
    {
        long now = System.nanoTime();
        if (now < nextAttempt) { return; }
        nextAttempt = now + 1_000_000_000L;
        if (!expanded) { return; }
        if (!images.containsKey(InfoPanelText.TIMER))
        {
            BufferedImage watch = load(2575, "Watch");
            if (watch != null)
            {
                images.put(InfoPanelText.TIMER, watch);
                images.put(InfoPanelText.ALARM, redCopy(watch));
            }
        }
        if (!images.containsKey(InfoPanelText.STOPWATCH))
        {
            BufferedImage watch = load(27414, "Giant stopwatch");
            if (watch != null) { images.put(InfoPanelText.STOPWATCH, watch); }
        }
        if (!images.containsKey(InfoPanelText.NOTES))
        {
            BufferedImage notes = load(714, "Radimus notes");
            if (notes != null) { images.put(InfoPanelText.NOTES, notes); }
        }
    }
    @Override public BufferedImage image(InfoPanelText tool) { return images.get(tool); }

    private BufferedImage load(int id, String name)
    {
        if (items == null) { return null; }
        try
        {
            if (!name.equalsIgnoreCase(items.getItemComposition(id).getName())) { return null; }
            BufferedImage source = items.getImage(id);
            if (source == null) { return null; }
            int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < source.getHeight(); y++)
            {
                for (int x = 0; x < source.getWidth(); x++)
                {
                    if ((source.getRGB(x, y) >>> 24) != 0)
                    { left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y); }
                }
            }
            if (right < left || bottom < top) { return null; } // Async image not ready yet.
            BufferedImage image = new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
            { for (int x = 0; x < image.getWidth(); x++) { image.setRGB(x, y, source.getRGB(left + x, top + y)); } }
            return image;
        }
        catch (RuntimeException notReady) { return null; }
    }
    private static BufferedImage redCopy(BufferedImage source)
    {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < copy.getHeight(); y++)
        {
            for (int x = 0; x < copy.getWidth(); x++)
            {
                int c = source.getRGB(x, y), v = Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
                copy.setRGB(x, y, v < 40 ? c : (c & 0xff000000) | (v << 16) | ((v * 32 / 100) << 8) | (v * 25 / 100));
            }
        }
        return copy;
    }
}
