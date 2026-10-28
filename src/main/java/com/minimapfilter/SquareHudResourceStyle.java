package com.minimapfilter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.gameval.SpriteID;

/** Square HUD artwork only; never changes the game's sprites or button actions. */
final class SquareHudResourceStyle
{
    static final Color INACTIVE_GRAY = new Color(136, 140, 144);
    static final Color INACTIVE_GRAY_HIGHLIGHT = new Color(160, 164, 168);
    static final Color RUN_YELLOW = new Color(192, 155, 47);
    static final Color SPEC_GREEN = new Color(57, 137, 79);
    static final Color SPEC_READY_GREEN = new Color(100, 173, 119);
    static final Color ICON_YELLOW = new Color(255, 224, 72);

    private SquareHudResourceStyle() {}

    static int runIconSprite(boolean running, boolean stamina)
    {
        return stamina ? SpriteID.OrbIcon.RUN_ICON_SLOWED_DEPLETION
            : running ? SpriteID.OrbIcon.RUN : SpriteID.OrbIcon.WALK;
    }

    static Color runFill(boolean running)
    {
        // Stamina changes the boot, not whether running is switched on.
        return running ? RUN_YELLOW : INACTIVE_GRAY;
    }

    static Color specialFill(boolean available, boolean selected)
    {
        return !available ? INACTIVE_GRAY : selected ? SPEC_GREEN : SPEC_READY_GREEN;
    }

    static Color fillHighlight(Color fill)
    {
        // Color.brighter() turns an inactive gray almost white. Keep that
        // gradient deliberately muted, without changing the active palettes.
        return INACTIVE_GRAY.equals(fill) ? INACTIVE_GRAY_HIGHLIGHT : fill.brighter();
    }

    static BufferedImage yellowIcon(BufferedImage source)
    {
        if (source == null) { return null; }
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                int r = (argb >>> 16) & 255;
                int g = (argb >>> 8) & 255;
                int b = argb & 255;
                int brightness = Math.max(r, Math.max(g, b));
                // Preserve transparent pixels and the dark outline, retaining shading.
                if (alpha != 0 && brightness > 48)
                {
                    r = brightness * ICON_YELLOW.getRed() / 255;
                    g = brightness * ICON_YELLOW.getGreen() / 255;
                    b = brightness * ICON_YELLOW.getBlue() / 255;
                    argb = (alpha << 24) | (r << 16) | (g << 8) | b;
                }
                result.setRGB(x, y, argb);
            }
        }
        return result;
    }
}
