package com.minimapfilter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Narrow correction for a native sprite-border artifact, never a
 * replacement minimap. The central disk and all native click handling are untouched.
 */
@Singleton
final class SquareHudTerrainSeamOverlay extends Overlay
{
    private final Client client;
    private final SquareHudTerrainCornersOverlay underlay;

    @Inject
    SquareHudTerrainSeamOverlay(Client client, SquareHudTerrainCornersOverlay underlay)
    {
        this.client = client;
        this.underlay = underlay;
        setPosition(OverlayPosition.DYNAMIC);
        // Unlike the main underlay, only a ~2.6px band sits above native widgets.
        // The circular centre is excluded to preserve icons and native terrain.
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_LOW);
        setMovable(false);
        setSnappable(false);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        SquareHudTerrainCornersOverlay.TerrainFrame view = underlay.frame();
        if (view == null)
        {
            return null;
        }
        Shape region = SquareHudTerrainSeamGeometry.region(view.map, client.getCameraYawTarget());
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(region);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(view.image, view.transform, null);
        }
        finally
        {
            g.dispose();
        }
        return null;
    }
}
