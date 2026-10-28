package com.minimapfilter;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import org.junit.Test;

import static org.junit.Assert.*;

/** Verifies the terrain-image transform against RuneLite's minimap projection. */
public class SquareHudTerrainProjectionTest
{
    private static final Rectangle MAP = new Rectangle(200, 30, 152, 152);
    private static final int X_LOCAL = 50 * 128;
    private static final int Y_LOCAL = 40 * 128;

    private static Point2D transform(AffineTransform matrix, double x, double y)
    {
        return matrix.transform(new Point2D.Double(x, y), null);
    }

    private static void checkProjection(int sourceSize, int yaw, double pixelsPerTile,
        double expectedEastX, double expectedEastY,
        double expectedNorthX, double expectedNorthY)
    {
        AffineTransform matrix = SquareHudTerrainProjection.forMap(
            MAP, sourceSize, sourceSize, X_LOCAL, Y_LOCAL, yaw, pixelsPerTile);
        assertNotNull(matrix);
        double padding = (sourceSize - 104 * 4) / 2.0;
        double sx = padding + 50 * 4;
        double sy = padding + (104 - 40) * 4;
        Point2D origin = transform(matrix, sx, sy);
        assertEquals(MAP.getCenterX(), origin.getX(), 1e-7);
        assertEquals(MAP.getCenterY(), origin.getY(), 1e-7);

        Point2D east = transform(matrix, sx + 4, sy);
        assertEquals(origin.getX() + expectedEastX, east.getX(), 1e-7);
        assertEquals(origin.getY() + expectedEastY, east.getY(), 1e-7);
        Point2D north = transform(matrix, sx, sy - 4);
        assertEquals(origin.getX() + expectedNorthX, north.getX(), 1e-7);
        assertEquals(origin.getY() + expectedNorthY, north.getY(), 1e-7);
    }

    @Test
    public void centersPlayerForCompactAndExtendedSceneSprites()
    {
        checkProjection(512, 0, 4.0, 4, 0, 0, -4);
        checkProjection(736, 0, 4.0, 4, 0, 0, -4);
    }

    @Test
    public void followsFourteenBitYawInAllCardinalDirections()
    {
        checkProjection(736, 4096, 4.0, 0, 4, 4, 0);
        checkProjection(736, 8192, 4.0, -4, 0, 0, 4);
        checkProjection(736, 12288, 4.0, 0, -4, -4, 0);
        checkProjection(736, 16384 + 4096, 4.0, 0, 4, 4, 0);
    }

    @Test
    public void followsMinimapZoomInPixelsPerTile()
    {
        checkProjection(512, 0, 2.0, 2, 0, 0, -2);
        checkProjection(736, 4096, 8.0, 0, 8, 8, 0);
    }

    @Test
    public void rejectsInvalidSourceOrScale()
    {
        assertNull(SquareHudTerrainProjection.forMap(MAP, 200, 736, X_LOCAL, Y_LOCAL, 0, 4.0));
        assertNull(SquareHudTerrainProjection.forMap(MAP, 736, 736, X_LOCAL, Y_LOCAL, 0, 0.0));
        assertNull(SquareHudTerrainProjection.forMap(MAP, 736, 736, X_LOCAL, Y_LOCAL, 0, Double.NaN));
    }
}
