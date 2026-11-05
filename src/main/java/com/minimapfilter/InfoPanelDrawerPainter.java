package com.minimapfilter;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Function;

/** Native-pixel drawer rendering. Tool images are runtime sprites; the hammer orb is original artwork. */
final class InfoPanelDrawerPainter
{
    interface Images { BufferedImage image(InfoPanelText mode); }
    private static final Color EDGE = new Color(30, 26, 21), TRIM = new Color(112, 101, 79);
    private static final Color BACKGROUND = new Color(77, 71, 52), HOVER = new Color(104, 94, 66);
    private static final Color TEXT = new Color(255, 237, 173), GOLD = new Color(244, 208, 102);

    static String paint(Graphics2D graphics, InfoPanelToolDrawer.View view, Point mouse,
        Images icons, Function<InfoPanelText, SquareHudInfoText.Content> content,
        InfoPanelVolumeBar volume, boolean muted)
    { return paint(graphics, view, mouse, icons, content, volume, muted, null); }

    static String paint(Graphics2D graphics, InfoPanelToolDrawer.View view, Point mouse,
        Images icons, Function<InfoPanelText, SquareHudInfoText.Content> content,
        InfoPanelVolumeBar volume, boolean muted, InfoPanelText openPopup)
    {
        if (view == null || !view.visible) { return ""; }
        Graphics2D g = (Graphics2D) graphics.create();
        String tip = "";
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Rectangle hammer = view.layout.hammer;
            if (!hammer.isEmpty())
            {
                boolean over = mouse != null && hammer.contains(mouse);
                InfoPanelHammerOrb.paint(g, hammer, over, view.state.expanded);
                if (over) { tip = view.state.expanded ? "Close tools" : "Open tools"; }
            }
            Rectangle row = view.layout.panel;
            if (row.isEmpty()) { return tip; }
            g.setColor(BACKGROUND); g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(EDGE); g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
            g.setColor(TRIM); g.drawRect(row.x + 1, row.y + 1, row.width - 3, row.height - 3);
            if (view.state.selected == null)
            {
                for (int i = 0; i < InfoPanelToolDrawer.TOOLS.length; i++)
                {
                    InfoPanelText mode = InfoPanelToolDrawer.TOOLS[i];
                    Rectangle rect = view.layout.icons[i];
                    boolean over = mouse != null && rect.contains(mouse);
                    boolean direct = InfoPanelToolDrawer.isDirectTool(mode);
                    button(g, rect, over, direct && openPopup == mode);
                    BufferedImage image = icons == null ? null : icons.image(mode);
                    if (image != null) { image(g, rect, image, 1); }
                    else { fallback(g, rect, mode, muted); }
                    if (over)
                    {
                        tip = direct ? (openPopup == mode ? "Close " : "Open ") + mode
                            : InfoPanelFormatting.toolHover(mode);
                    }
                }
            }
            else
            {
                Rectangle back = view.layout.back;
                boolean over = mouse != null && back.contains(mouse);
                button(g, back, over, false);
                int cx = back.x + back.width / 2, cy = back.y + back.height / 2;
                Polygon arrow = new Polygon(new int[]{cx-7,cx,cx,cx+7,cx+7,cx,cx},
                    new int[]{cy,cy-7,cy-3,cy-3,cy+3,cy+3,cy+7},7);
                g.setColor(EDGE); g.translate(1,1); g.fillPolygon(arrow); g.translate(-1,-1);
                g.setColor(over ? TEXT : GOLD); g.fillPolygon(arrow);
                if (over) { tip = "Back to tools"; }
                if (view.state.selected == InfoPanelText.VOLUME)
                {
                    if (volume != null)
                    {
                        volume.paint(g, mouse);
                        String volumeTip = volume.tooltip(mouse);
                        if (!volumeTip.isEmpty()) { tip = volumeTip; }
                    }
                }
                else
                {
                    SquareHudInfoText.Content c = content.apply(view.state.selected);
                    for (InfoPanelQuickControls.Button control : view.controls.buttons)
                    {
                        boolean controlHover = mouse != null && control.bounds.contains(mouse);
                        transportButton(g, control, controlHover);
                        if (controlHover) { tip = control.request.tooltip; }
                    }
                    Rectangle text = view.controls.text;
                    boolean textHover = mouse != null && text.contains(mouse);
                    String label = g.getFontMetrics().stringWidth(c.text) <= text.width - 4 ? c.text : c.compactText;
                    label = SquareHudText.fit(label, g.getFontMetrics(), text.width - 4);
                    centered(g, text, label, textHover ? GOLD : TEXT);
                    if (textHover) { tip = c.tooltip; }
                }
            }
        }
        finally { g.dispose(); }
        return tip;
    }
    /** All transport buttons are square; the separate hammer orb is deliberately unchanged. */
    private static void transportButton(Graphics2D graphics, InfoPanelQuickControls.Button button, boolean hover)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            Rectangle r = button.bounds;
            g.clip(r);
            boolean enabled = button.request.enabled;
            g.setColor(enabled && hover ? HOVER : new Color(49, 46, 36));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(EDGE); g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.setColor(enabled && hover ? GOLD : TRIM);
            g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
            int x = r.x + r.width / 2, y = r.y + r.height / 2;
            g.setColor(enabled ? (hover ? TEXT : GOLD) : new Color(112, 107, 91));
            switch (button.request.command.icon)
            {
                case PLAY:
                    g.fillPolygon(new int[]{x - 4, x - 4, x + 5}, new int[]{y - 5, y + 5, y}, 3);
                    break;
                case PAUSE:
                    g.fillRect(x - 5, y - 5, 3, 10); g.fillRect(x + 2, y - 5, 3, 10);
                    break;
                case STOP:
                    g.fillRect(x - 4, y - 4, 9, 9);
                    break;
                case LAP:
                    g.fillRect(x - 5, y - 6, 2, 13);
                    g.fillPolygon(new int[]{x - 3,x + 6,x + 3,x + 6,x - 3},
                        new int[]{y - 6,y - 6,y - 3,y,y},5);
                    break;
                case RESET:
                    g.drawArc(x - 6, y - 6, 12, 12, 45, -285);
                    g.drawArc(x - 5, y - 5, 10, 10, 45, -285);
                    g.fillPolygon(new int[]{x + 4,x - 1,x + 4},new int[]{y - 8,y - 4,y - 2},3);
                    break;
                case CANCEL:
                    g.drawLine(x - 4, y - 5, x + 5, y + 4); g.drawLine(x - 5, y - 4, x + 4, y + 5);
                    g.drawLine(x - 5, y + 4, x + 4, y - 5); g.drawLine(x - 4, y + 5, x + 5, y - 4);
                    break;
                default: break;
            }
        }
        finally { g.dispose(); }
    }
    private static void button(Graphics2D g, Rectangle r, boolean hover, boolean selected)
    {
        if (hover) { g.setColor(HOVER); g.fillRect(r.x, r.y, r.width, r.height); }
        if (hover || selected)
        { g.setColor(selected ? GOLD : TRIM); g.drawRect(r.x, r.y, r.width - 1, r.height - 1); }
    }
    private static void image(Graphics2D g, Rectangle cell, BufferedImage image, int pad)
    {
        double scale = Math.min(1, Math.min((cell.width - 2.0*pad)/image.getWidth(), (cell.height-2.0*pad)/image.getHeight()));
        int w = Math.max(1,(int)Math.round(image.getWidth()*scale)), h = Math.max(1,(int)Math.round(image.getHeight()*scale));
        g.drawImage(image, cell.x + (cell.width-w)/2, cell.y+(cell.height-h)/2,w,h,null);
    }
    static void centered(Graphics2D g, Rectangle r, String text, Color color)
    {
        FontMetrics fm = g.getFontMetrics();
        int x = r.x+(r.width-fm.stringWidth(text))/2, y = r.y+(r.height+fm.getAscent()-fm.getDescent())/2;
        g.setColor(Color.BLACK); g.drawString(text,x+1,y+1); g.setColor(color); g.drawString(text,x,y);
    }
    private static void fallback(Graphics2D graphics, Rectangle cell, InfoPanelText mode, boolean muted)
    {
        Graphics2D g = (Graphics2D)graphics.create();
        try
        {
            g.clip(cell);
            if (mode == InfoPanelText.VOLUME) { InfoPanelVolumeBar.paintSpeaker(g, cell, true, muted, false); return; }
            int x=cell.x+cell.width/2, y=cell.y+cell.height/2;
            if (mode == null)
            {
                g.setColor(EDGE);g.fillRect(x-3,y-4,5,10);g.fillRect(x-7,y-6,14,5);
                g.setColor(new Color(172,116,62));g.fillRect(x-1,y-2,2,8);
                g.setColor(new Color(190,190,177));g.fillRect(x-6,y-5,12,3);
            }
            else if (mode == InfoPanelText.CALCULATOR)
            {
                g.setColor(EDGE);g.fillRect(x-9,y-12,18,24);g.setColor(new Color(173,165,139));g.fillRect(x-8,y-11,16,22);
                g.setColor(new Color(40,63,49));g.fillRect(x-6,y-9,12,5);
                for(int row=0;row<3;row++)for(int col=0;col<3;col++)
                {g.setColor(col==2?new Color(57,107,165):new Color(54,52,46));g.fillRect(x-6+col*4,y-1+row*4,3,3);}
            }
            else if (mode == InfoPanelText.NOTES)
            {
                // Scroll/map fallback matching the Radimus-notes silhouette.
                g.setColor(EDGE);
                g.fillRect(x - 9, y - 8, 18, 17);
                g.fillRect(x - 7, y - 11, 14, 4);
                g.fillRect(x - 7, y + 8, 14, 4);
                g.setColor(new Color(211, 184, 126));
                g.fillRect(x - 8, y - 7, 16, 15);
                g.fillRect(x - 6, y - 10, 12, 3);
                g.fillRect(x - 6, y + 8, 12, 3);
                g.setColor(new Color(122, 86, 49));
                g.drawLine(x - 5, y - 4, x + 5, y - 4);
                g.drawLine(x - 5, y, x + 3, y);
                g.drawLine(x - 4, y + 4, x + 5, y + 4);
            }
            else
            {
                g.setColor(EDGE);g.fillOval(x-10,y-9,20,20);g.fillRect(x-3,y-12,6,4);
                g.setColor(mode==InfoPanelText.ALARM?new Color(192,65,49):GOLD);g.fillOval(x-8,y-7,16,16);
                g.setColor(TEXT);g.fillOval(x-6,y-5,12,12);g.setColor(EDGE);g.drawLine(x,y+1,x,y-4);g.drawLine(x,y+1,x+4,y+3);
            }
        }
        finally {g.dispose();}
    }
    private InfoPanelDrawerPainter() { }
}
