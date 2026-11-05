package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

/** Tracking button orb and reusable open/closed eye glyph. */
final class InfoPanelEyeOrb
{
    private static final int SIZE = InfoPanelToolDrawer.EYE_SIZE;

    static void paint(Graphics2D graphics, Rectangle cell, boolean hovered)
    { paint(graphics, cell, hovered, true); }

    static void paint(Graphics2D graphics, Rectangle cell, boolean hovered, boolean open)
    {
        if (cell == null || cell.width < SIZE || cell.height < SIZE) { return; }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(cell);
            int x = cell.x + (cell.width - SIZE) / 2;
            int y = cell.y + (cell.height - SIZE) / 2;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(25, 23, 17));
            g.fillOval(x, y, SIZE - 1, SIZE - 1);
            g.setColor(new Color(107, 100, 77));
            g.fillOval(x + 1, y + 1, SIZE - 3, SIZE - 3);
            g.setColor(hovered ? new Color(209, 193, 143) : new Color(152, 140, 106));
            g.drawArc(x + 2, y + 2, SIZE - 5, SIZE - 5, 40, 180);
            g.setColor(new Color(54, 49, 37));
            g.drawArc(x + 2, y + 2, SIZE - 5, SIZE - 5, 220, 180);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(x + 10, y + 8), 20,
                new float[]{0, .65f, 1f}, new Color[]{
                    hovered ? new Color(126, 116, 84) : new Color(99, 91, 68),
                    new Color(75, 69, 50), new Color(43, 40, 29)}));
            g.fillOval(x + 4, y + 4, SIZE - 9, SIZE - 9);
            g.setColor(new Color(45, 41, 30));
            g.drawOval(x + 3, y + 3, SIZE - 7, SIZE - 7);
            paintGlyph(g, new Rectangle(x + 4, y + 5, SIZE - 8, SIZE - 10), open, hovered, false);
        }
        finally { g.dispose(); }
    }

    static void paintGlyph(Graphics2D graphics, Rectangle cell, boolean open, boolean hovered, boolean muted)
    {
        if (cell == null || cell.width <= 0 || cell.height <= 0) { return; }
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.clip(cell);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int cx = cell.x + cell.width / 2;
            int cy = cell.y + cell.height / 2;
            int halfW = Math.max(5, Math.min(8, cell.width / 2 - 1));
            int halfH = Math.max(3, Math.min(5, cell.height / 2 - 1));
            Color outline = muted ? new Color(74, 72, 66) : new Color(24, 20, 15);
            Color sclera = muted ? new Color(126, 123, 113)
                : hovered ? new Color(255, 239, 185) : new Color(230, 211, 151);
            if (open)
            {
                Polygon eye = new Polygon(
                    new int[]{cx-halfW,cx-halfW/2,cx,cx+halfW/2,cx+halfW,cx+halfW/2,cx,cx-halfW/2},
                    new int[]{cy,cy-halfH+1,cy-halfH,cy-halfH+1,cy,cy+halfH-1,cy+halfH,cy+halfH-1},8);
                g.setColor(outline); g.fillPolygon(eye);
                int innerW = Math.max(3, halfW - 2), innerH = Math.max(2, halfH - 1);
                Polygon inner = new Polygon(
                    new int[]{cx-innerW,cx-innerW/2,cx,cx+innerW/2,cx+innerW,cx+innerW/2,cx,cx-innerW/2},
                    new int[]{cy,cy-innerH+1,cy-innerH,cy-innerH+1,cy,cy+innerH-1,cy+innerH,cy+innerH-1},8);
                g.setColor(sclera); g.fillPolygon(inner);
                g.setColor(muted ? new Color(91, 98, 98) : new Color(56, 98, 106));
                g.fillOval(cx - 3, cy - 3, 7, 7);
                g.setColor(new Color(18, 22, 20));
                g.fillRect(cx - 1, cy - 1, 3, 3);
                if (!muted)
                {
                    g.setColor(new Color(235, 243, 223));
                    g.fillRect(cx - 1, cy - 2, 1, 1);
                }
            }
            else
            {
                g.setColor(outline);
                g.drawArc(cx-halfW, cy-halfH, halfW*2, halfH*2+2, 200, 140);
                g.drawLine(cx-halfW+1, cy+1, cx-halfW-1, cy+3);
                g.drawLine(cx+halfW-1, cy+1, cx+halfW+1, cy+3);
                g.setColor(sclera);
                g.drawArc(cx-halfW+2, cy-halfH+1, Math.max(2, halfW*2-4), Math.max(2, halfH*2), 202, 136);
            }
        }
        finally { g.dispose(); }
    }

    private InfoPanelEyeOrb() { }
}
