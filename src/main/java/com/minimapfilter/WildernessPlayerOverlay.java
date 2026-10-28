package com.minimapfilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Replaces only ordinary-player dots while a reliable Wilderness bracket is available. */
@Singleton
final class WildernessPlayerOverlay extends Overlay
{
    private final Client client;
    private final MinimapFilterConfig config;
    interface MapView
    {
        boolean isSquare();
        boolean isPaintable();
        Shape clip();
        net.runelite.api.Point project(net.runelite.api.coords.LocalPoint point);
    }

    private final MapView view;
    private WildernessCombatRange range;
    private String lastLevelText;
    private int lastCombat = -1;
    private SpritePixels fallbackSource;
    private Color fallbackColor;
    private BufferedImage fallbackDot;

    @Inject
    WildernessPlayerOverlay(Client client, MinimapFilterConfig config, SquareHudController hud)
    {
        this(client, config, new MapView()
        {
            public boolean isSquare() { return hud.isActive(); }
            public boolean isPaintable() { return hud.isPaintable(); }
            public Shape clip() { return hud.markerClip(); }
            public net.runelite.api.Point project(net.runelite.api.coords.LocalPoint point)
            { return hud.project(point); }
        });
    }

    WildernessPlayerOverlay(Client client, MinimapFilterConfig config, MapView view)
    {
        this.client = client;
        this.config = config;
        this.view = view;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGH);
    }

    /** Called on the client thread before native dot selection and rendering. */
    void refresh()
    {
        Player local = client.getLocalPlayer();
        if (!config.wildernessPlayerSkulls() || !config.showPlayers()
            || client.getGameState() != GameState.LOGGED_IN || local == null
            || client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 1
            || !WildernessCombatRange.standardWorld(client.getWorldType()))
        {
            clearRange();
            return;
        }
        Widget level = client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL);
        // Read the game's actual Wilderness level, including dungeons. Do not
        // guess it from Y coordinates or from another player's overhead icon.
        String text = level == null ? null : level.getText();
        int combat = local.getCombatLevel();
        if (combat != lastCombat || !Objects.equals(text, lastLevelText))
        {
            lastCombat = combat;
            lastLevelText = text;
            range = WildernessCombatRange.from(combat, text);
        }
    }

    boolean isReplacingOtherPlayers()
    {
        return range != null;
    }

    void setFallbackDot(SpritePixels original, Color desired)
    {
        if (original == fallbackSource && Objects.equals(desired, fallbackColor)) { return; }
        fallbackSource = original;
        fallbackColor = desired;
        fallbackDot = original == null ? null : NativeDotColors.isCustomized(desired, NativeDotColors.PLAYER)
            ? client.createSpritePixels(NativeDotColors.recolor(original.getPixels(), desired),
                original.getWidth(), original.getHeight()).toBufferedImage()
            : original.toBufferedImage();
    }

    void clear()
    {
        clearRange();
        fallbackSource = null;
        fallbackColor = null;
        fallbackDot = null;
    }

    private void clearRange()
    {
        range = null;
        lastLevelText = null;
        lastCombat = -1;
    }

    static boolean isOtherPlayer(Player player, Player local)
    {
        return player != null && local != null && player != local
            && player.getId() != local.getId()
            && !player.isFriend() && !player.isFriendsChatMember() && !player.isClanMember()
            && !(local.getTeam() != 0 && local.getTeam() == player.getTeam());
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        WildernessCombatRange bracket = range;
        if (bracket == null || !config.showPlayers() || !config.wildernessPlayerSkulls()
            || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }
        boolean square = view.isSquare();
        if (square && !view.isPaintable()) { return null; }
        Shape clip = square ? view.clip() : classicClip();
        if (clip == null) { return null; }
        Player local = client.getLocalPlayer();
        if (local == null) { return null; }

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(clip);
            for (Player player : client.getPlayers())
            {
                if (!isOtherPlayer(player, local) || player.getPlayerComposition() == null
                    || player.getLocalLocation() == null)
                {
                    continue;
                }
                net.runelite.api.Point location = square
                    ? view.project(player.getLocalLocation()) : player.getMinimapLocation();
                if (location == null || !clip.contains(location.getX(), location.getY())) { continue; }
                WildernessCombatRange.Marker marker = bracket.marker(player.getCombatLevel());
                BufferedImage image = marker == WildernessCombatRange.Marker.RED ? WildernessSkullIcons.RED
                    : marker == WildernessCombatRange.Marker.WHITE ? WildernessSkullIcons.WHITE : fallbackDot;
                // Unknown combat levels keep the configured dot, never a white
                // skull that could be mistaken for a known out-of-range player.
                if (image != null)
                {
                    g.drawImage(image, location.getX() - image.getWidth() / 2,
                        location.getY() - image.getHeight() / 2, null);
                }
            }
        }
        finally { g.dispose(); }
        return null;
    }

    private Shape classicClip()
    {
        int top = client.getTopLevelInterfaceId();
        int id;
        if (top == InterfaceID.TOPLEVEL_PRE_EOC) { id = InterfaceID.ToplevelPreEoc.MINIMAP; }
        else if (top == InterfaceID.TOPLEVEL_OSRS_STRETCH) { id = InterfaceID.ToplevelOsrsStretch.MINIMAP; }
        else if (top == InterfaceID.TOPLEVEL) { id = InterfaceID.Toplevel.MINIMAP; }
        else { return null; }
        Widget widget = client.getWidget(id);
        if (widget == null || widget.isHidden()) { return null; }
        Rectangle bounds = widget.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) { return null; }
        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap != null && !worldMap.isHidden() && worldMap.getBounds() != null
            && worldMap.getBounds().intersects(bounds)) { return null; }
        // Clip the larger replacement artwork to the circular draw area, so it
        // cannot paint over the surrounding frame or compass.
        return new Ellipse2D.Double(bounds.x + 1, bounds.y + 1,
            Math.max(0, bounds.width - 2), Math.max(0, bounds.height - 2));
    }
}
