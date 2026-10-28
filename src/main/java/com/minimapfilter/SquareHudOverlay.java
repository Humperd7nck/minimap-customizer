package com.minimapfilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** Paints the frame and resources over the native, still-interactive widgets. */
@Singleton
final class SquareHudOverlay extends Overlay
{
    private static final Color EDGE = new Color(30, 26, 21);
    private static final Color TRIM = new Color(112, 101, 79);
    private static final Color EMPTY = new Color(37, 34, 29);
    private static final Color TEXT = new Color(255, 237, 173);
    private static final Color ACTIVE = new Color(244, 208, 102);
    private static final Color[] COLORS = {
        new Color(168, 50, 40), new Color(65, 121, 168),
        new Color(192, 155, 47), new Color(57, 137, 79)
    };
    private static final Color[] HEALTH = {
        COLORS[0], new Color(29, 146, 24), new Color(23, 67, 47), new Color(153, 139, 44)
    };
    private static final String[] LABELS = {"HP", "Prayer", "Run", "Spec"};
    private final Client client;
    private final SquareHudController hud;
    private final TooltipManager tooltips;
    private final SquareHudIconRenderer resourceIcons = new SquareHudIconRenderer();
    private BufferedImage specialTintSource;
    private BufferedImage specialTint;

    @Inject
    SquareHudOverlay(Client client, SquareHudController hud, TooltipManager tooltips)
    {
        this.client = client;
        this.hud = hud;
        this.tooltips = tooltips;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        SquareHudController.Snapshot view = hud.snapshot();
        if (view == null || !view.paint)
        {
            return null;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setFont(FontManager.getRunescapeSmallFont());
            Rectangle panel = view.geometry.panel();
            Rectangle map = view.geometry.map();
            // Draw only the frame and status column, never an opaque map background.
            g.setColor(EDGE);
            g.fillRect(panel.x, panel.y, panel.width, SquareHudGeometry.BORDER);
            g.fillRect(panel.x, panel.y + panel.height - SquareHudGeometry.BORDER, panel.width, SquareHudGeometry.BORDER);
            g.fillRect(panel.x, panel.y, map.x - panel.x, panel.height);
            g.fillRect(map.x + map.width, panel.y, SquareHudGeometry.BORDER, panel.height);
            g.setColor(TRIM);
            g.drawRect(panel.x + 1, panel.y + 1, panel.width - 3, panel.height - 3);
            g.drawRect(map.x - 1, map.y - 1, map.width + 1, map.height + 1);
            for (int i = 0; i < 4; i++)
            {
                paintBlock(g, view.geometry.block(i), i, view);
            }
            if (view.showCompass) { paintCompass(g, view); }
            if (view.footerVisible) { paintFooter(g, view); }
        }
        finally
        {
            g.dispose();
        }
        return null;
    }

    private void paintBlock(Graphics2D g, Rectangle rect, int index, SquareHudController.Snapshot view)
    {
        g.setColor(EMPTY);
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        Color color = index == 0 ? HEALTH[view.healthState]
            : index == 2 ? SquareHudResourceStyle.runFill(view.selected[2])
            : index == 3 ? SquareHudResourceStyle.specialFill(view.available[3], view.selected[3])
            : COLORS[index];
        int height = SquareHudValues.fillHeight(rect.height - 2,
            SquareHudValues.fraction(view.values[index], view.maxima[index]));
        if (height > 0)
        {
            g.setPaint(new GradientPaint(rect.x, rect.y, SquareHudResourceStyle.fillHighlight(color),
                rect.x + rect.width, rect.y, color));
            g.fillRect(rect.x + 1, rect.y + rect.height - 1 - height, rect.width - 2, height);
        }
        g.setColor(view.selected[index] && view.available[index] ? ACTIVE : TRIM.darker());
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
        BufferedImage icon = view.icons[index];
        if (index == 3 && view.available[3] && view.selected[3] && icon != null)
        {
            if (icon != specialTintSource)
            {
                specialTintSource = icon;
                specialTint = SquareHudResourceStyle.yellowIcon(icon);
            }
            icon = specialTint;
        }
        int iconCenter = rect.x + 11;
        if (icon != null && (index == 1 || index == 2))
        {
            // Prayer and the boots have fine detail that was lost in the old
            // forced 16px reduction. Keep their native pixels where they fit.
            BufferedImage prepared = resourceIcons.prepare(index, icon);
            resourceIcons.draw(g, prepared, iconCenter, rect.y + rect.height / 2);
        }
        else if (icon != null)
        {
            double scale = Math.min(1.0, 16.0 / Math.max(icon.getWidth(), icon.getHeight()));
            int w = Math.max(1, (int) Math.round(icon.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(icon.getHeight() * scale));
            g.drawImage(icon, iconCenter - w / 2, rect.y + (rect.height - h) / 2, w, h, null);
        }
        else
        {
            centeredText(g, index == 0 ? "H" : index == 1 ? "P" : index == 2 ? "R" : "S",
                iconCenter, rect.y + rect.height / 2, TEXT);
        }
        // A hidden native action (such as HP or an unusable special attack) does
        // not make the resource value unavailable.
        String number = Integer.toString(view.values[index]);
        centeredText(g, number, rect.x + 35, rect.y + rect.height / 2, TEXT);
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (!client.isMenuOpen() && mouse != null && rect.contains(mouse.getX(), mouse.getY()))
        {
            String label = index == 3 && view.soulreaper ? "Soul stacks" : LABELS[index];
            String value = view.values[index] + " / " + view.maxima[index];
            tooltips.add(new Tooltip(label + ": " + value));
        }
    }

    private void paintCompass(Graphics2D g, SquareHudController.Snapshot view)
    {
        Rectangle rect = view.geometry.compass();
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        boolean hovered = mouse != null && rect.contains(mouse.getX(), mouse.getY());
        g.setColor(new Color(25, 23, 19, 220));
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(hovered ? ACTIVE : TRIM);
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
        AffineTransform original = g.getTransform();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.rotate(view.northAngle, rect.getCenterX(), rect.getCenterY());
            int cx = (int) rect.getCenterX();
            int cy = (int) rect.getCenterY();
            Polygon north = new Polygon(new int[] {cx, cx + 5, cx - 5},
                new int[] {cy - 8, cy, cy}, 3);
            Polygon south = new Polygon(new int[] {cx - 5, cx + 5, cx},
                new int[] {cy, cy, cy + 8}, 3);
            g.setColor(new Color(255, 230, 147));
            g.fillPolygon(north);
            g.setColor(new Color(191, 119, 66));
            g.fillPolygon(south);
            g.setColor(Color.BLACK);
            g.drawPolygon(north);
            g.drawPolygon(south);
        }
        finally
        {
            g.setTransform(original);
        }
        if (hovered && view.compassClickable)
        {
            tooltips.add(new Tooltip("Face north"));
        }
    }

    private void paintFooter(Graphics2D g, SquareHudController.Snapshot view)
    {
        Rectangle rect = view.geometry.footer();
        g.setColor(EDGE);
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
        g.setColor(TRIM);
        g.drawRect(rect.x + 1, rect.y + 1, rect.width - 3, rect.height - 3);
        Rectangle text = view.geometry.zoneText();
        if (!view.infoText.isEmpty())
        {
            String display = SquareHudText.fit(view.infoText, g.getFontMetrics(), text.width - 4);
            centeredText(g, display, (int) text.getCenterX(), (int) text.getCenterY(), TEXT);
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            if (tooltips != null && !client.isMenuOpen() && mouse != null
                && text.contains(mouse.getX(), mouse.getY()))
            {
                tooltips.add(new Tooltip(view.infoTooltip));
            }
        }
    }

    private static void centeredText(Graphics2D g, String text, int centerX, int centerY, Color color)
    {
        FontMetrics metrics = g.getFontMetrics();
        int x = centerX - metrics.stringWidth(text) / 2;
        int y = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(Color.BLACK);
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}
