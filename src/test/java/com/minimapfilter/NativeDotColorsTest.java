package com.minimapfilter;

import java.awt.Color;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verify native-dot recoloring doesn't alter the source sprite or its silhouette. */
public class NativeDotColorsTest
{
    @Test
    public void recolorsNativeDotButPreservesTransparentPixelsAndShadow()
    {
        int[] original = {0, 0xFFFFFFFF, 1, 0xFF00FF00, 1, 0, 0xFF000000};
        int[] snapshot = Arrays.copyOf(original, original.length);
        int[] tinted = NativeDotColors.recolor(original, new Color(0x237FBC));
        assertArrayEquals(snapshot, original);
        assertEquals(0, tinted[0]);
        assertEquals(1, tinted[2]);
        assertEquals(1, tinted[4]);
        assertEquals(0, tinted[5]);
        assertEquals(0xFF237FBC, tinted[1]);
        assertEquals(0xFF237FBC, tinted[3]);
        assertEquals(0xFF237FBC, tinted[6]);
    }

    @Test
    public void defaultColorDoesNotRequireNativeSpriteReplacement()
    {
        assertFalse(NativeDotColors.isCustomized(NativeDotColors.NPC, NativeDotColors.NPC));
        assertFalse(NativeDotColors.isCustomized(NativeDotColors.PLAYER, NativeDotColors.PLAYER));
        assertFalse(NativeDotColors.isCustomized(null, NativeDotColors.ITEM));
        assertTrue(NativeDotColors.isCustomized(Color.CYAN, NativeDotColors.NPC));
    }

    @Test
    public void npcColorSeparationOnlyWhenColorsActuallyDiffer()
    {
        MinimapFilterConfig defaults = new MinimapFilterConfig() {};
        assertFalse(NativeDotColors.npcColorsDiffer(defaults));
        MinimapFilterConfig different = new MinimapFilterConfig()
        {
            @Override
            public Color attackableNpcDotColor()
            {
                return Color.MAGENTA;
            }
        };
        assertTrue(NativeDotColors.npcColorsDiffer(different));
        MinimapFilterConfig sameCustom = new MinimapFilterConfig()
        {
            @Override
            public Color attackableNpcDotColor()
            {
                return Color.MAGENTA;
            }
            @Override
            public Color nonCombatNpcDotColor()
            {
                return Color.MAGENTA;
            }
        };
        assertFalse(NativeDotColors.npcColorsDiffer(sameCustom));
    }
}
