package com.minimapfilter;

import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual overlay/service/controller integration for the full RuneLite dependency build. */
public class InfoPanelBannerIntegrationTest
{
    @Test public void realHudListenerClosesExactlyOnePaintedNotice() throws Exception
    {
        try(Fixture f=new Fixture())
        {
            f.reminders.test(InfoPanelText.TIMER);f.reminders.test(InfoPanelText.ALARM);f.paint();
            Point x=f.close(InfoPanelText.TIMER);
            assertTrue(f.send(MouseEvent.MOUSE_PRESSED,x).isConsumed());
            assertEquals(2,f.service.notices().size());
            assertTrue(f.send(MouseEvent.MOUSE_RELEASED,x).isConsumed());
            assertNull(f.reminders.notice(InfoPanelText.TIMER));
            assertNotNull(f.reminders.notice(InfoPanelText.ALARM));
            f.paint();assertTrue(f.send(MouseEvent.MOUSE_CLICKED,x).isConsumed());
            assertNotNull(f.reminders.notice(InfoPanelText.ALARM));
            assertFalse(f.send(MouseEvent.MOUSE_PRESSED,new Point(5,150)).isConsumed());
            assertFalse(f.service.isPopupOpen());
            // Test fixture intentionally has a circular minimap and Zone selected: alerts are independent.
            assertFalse(f.config.enableSquareMinimap());assertEquals(InfoPanelText.ZONE,f.config.infoPanelText());
            f.tap(f.close(InfoPanelText.ALARM));assertTrue(f.service.notices().isEmpty());
        }
    }

    @Test public void hiddenStaleOrInterruptedBannerCannotDismissANewNotice() throws Exception
    {
        for(String reason:new String[]{"setting","menu","focus","size","hop","profile","stop","replacement"})
        {
            try(Fixture f=new Fixture())
            {
                f.reminders.test(InfoPanelText.TIMER);f.paint();Point x=f.close(InfoPanelText.TIMER);
                assertTrue(f.send(MouseEvent.MOUSE_PRESSED,x).isConsumed());
                switch(reason)
                {
                    case "setting":f.enabled=false;break;
                    case "menu":f.menu=true;break;
                    case "focus":f.focused=false;break;
                    case "size":f.width=600;break;
                    case "hop":f.state=GameState.HOPPING;f.service.gameStateChanged(f.state);break;
                    case "profile":f.service.profileChanged();break;
                    case "stop":f.service.stop();break;
                    case "replacement":f.reminders.test(InfoPanelText.TIMER);break;
                    default:throw new AssertionError(reason);
                }
                assertTrue(reason,f.send(MouseEvent.MOUSE_RELEASED,x).isConsumed());
                assertTrue(reason,f.send(MouseEvent.MOUSE_CLICKED,x).isConsumed());
                if(!reason.equals("profile")&&!reason.equals("stop"))
                {assertNotNull(reason,f.reminders.notice(InfoPanelText.TIMER));}
            }
        }
    }

    @Test public void closeDoesNotResetOrStartTimerAndDoesNotOpenPopup() throws Exception
    {
        try(Fixture f=new Fixture())
        {
            InfoPanelTools tools=(InfoPanelTools)field(InfoPanelService.class,"tools").get(f.service);
            tools.startTimer(30);f.reminders.test(InfoPanelText.TIMER);f.paint();
            f.tap(f.close(InfoPanelText.TIMER));
            assertTrue(tools.snapshot().timerRunning);
            assertTrue(f.service.notices().isEmpty());assertFalse(f.service.isPopupOpen());
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        boolean enabled=true,focused=true,menu;
        int width=500,height=180;
        GameState state=GameState.LOGGED_IN;
        final Canvas canvas=new Canvas(){@Override public boolean isFocusOwner(){return focused;}};
        final MinimapFilterConfig config=new MinimapFilterConfig()
        {
            @Override public boolean enableSquareMinimap(){return false;}
            @Override public boolean toolCompletionBanner(){return enabled;}
            @Override public InfoPanelText infoPanelText(){return InfoPanelText.ZONE;}
        };
        final Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(p,m,a) ->
        {
            switch(m.getName())
            {
                case "getCanvas":return canvas;
                case "getCanvasWidth":return width;
                case "getCanvasHeight":return height;
                case "getGameState":return state;
                case "isMenuOpen":return menu;
                case "getMouseCanvasPosition":return new net.runelite.api.Point(0,0);
                default:
                    Class<?> type=m.getReturnType();
                    if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
                    if(type==float.class)return 0F;if(type==double.class)return 0D;return null;
            }
        });
        final InfoPanelService service=new InfoPanelService(client,config,null,null,null);
        final InfoPanelAlertOverlay overlay=new InfoPanelAlertOverlay(client,config,service);
        final SquareHudController controller=new SquareHudController(client,null,config,null,null,() -> 0L);
        final InfoPanelReminders reminders;
        final MouseAdapter listener;
        List<InfoPanelReminderBanner.Entry> rows;
        Fixture() throws Exception
        {
            field(InfoPanelService.class,"running").setBoolean(service,true);
            field(SquareHudController.class,"started").setBoolean(controller,true);
            field(SquareHudController.class,"infoPanel").set(controller,service);
            reminders=(InfoPanelReminders)field(InfoPanelService.class,"reminders").get(service);
            listener=(MouseAdapter)field(SquareHudController.class,"compassMouse").get(controller);
        }
        void paint()
        {
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();overlay.render(g);g.setFont(FontManager.getRunescapeSmallFont());
            rows=InfoPanelReminderBanner.layout(g.getFontMetrics(),width,height,service.notices());g.dispose();
        }
        Point close(InfoPanelText tool)
        {
            Rectangle r=rows.stream().filter(e -> e.notice.tool==tool).findFirst().orElseThrow(AssertionError::new).closeBounds();
            return new Point(r.x+r.width/2,r.y+r.height/2);
        }
        MouseEvent send(int id,Point p)
        {
            MouseEvent event=new MouseEvent(canvas,id,1L,0,p.x,p.y,1,false,MouseEvent.BUTTON1);
            switch(id)
            {
                case MouseEvent.MOUSE_PRESSED:return listener.mousePressed(event);
                case MouseEvent.MOUSE_RELEASED:return listener.mouseReleased(event);
                case MouseEvent.MOUSE_CLICKED:return listener.mouseClicked(event);
                default:throw new AssertionError(id);
            }
        }
        void tap(Point p){send(MouseEvent.MOUSE_PRESSED,p);send(MouseEvent.MOUSE_RELEASED,p);send(MouseEvent.MOUSE_CLICKED,p);}
        @Override public void close() throws Exception{service.stop();SwingUtilities.invokeAndWait(() -> {});}
    }
    private static Field field(Class<?> cls,String name)throws Exception
    {Field field=cls.getDeclaredField(name);field.setAccessible(true);return field;}
}
