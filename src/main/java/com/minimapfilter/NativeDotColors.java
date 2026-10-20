package com.minimapfilter;

import java.awt.Color;
import java.util.Arrays;
import java.util.Objects;

/**
 * RuneScape's normal dot colors. Selecting an unmodified default preserves
 * RuneLite's current native dot sprite (including any Minimap-plugin tint).
 * The shape and the native shadow pixel are retained when recoloring.
 */
final class NativeDotColors
{
    static final Color ITEM = new Color(0xFF0000);
    static final Color NPC = new Color(0xFFFF00);
    static final Color PLAYER = new Color(0xFFFFFF);
    static final Color FRIEND = new Color(0x00FF00);
    static final Color TEAM = new Color(0x0000FF);
    static final Color FRIENDS_CHAT = new Color(0x800080);
    static final Color CLAN = new Color(0xFFA500);

    private NativeDotColors()
    {
    }

    static boolean isCustomized(Color selected, Color defaultColor)
    {
        return selected != null && !selected.equals(defaultColor);
    }

    static boolean npcColorsDiffer(MinimapFilterConfig config)
    {
        // Same colors can be drawn by the single shared native NPC sprite.
        return !Objects.equals(config.attackableNpcDotColor(), config.nonCombatNpcDotColor());
    }

    static int[] recolor(int[] nativePixels, Color color)
    {
        int[] pixels = Arrays.copyOf(nativePixels, nativePixels.length);
        if (color == null)
        {
            return pixels;
        }
        int rgb = color.getRGB();
        for (int i = 0; i < pixels.length; i++)
        {
            // RuneLite native dots use 0 for transparency and 1 for shadow.
            // Neither should be tinted or converted to opaque color.
            if (pixels[i] != 0 && pixels[i] != 1)
            {
                pixels[i] = rgb;
            }
        }
        return pixels;
    }
}
