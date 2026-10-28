package com.minimapfilter;

import java.awt.Rectangle;
import java.awt.Shape;
import net.runelite.client.ui.overlay.OverlayLayer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression checks for the precisely limited black-line repair. */
public class SquareHudTerrainSeamGeometryTest
{
    @Test
    public void capturesMeasuredSeamsWithoutPaintingNativeCentre()
    {
        // Recorded capture: 152px map, 14-bit yaw of 14073.
        Shape region = SquareHudTerrainSeamGeometry.region(new Rectangle(0, 0, 152, 152), 14073);
        assertTrue("upper-left diagonal", region.contains(7, 30));
        assertTrue("upper-right diagonal", region.contains(128, 12));
        assertFalse("never paint native centre", region.contains(76, 76));
        assertFalse("never paint normal icon band", region.contains(76, 10));
        assertFalse("never paint outside minimap", region.contains(-1, 10));
    }

    @Test
    public void noRepairInCentralEightyOnePixelDiskForAllCameraAngles()
    {
        Rectangle map = new Rectangle(10, 20, 152, 152);
        for (int yaw = 0; yaw < 16384; yaw += 1024)
        {
            Shape region = SquareHudTerrainSeamGeometry.region(map, yaw);
            for (int dx = -80; dx <= 80; dx += 4)
            {
                for (int dy = -80; dy <= 80; dy += 4)
                {
                    if (dx * dx + dy * dy <= 80 * 80)
                    {
                        assertFalse("native centre must remain untouched",
                            region.contains(86 + dx, 96 + dy));
                    }
                }
            }
        }
    }

    @Test
    public void repairCoversOnlyAThinFractionOfTheMap()
    {
        Shape region = SquareHudTerrainSeamGeometry.region(new Rectangle(0, 0, 152, 152), 14073);
        int painted = 0;
        for (int y = 0; y < 152; y++)
        {
            for (int x = 0; x < 152; x++)
            {
                if (region.contains(x + 0.5, y + 0.5))
                {
                    painted++;
                }
            }
        }
        assertTrue("must find black-line repair pixels", painted > 20);
        assertTrue("must not cover meaningful native icon area", painted < 600);
    }

    @Test
    public void disabledGeometryIsEmpty()
    {
        assertTrue(SquareHudTerrainSeamGeometry.region(null, 0).getBounds().isEmpty());
    }

    @Test
    public void repairDrawsAboveWidgetsWithVisualOnlyOverlay()
    {
        SquareHudTerrainSeamOverlay seam = new SquareHudTerrainSeamOverlay(null, null);
        assertEquals(OverlayLayer.ABOVE_WIDGETS, seam.getLayer());
    }
}
