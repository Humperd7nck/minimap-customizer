package com.minimapfilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Custom minimap markers for attackable NPCs and ground-item piles.
 *
 * Native minimap rendering, rotation, zoom and input remain untouched. When a
 * custom NPC style is selected, MinimapFilterPlugin temporarily suppresses the
 * global native NPC dot sprite and this overlay redraws the native dot for
 * non-attackable NPCs so only attackable NPCs change appearance.
 */
@Singleton
public class EntityMarkerOverlay extends Overlay
{
    private static final BufferedImage SWORD_ICON = createSwordIcon();
    private static final BufferedImage SMALL_SWORD_B_ICON = createSmallSwordBIcon();
    private static final BufferedImage SMALL_SWORD_C_ICON = createSmallSwordCIcon();

    private final Client client;
    private final MinimapFilterConfig config;
    private final MinimapMarkerAssets assets;
    private final SquareHudController hud;
    private final LootingBagMarkers lootingBags;
    @Inject
    private TrackingVisibility trackingVisibility;

    @Inject
    public EntityMarkerOverlay(Client client, MinimapFilterConfig config, MinimapMarkerAssets assets, SquareHudController hud, LootingBagMarkers lootingBags)
    {
        this.client = client;
        this.config = config;
        this.assets = assets;
        this.hud = hud;
        this.lootingBags = lootingBags;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }
        boolean entities = trackingVisibility == null || trackingVisibility.entities();
        boolean groundItems = trackingVisibility == null || trackingVisibility.groundItems();
        if (!entities && !groundItems) { return null; }

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
                if (entities) { renderNpcMarkers(clipped); }
                if (groundItems) { renderGroundItemMarkers(clipped); }
            }
            finally
            {
                clipped.dispose();
            }
        }
        else
        {
            if (entities) { renderNpcMarkers(graphics); }
            if (groundItems) { renderGroundItemMarkers(graphics); }
        }
        return null;
    }

    private void renderNpcMarkers(Graphics2D graphics)
    {
        AttackableNpcMarkerStyle style = config.attackableNpcMarkerStyle();
        boolean showNonCombat = config.showNonCombatNpcs();

        // When both categories use their normal native dots, the client can do
        // all NPC-dot rendering itself. Otherwise the global native NPC sprite
        // is suppressed and we redraw each category independently below.
        if (style == AttackableNpcMarkerStyle.NATIVE_DOT && showNonCombat
            && !NativeDotColors.npcColorsDiffer(config))
        {
            return;
        }

        BufferedImage attackableNpcDot = assets.getAttackableNpcDot();
        BufferedImage nonCombatNpcDot = assets.getNonCombatNpcDot();
        for (NPC npc : client.getNpcs())
        {
            if (npc == null)
            {
                continue;
            }

            NPCComposition composition = npc.getTransformedComposition();
            if (composition == null)
            {
                composition = npc.getComposition();
            }
            if (composition == null || !composition.isMinimapVisible())
            {
                continue;
            }

            net.runelite.api.Point location = hud.isActive()
                ? hud.project(npc.getLocalLocation()) : npc.getMinimapLocation();
            if (location == null)
            {
                continue;
            }

            if (isAttackable(composition))
            {
                if (style == AttackableNpcMarkerStyle.SWORD)
                {
                    drawCentered(graphics, SWORD_ICON, location);
                }
                else if (style == AttackableNpcMarkerStyle.SMALL_SWORD_B)
                {
                    drawCentered(graphics, SMALL_SWORD_B_ICON, location);
                }
                else if (style == AttackableNpcMarkerStyle.SMALL_SWORD_C)
                {
                    drawCentered(graphics, SMALL_SWORD_C_ICON, location);
                }
                else if (style == AttackableNpcMarkerStyle.NATIVE_DOT && attackableNpcDot != null)
                {
                    drawCentered(graphics, attackableNpcDot, location);
                }
                // OFF intentionally draws nothing for attackable NPCs.
            }
            else if (showNonCombat && nonCombatNpcDot != null)
            {
                drawCentered(graphics, nonCombatNpcDot, location);
            }
        }
    }

    private static boolean isAttackable(NPCComposition composition)
    {
        String[] actions = composition.getActions();
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action != null && "Attack".equalsIgnoreCase(action))
            {
                return true;
            }
        }
        return false;
    }

    private void renderGroundItemMarkers(Graphics2D graphics)
    {
        if (config.groundItemMarkerStyle() != GroundItemMarkerStyle.LOOTING_BAG)
        {
            return;
        }

        lootingBags.beginFrame();
        Player localPlayer = client.getLocalPlayer();
        WorldView worldView = client.getTopLevelWorldView();
        if (localPlayer == null || worldView == null)
        {
            return;
        }

        LocalPoint playerLocation = localPlayer.getLocalLocation();
        Scene scene = worldView.getScene();
        if (playerLocation == null || scene == null)
        {
            return;
        }

        Tile[][][] tiles = scene.getTiles();
        int plane = worldView.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
        {
            return;
        }

        // Native minimap markers only matter near the player. Restricting the
        // scan to a 45x45 scene-tile window avoids walking the entire scene on
        // every frame while still exceeding the minimap's visible radius.
        int centerX = playerLocation.getSceneX();
        int centerY = playerLocation.getSceneY();
        int radius = hud.isActive()
            ? Math.min(104, (int) Math.ceil(Math.sqrt(2.0) * SquareHudGeometry.MAP_SIZE
                / (2.0 * Math.max(0.25, client.getMinimapZoom()))) + 2) : 22;
        int minX = Math.max(0, centerX - radius);
        int maxX = Math.min(tiles[plane].length - 1, centerX + radius);

        for (int x = minX; x <= maxX; x++)
        {
            Tile[] column = tiles[plane][x];
            if (column == null)
            {
                continue;
            }

            int minY = Math.max(0, centerY - radius);
            int maxY = Math.min(column.length - 1, centerY + radius);
            for (int y = minY; y <= maxY; y++)
            {
                Tile tile = column[y];
                if (tile == null) { continue; }
                List<TileItem> items = tile.getGroundItems();
                if (items == null || items.isEmpty()) { continue; }

                if (hud.isActive())
                {
                    net.runelite.api.Point center = hud.project(tile.getLocalLocation());
                    if (center != null)
                    {
                        drawCentered(graphics, lootingBags.imageFor(items), center);
                    }
                }
                else
                {
                    net.runelite.api.Point location = Perspective.getMiniMapImageLocation(
                        client, tile.getLocalLocation(), LootingBagIcons.original());
                    if (location != null)
                    {
                        graphics.drawImage(lootingBags.imageFor(items), location.getX(), location.getY(), null);
                    }
                }
            }
        }
    }

    private static void drawCentered(Graphics2D graphics, BufferedImage image, net.runelite.api.Point center)
    {
        int x = center.getX() - image.getWidth() / 2;
        int y = center.getY() - image.getHeight() / 2;
        graphics.drawImage(image, x, y, null);
    }

    private static BufferedImage createSwordIcon()
    {
        try (InputStream in = EntityMarkerOverlay.class.getResourceAsStream("sword_option_a.png"))
        {
            if (in != null)
            {
                BufferedImage image = ImageIO.read(in);
                if (image != null)
                {
                    return image;
                }
            }
        }
        catch (IOException ignored)
        {
            // Fall through to the tiny built-in fallback below.
        }

        // Resource failure should never remove the marker entirely. Keep a
        // compact slim sword fallback so the option remains usable.
        BufferedImage image = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            Color outline = new Color(20, 18, 15, 255);
            Color steel = new Color(198, 205, 211, 255);
            Color steelLight = new Color(244, 247, 246, 255);
            Color steelDark = new Color(137, 145, 153, 255);
            Color gold = new Color(226, 158, 21, 255);
            Color goldLight = new Color(255, 213, 57, 255);
            Color grip = new Color(116, 70, 40, 255);

            Polygon blade = new Polygon(
                new int[] {8, 10, 10, 9, 9, 7, 7, 6},
                new int[] {0, 2, 10, 11, 11, 11, 10, 2},
                8);
            g.setColor(outline);
            g.fillPolygon(blade);

            Polygon innerBlade = new Polygon(
                new int[] {8, 9, 9, 8, 7, 7},
                new int[] {1, 3, 9, 10, 9, 3},
                6);
            g.setColor(steel);
            g.fillPolygon(innerBlade);
            g.setColor(steelLight);
            g.drawLine(7, 3, 7, 9);
            g.setColor(steelDark);
            g.drawLine(9, 3, 9, 9);

            g.setColor(outline);
            g.fillRect(3, 10, 11, 3);
            g.setColor(gold);
            g.fillRect(4, 11, 9, 1);
            g.setColor(goldLight);
            g.drawLine(4, 11, 8, 11);

            g.setColor(outline);
            g.fillRect(6, 12, 5, 4);
            g.setColor(grip);
            g.fillRect(7, 12, 3, 3);
            g.setColor(outline);
            g.fillRect(6, 16, 5, 1);
            g.setColor(gold);
            g.fillRect(7, 15, 3, 2);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage createSmallSwordBIcon()
    {
        try (InputStream in = EntityMarkerOverlay.class.getResourceAsStream("sword_small_b.png"))
        {
            if (in != null)
            {
                BufferedImage image = ImageIO.read(in);
                if (image != null) { return image; }
            }
        }
        catch (IOException ignored)
        {
            // Fall through to the purpose-built 9x9 fallback below.
        }

        BufferedImage image = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        int outline = new Color(18, 16, 14, 255).getRGB();
        int steel = new Color(218, 226, 232, 255).getRGB();
        int light = new Color(255, 255, 255, 255).getRGB();
        int gold = new Color(235, 170, 28, 255).getRGB();
        int goldLight = new Color(255, 216, 66, 255).getRGB();
        int grip = new Color(118, 69, 39, 255).getRGB();
        int[][] pixels = {
            {8,0,outline},{7,0,outline},
            {8,1,outline},{7,1,light},{6,1,outline},
            {7,2,outline},{6,2,steel},{5,2,outline},
            {6,3,outline},{5,3,light},{4,3,outline},
            {5,4,outline},{4,4,steel},{3,4,outline},
            {2,5,outline},{3,5,gold},{4,5,outline},{5,5,gold},{6,5,outline},
            {3,6,outline},{4,6,grip},{5,6,outline},
            {2,7,outline},{3,7,grip},{4,7,outline},
            {1,8,outline},{2,8,goldLight},{3,8,outline}
        };
        for (int[] pixel : pixels) { image.setRGB(pixel[0], pixel[1], pixel[2]); }
        return image;
    }

    private static BufferedImage createSmallSwordCIcon()
    {
        try (InputStream in = EntityMarkerOverlay.class.getResourceAsStream("sword_small_c.png"))
        {
            if (in != null)
            {
                BufferedImage image = ImageIO.read(in);
                if (image != null) { return image; }
            }
        }
        catch (IOException ignored)
        {
            // Fall through to the purpose-built 11x11 fallback below.
        }

        BufferedImage image = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
        int outline = new Color(18, 16, 14, 255).getRGB();
        int steel = new Color(218, 226, 232, 255).getRGB();
        int light = new Color(255, 255, 255, 255).getRGB();
        int steelDark = new Color(124, 136, 146, 255).getRGB();
        int gold = new Color(235, 170, 28, 255).getRGB();
        int goldLight = new Color(255, 216, 66, 255).getRGB();
        int grip = new Color(118, 69, 39, 255).getRGB();
        int[][] pixels = {
            {9,0,outline},{10,0,outline},
            {8,1,outline},{9,1,light},{10,1,outline},
            {7,2,outline},{8,2,steel},{9,2,light},{10,2,outline},
            {6,3,outline},{7,3,steel},{8,3,light},{9,3,outline},
            {5,4,outline},{6,4,steel},{7,4,light},{8,4,outline},
            {4,5,outline},{5,5,steel},{6,5,steelDark},{7,5,outline},
            {2,6,outline},{3,6,gold},{4,6,goldLight},{5,6,outline},{6,6,goldLight},{7,6,gold},{8,6,outline},
            {4,7,outline},{5,7,grip},{6,7,outline},
            {3,8,outline},{4,8,grip},{5,8,grip},{6,8,outline},
            {2,9,outline},{3,9,grip},{4,9,outline},
            {1,10,outline},{2,10,gold},{3,10,goldLight},{4,10,outline}
        };
        for (int[] pixel : pixels) { image.setRGB(pixel[0], pixel[1], pixel[2]); }
        return image;
    }

}
