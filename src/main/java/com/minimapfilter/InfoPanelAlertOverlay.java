package com.minimapfilter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Persistent notices with close buttons; input uses the existing translated HUD listener. */
@Singleton
final class InfoPanelAlertOverlay extends Overlay
{
    private final Client client;
    private final MinimapFilterConfig config;
    private final InfoPanelService service;
    @Inject InfoPanelAlertOverlay(Client client, MinimapFilterConfig config, InfoPanelService service)
    {
        this.client=client; this.config=config; this.service=service;
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_WIDGETS); setPriority(PRIORITY_HIGHEST);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        InfoPanelReminderInput input = service.prepareReminderInput();
        if (!config.toolCompletionBanner() || client.getGameState()!=GameState.LOGGED_IN)
        { input.suspend(); return null; }
        Graphics2D g=(Graphics2D) graphics.create();
        try
        {
            g.setFont(FontManager.getRunescapeSmallFont());
            List<InfoPanelReminderBanner.Entry> entries = InfoPanelReminderBanner.layout(g.getFontMetrics(),
                client.getCanvasWidth(), client.getCanvasHeight(), service.notices());
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            InfoPanelReminderBanner.paint(g, entries, mouse == null ? null : new java.awt.Point(mouse.getX(), mouse.getY()),
                !client.isMenuOpen() && client.getCanvas() != null && client.getCanvas().isFocusOwner());
            input.painted(client.getCanvasWidth(), client.getCanvasHeight(), entries);
        }
        finally { g.dispose(); }
        return null;
    }
}
