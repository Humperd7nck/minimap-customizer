package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Optional local-player direction marker drawn over the native minimap center.
 * The native minimap remains responsible for rotation, zoom, terrain and input.
 */
@Singleton
public class PlayerDirectionOverlay extends Overlay
{
    private static final int FACING_SAMPLE_DISTANCE = 4 * Perspective.LOCAL_TILE_SIZE;

    // Compact faceted navigation-arrow proportions. The shape intentionally
    // covers the native center marker while remaining small enough not to hide
    // nearby minimap detail.
    private static final double TIP_DISTANCE = 9.0;
    private static final double REAR_DISTANCE = -5.5;
    private static final double REAR_HALF_WIDTH = 6.0;
    private static final double NOTCH_DISTANCE = -1.2;

    private static final Color OUTER_DARK = new Color(111, 61, 0, 245);
    private static final Color TOP_GOLD = new Color(255, 225, 96, 255);
    private static final Color TOP_GOLD_TIP = new Color(255, 184, 28, 255);
    private static final Color LOWER_GOLD = new Color(239, 139, 10, 255);
    private static final Color LOWER_GOLD_TIP = new Color(255, 194, 35, 255);
    private static final Color INNER_GOLD = new Color(255, 202, 45, 235);
    private static final Color INNER_HIGHLIGHT = new Color(255, 246, 181, 225);
    private static final Color RIDGE = new Color(255, 232, 136, 210);

    private final Client client;
    private final MinimapFilterConfig config;

    @Inject
    public PlayerDirectionOverlay(Client client, MinimapFilterConfig config)
    {
        this.client = client;
        this.config = config;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.playerMarkerStyle() != PlayerMarkerStyle.DIRECTION_ARROW
            || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return null;
        }

        LocalPoint local = player.getLocalLocation();
        net.runelite.api.Point center = player.getMinimapLocation();
        if (local == null || center == null)
        {
            return null;
        }

        int orientation = player.getCurrentOrientation() & 2047;
        double radians = orientation * (Math.PI * 2.0 / 2048.0);

        // RuneScape orientation: 0=S, 512=W, 1024=N, 1536=E.
        int dx = (int) Math.round(-Math.sin(radians) * FACING_SAMPLE_DISTANCE);
        int dy = (int) Math.round(-Math.cos(radians) * FACING_SAMPLE_DISTANCE);

        LocalPoint facingPoint = new LocalPoint(
            local.getX() + dx,
            local.getY() + dy,
            local.getWorldView());

        net.runelite.api.Point facing = Perspective.localToMinimap(client, facingPoint);
        if (facing == null)
        {
            return null;
        }

        double vx = facing.getX() - center.getX();
        double vy = facing.getY() - center.getY();
        double length = Math.hypot(vx, vy);
        if (length < 0.5)
        {
            return null;
        }

        double ux = vx / length;
        double uy = vy / length;
        double px = -uy;
        double py = ux;

        // The outer silhouette mirrors the supplied gold navigation-arrow
        // reference: a sharp nose, two swept rear wings and a shallow center notch.
        Polygon outer = polygon(
            center, ux, uy, px, py,
            TIP_DISTANCE, 0.0,
            REAR_DISTANCE, REAR_HALF_WIDTH,
            NOTCH_DISTANCE, 0.0,
            REAR_DISTANCE, -REAR_HALF_WIDTH);

        Polygon upperFacet = polygon(
            center, ux, uy, px, py,
            TIP_DISTANCE - 0.7, 0.0,
            REAR_DISTANCE + 0.8, REAR_HALF_WIDTH - 0.8,
            NOTCH_DISTANCE + 0.1, 0.0);

        Polygon lowerFacet = polygon(
            center, ux, uy, px, py,
            TIP_DISTANCE - 0.7, 0.0,
            NOTCH_DISTANCE + 0.1, 0.0,
            REAR_DISTANCE + 0.8, -REAR_HALF_WIDTH + 0.8);

        Polygon innerFacet = polygon(
            center, ux, uy, px, py,
            TIP_DISTANCE - 1.8, 0.0,
            REAR_DISTANCE + 3.0, REAR_HALF_WIDTH - 2.3,
            NOTCH_DISTANCE + 1.0, 0.0);

        net.runelite.api.Point tip = point(center, ux, uy, px, py, TIP_DISTANCE, 0.0);
        net.runelite.api.Point upperRear = point(center, ux, uy, px, py,
            REAR_DISTANCE, REAR_HALF_WIDTH);
        net.runelite.api.Point lowerRear = point(center, ux, uy, px, py,
            REAR_DISTANCE, -REAR_HALF_WIDTH);
        net.runelite.api.Point notch = point(center, ux, uy, px, py,
            NOTCH_DISTANCE, 0.0);

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Dark gold shell gives the arrow separation from light terrain and
            // also provides the beveled edge visible in the reference artwork.
            g.setColor(OUTER_DARK);
            g.fillPolygon(outer);

            g.setPaint(new GradientPaint(
                upperRear.getX(), upperRear.getY(), TOP_GOLD,
                tip.getX(), tip.getY(), TOP_GOLD_TIP));
            g.fillPolygon(upperFacet);

            g.setPaint(new GradientPaint(
                lowerRear.getX(), lowerRear.getY(), LOWER_GOLD,
                tip.getX(), tip.getY(), LOWER_GOLD_TIP));
            g.fillPolygon(lowerFacet);

            g.setPaint(new GradientPaint(
                notch.getX(), notch.getY(), INNER_GOLD,
                tip.getX(), tip.getY(), INNER_HIGHLIGHT));
            g.fillPolygon(innerFacet);

            // Fine ridge/highlight lines reinforce the faceted, gem-like shape
            // without relying on a raster asset, so rotation remains crisp.
            g.setStroke(new BasicStroke(0.85f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(RIDGE);
            g.drawLine(notch.getX(), notch.getY(), tip.getX(), tip.getY());

            net.runelite.api.Point upperInner = point(center, ux, uy, px, py,
                REAR_DISTANCE + 3.0, REAR_HALF_WIDTH - 2.3);
            g.setColor(new Color(255, 247, 190, 180));
            g.drawLine(upperInner.getX(), upperInner.getY(), tip.getX(), tip.getY());

            g.setColor(OUTER_DARK);
            g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(outer);
        }
        finally
        {
            g.dispose();
        }

        return null;
    }

    private static Polygon polygon(
        net.runelite.api.Point center,
        double ux,
        double uy,
        double px,
        double py,
        double... coordinates)
    {
        Polygon polygon = new Polygon();
        for (int i = 0; i < coordinates.length; i += 2)
        {
            net.runelite.api.Point p = point(
                center, ux, uy, px, py, coordinates[i], coordinates[i + 1]);
            polygon.addPoint(p.getX(), p.getY());
        }
        return polygon;
    }

    private static net.runelite.api.Point point(
        net.runelite.api.Point center,
        double ux,
        double uy,
        double px,
        double py,
        double forward,
        double side)
    {
        int x = (int) Math.round(center.getX() + ux * forward + px * side);
        int y = (int) Math.round(center.getY() + uy * forward + py * side);
        return new net.runelite.api.Point(x, y);
    }
}
