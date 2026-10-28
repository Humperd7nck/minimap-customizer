package com.minimapfilter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Re-renders individual tree MAPSCENE symbols only when ordinary foliage and
 * Rare Trees share the same native sprite frame but their visibility settings
 * differ. The native minimap remains authoritative for terrain, rotation,
 * zoom, clicks, and all non-shared map-scene graphics.
 */
final class TreeMapSceneOverlay extends Overlay
{
    private final MinimapFilterPlugin plugin;
    private final SquareHudController hud;

    TreeMapSceneOverlay(MinimapFilterPlugin plugin, SquareHudController hud)
    {
        super(plugin);
        this.plugin = plugin;
        this.hud = hud;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (hud.isActive())
        {
            if (!hud.isPaintable())
            {
                return null;
            }
            Graphics2D clipped = (Graphics2D) graphics.create();
            try
            {
                clipped.clip(hud.markerClip());
                plugin.renderSeparatedTreeObjects(clipped);
            }
            finally
            {
                clipped.dispose();
            }
        }
        else
        {
            plugin.renderSeparatedTreeObjects(graphics);
        }
        return null;
    }
}
