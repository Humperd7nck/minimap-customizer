package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * A thin repair region around the native minimap's rotated sprite borders.
 * Excludes the central native minimap disk to protect icons and map content.
 * Based on measured 152px-square captures: source sprite borders appear
 * ~83 logical pixels from the centre (152/2 + 7).
 */
final class SquareHudTerrainSeamGeometry
{
    private static final double EXTRA_EDGE_PIXELS = 7.0;
    private static final double CENTRE_PROTECTION_PIXELS = 5.5;
    private static final float SEAM_WIDTH_PIXELS = 2.6f;
    private static final int YAW_UNITS = 16384;

    private SquareHudTerrainSeamGeometry() {}

    static Shape region(Rectangle map, int cameraYawTarget)
    {
        if (map == null || map.width < 40 || map.height < 40)
        {
            return new Area();
        }
        double halfSpan = Math.min(map.width, map.height) / 2.0 + EXTRA_EDGE_PIXELS;
        double protectedRadius = Math.min(map.width, map.height) / 2.0 + CENTRE_PROTECTION_PIXELS;
        double radians = (cameraYawTarget & (YAW_UNITS - 1)) * (2.0 * Math.PI / YAW_UNITS);
        AffineTransform rotated = new AffineTransform();
        rotated.translate(map.getCenterX(), map.getCenterY());
        rotated.rotate(radians);
        Rectangle2D nativeSquare = new Rectangle2D.Double(-halfSpan, -halfSpan,
            2.0 * halfSpan, 2.0 * halfSpan);
        Shape border = rotated.createTransformedShape(nativeSquare);
        Area repair = new Area(new BasicStroke(SEAM_WIDTH_PIXELS,
            BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER).createStrokedShape(border));
        repair.intersect(new Area(map));
        repair.subtract(new Area(new Ellipse2D.Double(
            map.getCenterX() - protectedRadius, map.getCenterY() - protectedRadius,
            protectedRadius * 2, protectedRadius * 2)));
        return repair;
    }
}
