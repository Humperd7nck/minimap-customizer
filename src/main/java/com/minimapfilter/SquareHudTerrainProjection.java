package com.minimapfilter;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

/**
 * Projects the north-up scene terrain sprite into native minimap canvas coordinates.
 * The scene is 104 tiles wide, sampled at four source pixels per tile. RuneLite's
 * expanded map sprites can add padding on all four sides (for example 736px vs
 * 512px), so the origin must be derived from the actual sprite dimensions.
 */
final class SquareHudTerrainProjection
{
    private static final int SCENE_TILES = 104;
    private static final double SOURCE_PIXELS_PER_TILE = 4.0;
    private static final double SOURCE_SCENE_PIXELS = SCENE_TILES * SOURCE_PIXELS_PER_TILE;
    private static final double LOCAL_UNITS_PER_SOURCE_PIXEL = 128.0 / SOURCE_PIXELS_PER_TILE;
    private static final int YAW_UNITS = 16384;

    private SquareHudTerrainProjection() {}

    static AffineTransform forMap(Rectangle bounds, int sourceWidth, int sourceHeight,
        int focusLocalX, int focusLocalY, int cameraYawTarget, double pixelsPerTile)
    {
        if (bounds == null || bounds.width < 40 || bounds.height < 40
            || sourceWidth < SOURCE_SCENE_PIXELS || sourceHeight < SOURCE_SCENE_PIXELS
            || !Double.isFinite(pixelsPerTile) || pixelsPerTile <= 0)
        {
            return null;
        }

        // The scene starts after the map sprite's surrounding padding. Y increases
        // downward in the image, but northward in LocalPoint coordinates.
        double paddingX = (sourceWidth - SOURCE_SCENE_PIXELS) / 2.0;
        double paddingY = (sourceHeight - SOURCE_SCENE_PIXELS) / 2.0;
        double sourceX = paddingX + focusLocalX / LOCAL_UNITS_PER_SOURCE_PIXEL;
        double sourceY = paddingY + SOURCE_SCENE_PIXELS
            - focusLocalY / LOCAL_UNITS_PER_SOURCE_PIXEL;

        // Same yaw convention as Perspective.localToMinimap: 14-bit camera yaw,
        // east to screen-right at zero, with positive angles clockwise on screen.
        double radians = (cameraYawTarget & (YAW_UNITS - 1))
            * (2.0 * Math.PI / YAW_UNITS);
        double scale = pixelsPerTile / SOURCE_PIXELS_PER_TILE;

        AffineTransform transform = new AffineTransform();
        transform.translate(bounds.getCenterX(), bounds.getCenterY());
        transform.rotate(radians);
        transform.scale(scale, scale);
        transform.translate(-sourceX, -sourceY);
        return transform;
    }
}
