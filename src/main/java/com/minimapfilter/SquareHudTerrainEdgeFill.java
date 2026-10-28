package com.minimapfilter;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Extends the loaded scene's outer terrain colors into the empty padding of
 * drawInstanceMap(). The native minimap paints over this terrain underlay.
 * This is a visual fallback for unavailable scene pixels, not real map data.
 */
final class SquareHudTerrainEdgeFill
{
    private static final int SCENE_PIXELS = 104 * 4;
    // A compact source can otherwise run out of image at oblique camera angles
    // near the scene edge. Centering it on a 736px canvas preserves the map
    // coordinates while providing room to extend the missing terrain pixels.
    private static final int MIN_CANVAS_SIZE = 736;
    // The client leaves the outermost scene tile unpainted in some situations.
    // Sample two tiles inward so the one-tile blank rim cannot be repeated.
    private static final int SAFE_INSET = 8;

    private SquareHudTerrainEdgeFill() {}

    static BufferedImage extend(int width, int height, int[] pixels)
    {
        // Keep existing dimension validation and color behavior for
        // unexpected sprite sizes. No input buffer is modified in place.
        if (width < SCENE_PIXELS || height < SCENE_PIXELS
            || (long) width * height > 4_194_304L
            || pixels == null || (long) width * height != pixels.length)
        {
            return toImage(width, height, pixels);
        }

        final int outWidth = Math.max(width, MIN_CANVAS_SIZE);
        final int outHeight = Math.max(height, MIN_CANVAS_SIZE);
        if ((long) outWidth * outHeight > 4_194_304L)
        {
            return toImage(width, height, pixels);
        }
        final int offsetX = (outWidth - width) / 2;
        final int offsetY = (outHeight - height) / 2;
        final int[] expanded = new int[outWidth * outHeight];
        Arrays.fill(expanded, 1); // RuneLite's empty map-background color
        for (int y = 0; y < height; y++)
        {
            System.arraycopy(pixels, y * width, expanded,
                (y + offsetY) * outWidth + offsetX, width);
        }

        final int sceneX = (outWidth - SCENE_PIXELS) / 2;
        final int sceneY = (outHeight - SCENE_PIXELS) / 2;
        final int left = sceneX + SAFE_INSET;
        final int top = sceneY + SAFE_INSET;
        final int right = sceneX + SCENE_PIXELS - SAFE_INSET - 1;
        final int bottom = sceneY + SCENE_PIXELS - SAFE_INSET - 1;
        int[] result = Arrays.copyOf(expanded, expanded.length);

        // Only treat the game's reserved near-black background values as
        // missing. Preserve genuine terrain, buildings and black outlines in
        // the scene interior, and preserve valid expanded-scene pixels.
        for (int y = 0; y < outHeight; y++)
        {
            int sourceY = clamp(y, top, bottom);
            int row = y * outWidth;
            int sourceRow = sourceY * outWidth;
            for (int x = 0; x < outWidth; x++)
            {
                if ((x >= left && x <= right && y >= top && y <= bottom)
                    || !missing(expanded[row + x]))
                {
                    continue;
                }
                int sourceX = clamp(x, left, right);
                int color = expanded[sourceRow + sourceX] & 0xFFFFFF;
                if (!missing(color))
                {
                    result[row + x] = color;
                }
            }
        }
        return toImage(outWidth, outHeight, result);
    }

    // Equivalent to the old terrain-image copy helper; preserves the exact
    // RGB and pixel ordering used by the validated square minimap.
    private static BufferedImage toImage(int width, int height, int[] pixels)
    {
        long count = (long) width * height;
        if (width <= 0 || height <= 0 || count > 4_194_304L
            || pixels == null || count != pixels.length)
        {
            throw new IllegalArgumentException("Unexpected terrain image dimensions");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static boolean missing(int color)
    {
        return (color & 0xFFFFFF) <= 1;
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
