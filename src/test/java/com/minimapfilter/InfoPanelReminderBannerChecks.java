package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/** Uses the actual renderer, input state and reminder model, without a RuneLite client. */
public final class InfoPanelReminderBannerChecks
{
    private int assertions;
    public static void main(String[] args) { System.out.println("Dismissible banner checks passed: " + runAll()); }
    static int runAll()
    {
        InfoPanelReminderBannerChecks test = new InfoPanelReminderBannerChecks();
        test.layoutAndPaint();
        test.closeIndividually();
        test.pressCaptureAndCancellation();
        test.replacementAndReflow();
        test.contextChanges();
        test.noSideEffects();
        return test.assertions;
    }
    private void check(String name, boolean ok) { assertions++; if (!ok) { throw new AssertionError(name); } }

    private void layoutAndPaint()
    {
        Fixture f = new Fixture();
        for (int width : new int[]{96, 213, 320, 765, 1920})
        {
            f.width = width; f.frame();
            check("two rows at width " + width, f.rows.size() == 2);
            Rectangle previous = null;
            for (InfoPanelReminderBanner.Entry row : f.rows)
            {
                Rectangle b = row.bounds(), x = row.closeBounds(), text = row.textBounds();
                check("banner in canvas", new Rectangle(0,0,width,f.height).contains(b));
                check("close inside banner", b.contains(x));
                check("text clear of close", !text.intersects(x));
                check("single-line row", b.height == 32);
                check("close touch area", x.width == 20 && x.height == 20);
                check("rows distinct", previous == null || !previous.intersects(b));
                previous = b;
                Rectangle copy = row.closeBounds(); copy.setBounds(0,0,1,1);
                check("geometry defensive copy", row.closeBounds().equals(x));
            }
        }
        f.width = 95; f.frame(); check("tiny canvas has no hidden controls", f.rows.isEmpty());
        f.width = 500; f.height = 45; f.frame(); check("short canvas has no rows", f.rows.isEmpty());
        f.height = 51; f.frame(); check("fits first row only", f.rows.size() == 1);
        f.height = 180; f.frame();
        f.reminders.test(InfoPanelText.ALARM); f.frame();
        BufferedImage normal = f.paint(null), hover = f.paint(f.point(InfoPanelText.TIMER));
        Rectangle close = f.row(InfoPanelText.TIMER).closeBounds();
        check("hover fill visible", normal.getRGB(close.x+2,close.y+10) != hover.getRGB(close.x+2,close.y+10));
        check("X painted", (normal.getRGB(close.x+9,close.y+9)>>>24) != 0);
    }

    private void closeIndividually()
    {
        Fixture f = new Fixture();
        Point x = f.point(InfoPanelText.TIMER);
        check("close press consumed", f.send(MouseEvent.MOUSE_PRESSED,x,1,0));
        check("press does not dismiss early", f.reminders.notices().size() == 2);
        check("close release consumed", f.send(MouseEvent.MOUSE_RELEASED,x,1,0));
        check("only Timer dismissed", f.reminders.notice(InfoPanelText.TIMER)==null && f.reminders.notice(InfoPanelText.ALARM)!=null);
        f.frame();
        check("post-dismiss click consumed", f.send(MouseEvent.MOUSE_CLICKED,x,1,0));
        check("reflow leaves alarm", f.reminders.notices().size()==1);
        check("next canvas click untouched", !f.send(MouseEvent.MOUSE_PRESSED,new Point(10,120),1,0));
        f.tap(f.point(InfoPanelText.ALARM));
        check("alarm closes independently", f.reminders.notices().isEmpty());
        f.frame(); check("no stale X after dismissal", !f.send(MouseEvent.MOUSE_PRESSED,x,1,0));
    }

    private void pressCaptureAndCancellation()
    {
        Fixture f = new Fixture(); Point x = f.point(InfoPanelText.TIMER), outside = new Point(10,140);
        check("body keeps old pass-through", !f.send(MouseEvent.MOUSE_PRESSED,new Point(245,25),1,0));
        check("outside release cannot close", !f.send(MouseEvent.MOUSE_RELEASED,x,1,0));
        check("outside-start drag cannot acquire X", !f.send(MouseEvent.MOUSE_DRAGGED,x,0,InputEvent.BUTTON1_DOWN_MASK));
        f.send(MouseEvent.MOUSE_PRESSED,x,1,0);
        check("drag captured outside banner", f.send(MouseEvent.MOUSE_DRAGGED,outside,0,InputEvent.BUTTON1_DOWN_MASK));
        check("outside release captured", f.send(MouseEvent.MOUSE_RELEASED,outside,1,0));
        check("outside release cancels close", f.reminders.notices().size()==2);
        check("cancelled click captured", f.send(MouseEvent.MOUSE_CLICKED,outside,1,0));
        for (int button : new int[]{2,3})
        {
            check("nonprimary X press captured", f.send(MouseEvent.MOUSE_PRESSED,x,button,0));
            check("nonprimary X release captured", f.send(MouseEvent.MOUSE_RELEASED,x,button,0));
            check("nonprimary X click captured", f.send(MouseEvent.MOUSE_CLICKED,x,button,0));
            check("nonprimary doesn't dismiss", f.reminders.notices().size()==2);
        }
        for (int modifiers : new int[]{InputEvent.ALT_DOWN_MASK,InputEvent.SHIFT_DOWN_MASK,InputEvent.CTRL_DOWN_MASK,InputEvent.META_DOWN_MASK})
        {
            check("modified press captured", f.send(MouseEvent.MOUSE_PRESSED,x,1,modifiers));
            f.send(MouseEvent.MOUSE_RELEASED,x,1,modifiers); f.send(MouseEvent.MOUSE_CLICKED,x,1,modifiers);
            check("modified click doesn't close", f.reminders.notices().size()==2);
        }
        MouseEvent used=f.event(MouseEvent.MOUSE_PRESSED,x,1,0); used.consume();
        check("respect earlier listener", !f.input.mousePressed(used));
        check("unowned consumed release ignored", !f.send(MouseEvent.MOUSE_RELEASED,x,1,0));
        f.send(MouseEvent.MOUSE_PRESSED,x,1,0);
        MouseEvent release=f.event(MouseEvent.MOUSE_RELEASED,x,1,0); release.consume();
        check("earlier-consumed release drained", f.input.mouseReleased(release));
        check("earlier-consumed release doesn't close", f.reminders.notices().size()==2);
        f.send(MouseEvent.MOUSE_CLICKED,x,1,0);
        f.send(MouseEvent.MOUSE_PRESSED,x,1,0); f.send(MouseEvent.MOUSE_PRESSED,x,3,0);
        check("chord first release consumed", f.send(MouseEvent.MOUSE_RELEASED,x,1,0));
        check("chord second release consumed", f.send(MouseEvent.MOUSE_RELEASED,x,3,0));
        check("chord inert", f.reminders.notices().size()==2);
    }

    private void replacementAndReflow()
    {
        Fixture f=new Fixture(); Point timer=f.point(InfoPanelText.TIMER);
        InfoPanelReminders.Notice old=f.reminders.notice(InfoPanelText.TIMER);
        f.send(MouseEvent.MOUSE_PRESSED,timer,1,0);
        f.reminders.test(InfoPanelText.TIMER); f.frame();
        check("replacement tail consumed", f.send(MouseEvent.MOUSE_RELEASED,timer,1,0));
        check("new Timer not dismissed", f.reminders.notice(InfoPanelText.TIMER)!=old);
        f.reminders.dismiss(old); check("atomic exact dismiss guards replacement", f.reminders.notice(InfoPanelText.TIMER)!=null);
        f.send(MouseEvent.MOUSE_CLICKED,timer,1,0);
        Point alarm=f.point(InfoPanelText.ALARM);
        f.send(MouseEvent.MOUSE_PRESSED,alarm,1,0);
        f.reminders.dismiss(InfoPanelText.TIMER); f.frame();
        Point moved=f.point(InfoPanelText.ALARM);
        check("reflow changed coordinates", !alarm.equals(moved));
        check("reflow release consumed", f.send(MouseEvent.MOUSE_RELEASED,moved,1,0));
        check("moved row not accidentally dismissed", f.reminders.notice(InfoPanelText.ALARM)!=null);
        f.send(MouseEvent.MOUSE_CLICKED,moved,1,0); f.tap(moved);
        check("fresh click closes moved row", f.reminders.notices().isEmpty());
    }

    private void contextChanges()
    {
        for (String change : new String[]{"hidden","focus","menu","resize","height","cleared","suspend"})
        {
            Fixture f=new Fixture(); Point x=f.point(InfoPanelText.TIMER);
            f.send(MouseEvent.MOUSE_PRESSED,x,1,0);
            switch(change)
            {
                case "hidden": f.visible=false; break;
                case "focus": case "menu": f.interactive=false; break;
                case "resize": f.width=600; break;
                case "height": f.height=250; break;
                case "cleared": f.reminders.clear(); break;
                case "suspend": f.input.suspend(); break;
                default: throw new AssertionError(change);
            }
            check(change+" release swallowed",f.send(MouseEvent.MOUSE_RELEASED,x,1,0));
            check(change+" click swallowed",f.send(MouseEvent.MOUSE_CLICKED,x,1,0));
            check(change+" cannot close",f.reminders.notices().size()==(change.equals("cleared")?0:2));
        }
        Fixture f=new Fixture(); Point x=f.point(InfoPanelText.TIMER); f.width++;
        check("no old geometry after resize",!f.send(MouseEvent.MOUSE_PRESSED,x,1,0));
        f.frame(); f.visible=false; f.context();
        check("invisible controls not clickable",!f.send(MouseEvent.MOUSE_PRESSED,x,1,0));
    }

    private void noSideEffects()
    {
        Fixture f=new Fixture();
        f.tools.startTimer(30); f.tools.startStopwatch();
        f.reminders.alarm.arm(23,59,InfoPanelAlarm.TimeZone.UTC,java.time.ZoneOffset.UTC,"Next alarm");
        int notifications=f.delivered;
        InfoPanelTools.Snapshot before=f.tools.snapshot();
        f.tap(f.point(InfoPanelText.TIMER)); f.frame(); f.tap(f.point(InfoPanelText.ALARM));
        check("dismiss emits no notification",f.delivered==notifications);
        check("dismiss doesn't cancel timer",f.tools.snapshot().timerRunning==before.timerRunning);
        check("dismiss doesn't cancel alarm",f.reminders.alarm.snapshot().armed);
        f.reminders.test(InfoPanelText.TIMER); f.frame(); f.tap(f.point(InfoPanelText.TIMER));
        f.reminders.poll(); check("dismissed one-shot test not recreated",f.reminders.notices().isEmpty());
    }

    static final class Fixture
    {
        final Canvas canvas=new Canvas();
        final InfoPanelTools tools=new InfoPanelTools();
        int delivered;
        final InfoPanelReminders reminders=new InfoPanelReminders(tools,message -> delivered++);
        final InfoPanelReminderInput input=new InfoPanelReminderInput(reminders::dismiss);
        int width=500,height=180; boolean visible=true,interactive=true;
        List<InfoPanelReminderBanner.Entry> rows;
        Fixture(){reminders.test(InfoPanelText.TIMER);reminders.test(InfoPanelText.ALARM);frame();}
        void context(){input.context(width,height,visible,interactive,reminders.notices());}
        void frame()
        {
            context(); BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
            rows=InfoPanelReminderBanner.layout(g.getFontMetrics(),width,height,reminders.notices());
            InfoPanelReminderBanner.paint(g,rows,null,interactive);g.dispose();input.painted(width,height,rows);
        }
        BufferedImage paint(Point mouse)
        {
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
            InfoPanelReminderBanner.paint(g,rows,mouse,interactive);g.dispose();return image;
        }
        InfoPanelReminderBanner.Entry row(InfoPanelText tool)
        {return rows.stream().filter(r->r.notice.tool==tool).findFirst().orElseThrow(AssertionError::new);}
        Point point(InfoPanelText tool){Rectangle r=row(tool).closeBounds();return new Point(r.x+r.width/2,r.y+r.height/2);}
        MouseEvent event(int id,Point p,int button,int modifiers)
        {return new MouseEvent(canvas,id,1,modifiers,p.x,p.y,1,false,button);}
        boolean send(int id,Point point,int button,int modifiers)
        {
            context();MouseEvent event=event(id,point,button,modifiers);boolean captured;
            switch(id)
            {
                case MouseEvent.MOUSE_PRESSED:captured=input.mousePressed(event);break;
                case MouseEvent.MOUSE_RELEASED:captured=input.mouseReleased(event);break;
                case MouseEvent.MOUSE_CLICKED:captured=input.mouseClicked(event);break;
                case MouseEvent.MOUSE_DRAGGED:captured=input.mouseDragged(event);break;
                default:throw new AssertionError(id);
            }
            if(captured&&!event.isConsumed())throw new AssertionError("captured without consumption");
            return captured;
        }
        void tap(Point p){send(MouseEvent.MOUSE_PRESSED,p,1,0);send(MouseEvent.MOUSE_RELEASED,p,1,0);send(MouseEvent.MOUSE_CLICKED,p,1,0);}
    }
}
