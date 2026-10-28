package com.minimapfilter;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;

import static org.junit.Assert.*;

/** Guards the native icon visibility and backdrop ordering of the full-square underlay. */
public class SquareHudTerrainCornersOverlayTest
{
    @Test
    public void terrainDrawsAfterBlackBackdropButBeforeNativeWidgets()
    {
        SquareHudBackdropOverlay backdrop = new SquareHudBackdropOverlay(() -> null);
        SquareHudTerrainCornersOverlay terrain = new SquareHudTerrainCornersOverlay(null, null);

        assertEquals(OverlayPosition.DYNAMIC, terrain.getPosition());
        assertEquals(OverlayLayer.UNDER_WIDGETS, backdrop.getLayer());
        assertEquals(OverlayLayer.UNDER_WIDGETS, terrain.getLayer());
        assertTrue("Terrain must paint over the black HUD backdrop",
            terrain.getPriority() > backdrop.getPriority());
    }
}
