package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Real AWT input over a test canvas, not a RuneLite client. Native audio is simulated. */
public final class InfoPanelToolDrawerWindowChecks
{
    private static int checks;
    private static Fixture f;
    private static Robot robot;
    public static void main(String[] args) throws Exception
    {
        robot=new Robot();robot.setAutoDelay(25);
        SwingUtilities.invokeAndWait(()->{f=new Fixture();f.frame.setVisible(true);f.canvas.requestFocusInWindow();f.tick.start();});
        try
        {
            pause(250);
            Point hammer=center(f.view.layout.hammer);
            click(hammer);check(f.drawer.state().expanded,"hammer opens");
            check(f.drawer.state().selected==null,"starts with icons");
            screenshot(args,"tool-icons.png");
            click(center(f.view.layout.icons[0]));check(f.drawer.state().selected==InfoPanelText.TIMER,"select Timer");
            check(f.opens==0,"selecting does not open popup/start timer");
            click(center(f.view.layout.content));check(f.opens==1&&f.lastOpen==InfoPanelText.TIMER,"text opens controls request");
            check(f.mainText.equals("3200, 3420 | P: 2"),"main info selection independent");
            screenshot(args,"selected-timer.png");
            click(center(f.view.layout.back));check(f.drawer.state().expanded&&f.drawer.state().selected==null,"back to tools");
            click(center(f.view.layout.icons[5]));check(f.drawer.state().selected==InfoPanelText.VOLUME,"select inline volume");
            check(f.opens==1,"volume never requests popup");
            click(center(f.volumeBar.layout().speaker));check(f.nativeVolume==0,"drawer speaker mutes");
            Point track=new Point(f.volumeBar.layout().position(30),f.volumeBar.layout().cy);
            click(track);check(f.nativeVolume==0,"slider stages silently while muted");
            click(center(f.volumeBar.layout().speaker));check(Math.abs(f.nativeVolume-30)<=1,"unmute restores staged setting");
            screenshot(args,"selected-volume.png");
            // Real slider drag exits the bar: ownership consumes release, then hover hiding resumes.
            Point thumb=center(f.volumeBar.layout().thumb(f.nativeVolume));
            move(thumb);press();pause(80);
            move(new Point(330,245));pause(100);
            check(f.volumeBar.isHolding(),"slider owns drag outside");
            release();pause(150);
            check(f.nativeVolume==100,"outside drag clamps to 100, actual="+f.nativeVolume+", visible="+f.volumeBar.isVisible()+", holding="+f.volumeBar.isHolding()+", selected="+f.drawer.state().selected);check(f.gameClicks==0,"drag has not clicked game");
            click(center(f.view.layout.back));click(center(f.view.layout.icons[2]));
            SwingUtilities.invokeAndWait(()->{f.mode=SquareHudFooterMode.MOUSEOVER_ONLY;});
            move(center(f.view.layout.content));pause(180);check(f.shown,"toolbar hover shows both");
            move(new Point(100,f.view.layout.panel.y));pause(150);check(f.shown,"seam remains visible");
            move(center(f.view.layout.hammer));pause(150);check(f.shown,"info area keeps both visible");
            move(new Point(340,280));pause(900);check(!f.shown,"leave hides both; mouse="+f.mouse+", drawer hold="+f.drawer.isHolding()+", volume hold="+f.volumeBar.isHolding()+", mode="+f.mode);
            check(f.drawer.state().selected==InfoPanelText.STOPWATCH,"hidden selection preserved");
            move(new Point(140,f.view.layout.footer.y+f.view.layout.footer.height+16));pause(180);check(f.shown,"hidden toolbar region reveals both");
            check(f.drawer.state().selected==InfoPanelText.STOPWATCH,"same tool returns");
            Point back=center(f.view.layout.back);move(back);press();
            move(new Point(340,280));pause(600);check(f.shown,"owned button gesture holds both panels");
            release();pause(900);
            check(!f.shown&&f.drawer.state().selected==InfoPanelText.STOPWATCH,"release outside cancels action then hides");
            check(f.gameClicks==0,"cancelled release stays off game");
            move(new Point(140,f.view.layout.footer.y+f.view.layout.footer.height+16));pause(180);click(center(f.view.layout.hammer));
            check(!f.drawer.state().expanded,"hammer closes toolbar");
            move(new Point(140,f.view.layout.footer.y+f.view.layout.footer.height+16));pause(900);check(!f.shown,"collapsed lower region cannot reveal panels");
            move(new Point(120,190));pause(180);check(f.shown,"closed toolbar retains normal info hover");
            click(center(f.view.layout.hammer));check(f.drawer.state().selected==null,"reopen resets to icons");
            // Neighboring controls are outside every hammer/drawer hitbox.
            click(new Point(36,f.view.layout.hammer.y+14));click(new Point(215,f.view.layout.hammer.y+14));check(f.nativeClicks==2,"XP and World Map targets pass through");
            check(f.gameClicks==0,"no incidental game click");
            System.out.println("Tool drawer real-canvas checks passed: "+checks);
        }
        finally{SwingUtilities.invokeAndWait(()->{f.tick.stop();f.frame.dispose();});}
    }
    private static void screenshot(String[] args,String name) throws Exception
    {
        if(args.length==0)return;
        SwingUtilities.invokeAndWait(()->{
            try{BufferedImage im=new BufferedImage(f.canvas.getWidth(),f.canvas.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=im.createGraphics();f.canvas.paint(g);g.dispose();File dir=new File(args[0]);dir.mkdirs();ImageIO.write(im,"png",new File(dir,name));}
            catch(Exception ex){throw new RuntimeException(ex);}});
    }
    private static void await(java.util.function.BooleanSupplier ready,String label) throws Exception
    {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(!ready.getAsBoolean()&&System.nanoTime()<deadline){pause(15);}
        if(!ready.getAsBoolean())throw new AssertionError("Native event not delivered: "+label);
        SwingUtilities.invokeAndWait(()->{});
    }
    private static void move(Point p)throws Exception
    {Point origin=f.canvas.getLocationOnScreen();robot.mouseMove(origin.x+p.x,origin.y+p.y);await(()->p.equals(f.mouse),"mouse move "+p);}
    private static void press()throws Exception
    {int before=f.presses;robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);await(()->f.presses>before,"press");}
    private static void release()throws Exception
    {int before=f.releases;robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);await(()->f.releases>before,"release");}
    private static void click(Point p)throws Exception{move(p);press();release();pause(100);}
    private static Point center(Rectangle r){return new Point(r.x+r.width/2,r.y+r.height/2);}
    private static void pause(int ms)throws Exception{Thread.sleep(ms);SwingUtilities.invokeAndWait(()->{});}
    private static void check(boolean condition,String what){checks++;if(!condition)throw new AssertionError(what);}

    private static final class Fixture
    {
        final JFrame frame=new JFrame("Minimap tool drawer — standalone input test");
        final InfoPanelToolDrawer drawer=new InfoPanelToolDrawer();
        final InfoPanelVolume volume=new InfoPanelVolume();
        final InfoPanelVolumeBar volumeBar=new InfoPanelVolumeBar(volume,false);
        final SquareHudFooterVisibility visibility=new SquareHudFooterVisibility();
        final String mainText="3200, 3420 | P: 2";
        volatile InfoPanelToolDrawer.View view;
        volatile SquareHudFooterMode mode=SquareHudFooterMode.ALWAYS_VISIBLE;
        volatile boolean shown;
        volatile int opens,gameClicks,nativeClicks,nativeVolume=65;
        volatile InfoPanelText lastOpen;
        volatile Point mouse=new Point(-1,-1);
        volatile int presses,releases;
        final InfoPanelVolume.Backend backend=new InfoPanelVolume.Backend(){public int read(){return nativeVolume;}public void write(int v){nativeVolume=v;}};
        final Canvas canvas=new Canvas(){public void paint(Graphics graphics)
        {
            Graphics2D g=(Graphics2D)graphics.create();try
            {
                g.setColor(new Color(24,27,24));g.fillRect(0,0,getWidth(),getHeight());
                g.setFont(new Font(Font.DIALOG,Font.PLAIN,11));
                g.setColor(Color.LIGHT_GRAY);g.drawString("Standalone canvas; fallback symbols, simulated audio",14,30);
                if(shown)
                {
                    Rectangle r=view.layout.footer;g.setColor(new Color(77,71,52));g.fillRect(r.x,r.y,r.width,r.height);
                    g.setColor(new Color(112,101,79));g.drawRect(r.x,r.y,r.width-1,r.height-1);
                    g.setColor(new Color(255,237,173));g.drawString("XP",27,view.layout.hammer.y+19);g.drawString("Map",207,view.layout.hammer.y+19);
                    InfoPanelDrawerPainter.centered(g,view.layout.primaryText,mainText,new Color(255,237,173));
                    InfoPanelDrawerPainter.paint(g,view,mouse,null,t->new SquareHudInfoText.Content(
                        t==InfoPanelText.TIMER?"Timer 04:32":t==InfoPanelText.STOPWATCH?"Stopwatch 00:00":t.toString(),"Click for controls"),volumeBar,volume.snapshot().muted);
                }
            }finally{g.dispose();}
        }};
        final Timer tick=new Timer(20,e->update());
        Fixture()
        {
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);canvas.setPreferredSize(new Dimension(390,310));frame.add(canvas);frame.pack();frame.setLocation(120,100);
            MouseAdapter listener=new MouseAdapter(){
                public void mouseMoved(MouseEvent e){mouse=e.getPoint();drawer.mouseMoved(e);volumeBar.mouseMoved(e);update();}
                public void mouseExited(MouseEvent e){mouse=new Point(-1,-1);update();}
                public void mousePressed(MouseEvent e){presses++;mouse=e.getPoint();if(!volumeBar.mousePressed(e))drawer.mousePressed(e);}
                public void mouseDragged(MouseEvent e){mouse=e.getPoint();if(!volumeBar.mouseDragged(e))drawer.mouseDragged(e);update();}
                public void mouseReleased(MouseEvent e){releases++;mouse=e.getPoint();if(!volumeBar.mouseReleased(e)){
                    InfoPanelToolDrawer.Action action=drawer.mouseReleased(e);
                    if(action!=null){volume.cancelPending();if(action.openTool){opens++;lastOpen=action.tool;}}}
                    if(!e.isConsumed()){if(view.layout.footer.contains(e.getPoint())&&(e.getX()<60||e.getX()>192))nativeClicks++;else gameClicks++;}
                    update();}
                public void mouseClicked(MouseEvent e){if(!volumeBar.mouseClicked(e))drawer.mouseClicked(e);}
            };canvas.addMouseListener(listener);canvas.addMouseMotionListener(listener);update();
        }
        void update()
        {
            InfoPanelToolDrawer.State state=drawer.state();
            int header=InfoPanelToolDrawer.headerHeight(132,130,16);
            InfoPanelToolDrawer.Layout layout=new InfoPanelToolDrawer.Layout(new Rectangle(20,180,213,34+header),new Rectangle(60,183,132,28+header),state.expanded,true,130);
            shown=visibility.update(mode,layout.reveal().contains(mouse),drawer.isHolding()||volumeBar.isHolding(),false,System.nanoTime()/1_000_000L);
            view=new InfoPanelToolDrawer.View(state,layout,shown);drawer.publish(view,canvas.isFocusOwner());
            boolean volumeVisible=shown&&view.shows(InfoPanelText.VOLUME);
            volumeBar.update(volumeVisible?layout.content:null,volumeVisible,canvas.isFocusOwner());
            volume.tick(backend,volumeVisible,volumeVisible&&canvas.isFocusOwner(),System.nanoTime()/1_000_000L);
            volumeBar.update(volumeVisible?layout.content:null,volumeVisible,canvas.isFocusOwner());canvas.repaint();
        }
    }
}
