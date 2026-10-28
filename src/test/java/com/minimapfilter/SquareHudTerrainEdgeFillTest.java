package com.minimapfilter;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** The terrain underlay extends scene edges without damaging real map pixels. */
public class SquareHudTerrainEdgeFillTest
{
    private static int color(BufferedImage image, int x, int y)
    {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private static void verify(int size, int blank)
    {
        int[] pixels = new int[size * size];
        Arrays.fill(pixels, blank);
        int start = (size - 416) / 2;
        for (int y = start + 8; y < start + 408; y++)
        {
            for (int x = start + 8; x < start + 408; x++)
            {
                pixels[y * size + x] = (x < size / 2 ? 0x326C42 : 0xB6A552);
            }
        }
        // Preserve a black interior feature instead of replacing dark outlines.
        pixels[(size / 2) * size + size / 2] = 0;
        // A valid extended-scene pixel must not be overwritten.
        pixels[size * 3 + 3] = 0x112233;
        BufferedImage fixed = SquareHudTerrainEdgeFill.extend(size, size, pixels);
        int outputSize = Math.max(736, size);
        int inset = (outputSize - size) / 2;
        int outputSceneStart = (outputSize - 416) / 2;

        assertEquals(outputSize, fixed.getWidth());
        assertEquals(outputSize, fixed.getHeight());
        assertEquals("Left corner extends scene color", 0x326C42, color(fixed, 0, 0));
        assertEquals("Right corner extends scene color", 0xB6A552, color(fixed, outputSize - 1, 0));
        assertEquals(0x326C42, color(fixed, 0, outputSize - 1));
        assertEquals(0xB6A552, color(fixed, outputSize - 1, outputSize - 1));
        assertEquals("Covers blank first scene tile", 0x326C42, color(fixed, outputSceneStart + 1, outputSize / 2));
        assertEquals("Preserves legitimate black interior", 0, color(fixed, outputSize / 2, outputSize / 2));
        assertEquals("Preserves real expanded-scene terrain", 0x112233, color(fixed, inset + 3, inset + 3));
        assertEquals("Input pixel array unchanged", blank, pixels[0]);
    }

    @Test
    public void repairsNoDataOnCompactAndExpandedSprites()
    {
        verify(512, 0);
        verify(736, 1);
    }

    @Test
    public void preservesUnrelatedSmallImages()
    {
        int[] pixels = {1, 0x123456, 0, 0xABCDEF};
        BufferedImage image = SquareHudTerrainEdgeFill.extend(2, 2, pixels);
        assertEquals(1, color(image, 0, 0));
        assertEquals(0x123456, color(image, 1, 0));
        assertEquals(0xABCDEF, color(image, 1, 1));
    }
}
