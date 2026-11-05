package com.minimapfilter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single-line completion notices. Painting and hit testing share the same bounds. */
final class InfoPanelReminderBanner
{
    static final class Entry
    {
        final InfoPanelReminders.Notice notice;
        private final Rectangle bounds, text, close;

        Entry(InfoPanelReminders.Notice notice, Rectangle bounds, Rectangle text, Rectangle close)
        {
            this.notice = notice;
            this.bounds = new Rectangle(bounds);
            this.text = new Rectangle(text);
            this.close = new Rectangle(close);
        }

        Rectangle bounds() { return new Rectangle(bounds); }
        Rectangle textBounds() { return new Rectangle(text); }
        Rectangle closeBounds() { return new Rectangle(close); }
    }

    static List<Entry> layout(FontMetrics metrics, int canvasWidth, int canvasHeight,
        List<InfoPanelReminders.Notice> notices)
    {
        int width = Math.min(320, Math.max(0, canvasWidth - 16));
        if (width < 80 || notices.isEmpty()) { return Collections.emptyList(); }
        int height = Math.max(32, metrics.getHeight() + 14);
        int x = (canvasWidth - width) / 2;
        int y = 14;
        List<Entry> entries = new ArrayList<>();
        for (InfoPanelReminders.Notice notice : notices)
        {
            if (y + height > canvasHeight - 4) { break; }
            Rectangle bounds = new Rectangle(x, y, width, height);
            Rectangle close = new Rectangle(x + width - 26, y + (height - 20) / 2, 20, 20);
            Rectangle text = new Rectangle(x + 9, y, Math.max(0, close.x - 6 - (x + 9)), height);
            entries.add(new Entry(notice, bounds, text, close));
            y += height + 5;
        }
        return Collections.unmodifiableList(entries);
    }

    static void paint(Graphics2D graphics, List<Entry> entries, Point mouse, boolean interactive)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            FontMetrics fm = g.getFontMetrics();
            for (Entry entry : entries)
            {
                Rectangle b = entry.bounds, text = entry.text, close = entry.close;
                g.setStroke(new BasicStroke(1f));
                boolean hovered = interactive && mouse != null && close.contains(mouse);
                g.setColor(new Color(28, 25, 20, 235));
                g.fillRoundRect(b.x, b.y, b.width, b.height, 6, 6);
                g.setColor(new Color(206, 167, 85));
                g.drawRoundRect(b.x, b.y, b.width - 1, b.height - 1, 6, 6);
                g.setColor(new Color(255, 234, 188));
                String title = SquareHudText.fit(entry.notice.message, fm, text.width);
                g.drawString(title, text.x + (text.width - fm.stringWidth(title)) / 2,
                    text.y + (text.height - fm.getHeight()) / 2 + fm.getAscent());
                g.setColor(hovered ? new Color(110, 43, 34) : new Color(48, 40, 31));
                g.fillRoundRect(close.x, close.y, close.width, close.height, 4, 4);
                g.setColor(hovered ? new Color(255, 221, 163) : new Color(164, 135, 84));
                g.drawRoundRect(close.x, close.y, close.width - 1, close.height - 1, 4, 4);
                g.setStroke(new BasicStroke(1.5f));
                g.setColor(hovered ? Color.WHITE : new Color(255, 234, 188));
                g.drawLine(close.x + 6, close.y + 6, close.x + 13, close.y + 13);
                g.drawLine(close.x + 13, close.y + 6, close.x + 6, close.y + 13);
            }
        }
        finally { g.dispose(); }
    }

    // Retains the paint-only entry point used by the existing reminder checks.
    static void paint(Graphics2D g, int canvasWidth, List<InfoPanelReminders.Notice> notices)
    { paint(g, layout(g.getFontMetrics(), canvasWidth, Integer.MAX_VALUE, notices), null, false); }

    private InfoPanelReminderBanner() { }
}
