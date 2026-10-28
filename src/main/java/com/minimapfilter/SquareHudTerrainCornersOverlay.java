package com.minimapfilter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.CameraFocusableEntity;
import net.runelite.api.WorldView;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Square HUD terrain underlay; native minimap terrain and icons paint on top. */
@Singleton
final class SquareHudTerrainCornersOverlay extends Overlay
{
    private final Client client;
    private final SquareHudController hud;
    private volatile BufferedImage terrain;
    private int lastRefreshCycle = -1000;

    @Inject
    SquareHudTerrainCornersOverlay(Client client, SquareHudController hud)
    {
        this.client = client;
        this.hud = hud;
        setPosition(OverlayPosition.DYNAMIC);
        // Paint after SquareHudBackdropOverlay's black underlay but before all
        // native widgets. Painting ABOVE_WIDGETS obscures native minimap icons
        // and causes overlapping, slightly different terrain samples to shimmer.
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(PRIORITY_MED);
        setMovable(false);
        setSnappable(false);
        setResizable(false);
    }

    void refreshOnClientThread()
    {
        if (!hud.isPaintable()
            || client.getGameState() != GameState.LOGGED_IN)
        {
            terrain = null;
            lastRefreshCycle = -1000;
            return;
        }
        int cycle = client.getGameCycle();
        if (cycle - lastRefreshCycle < 30 && terrain != null)
        {
            return;
        }
        lastRefreshCycle = cycle;
        try
        {
            SpritePixels sprite = client.drawInstanceMap(client.getPlane());
            if (sprite == null || sprite.getPixels() == null)
            {
                terrain = null;
                return;
            }
            // drawInstanceMap() can contain near-black padding around the loaded
            // scene. Extrude adjacent scene-edge colors only into those blank
            // pixels so rotated sprite boundaries do not cut across corners.
            terrain = SquareHudTerrainEdgeFill.extend(sprite.getWidth(), sprite.getHeight(), sprite.getPixels());
        }
        catch (RuntimeException ex)
        {
            terrain = null;
        }
    }

    /** One live projection for the terrain underlay and its narrow seam repair. */
    TerrainFrame frame()
    {
        BufferedImage image = terrain;
        if (image == null || !hud.isPaintable())
        {
            return null;
        }
        CameraFocusableEntity cameraFocus = client.getCameraFocusEntity();
        LocalPoint pos = cameraFocus == null ? null : cameraFocus.getCameraFocus();
        Rectangle map = hud.mapBounds();
        // The scene sprite is for the top-level map. Do not draw incorrect
        // corner terrain while a sub-worldview is focused (e.g. a vessel).
        if (pos == null || pos.getWorldView() != WorldView.TOPLEVEL
            || map == null || map.width < 40 || map.height < 40)
        {
            return null;
        }
        AffineTransform transform = SquareHudTerrainProjection.forMap(map,
            image.getWidth(), image.getHeight(), pos.getX(), pos.getY(),
            client.getCameraYawTarget(), client.getMinimapZoom());
        return transform == null ? null : new TerrainFrame(image, map, transform);
    }

    static final class TerrainFrame
    {
        final BufferedImage image;
        final Rectangle map;
        final AffineTransform transform;

        TerrainFrame(BufferedImage image, Rectangle map, AffineTransform transform)
        {
            this.image = image;
            this.map = map;
            this.transform = transform;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        TerrainFrame view = frame();
        if (view == null)
        {
            return null;
        }
        // The native game paints terrain and icons above this underlay.
        // Only the previously unpainted corners remain visible.
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(view.map);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(view.image, view.transform, null);
        }
        finally
        {
            g.dispose();
        }
        return null;
    }
}
