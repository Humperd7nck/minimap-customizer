package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Real mouse input on a standalone canvas; not a RuneLite session. */
public final class InfoPanelReminderBannerWindowChecks
{
    private int checks;
    private final InfoPanelReminderBannerChecks.Fixture f = new InfoPanelReminderBannerChecks.Fixture();
    private final int[] game = {0,0,0,0};
    private final int[] events = {0,0,0,0};
    private int scale=1;
    private Point mouse;
    private JFrame frame;
    private Timer refresh;
    private Robot robot;
    private final Canvas canvas = new Canvas()
    {
        @Override public void paint(Graphics graphics)
        {
            f.frame();
            Graphics2D g=(Graphics2D)graphics.create();
            try
            {
                g.setColor(new Color(43,46,49));g.fillRect(0,0,getWidth(),getHeight());g.scale(scale,scale);
                g.drawImage(f.paint(mouse),0,0,null);
            }
            finally{g.dispose();}
        }
        @Override public void update(Graphics g){paint(g);}
    };

    public static void main(String[] args) throws Exception
    {
        InfoPanelReminderBannerWindowChecks t=new InfoPanelReminderBannerWindowChecks();
        t.run(Path.of(args.length==0?"build/banner-smoke":args[0]));
        System.out.println("Banner real-canvas checks passed: "+t.checks);
    }
    private void check(String name,boolean ok){checks++;if(!ok)throw new AssertionError(name);}
    private void run(Path output) throws Exception
    {
        Files.createDirectories(output);robot=new Robot();robot.setAutoDelay(40);
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                MouseAdapter listener=new MouseAdapter()
                {
                    private boolean send(MouseEvent e,int index)
                    {
                        events[index]++;
                        mouse=new Point(e.getX()/scale,e.getY()/scale);
                        boolean captured=f.send(e.getID(),mouse,e.getButton(),e.getModifiersEx());
                        if(captured)e.consume();else game[index]++;
                        canvas.repaint();return captured;
                    }
                    @Override public void mousePressed(MouseEvent e){send(e,0);}
                    @Override public void mouseReleased(MouseEvent e){send(e,1);}
                    @Override public void mouseClicked(MouseEvent e){send(e,2);}
                    @Override public void mouseDragged(MouseEvent e){send(e,3);}
                    @Override public void mouseMoved(MouseEvent e){mouse=new Point(e.getX()/scale,e.getY()/scale);canvas.repaint();}
                };
                canvas.addMouseListener(listener);canvas.addMouseMotionListener(listener);
                canvas.setPreferredSize(new Dimension(f.width,f.height));
                frame=new JFrame("Completion banner test - standalone canvas");frame.add(canvas);frame.pack();frame.setLocation(60,60);
                frame.setVisible(true);canvas.requestFocusInWindow();
                refresh=new Timer(40,e -> canvas.repaint());refresh.start();
            });
            settle();check("two initial banners",read(() -> f.rows.size()==2));
            move(read(() -> f.point(InfoPanelText.TIMER)));settle();capture(output.resolve("banner-hover.png"));
            tap(read(() -> f.point(InfoPanelText.TIMER)),InputEvent.BUTTON1_DOWN_MASK);settle();
            check("Timer X closes Timer",read(() -> f.reminders.notice(InfoPanelText.TIMER)==null));
            check("Alarm unaffected",read(() -> f.reminders.notice(InfoPanelText.ALARM)!=null));
            check("full X gesture blocked from game",read(() -> game[0]+game[1]+game[2]+game[3]==0));
            tap(read(() -> f.point(InfoPanelText.ALARM)),InputEvent.BUTTON1_DOWN_MASK);settle();
            check("Alarm X closes remaining notice",read(() -> f.reminders.notices().isEmpty()));
            check("reflow didn't leak click",read(() -> game[0]+game[1]+game[2]+game[3]==0));
            tap(new Point(20,130),InputEvent.BUTTON1_DOWN_MASK);settle();
            check("ordinary game press passes",read(() -> game[0]==1));
            check("ordinary game release passes",read(() -> game[1]==1));
            check("ordinary game click passes",read(() -> game[2]==1));
            SwingUtilities.invokeAndWait(() -> f.reminders.test(InfoPanelText.TIMER));settle();
            move(read(() -> f.point(InfoPanelText.TIMER)));press(InputEvent.BUTTON1_DOWN_MASK);
            move(new Point(20,130));release(InputEvent.BUTTON1_DOWN_MASK);settle();
            check("drag-out cancels dismissal",read(() -> f.reminders.notice(InfoPanelText.TIMER)!=null));
            check("drag-out tail isolated",read(() -> game[0]==1&&game[1]==1&&game[2]==1&&game[3]==0));
            for(int button:new int[]{InputEvent.BUTTON3_DOWN_MASK,InputEvent.BUTTON2_DOWN_MASK})
            {
                tap(read(() -> f.point(InfoPanelText.TIMER)),button);settle();
                check("secondary/middle X inert",read(() -> f.reminders.notice(InfoPanelText.TIMER)!=null));
                check("secondary/middle blocked",read(() -> game[0]==1&&game[1]==1&&game[2]==1));
            }
            Point x=read(() -> f.point(InfoPanelText.TIMER));move(x);press(InputEvent.BUTTON1_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> f.reminders.test(InfoPanelText.TIMER));settle();
            release(InputEvent.BUTTON1_DOWN_MASK);settle();
            check("new notice survives old click",read(() -> f.reminders.notice(InfoPanelText.TIMER)!=null));
            check("replacement tail blocked",read(() -> game[0]==1&&game[1]==1&&game[2]==1));
            move(x);press(InputEvent.BUTTON1_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {f.interactive=false;f.context();});
            release(InputEvent.BUTTON1_DOWN_MASK);settle();
            check("context loss cancels",read(() -> f.reminders.notice(InfoPanelText.TIMER)!=null));
            check("context-loss tail blocked",read(() -> game[0]==1&&game[1]==1&&game[2]==1));
            SwingUtilities.invokeAndWait(() ->
            {
                f.interactive=true;scale=2;canvas.setPreferredSize(new Dimension(f.width*2,f.height*2));frame.pack();
                f.reminders.test(InfoPanelText.ALARM);
            });settle();
            move(new Point(10,140));settle();capture(output.resolve("banner-normal.png"));
            tap(read(() -> f.point(InfoPanelText.TIMER)),InputEvent.BUTTON1_DOWN_MASK);settle();
            check("2x translated X closes correct notice",read(() -> f.reminders.notice(InfoPanelText.TIMER)==null));
            check("2x other notice retained",read(() -> f.reminders.notice(InfoPanelText.ALARM)!=null));
            check("2x no game click",read(() -> game[0]==1&&game[1]==1&&game[2]==1));
            tap(read(() -> f.point(InfoPanelText.ALARM)),InputEvent.BUTTON1_DOWN_MASK);settle();
            check("fresh scaled click closes alarm",read(() -> f.reminders.notices().isEmpty()));
        }
        finally
        {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK|InputEvent.BUTTON2_DOWN_MASK|InputEvent.BUTTON3_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {if(refresh!=null)refresh.stop();if(frame!=null)frame.dispose();});
        }
    }
    private static <T>T read(Callable<T> action) throws Exception
    {
        Object[] result=new Object[1];Exception[] error=new Exception[1];
        SwingUtilities.invokeAndWait(() -> {try{result[0]=action.call();}catch(Exception e){error[0]=e;}});
        if(error[0]!=null)throw error[0];
        @SuppressWarnings("unchecked") T value=(T)result[0];return value;
    }
    private void move(Point nativePoint) throws Exception
    {
        Point origin=read(canvas::getLocationOnScreen);int factor=read(() -> scale);
        robot.mouseMove(origin.x+nativePoint.x*factor+1,origin.y+nativePoint.y*factor);
        robot.mouseMove(origin.x+nativePoint.x*factor,origin.y+nativePoint.y*factor);
        await(() -> nativePoint.equals(mouse), "mouse movement delivery");
    }
    private void press(int mask) throws Exception
    {int before=read(() -> events[0]);robot.mousePress(mask);await(() -> events[0]>before,"mouse press delivery");}
    private void release(int mask) throws Exception
    {int before=read(() -> events[1]);robot.mouseRelease(mask);await(() -> events[1]>before,"mouse release delivery");}
    private void tap(Point p,int mask) throws Exception{move(p);press(mask);release(mask);}
    private static void await(Callable<Boolean> condition,String reason) throws Exception
    {
        long end=System.nanoTime()+3_000_000_000L;
        while(!read(condition))
        {if(System.nanoTime()>end)throw new AssertionError("Timed out: "+reason);Thread.sleep(20);}
    }
    private void settle() throws Exception{robot.delay(200);SwingUtilities.invokeAndWait(() -> {});}
    private void capture(Path path) throws Exception
    {
        BufferedImage image=read(() ->
        {
            BufferedImage result=new BufferedImage(canvas.getWidth(),canvas.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=result.createGraphics();canvas.paint(g);g.dispose();return result;
        });
        ImageIO.write(image,"png",path.toFile());
    }
}
