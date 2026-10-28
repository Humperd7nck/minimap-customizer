package com.minimapfilter;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Covers the world beneath pixels the native minimap does not paint. */
@Singleton
final class SquareHudBackdropOverlay extends Overlay
{
    private final Supplier<SquareHudController.Snapshot> snapshots;

    @Inject
    SquareHudBackdropOverlay(SquareHudController hud)
    {
        this(hud::snapshot);
    }

    SquareHudBackdropOverlay(Supplier<SquareHudController.Snapshot> snapshots)
    {
        this.snapshots = snapshots;
        setPosition(OverlayPosition.DYNAMIC);
        // The native minimap must paint after this, not underneath it.
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setMovable(false);
        setSnappable(false);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        SquareHudController.Snapshot view = snapshots.get();
        if (view == null || !view.paint)
        {
            return null;
        }
        Rectangle map = view.geometry.map();
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.BLACK);
            g.fillRect(map.x, map.y, map.width, map.height);
            if (view.footerVisible)
            {
                Rectangle footer = view.geometry.footer();
                g.setColor(new Color(80, 73, 56));
                g.fillRect(footer.x, footer.y, footer.width, footer.height);
            }
        }
        finally
        {
            g.dispose();
        }
        // No overlay bounds, menu entries or input listeners: native clicks remain unchanged.
        return null;
    }
}
