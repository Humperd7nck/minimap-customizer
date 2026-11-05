package com.minimapfilter;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual popup windows and OS input on an isolated test canvas, not a RuneLite session. */
public final class InfoPanelDirectToolsWindowChecks
{
    private static Fixture f;
    private static Robot robot;
    private static int checks;
    public static void main(String[] args) throws Exception
    {
        robot = new Robot(); robot.setAutoDelay(25);
        SwingUtilities.invokeAndWait(() -> { f = new Fixture(); f.frame.setVisible(true); f.canvas.requestFocus(); f.tick.start(); });
        try
        {
            await(() -> f.canvas.isFocusOwner(), "initial canvas focus");
            pause(100);
            click(center(f.view.layout.hammer));
            check(f.drawer.state().expanded,"hammer opens");
            check(f.view.layout.hammer.width==28&&f.view.layout.hammer.height==28,"full-size round hammer");
            capture(args,"toolbar-orb.png");
            click(center(f.view.layout.icons[3]));
            await(() -> f.openMode==InfoPanelText.CALCULATOR,"calculator open");
            check(f.drawer.state().selected==null,"calculator stays on icon row");
            check(dialogs()==1,"one calculator window");
            type("7+5"); robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER); pause(150);
            check(f.tools.snapshot().answer.toPlainString().equals("12"),"calculator controls work immediately");
            click(center(f.view.layout.icons[3]));
            await(() -> f.openMode==null,"same icon closes");
            check(dialogs()==0,"second click closes without another popup");
            check(f.tools.snapshot().answer.toPlainString().equals("12"),"closing preserves answer");
            click(center(f.view.layout.icons[3]));await(() -> f.openMode==InfoPanelText.CALCULATOR,"reopen calculator");
            check(field("calculator-input").getText().equals("7+5"),"expression survives reopen");
            click(center(f.view.layout.icons[4]));await(() -> f.openMode==InfoPanelText.NOTES,"switch to notes");
            check(dialogs()==1,"switch replaces popup, no duplicate windows");
            check(f.drawer.state().selected==null,"notes stays on icon row");
            type("Keep me");pause(200);
            check(f.notebook.snapshot().notes.equals("Keep me"),"notes editor accepts input");
            capture(args,"notes-direct.png");
            click(center(f.view.layout.icons[4]));await(() -> f.openMode==null,"notes same icon closes");
            check(f.notebook.snapshot().notes.equals("Keep me"),"notes data preserved on close");
            click(center(f.view.layout.icons[4]));await(() -> f.openMode==InfoPanelText.NOTES,"notes reopens");
            check(f.notebook.snapshot().notes.equals("Keep me"),"same notebook reused");
            robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);pause(150);
            check(f.openMode==null,"Escape clears toggle state");
            click(center(f.view.layout.icons[3]));await(() -> f.openMode==InfoPanelText.CALCULATOR,"calculator opens after escape");
            int before = f.gameClicks;
            click(new Point(560,70));pause(150);
            check(f.openMode==null,"outside canvas dismisses popup");
            check(f.gameClicks==before,"dismissal click does not reach game");
            click(center(f.view.layout.icons[3]));await(() -> f.openMode==InfoPanelText.CALCULATOR,"calculator opens after outside dismiss");
            SwingUtilities.invokeAndWait(() -> { f.mode=SquareHudFooterMode.MOUSEOVER_ONLY; });
            move(new Point(560,70));pause(600);
            check(!f.shown,"both bars hide while popup remains open");
            check(f.openMode==InfoPanelText.CALCULATOR,"hover hide does not close the editor");
            move(center(f.view.layout.icons[3]));pause(180);
            check(f.shown,"toolbar hover reveals both again");
            click(center(f.view.layout.icons[3]));await(() -> f.openMode==null,"revealed icon closes same window");
            click(center(f.view.layout.icons[3]));await(() -> f.openMode==InfoPanelText.CALCULATOR,"direct open after hover");
            JFrame[] other={null};
            SwingUtilities.invokeAndWait(() -> { other[0]=new JFrame("Other window focus test");other[0].setSize(160,90);other[0].setLocation(720,60);other[0].setVisible(true);other[0].toFront();other[0].requestFocus(); });
            try
            {
                await(() -> f.openMode==null,"switch away closes popup");
                pause(150);
                check(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow()==other[0],"close on deactivation does not steal focus");
                check(dialogs()==0,"deactivated popup disposed");
            }
            finally { SwingUtilities.invokeAndWait(() -> { other[0].dispose();f.frame.toFront();f.canvas.requestFocus(); }); }
            await(() -> f.canvas.isFocusOwner(),"canvas restored after other window test");
            move(center(f.view.layout.icons[3]));pause(180);click(center(f.view.layout.icons[3]));await(() -> f.openMode==InfoPanelText.CALCULATOR,"reopen after focus transfer");
            move(center(f.view.layout.hammer));pause(180);click(center(f.view.layout.hammer));pause(150);
            check(!f.drawer.state().expanded&&f.openMode==null,"collapse closes drawer popup");
            check(f.tools.snapshot().answer.toPlainString().equals("12"),"collapse retains calculation");
            check(f.notebook.snapshot().notes.equals("Keep me"),"collapse retains notes");
            check(f.mainText.equals("Local Time"),"main display unchanged");
            check(f.gameClicks==before,"toolbar and close clicks do not leak");
            System.out.println("Direct-tool real-window checks passed: "+checks);
        }
        finally { SwingUtilities.invokeAndWait(() -> { f.tick.stop(); f.popup.close(); f.frame.dispose(); }); }
    }
    private static int dialogs() throws Exception
    {
        int[] n={0}; SwingUtilities.invokeAndWait(() -> { for(Window w:Window.getWindows()) if(w instanceof JDialog && w.isShowing())n[0]++; }); return n[0];
    }
    private static JTextField field(String name) throws Exception
    {
        JTextField[] result={null};SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof JDialog){Component c=find(w,name);if(c instanceof JTextField)result[0]=(JTextField)c;}});
        if(result[0]==null)throw new AssertionError("Missing field "+name);return result[0];
    }
    private static Component find(Container root,String name)
    {
        for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container){Component nested=find((Container)c,name);if(nested!=null)return nested;}}return null;
    }
    private static void type(String value)
    {
        for(char c:value.toCharArray())
        {
            if(c=='+'){robot.keyPress(KeyEvent.VK_SHIFT);robot.keyPress(KeyEvent.VK_EQUALS);robot.keyRelease(KeyEvent.VK_EQUALS);robot.keyRelease(KeyEvent.VK_SHIFT);continue;}
            boolean upper=Character.isUpperCase(c);int key=KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(c));
            if(upper)robot.keyPress(KeyEvent.VK_SHIFT);robot.keyPress(key);robot.keyRelease(key);if(upper)robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }
    private static void capture(String[] args,String filename)throws Exception
    {
        if(args.length==0)return;
        SwingUtilities.invokeAndWait(()->{try{BufferedImage image=new BufferedImage(600,650,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();f.canvas.paint(g);g.dispose();File dir=new File(args[0]);dir.mkdirs();ImageIO.write(image,"png",new File(dir,filename));}catch(Exception ex){throw new RuntimeException(ex);}});
    }
    private static void await(BooleanSupplier condition,String label)throws Exception
    {long end=System.nanoTime()+3_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end)pause(15);if(!condition.getAsBoolean())throw new AssertionError(label);}
    private static void pause(int ms)throws Exception{Thread.sleep(ms);SwingUtilities.invokeAndWait(()->{});}
    private static Point center(Rectangle r){return new Point(r.x+r.width/2,r.y+r.height/2);}
    private static void move(Point point)throws Exception
    {Point p=f.canvas.getLocationOnScreen();robot.mouseMove(p.x+point.x,p.y+point.y);await(()->point.equals(f.mouse),"mouse delivered");pause(75);}
    private static void click(Point point)throws Exception
    {
        move(point);int presses=f.presses;robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        await(()->f.presses>presses,"press delivered");
        int releases=f.releases;robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        await(()->f.releases>releases,"release delivered");pause(100);
    }
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static final class Fixture
    {
        final JFrame frame=new JFrame("Direct tool launchers — standalone test");
        final InfoPanelToolDrawer drawer=new InfoPanelToolDrawer();
        final InfoPanelTools tools=new InfoPanelTools();
        final InfoPanelNotebook notebook=InfoPanelNotebook.memory();
        final InfoPanelReminders reminders=new InfoPanelReminders(tools,m->{});
        final InfoPanelPopup popup=new InfoPanelPopup(tools,()->openMode=null,()->drawer.allows(this.openMode),reminders,()->notebook);
        final SquareHudFooterVisibility visibility=new SquareHudFooterVisibility();
        final String mainText="Local Time";
        volatile InfoPanelText openMode;
        volatile InfoPanelToolDrawer.View view;
        volatile Point mouse=new Point(-1,-1);
        volatile boolean shown;
        volatile int gameClicks, presses, releases;
        int dismissButton;
        volatile SquareHudFooterMode mode=SquareHudFooterMode.ALWAYS_VISIBLE;
        final Canvas canvas=new Canvas(){@Override public void paint(Graphics graphics)
        {
            Graphics2D g=(Graphics2D)graphics.create();try
            {
                g.setColor(new Color(24,27,24));g.fillRect(0,0,getWidth(),getHeight());
                g.setFont(new Font(Font.DIALOG,Font.PLAIN,11));
                g.setColor(Color.LIGHT_GRAY);g.drawString("Standalone test canvas; fallback artwork",20,30);
                if(shown){Rectangle r=view.layout.footer;g.setColor(new Color(77,71,52));g.fillRect(r.x,r.y,r.width,r.height);
                    InfoPanelDrawerPainter.centered(g,view.layout.primaryText,mainText,Color.WHITE);
                    InfoPanelDrawerPainter.paint(g,view,mouse,null,t->new SquareHudInfoText.Content(t.toString(),"Click"),null,false,openMode);}
            }finally{g.dispose();}
        }};
        final Timer tick=new Timer(20,e->update());
        Fixture()
        {
            tools.activate();reminders.activate();frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            canvas.setPreferredSize(new Dimension(600,650));frame.add(canvas);frame.pack();frame.setLocation(60,40);
            MouseAdapter mouseListener=new MouseAdapter()
            {
                @Override public void mouseMoved(MouseEvent e){mouse=e.getPoint();drawer.mouseMoved(e);update();}
                @Override public void mouseExited(MouseEvent e){mouse=new Point(-1,-1);update();}
                @Override public void mousePressed(MouseEvent e)
                {
                    presses++;update();boolean owned=drawer.owns(e.getPoint());
                    if(!owned&&popup.isShowing()){dismissButton=e.getButton();popup.close();e.consume();return;}
                    drawer.mousePressed(e);
                }
                @Override public void mouseDragged(MouseEvent e){mouse=e.getPoint();drawer.mouseDragged(e);update();}
                @Override public void mouseReleased(MouseEvent e)
                {
                    releases++;
                    if(dismissButton==e.getButton()){e.consume();return;}
                    InfoPanelToolDrawer.Action action=drawer.mouseReleased(e);
                    if(action!=null)
                    {
                        if(action.toggleTool && popup.isShowing() && openMode==action.tool)popup.close();
                        else if(action.openTool){popup.open(action.tool,canvas,e.getLocationOnScreen());openMode=popup.isShowing()?action.tool:null;}
                        else if(popup.isShowing())popup.close();
                    }
                    if(!e.isConsumed())gameClicks++;
                    update();
                }
                @Override public void mouseClicked(MouseEvent e)
                {if(dismissButton==e.getButton()){dismissButton=0;e.consume();return;}drawer.mouseClicked(e);}
            };
            canvas.addMouseListener(mouseListener);canvas.addMouseMotionListener(mouseListener);update();
        }
        void update()
        {
            InfoPanelToolDrawer.State state=drawer.state();
            InfoPanelToolDrawer.Layout layout=new InfoPanelToolDrawer.Layout(new Rectangle(20,80,213,34),new Rectangle(60,83,132,28),state.expanded,true,60);
            shown=visibility.update(mode,layout.reveal().contains(mouse),drawer.isHolding(),false,System.nanoTime()/1_000_000L);
            view=new InfoPanelToolDrawer.View(state,layout,shown);drawer.publish(view,canvas.isFocusOwner()||popup.isShowing());canvas.repaint();
        }
    }
}
