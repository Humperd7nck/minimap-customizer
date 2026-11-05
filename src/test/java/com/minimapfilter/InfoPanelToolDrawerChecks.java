package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Dependency-free tests against the real drawer state/input/layout/painter and shared volume model. */
public final class InfoPanelToolDrawerChecks
{
    private static int checks;
    private static final Canvas CANVAS = new Canvas();
    private static final Rectangle FOOTER = new Rectangle(20, 180, 213, 34);
    private static final Rectangle FREE = new Rectangle(60, 183, 132, 28);
    public static void main(String[] args)
    { System.out.println("Tool drawer checks passed: " + runAll()); }
    static int runAll()
    {
        checks=0;
        geometry(); navigation(); rejectedGestures(); visibility(); sharedAudio(); rendering(); hammerOrb(); wrapText();
        return checks;
    }
    private static void geometry()
    {
        for (int x : new int[]{0,20,500}) for (int width : new int[]{93,110,132,155})
        {
            for (int textWidth : new int[]{0,40,100,132,180})
            {
                int header = InfoPanelToolDrawer.headerHeight(width, textWidth, 16);
                Rectangle footer = new Rectangle(x,180,213,34), free = new Rectangle(x+40,183,width,28);
                InfoPanelToolDrawer.Layout l=new InfoPanelToolDrawer.Layout(footer,free,true,true,textWidth);
                check(header == 0 && !l.stacked, "Info footer never reserves extra height");
                check(free.contains(l.hammer),"hammer inside free space");
                check(free.contains(l.eye),"eye inside free space");
                check(l.hammer.width == 28 && l.hammer.height == 28, "hammer is always a full-size round button");
                check(l.eye.width == 28 && l.eye.height == 28, "eye is always a full-size round button");
                check(footer.contains(l.primaryText),"readout uses existing footer height");
                check(l.primaryText.x >= free.x && l.primaryText.getMaxX() <= free.getMaxX(), "readout stays in free horizontal span");
                check(l.primaryText.height == footer.height - 2, "readout gets full vertical wrap space");
                check(!l.primaryText.intersects(l.hammer),"text and hammer disjoint");
                check(!l.primaryText.intersects(l.eye),"text and eye disjoint");
                check(l.panel.y==footer.y+footer.height && l.panel.width==footer.width,"attached full-width row");
                check(l.reveal().contains(l.panel) && l.reveal().contains(footer),"connected hover union");
                check(l.panel.contains(l.back) && l.panel.contains(l.content),"selected controls inside row");
                check(!l.back.intersects(l.content),"back independent of text/volume");
                for(int i=0;i<l.icons.length;i++)
                {check(l.panel.contains(l.icons[i]),"icon cell inside");if(i>0)check(!l.icons[i].intersects(l.icons[i-1]),"cells disjoint");}
            }
        }
        InfoPanelToolDrawer.Layout closed=new InfoPanelToolDrawer.Layout(FOOTER,FREE,false,true,20);
        check(closed.panel.isEmpty(),"collapsed panel empty");
        check(!closed.reveal().contains(25,220),"collapsed panel has no hover area below");
        InfoPanelToolDrawer.Layout noRoom=new InfoPanelToolDrawer.Layout(FOOTER,FREE,true,false,20);
        check(noRoom.panel.isEmpty() && !noRoom.hammer.isEmpty(),"no-space drawer retains close control");
        InfoPanelToolDrawer.Layout tiny=new InfoPanelToolDrawer.Layout(FOOTER,new Rectangle(60,183,20,28),true,true,20);
        check(tiny.panel.isEmpty() && tiny.hammer.isEmpty(),"tiny layout no invisible controls");
        InfoPanelToolDrawer.Layout disabled=new InfoPanelToolDrawer.Layout(FOOTER,FREE,true,true,200,16,false);
        check(disabled.hammer.isEmpty() && disabled.panel.isEmpty(),"disabled Tools button has no hammer or drawer");
        check(!disabled.eye.isEmpty(),"Tracker eye remains when Tools button is disabled");
        check(disabled.primaryText.width < FREE.width && !disabled.primaryText.intersects(disabled.eye),
            "disabled Tools button gives remaining text space to the eye");
        InfoPanelToolDrawer.Layout noTracking=new InfoPanelToolDrawer.Layout(FOOTER,FREE,true,true,200,16,true,false);
        check(!noTracking.hammer.isEmpty() && noTracking.eye.isEmpty(),"disabled Tracking button removes only eye");
        check(noTracking.panel.width==FOOTER.width,"Tools drawer still works without Tracking button");
        check(noTracking.primaryText.width > lWidthWithBoth(),"Tracking button width returns to Info text");
        InfoPanelToolDrawer.Layout noButtons=new InfoPanelToolDrawer.Layout(FOOTER,FREE,true,true,200,16,false,false);
        check(noButtons.hammer.isEmpty() && noButtons.eye.isEmpty() && noButtons.panel.isEmpty(),
            "both footer buttons disabled leave no button hitboxes");
        check(noButtons.primaryText.x==FREE.x && noButtons.primaryText.width==FREE.width,
            "both footer buttons disabled return all free width to Info text");
    }
    private static int lWidthWithBoth()
    { return new InfoPanelToolDrawer.Layout(FOOTER,FREE,false,true,200,16,true,true).primaryText.width; }

    private static void navigation()
    {
        InfoPanelToolDrawer d=new InfoPanelToolDrawer(); InfoPanelToolDrawer.View v=frame(d,true,true);
        check(!d.state().expanded,"starts closed");
        long eyeGeneration = d.state().generation;
        InfoPanelToolDrawer.Action eyeAction = tap(d, center(v.layout.eye));
        check(eyeAction != null && eyeAction.toggleTracking && !eyeAction.openTool
            && !d.state().expanded && d.state().generation == eyeGeneration,
            "Tracker eye requests popup without changing tool state");
        tap(d,center(v.layout.hammer));
        check(d.state().expanded && d.state().selected==null,"hammer opens icon row");
        for(int i=0;i<InfoPanelToolDrawer.TOOLS.length;i++)
        {
            v=frame(d,true,true);InfoPanelToolDrawer.Action iconAction=tap(d,center(v.layout.icons[i]));
            if (InfoPanelToolDrawer.isDirectTool(InfoPanelToolDrawer.TOOLS[i]))
            {
                check(d.state().selected == null && d.state().expanded, "direct tool keeps icons visible");
                check(iconAction != null && iconAction.openTool && iconAction.toggleTool
                    && iconAction.tool == InfoPanelToolDrawer.TOOLS[i], "icon directly requests matching toggle");
                check(d.allows(iconAction.tool), "home authorizes direct tool callbacks");
                long generation = d.state().generation;
                v=frame(d,true,true);InfoPanelToolDrawer.Action again=tap(d,center(v.layout.icons[i]));
                check(again != null && again.toggleTool && again.tool == iconAction.tool, "second icon click toggles same tool");
                check(d.state().selected == null && d.state().generation == generation, "toggle keeps home geometry and generation");
                continue;
            }
            check(d.state().selected==InfoPanelToolDrawer.TOOLS[i],"icon chooses correct tool");
            v=frame(d,true,true);
            if(d.state().selected.isTool())
            {
                InfoPanelToolDrawer.Action a=tap(d,center(v.controls.text));
                boolean toggles = InfoPanelToolDrawer.TOOLS[i] == InfoPanelText.TIMER
                    || InfoPanelToolDrawer.TOOLS[i] == InfoPanelText.ALARM
                    || InfoPanelToolDrawer.TOOLS[i] == InfoPanelText.STOPWATCH;
                check(a!=null&&a.openTool&&a.tool==InfoPanelToolDrawer.TOOLS[i],"readout requests shared popup");
                check(a.toggleTool == toggles,"timer/alarm/stopwatch readout toggles popup");
            }
            else check(tap(d,center(v.layout.content))==null,"volume does not request a popup");
            v=frame(d,true,true);tap(d,center(v.layout.back));
            check(d.state().expanded&&d.state().selected==null,"back returns icons, not collapse");
        }
        v=frame(d,true,true);tap(d,center(v.layout.icons[2]));
        frame(d,false,true);check(d.state().selected==InfoPanelText.STOPWATCH,"hidden preserves selection");
        v=frame(d,true,true);tap(d,center(v.layout.hammer));
        check(!d.state().expanded&&d.state().selected==null,"hammer collapses, clears only view selection");
        v=frame(d,true,true);tap(d,center(v.layout.hammer));
        check(d.state().expanded&&d.state().selected==null,"fresh opening starts at all icons");
        d.reset();check(!d.state().expanded&&d.state().selected==null,"profile/disable reset presentation only");
    }
    private static void rejectedGestures()
    {
        for(String reason:new String[]{"right","middle","shift","consumed","outside","move","releaseOutside","focus","hidden","geometry","generation"})
        {
            InfoPanelToolDrawer d=new InfoPanelToolDrawer();InfoPanelToolDrawer.View v=frame(d,true,true);
            Point p=center(v.layout.hammer);int button=reason.equals("right")?3:reason.equals("middle")?2:1;
            MouseEvent press=event(MouseEvent.MOUSE_PRESSED,p,button,reason.equals("shift")?InputEvent.SHIFT_DOWN_MASK:0);
            if(reason.equals("consumed"))press.consume();
            if(reason.equals("outside"))press=event(MouseEvent.MOUSE_PRESSED,new Point(0,0),1,0);
            boolean captured=d.mousePressed(press);
            if(reason.equals("consumed")||reason.equals("outside"))check(!captured,"foreign/outside press ignored");
            else check(captured&&press.isConsumed(),"visible UI captures gesture");
            if(reason.equals("move"))d.mouseDragged(event(MouseEvent.MOUSE_DRAGGED,new Point(p.x+20,p.y),0,InputEvent.BUTTON1_DOWN_MASK));
            if(reason.equals("focus"))frame(d,true,false);
            if(reason.equals("hidden"))frame(d,false,true);
            if(reason.equals("geometry"))d.publish(new InfoPanelToolDrawer.View(d.state(),new InfoPanelToolDrawer.Layout(
                new Rectangle(21,180,213,34),new Rectangle(61,183,132,28),false,true,20),true),true);
            if(reason.equals("generation"))d.reset();
            Point release=reason.equals("releaseOutside")?new Point(0,0):p;
            MouseEvent up=event(MouseEvent.MOUSE_RELEASED,release,button,reason.equals("shift")?InputEvent.SHIFT_DOWN_MASK:0);
            check(d.mouseReleased(up)==null,reason+" no action");
            if(captured)check(up.isConsumed(),reason+" release does not leak");
            check(!d.state().expanded,reason+" does not open");
            MouseEvent click=event(MouseEvent.MOUSE_CLICKED,release,button,0);
            check(d.mouseClicked(click)==captured,reason+" matching clicked ownership");
        }
        InfoPanelToolDrawer d=new InfoPanelToolDrawer();InfoPanelToolDrawer.View v=frame(d,false,true);
        check(!d.mousePressed(event(MouseEvent.MOUSE_PRESSED,center(v.layout.hammer),1,0)),"hidden hammer does not block game");
        v=frame(d,true,true);tap(d,center(v.layout.hammer));v=frame(d,true,true);
        check(d.mousePressed(event(MouseEvent.MOUSE_PRESSED,new Point(v.layout.panel.x+1,v.layout.panel.y+1),3,0)),"drawer background blocks right-click");
        d.mouseReleased(event(MouseEvent.MOUSE_RELEASED,new Point(0,0),3,0));
        check(d.state().selected==null,"background never selects");
    }
    private static void visibility()
    {
        InfoPanelToolDrawer d=new InfoPanelToolDrawer();InfoPanelToolDrawer.View v=frame(d,true,true);
        tap(d,center(v.layout.hammer));v=frame(d,true,true);tap(d,center(v.layout.icons[0]));v=frame(d,true,true);
        SquareHudFooterVisibility visibility=new SquareHudFooterVisibility();
        Rectangle reveal=v.layout.reveal();Point lower=center(v.layout.content);
        check(!visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,reveal.contains(lower),false,false,0),"shared reveal delay");
        check(visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,reveal.contains(lower),false,false,75),"lower area reveals both");
        check(visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,reveal.contains(80,213),false,false,100),"seam stays revealed");
        check(visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,false,false,false,200),"hide delay starts");
        check(!visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,false,false,false,550),"panels hide together");
        check(d.state().selected==InfoPanelText.TIMER,"hover hide keeps selected tool");
        visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,true,false,false,600);
        check(visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,true,false,false,675),"toolbar alone reveals again");
        check(visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,false,true,false,2000),"held gesture keeps visible");
        check(!visibility.update(SquareHudFooterMode.MOUSEOVER_ONLY,true,false,true,2100),"cover hides both");
    }
    private static void sharedAudio()
    {
        InfoPanelVolume model=new InfoPanelVolume(); int[] actual={65}, writes={0};
        InfoPanelVolume.Backend backend=new InfoPanelVolume.Backend(){public int read(){return actual[0];}public void write(int p){actual[0]=p;writes[0]++;}};
        InfoPanelVolumeBar top=new InfoPanelVolumeBar(model,false), bottom=new InfoPanelVolumeBar(model,false);
        model.tick(backend,true,true,0);
        top.update(new Rectangle(0,0,120,28),true,true);bottom.update(new Rectangle(0,40,170,28),true,true);
        Point p=center(bottom.layout().speaker);
        bottom.mousePressed(event(MouseEvent.MOUSE_PRESSED,p,1,0));bottom.mouseReleased(event(MouseEvent.MOUSE_RELEASED,p,1,0));
        top.update(null,false,false); // An inactive sibling must not discard the active view's command.
        check(model.snapshot().pending,"inactive primary does not cancel drawer volume");
        model.tick(backend,true,true,300);check(actual[0]==0&&writes[0]==1,"one shared mute write");
        bottom.update(new Rectangle(0,40,170,28),true,true);model.choose(25);
        check(actual[0]==0&&model.snapshot().selected==25,"muted staging remains shared and silent");
        top.update(new Rectangle(0,0,120,28),true,true);p=center(top.layout().speaker);
        top.mousePressed(event(MouseEvent.MOUSE_PRESSED,p,1,0));top.mouseReleased(event(MouseEvent.MOUSE_RELEASED,p,1,0));
        bottom.suspend();model.tick(backend,true,true,600);check(actual[0]==25&&writes[0]==2,"other surface unmutes same model");
        top.update(new Rectangle(0,0,120,28),true,true);check(top.displayedPercent()==25,"top sees shared percentage");
        bottom.update(new Rectangle(0,40,170,28),true,true);check(bottom.displayedPercent()==25,"drawer sees shared percentage");
    }
    private static void rendering()
    {
        InfoPanelToolDrawer d=new InfoPanelToolDrawer();InfoPanelToolDrawer.View v=frame(d,true,true);
        tap(d,center(v.layout.hammer));v=frame(d,true,true);
        BufferedImage image=new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try
        {
            String tip=InfoPanelDrawerPainter.paint(g,v,center(v.layout.icons[3]),null,
                mode->new SquareHudInfoText.Content(mode.toString(),"Click"),null,false);
            check(tip.equals("Open Calculator"),"direct icon tooltip explains open");
            check(InfoPanelDrawerPainter.paint(g,v,center(v.layout.icons[3]),null,
                mode->new SquareHudInfoText.Content(mode.toString(),"Click"),null,false,
                InfoPanelText.CALCULATOR).equals("Close Calculator"), "open direct icon tooltip explains close");
            check(InfoPanelDrawerPainter.paint(g,v,center(v.layout.icons[4]),null,
                mode->new SquareHudInfoText.Content(mode.toString(),"Click"),null,false,
                InfoPanelText.NOTES).equals("Close Notes / Checklist"), "notes close tooltip");
            check(image.getRGB(25,220)!=0,"drawer background painted");
            tap(d,center(v.layout.icons[0]));v=frame(d,true,true);
            check(InfoPanelDrawerPainter.paint(g,v,center(v.layout.back),null,
                mode->new SquareHudInfoText.Content("Timer 00:05","Click for controls"),null,false).equals("Back to tools"),"back tooltip");
        }
        finally {g.dispose();}
    }
    private static void hammerOrb()
    {
        Rectangle cell = new Rectangle(10, 10, 28, 28);
        BufferedImage normal = renderOrb(cell, false, false, 1);
        java.util.Set<Integer> palette = new java.util.HashSet<>(java.util.Arrays.asList(
            0xff1b1711, 0xffa46933, 0xffd8a457, 0xff191915, 0xffa4a797, 0xffe7e6cf, 0xff585a51));
        int solidHammerPixels = 0;
        for (int y = 0; y < normal.getHeight(); y++)
        {
            for (int x = 0; x < normal.getWidth(); x++)
            {
                if (palette.contains(normal.getRGB(x, y))) { solidHammerPixels++; }
            }
        }
        check(solidHammerPixels >= 90, "hammer has a substantial full-contrast silhouette");
        check(normal.getRGB(cell.x + 13, cell.y + 8) == 0xffe7e6cf, "crisp steel highlight at its designed pixel");
        check(normal.getRGB(cell.x + 8, cell.y + 19) == 0xffd8a457, "crisp wooden grip highlight at its designed pixel");
        for (boolean hovered : new boolean[]{false, true}) for (boolean selected : new boolean[]{false, true})
        {
            BufferedImage output = renderOrb(cell, hovered, selected, 1);
            check(output.getRGB(10, 10) == 0, "sphere has no square-filled corner");
            check(output.getRGB(24, 11) != 0, "visible bevel at top of sphere");
            boolean contained = true, glyphUnchanged = true;
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            {
                if (!cell.contains(x, y) && output.getRGB(x, y) != 0) { contained = false; }
                if (palette.contains(normal.getRGB(x, y)) && normal.getRGB(x, y) != output.getRGB(x, y))
                { glyphUnchanged = false; }
            }
            check(contained, "orb painting respects unchanged cell bounds");
            check(glyphUnchanged, "hover and selected states do not blur or tint the hammer");
            check(java.util.Arrays.equals(pixels(output), pixels(renderOrb(cell, hovered, selected, 1))),
                "repeated rendering is stable; no asynchronous sprite swap");
            for (int scale : new int[]{2, 3})
            {
                BufferedImage large = renderOrb(cell, hovered, selected, scale);
                boolean exact = true;
                for (int y = 0; y < large.getHeight(); y++) for (int x = 0; x < large.getWidth(); x++)
                {
                    if (large.getRGB(x, y) != output.getRGB(x / scale, y / scale)) { exact = false; }
                }
                check(exact, "integer scale preserves every artwork pixel despite inherited smoothing");
            }
        }
        check(!java.util.Arrays.equals(pixels(normal), pixels(renderOrb(cell, true, false, 1))),
            "hover rim is still visible");
        check(!java.util.Arrays.equals(pixels(normal), pixels(renderOrb(cell, false, true, 1))),
            "expanded rim is still visible");
        // A wider hit cell must center, not stretch, the same 28px artwork.
        BufferedImage roomy = renderOrb(new Rectangle(4, 4, 40, 40), false, false, 1);
        check(java.util.Arrays.equals(pixels(normal), pixels(roomy)), "larger cell centers without upscaling");
        for (Rectangle empty : new Rectangle[]{null, new Rectangle(10, 10, 27, 28),
            new Rectangle(10, 10, 28, 27), new Rectangle(10, 10, 0, 0)})
        {
            BufferedImage output = renderOrb(empty, false, false, 1);
            boolean blank = true;
            for (int pixel : pixels(output)) { if (pixel != 0) { blank = false; } }
            check(blank, "absent or undersized orb cannot paint outside its allocation");
        }
        BufferedImage clipped = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = clipped.createGraphics();
        try
        {
            Rectangle clip = new Rectangle(13, 14, 8, 9);
            g.setClip(clip);
            g.setColor(Color.MAGENTA);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            java.awt.geom.AffineTransform transform = g.getTransform();
            InfoPanelHammerOrb.paint(g, cell, false, false);
            check(g.getColor().equals(Color.MAGENTA), "orb leaves caller color unchanged");
            check(g.getTransform().equals(transform), "orb leaves caller transform unchanged");
            check(g.getClipBounds().equals(clip), "orb leaves caller clip unchanged");
            check(g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION)
                == java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR, "orb isolates its rendering hints");
            boolean contained = true;
            for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++)
            { if (!clip.contains(x, y) && clipped.getRGB(x, y) != 0) { contained = false; } }
            check(contained, "orb obeys parent clip");
        }
        finally { g.dispose(); }
        // Hammer-only rendering is independent of inventory images or cache readiness.
        InfoPanelToolDrawer d = new InfoPanelToolDrawer();
        InfoPanelToolDrawer.View v = frame(d, true, true);
        BufferedImage canvas = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painter = canvas.createGraphics();
        try
        {
            String tip = InfoPanelDrawerPainter.paint(painter, v, center(v.layout.hammer),
                mode -> { throw new AssertionError("Collapsed hammer queried a game item image"); },
                mode -> new SquareHudInfoText.Content("Local Time", ""), null, false);
            check(tip.equals("Open tools"), "hammer renders with its existing tooltip and no native item lookup");
        }
        finally { painter.dispose(); }
        for(int lineHeight:new int[]{14,16,20,24})
        {
            int header=InfoPanelToolDrawer.headerHeight(132,150,lineHeight);
            InfoPanelToolDrawer.Layout layout=new InfoPanelToolDrawer.Layout(
                new Rectangle(20,180,213,34),new Rectangle(60,183,132,28),true,true,150,lineHeight);
            check(header==0 && !layout.stacked,"long text never changes footer height");
            check(layout.primaryText.height==32,"two-line text uses fixed footer vertical space");
            check(layout.hammer.width==28 && layout.hammer.height==28,"same orb size for every line height");
            check(!layout.primaryText.intersects(layout.hammer),"no text/orb collision");
            check(!layout.primaryText.intersects(layout.eye),"no text/eye collision");
        }
    }


    private static void wrapText()
    {
        BufferedImage image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            java.awt.FontMetrics fm = g.getFontMetrics();
            int width = Math.max(fm.stringWidth("Friends +"), fm.stringWidth("Clanmates Online")) + 2;
            String[] lines = SquareHudText.wrapTwoLines("Friends + Clanmates Online", fm, width);
            check(!lines[0].isEmpty() && !lines[1].isEmpty(), "long Info text wraps to two lines");
            check(fm.stringWidth(lines[0]) <= width && fm.stringWidth(lines[1]) <= width, "wrapped lines fit available width");
            String[] shortText = SquareHudText.wrapTwoLines("FPS 144", fm, 200);
            check(shortText[0].equals("FPS 144") || shortText[0].startsWith("FPS"), "short text remains readable");
            int coordWidth = Math.max(fm.stringWidth("2664, 3303"), fm.stringWidth("P: 0")) + 2;
            String[] coords = SquareHudText.wrapTwoLines("2664, 3303 | P: 0", fm, coordWidth);
            check(coords[0].equals("2664, 3303") && coords[1].equals("P: 0"), "Coords + Plane wraps cleanly at separator");
            int xpWidth = fm.stringWidth("390,553,564") + 2;
            String[] xp = SquareHudText.wrapTwoLines("XP: 390,553,564", fm, xpWidth);
            check(xp[0].equals("XP:") && xp[1].equals("390,553,564"), "Total XP wraps label above value");

            InfoPanelToolDrawer.Layout layout = new InfoPanelToolDrawer.Layout(FOOTER, FREE, false, true, 200, fm.getHeight(), false, true);
            check(layout.hammer.isEmpty() && !layout.eye.isEmpty(), "Eye is independent of Tools button");
            InfoPanelToolDrawer.Layout hiddenEye = new InfoPanelToolDrawer.Layout(FOOTER, FREE, false, true, 200, fm.getHeight(), true, false);
            check(hiddenEye.eye.isEmpty() && !hiddenEye.hammer.isEmpty(), "Tracking toggle controls only Eye orb");
            BufferedImage eye = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D eg = eye.createGraphics();
            try { InfoPanelEyeOrb.paint(eg, new Rectangle(20, 20, 28, 28), true); } finally { eg.dispose(); }
            int opaque = 0;
            for (int y = 0; y < eye.getHeight(); y++) for (int x = 0; x < eye.getWidth(); x++)
                if ((eye.getRGB(x, y) >>> 24) != 0) opaque++;
            check(opaque > 250, "Eye orb paints visible pixels");
        }
        finally { g.dispose(); }
    }
    private static BufferedImage renderOrb(Rectangle cell, boolean hover, boolean selected, int scale)
    {
        BufferedImage output = new BufferedImage(60 * scale, 60 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(scale, scale);
            InfoPanelHammerOrb.paint(g, cell, hover, selected);
        }
        finally { g.dispose(); }
        return output;
    }
    private static int[] pixels(BufferedImage image)
    { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }

    static InfoPanelToolDrawer.View frame(InfoPanelToolDrawer d,boolean visible,boolean input)
    {
        InfoPanelToolDrawer.State s=d.state();InfoPanelToolDrawer.View v=new InfoPanelToolDrawer.View(s,
            new InfoPanelToolDrawer.Layout(FOOTER,FREE,s.expanded,true,50),visible);d.publish(v,input);return v;
    }
    static InfoPanelToolDrawer.Action tap(InfoPanelToolDrawer d,Point p)
    {
        MouseEvent down=event(MouseEvent.MOUSE_PRESSED,p,1,0);check(d.mousePressed(down),"tap press captured");
        InfoPanelToolDrawer.Action action=d.mouseReleased(event(MouseEvent.MOUSE_RELEASED,p,1,0));
        check(d.mouseClicked(event(MouseEvent.MOUSE_CLICKED,p,1,0)),"tap click captured");return action;
    }
    static MouseEvent event(int id,Point p,int button,int modifiers)
    {return new MouseEvent(CANVAS,id,System.currentTimeMillis(),modifiers,p.x,p.y,p.x,p.y,1,false,button);}
    static Point center(Rectangle r){return new Point(r.x+r.width/2,r.y+r.height/2);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
