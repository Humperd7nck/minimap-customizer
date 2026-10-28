package com.minimapfilter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/** Runs the controller repeatedly against one widget per real interface ID. */
public final class SquareHudControllerChecks
{
    private int cases;

    public static void main(String[] args) throws Exception
    {
        if (args.length > 0 && "--trace".equals(args[0]))
        {
            Fixture f = new Fixture(true);
            for (int frame = 1; frame <= 8; frame++)
            {
                f.hud.update();
                System.out.println("frame=" + frame + " square=" + f.hud.isActive()
                    + " surroundHidden=" + f.ring.widget.isSelfHidden());
            }
            return;
        }
        if (args.length > 0 && "--world-map".equals(args[0]))
        {
            SquareHudControllerChecks checks = new SquareHudControllerChecks();
            checks.worldMapBehavior(true);
            checks.worldMapBehavior(false);
            System.out.println("World Map regression checks passed: " + checks.cases);
            return;
        }
        System.out.println("Square HUD controller checks passed: " + runAll());
    }

    public static int runAll() throws Exception
    {
        SquareHudControllerChecks checks = new SquareHudControllerChecks();
        checks.stableFrames(true);
        checks.stableFrames(false);
        checks.nativeButtonVisibility();
        checks.childOwnership(true);
        checks.childOwnership(false);
        checks.failedCreationRestores();
        checks.worldMapBehavior(true);
        checks.worldMapBehavior(false);
        checks.footerBehavior(true);
        checks.footerBehavior(false);
        checks.compactFooter(true);
        checks.compactFooter(false);
        checks.multiCombatPlacement(true);
        checks.multiCombatPlacement(false);
        checks.wildernessPlacement(true);
        checks.wildernessPlacement(false);
        checks.resourceStates();
        checks.infoPanelAndCompass(true);
        checks.infoPanelAndCompass(false);
        checks.fixedCompass();
        return checks.cases;
    }

    private void check(String name, boolean result)
    {
        cases++;
        if (!result)
        {
            throw new AssertionError(name);
        }
    }

    private void stableFrames(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        String layout = modern ? "modern" : "classic resizable";
        check(layout + " actual surround alias", (f.ring.id & 0xffff) == 32);
        check(layout + " distinct actual map", (f.map.id & 0xffff) == 30 && f.ring != f.map);
        int originalSprite = f.map.widget.getSpriteId();
        for (int frame = 1; frame <= 120; frame++)
        {
            f.hud.update();
            check(layout + " stays square on frame " + frame, f.hud.isActive());
            check(layout + " surround hidden on frame " + frame, f.ring.widget.isSelfHidden());
        }
        check(layout + " no sprite cache churn", f.cacheResets == 1);
        check(layout + " decorative geometry untouched", f.ring.widget.getOriginalWidth() == 211
            && f.ring.widget.getOriginalHeight() == 179);
        check(layout + " map remains visible", !f.map.widget.isHidden());
        Widget replacement = f.inner.widget.getChildren()[0];
        check(layout + " native child created", replacement != null && replacement != f.map.widget);
        check(layout + " native minimap content", replacement.getContentType() == 1338);
        check(layout + " square dimensions", replacement.getWidth() == 152 && replacement.getHeight() == 152);
        check(layout + " matching positions", replacement.getCanvasLocation().getX() == f.map.widget.getCanvasLocation().getX()
            && replacement.getCanvasLocation().getY() == f.map.widget.getCanvasLocation().getY());
        check(layout + " original mask left alone", f.map.widget.getSpriteId() == originalSprite);
        check(layout + " original minimap drawing disabled", f.map.widget.getContentType() == 0
            && f.map.widget.getType() == WidgetType.LAYER);
        check(layout + " no border variant", replacement.getBorderType() == 0 && f.map.widget.getBorderType() == 0);
        check(layout + " only private sprite overridden", f.overrides.size() == 1
            && f.overrides.get(replacement.getSpriteId()) != null
            && !f.overrides.containsKey(1178) && !f.overrides.containsKey(2154) && !f.overrides.containsKey(3513));

        // Simulate game scripts repainting the circular surround each frame.
        for (int frame = 1; frame <= 20; frame++)
        {
            f.ring.widget.setHidden(false);
            f.hud.update();
            check(layout + " script refresh " + frame, f.hud.isActive() && f.ring.widget.isSelfHidden());
        }
        check(layout + " script refresh does not rebuild masks", f.cacheResets == 1);

        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(layout + " Classic clears snapshot", !f.hud.isActive());
        check(layout + " Classic restores surround", !f.ring.widget.isSelfHidden());
        check(layout + " Classic restores map sprite", f.map.widget.getSpriteId() == originalSprite);
        check(layout + " Classic restores width", f.block.widget.getOriginalWidth() == 211);
        check(layout + " Classic releases masks", f.overrides.isEmpty());
        check(layout + " Classic removes dynamic map", f.inner.widget.getChildren().length == 0);
        check(layout + " Classic restores native renderer", f.map.widget.getContentType() == 1338
            && f.map.widget.getType() == WidgetType.GRAPHIC);

        // A frame already hidden by the game is decorative, not a missing map.
        f.ring.widget.setHidden(true);
        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        check(layout + " initially hidden surround accepted", f.hud.isActive());
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(layout + " externally hidden surround preserved", f.ring.widget.isSelfHidden());
        f.ring.widget.setHidden(false);

        for (int toggle = 0; toggle < 10; toggle++)
        {
            f.appearance = MinimapAppearance.SQUARE_HUD;
            f.hud.update();
            f.hud.update();
            check(layout + " repeated Square toggle " + toggle, f.hud.isActive());
            f.appearance = MinimapAppearance.CLASSIC;
            f.hud.update();
            check(layout + " repeated Classic toggle " + toggle,
                !f.hud.isActive() && !f.ring.widget.isSelfHidden() && f.overrides.isEmpty());
        }
        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        f.top = 548;
        f.hud.update();
        check(layout + " fixed mode releases layout", !f.hud.isActive() && f.overrides.isEmpty());
        f.top = modern ? InterfaceID.TOPLEVEL_PRE_EOC : InterfaceID.TOPLEVEL_OSRS_STRETCH;
        f.hud.update();
        check(layout + " resizable recovers", f.hud.isActive());

        f.worldMap.widget.setHidden(false);
        f.hud.update();
        check(layout + " separate world map preserves painting", f.hud.isActive() && f.hud.isPaintable());
        check(layout + " separate world map preserves compass", f.hud.snapshot().compassClickable);
        f.worldMap.widget.setHidden(true);
        f.hud.update();
        check(layout + " world map close recovers", f.hud.isPaintable());

        // Unlike the surround, the true minimap's hidden state must still be respected.
        f.map.widget.setHidden(true);
        f.hud.update();
        check(layout + " true minimap hidden restores", !f.hud.isActive() && f.overrides.isEmpty());
        check(layout + " true minimap stays hidden", f.map.widget.isSelfHidden());
    }

    private void worldMapBehavior(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        String layout = modern ? "modern" : "classic resizable";
        f.hud.update();
        Widget nativeMap = f.inner.widget.getChildren()[0];
        Rectangle mapBounds = f.hud.mapBounds();
        BufferedImage before = f.render();
        int[] buttons = {InterfaceID.Orbs.HEALTHBUTTON, InterfaceID.Orbs.PRAYERBUTTON,
            InterfaceID.Orbs.RUNBUTTON, InterfaceID.Orbs.SPECBUTTON};

        // Root spans the full canvas; only the actual map window is beside the HUD.
        f.worldMap.widget.setHidden(false);
        f.hud.update();
        check(layout + " open World Map keeps bars visible", f.hud.isPaintable());
        check(layout + " World Map root is not used as its window", f.worldMap.widget.getBounds().contains(mapBounds));
        check(layout + " open World Map keeps N active", f.hud.snapshot().compassClickable);
        check(layout + " native minimap remains visible and interactive",
            !nativeMap.isHidden() && nativeMap.getContentType() == 1338 && nativeMap.getNoClickThrough());
        check(layout + " native input containers stay protected", f.inner.widget.getNoClickThrough()
            && f.block.widget.getNoClickThrough());
        check(layout + " map geometry unchanged", mapBounds.equals(f.hud.mapBounds()));
        check(layout + " no overlay changes beside World Map",
            samePixels(before, f.render(), f.hud.snapshot().geometry.panel()));
        for (int id : buttons)
        {
            Widget button = f.widgets.get(id).widget;
            check(layout + " visible native resource button " + id,
                !button.isHidden() && button.getNoClickThrough());
        }
        for (int frame = 0; frame < 120; frame++) { f.hud.update(); }
        check(layout + " World Map does not recreate native minimap", f.inner.widget.getChildren()[0] == nativeMap);
        check(layout + " World Map does not churn sprite cache", f.cacheResets == 1);
        check(layout + " World Map controls not modified", !f.worldMapWindow.widget.getNoClickThrough()
            && !f.worldMapClose.widget.getNoClickThrough() && !f.worldMapClose.widget.isHidden());

        f.menuOpen = true;
        f.hud.update();
        check(layout + " open context menu still blocks N", !f.hud.snapshot().compassClickable && f.hud.isPaintable());
        f.menuOpen = false;

        // A genuinely covering window gets priority over the entire HUD.
        f.worldMapWindow.widget.setOriginalX(0);
        f.worldMapWindow.widget.setOriginalY(0);
        f.hud.update();
        check(layout + " covering World Map suppresses painting", f.hud.isActive() && !f.hud.isPaintable());
        check(layout + " covering World Map blocks N", !f.hud.snapshot().compassClickable);
        check(layout + " covered minimap does not accept invisible clicks", nativeMap.isHidden()
            && !nativeMap.getNoClickThrough() && !f.inner.widget.getNoClickThrough() && !f.block.widget.getNoClickThrough());
        for (int id : buttons)
        {
            check(layout + " covered button cannot intercept World Map " + id,
                f.widgets.get(id).widget.isHidden() && !f.widgets.get(id).widget.getNoClickThrough());
        }
        f.worldMapWindow.widget.setOriginalX(350);
        f.worldMapWindow.widget.setOriginalY(200);
        f.hud.update();
        check(layout + " moving World Map away restores HUD", f.hud.isPaintable() && !nativeMap.isHidden());
        check(layout + " no native map rebuild after overlap", f.inner.widget.getChildren()[0] == nativeMap);
        for (int id : buttons)
        {
            check(layout + " moving window away restores original action " + id,
                !f.widgets.get(id).widget.isHidden() && f.widgets.get(id).widget.getNoClickThrough());
        }

        // Hidden native actions must not be accidentally enabled by overlap recovery.
        f.healthButton.widget.setHidden(true);
        f.specButton.widget.setHidden(true);
        f.worldMapWindow.widget.setOriginalX(0);
        f.worldMapWindow.widget.setOriginalY(0);
        f.hud.update();
        f.worldMap.widget.setHidden(true);
        f.hud.update();
        check(layout + " closing covering map restores visible actions",
            !f.widgets.get(InterfaceID.Orbs.PRAYERBUTTON).widget.isHidden()
            && !f.widgets.get(InterfaceID.Orbs.RUNBUTTON).widget.isHidden());
        check(layout + " unavailable HP/Spec stay unavailable", f.healthButton.widget.isHidden() && f.specButton.widget.isHidden());
        check(layout + " closing map restores painting and native map", f.hud.isPaintable() && !nativeMap.isHidden());
        f.healthButton.widget.setHidden(false);
        f.specButton.widget.setHidden(false);

        // Do not claim the Close button's space even when it extends past the window.
        f.worldMap.widget.setHidden(false);
        f.worldMapWindow.widget.setOriginalX(350);
        f.worldMapWindow.widget.setOriginalY(200);
        f.worldMapClose.widget.setOriginalX(-350);
        f.worldMapClose.widget.setOriginalY(-200);
        f.hud.update();
        check(layout + " close-button overlap is guarded", !f.hud.isPaintable() && nativeMap.isHidden());
        f.worldMapClose.widget.setOriginalX(576);
        f.worldMapClose.widget.setOriginalY(0);
        f.hud.update();
        check(layout + " close-button movement restores HUD", f.hud.isPaintable());

        // No geometry yet while the World Map loads: wait rather than steal a click.
        f.worldMapWindow.widget.setHidden(true);
        f.hud.update();
        check(layout + " incomplete World Map waits for real bounds", !f.hud.isPaintable());
        f.worldMapWindow.widget.setHidden(false);
        f.hud.update();
        check(layout + " incomplete World Map recovers without toggle", f.hud.isPaintable());

        // A genuinely overlapping utility row must also yield, not just the main panel.
        f.add(InterfaceID.Orbs.XP_DROPS, f.orbs, 40, 24);
        f.worldMapWindow.widget.setOriginalX(10);
        f.worldMapWindow.widget.setOriginalY(SquareHudGeometry.HEIGHT + 4);
        f.hud.update();
        check(layout + " utility-row overlap yields to World Map", !f.hud.isPaintable());
        f.worldMapWindow.widget.setOriginalX(350);
        f.worldMapWindow.widget.setOriginalY(200);
        f.hud.update();
        check(layout + " utility-row move away recovers", f.hud.isPaintable());

        // Repeated open/close must not change the mask or accumulate children.
        for (int i = 0; i < 25; i++)
        {
            f.worldMap.widget.setHidden(i % 2 == 0);
            f.hud.update();
            check(layout + " open/close stays visible " + i, f.hud.isPaintable()
                && f.inner.widget.getChildren().length == 1 && !nativeMap.isHidden());
        }
        check(layout + " repeated World Map no cache churn", f.cacheResets == 1);

        // Switching to Classic from an overlapping window must release both leases.
        f.worldMap.widget.setHidden(false);
        f.worldMapWindow.widget.setOriginalX(0);
        f.worldMapWindow.widget.setOriginalY(0);
        f.hud.update();
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(layout + " Classic releases overlap flags", !f.hud.isActive()
            && !f.healthButton.widget.isSelfHidden() && !f.specButton.widget.isSelfHidden());
        check(layout + " Classic removes owned map and masks", f.inner.widget.getChildren().length == 0 && f.overrides.isEmpty());
        check(layout + " Classic restores original map renderer", f.map.widget.getContentType() == 1338);
    }

    private static MenuEntry entry(MenuAction type, int component)
    {
        return (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(),
            new Class<?>[] {MenuEntry.class}, (p, m, a) ->
            {
                if (m.getName().equals("getType")) { return type; }
                if (m.getName().equals("getParam1")) { return component; }
                return emptyValue(m.getReturnType());
            });
    }

    private void footerBehavior(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        String prefix = modern ? "modern footer " : "classic resizable footer ";
        TestWidget xp = f.add(InterfaceID.Orbs.XP_DROPS, f.orbs, 34, 34);
        TestWidget world = f.add(InterfaceID.Orbs.ORB_WORLDMAP, f.orbs, 57, 34);
        TestWidget worldButton = f.add(InterfaceID.Orbs.WORLDMAP, world, 26, 26);
        TestWidget wiki = f.add(InterfaceID.Orbs.WIKI, f.orbs, 90, 20);
        world.widget.setOriginalX(143);
        world.widget.setOriginalY(101);
        world.widget.setNoClickThrough(true);
        worldButton.widget.setNoClickThrough(true);
        Object[] nativeOp = new Object[] {1700};
        worldButton.widget.setOnOpListener(nativeOp);
        Object[] originalOp = worldButton.widget.getOnOpListener();
        f.hud.update();
        Rectangle originalMap = f.hud.mapBounds();
        check(prefix + "always visible", f.hud.snapshot().footerVisible);
        check(prefix + "XP inside footer", f.hud.snapshot().geometry.footer().contains(xp.widget.getBounds()));
        check(prefix + "World Map inside footer", f.hud.snapshot().geometry.footer().contains(world.widget.getBounds()));
        check(prefix + "buttons separated", xp.widget.getBounds().getMaxX() < world.widget.getBounds().x);
        check(prefix + "wiki hidden", wiki.widget.isSelfHidden());
        check(prefix + "native action untouched", worldButton.widget.getOnOpListener() == originalOp);
        for (int i = 0; i < 10; i++)
        {
            wiki.widget.setHidden(false);
            f.hud.update();
        }
        check(prefix + "wiki script updates hidden", wiki.widget.isSelfHidden());
        f.footerMode = SquareHudFooterMode.MOUSEOVER_ONLY;
        f.hud.update();
        check(prefix + "hover starts hidden", !f.hud.snapshot().footerVisible);
        check(prefix + "native world widget remains enabled for keys", !world.widget.isHidden()
            && world.widget.getWidth() == 57 && world.widget.getHeight() == 34);
        check(prefix + "native op retained when invisible", worldButton.widget.getOnOpListener() == originalOp);
        check(prefix + "art hidden recursively", world.widget.getOpacity() == 255
            && worldButton.widget.getOpacity() == 255 && xp.widget.getOpacity() == 255);
        check(prefix + "no invisible click blocker", !f.block.widget.getNoClickThrough()
            && !world.widget.getNoClickThrough() && !worldButton.widget.getNoClickThrough());
        MenuEntry scene = entry(MenuAction.WALK, 1234);
        MenuEntry unrelated = entry(MenuAction.CC_OP, InterfaceID.Orbs.RUNBUTTON);
        f.entries = new MenuEntry[] {scene, entry(MenuAction.CC_OP, InterfaceID.Orbs.WORLDMAP),
            entry(MenuAction.CC_OP_LOW_PRIORITY, InterfaceID.Orbs.XP_DROPS), unrelated};
        f.hud.filterHiddenFooterMenus();
        check(prefix + "hidden menus removed only for footer", f.entries.length == 2
            && f.entries[0] == scene && f.entries[1] == unrelated);
        Rectangle footer = f.hud.snapshot().geometry.footer();
        f.mouse = new Point(footer.x + 90, footer.y + 10);
        f.now = 100;
        f.hud.update();
        check(prefix + "reveal delay respected", !f.hud.snapshot().footerVisible);
        f.now = 175;
        f.hud.update();
        check(prefix + "hover reveals", f.hud.snapshot().footerVisible);
        check(prefix + "native art restored", world.widget.getOpacity() == 0 && worldButton.widget.getOpacity() == 0);
        check(prefix + "native click protection restored", world.widget.getNoClickThrough() && worldButton.widget.getNoClickThrough());
        check(prefix + "map geometry stationary", originalMap.equals(f.hud.mapBounds()));
        Field listenerField = SquareHudController.class.getDeclaredField("compassMouse");
        listenerField.setAccessible(true);
        net.runelite.client.input.MouseAdapter listener = (net.runelite.client.input.MouseAdapter) listenerField.get(f.hud);
        java.awt.Canvas canvas = new java.awt.Canvas();
        java.awt.event.MouseEvent middlePress = new java.awt.event.MouseEvent(canvas,
            java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, footer.x + 100, footer.y + 20,
            1, false, java.awt.event.MouseEvent.BUTTON1);
        listener.mousePressed(middlePress);
        check(prefix + "zone click consumed", middlePress.isConsumed());
        java.awt.event.MouseEvent nativePress = new java.awt.event.MouseEvent(canvas,
            java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, xp.widget.getBounds().x + 10,
            xp.widget.getBounds().y + 10, 1, false, java.awt.event.MouseEvent.BUTTON1);
        listener.mousePressed(nativePress);
        check(prefix + "XP click left native", !nativePress.isConsumed());
        f.menuOpen = true;
        java.awt.event.MouseEvent menuPress = new java.awt.event.MouseEvent(canvas,
            java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, footer.x + 100, footer.y + 20,
            1, false, java.awt.event.MouseEvent.BUTTON1);
        listener.mousePressed(menuPress);
        check(prefix + "open menu is not intercepted", !menuPress.isConsumed());
        f.menuOpen = false;
        f.entries = new MenuEntry[] {entry(MenuAction.CC_OP, InterfaceID.Orbs.WORLDMAP)};
        f.hud.filterHiddenFooterMenus();
        check(prefix + "shown menus retained", f.entries.length == 1);
        f.mouse = new Point(-1, -1);
        f.menuOpen = true;
        f.now = 10000;
        f.hud.update();
        check(prefix + "right-click menu holds bar", f.hud.snapshot().footerVisible);
        f.menuOpen = false;
        f.hud.update();
        f.now += 350;
        f.hud.update();
        check(prefix + "bar hides after menu exit", !f.hud.snapshot().footerVisible);
        // The same pass-through must hold after the World Map covers and uncovers us.
        f.worldMap.widget.setHidden(false);
        f.worldMapWindow.widget.setOriginalX(0);
        f.worldMapWindow.widget.setOriginalY(0);
        f.hud.update();
        check(prefix + "covered bar invisible", !f.hud.snapshot().footerVisible && !f.hud.isPaintable());
        f.worldMap.widget.setHidden(true);
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(prefix + "Classic restores wiki", !wiki.widget.isHidden());
        check(prefix + "Classic restores footer widget", world.widget.getOpacity() == 0
            && world.widget.getOriginalX() == 143 && world.widget.getOriginalY() == 101);
        check(prefix + "Classic restores child opacity", worldButton.widget.getOpacity() == 0);
        wiki.widget.setHidden(true);
        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(prefix + "already-hidden wiki remains hidden", wiki.widget.isHidden());
    }

    private void compactFooter(boolean modern) throws Exception
    {
        for (int[] sizes : new int[][] {{27, 30}, {34, 34}, {44, 40}})
        {
            Fixture f = new Fixture(modern);
            TestWidget xp = f.add(InterfaceID.Orbs.XP_DROPS, f.orbs, 27, sizes[0]);
            TestWidget world = f.add(InterfaceID.Orbs.ORB_WORLDMAP, f.orbs, 30, sizes[1]);
            TestWidget nativeButton = f.add(InterfaceID.Orbs.WORLDMAP, world, 22, 22);
            nativeButton.widget.setOriginalX(4);
            nativeButton.widget.setOriginalY(4);
            Object[] action = new Object[] {1700};
            nativeButton.widget.setOnOpListener(action);
            f.hud.update();
            Rectangle map = f.hud.mapBounds();
            Rectangle footer = f.hud.snapshot().geometry.footer();
            Rectangle inner = new Rectangle(footer.x + 2, footer.y + 2, footer.width - 4, footer.height - 4);
            String label = (modern ? "modern " : "classic ") + "footer " + sizes[0] + "/" + sizes[1] + " ";
            check(label + "safe height", footer.height == Math.max(34, Math.max(sizes[0], sizes[1]) + 4));
            check(label + "XP clears frame", inner.contains(xp.widget.getBounds()));
            check(label + "globe clears frame", inner.contains(world.widget.getBounds()));
            check(label + "native dimensions preserved", xp.widget.getHeight() == sizes[0]
                && world.widget.getHeight() == sizes[1] && nativeButton.widget.getWidth() == 22);
            check(label + "native action retained", nativeButton.widget.getOnOpListener() == action);
            check(label + "both controls registered", f.hud.snapshot().footerButtons.length == 2);
            f.footerMode = SquareHudFooterMode.MOUSEOVER_ONLY;
            f.hud.update();
            check(label + "hidden panel keeps height", !f.hud.snapshot().footerVisible
                && footer.equals(f.hud.snapshot().geometry.footer()));
            check(label + "hidden panel keeps map stationary", map.equals(f.hud.mapBounds()));
            f.mouse = new Point(footer.x + 90, footer.y + 5);
            f.now = 100; f.hud.update();
            f.now = 175; f.hud.update();
            check(label + "reveal preserves geometry", f.hud.snapshot().footerVisible
                && footer.equals(f.hud.snapshot().geometry.footer()) && map.equals(f.hud.mapBounds()));
            world.widget.setOriginalHeight(48);
            f.hud.update();
            Rectangle taller = f.hud.snapshot().geometry.footer();
            check(label + "native height change gets room", taller.height == 52
                && taller.contains(world.widget.getBounds()));
            check(label + "resize still leaves map alone", map.equals(f.hud.mapBounds()));
            f.appearance = MinimapAppearance.CLASSIC; f.hud.update();
            check(label + "Classic releases layout without losing native size",
                !f.hud.isActive() && xp.widget.getHeight() == sizes[0] && world.widget.getHeight() == 48);
        }
    }

    private void multiCombatPlacement(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        int id = modern ? InterfaceID.ToplevelPreEoc.MULTIWAY_ICON : InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON;
        TestWidget multi = f.add(id, f.block, 16, 16);
        multi.widget.setOriginalX(184);
        multi.widget.setOriginalY(170);
        multi.widget.setXPositionMode(2);
        TestWidget world = f.add(InterfaceID.Orbs.ORB_WORLDMAP, f.orbs, 57, 34);
        TestWidget globe = f.add(InterfaceID.Orbs.WORLDMAP, world, 26, 26);
        globe.widget.setOriginalX(27);
        globe.widget.setOriginalY(4);
        TestWidget store = f.add(InterfaceID.Orbs.ORB_STORE, f.orbs, 57, 34);
        TestWidget advisor = f.add(InterfaceID.Orbs.ORB_CONTENTRECOM, f.orbs, 57, 34);
        f.hud.update();
        Rectangle footer = f.hud.snapshot().geometry.footer();
        Rectangle placed = multi.widget.getBounds();
        String prefix = (modern ? "modern" : "classic") + " multi-combat ";
        check(prefix + "outside frame", placed.y >= footer.y + footer.height + SquareHudGeometry.GAP);
        check(prefix + "under globe centre", placed.x + placed.width / 2
            == globe.widget.getBounds().x + globe.widget.getWidth() / 2);
        check(prefix + "within parent drawing area", f.block.widget.getBounds().contains(placed));
        check(prefix + "natural visibility retained", !multi.widget.isHidden());
        check(prefix + "no utility overlap", !placed.intersects(store.widget.getBounds())
            && !placed.intersects(advisor.widget.getBounds()));
        f.footerMode = SquareHudFooterMode.MOUSEOVER_ONLY;
        f.hud.update();
        check(prefix + "footer hides", !f.hud.snapshot().footerVisible);
        check(prefix + "indicator independent of footer", !multi.widget.isHidden()
            && placed.equals(multi.widget.getBounds()));
        multi.widget.setHidden(true);
        f.hud.update();
        check(prefix + "single combat stays hidden", multi.widget.isHidden());
        multi.widget.setHidden(false);
        f.hud.update();
        check(prefix + "multi combat reappears natively", !multi.widget.isHidden());
        f.worldMap.widget.setHidden(false);
        f.worldMapWindow.widget.setOriginalX(0);
        f.worldMapWindow.widget.setOriginalY(0);
        f.hud.update();
        check(prefix + "yields to covering map", multi.widget.isHidden());
        f.worldMap.widget.setHidden(true);
        f.hud.update();
        check(prefix + "returns after map closes", !multi.widget.isHidden());
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(prefix + "Classic position restored", multi.widget.getOriginalX() == 184
            && multi.widget.getOriginalY() == 170 && multi.widget.getXPositionMode() == 2);
        check(prefix + "Classic visibility restored", !multi.widget.isHidden());
        multi.widget.setHidden(true);
        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(prefix + "original hidden state retained", multi.widget.isHidden());
    }

    private void wildernessPlacement(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        TestWidget pvpRoot = f.add(InterfaceID.PvpIcons.UNIVERSE, f.root, 1000, 700);
        TestWidget wild = f.add(InterfaceID.PvpIcons.ICONS, pvpRoot, 78, 73);
        wild.widget.setOriginalX(133);
        wild.widget.setOriginalY(155);
        wild.widget.setXPositionMode(2);
        TestWidget skull = f.add(InterfaceID.PvpIcons.PVP, wild, 24, 24);
        skull.widget.setSpriteId(123);
        TestWidget level = f.add(InterfaceID.PvpIcons.WILDERNESSLEVEL, wild, 78, 15);
        level.widget.setText("Level: 1");
        level.widget.setOriginalY(33);
        TestWidget range = f.add(InterfaceID.PvpIcons.COMLEVELRANGE, wild, 78, 15);
        range.widget.setOriginalY(49);
        range.widget.setText("125-126");
        int multiId = modern ? InterfaceID.ToplevelPreEoc.MULTIWAY_ICON
            : InterfaceID.ToplevelOsrsStretch.MULTIWAY_ICON;
        TestWidget multi = f.add(multiId, f.block, 16, 16);
        multi.widget.setOriginalX(184);
        multi.widget.setOriginalY(170);
        TestWidget world = f.add(InterfaceID.Orbs.ORB_WORLDMAP, f.orbs, 57, 34);
        TestWidget globe = f.add(InterfaceID.Orbs.WORLDMAP, world, 26, 26);
        globe.widget.setOriginalX(27);
        globe.widget.setOriginalY(4);
        TestWidget store = f.add(InterfaceID.Orbs.ORB_STORE, f.orbs, 57, 34);
        TestWidget advisor = f.add(InterfaceID.Orbs.ORB_CONTENTRECOM, f.orbs, 57, 34);
        f.wildernessActive = true;
        f.hud.update();
        String label = (modern ? "modern" : "classic") + " wilderness ";
        Rectangle wildBounds = wild.widget.getBounds(), multiBounds = multi.widget.getBounds();
        Rectangle footer = f.hud.snapshot().geometry.footer();
        check(label + "whole skull/levels below frame", wildBounds.y >= footer.y + footer.height + SquareHudGeometry.GAP);
        check(label + "right aligned", wildBounds.x + wildBounds.width == footer.x + footer.width - SquareHudGeometry.BORDER);
        check(label + "swords below full group", multiBounds.y >= wildBounds.y + wildBounds.height + SquareHudGeometry.GAP);
        check(label + "no clipping overlap", !wildBounds.intersects(multiBounds));
        check(label + "same native label contents", level.widget.getText().equals("Level: 1") && range.widget.getText().equals("125-126"));
        check(label + "child positions unchanged", level.widget.getOriginalY() == 33 && range.widget.getOriginalY() == 49);
        check(label + "swords fit expanded parent", f.block.widget.getBounds().contains(multiBounds));
        check(label + "left utilities avoid right group", !wildBounds.intersects(store.widget.getBounds())
            && !wildBounds.intersects(advisor.widget.getBounds()) && !multiBounds.intersects(store.widget.getBounds())
            && !multiBounds.intersects(advisor.widget.getBounds()));
        f.footerMode = SquareHudFooterMode.MOUSEOVER_ONLY;
        f.hud.update();
        check(label + "footer hidden only", !f.hud.snapshot().footerVisible && !wild.widget.isHidden() && !multi.widget.isHidden());
        check(label + "hover cannot move indicators", wildBounds.equals(wild.widget.getBounds()) && multiBounds.equals(multi.widget.getBounds()));
        // A longer native PvP status block still leaves the entire sword clear.
        wild.widget.setOriginalHeight(115);
        f.hud.update();
        check(label + "dynamic height honored", multi.widget.getBounds().y >= wild.widget.getBounds().y + 115 + SquareHudGeometry.GAP);
        // Cover just the external indicator rows; must not oscillate each frame.
        f.worldMap.widget.setHidden(false);
        f.worldMapWindow.widget.setOriginalX(wildBounds.x);
        f.worldMapWindow.widget.setOriginalY(wildBounds.y + 5);
        for (int frame = 0; frame < 20; frame++)
        {
            f.hud.update();
            check(label + "covered indicators yield " + frame, wild.widget.isHidden() && multi.widget.isHidden());
            check(label + "covered stack stable " + frame, multi.widget.getBounds().y >= wild.widget.getBounds().y + 115 + SquareHudGeometry.GAP);
        }
        f.worldMap.widget.setHidden(true);
        f.hud.update();
        check(label + "uncover restores native visibility", !wild.widget.isHidden() && !multi.widget.isHidden());
        // Native visibility, not presence of the interface, controls extra spacing.
        wild.widget.setHidden(true);
        f.hud.update();
        check(label + "hidden wilderness remains hidden", wild.widget.isHidden());
        check(label + "multi-only returns to footer", multi.widget.getBounds().y == footer.y + footer.height + SquareHudGeometry.GAP);
        wild.widget.setHidden(false);
        f.wildernessActive = false;
        skull.widget.setHidden(true);
        level.widget.setText("");
        range.widget.setText("");
        f.hud.update();
        check(label + "empty group uses no visible row", multi.widget.getBounds().y == footer.y + footer.height + SquareHudGeometry.GAP);
        range.widget.setText("100-126");
        f.hud.update();
        check(label + "non-Wilderness PvP text also avoids collision", multi.widget.getBounds().y > wild.widget.getBounds().y + 115);
        multi.widget.setHidden(true);
        f.hud.update();
        check(label + "single combat does not reveal swords", multi.widget.isHidden());
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check(label + "Classic restores skull group position", wild.widget.getOriginalX() == 133
            && wild.widget.getOriginalY() == 155 && wild.widget.getXPositionMode() == 2);
        check(label + "native size updates not rolled back", wild.widget.getHeight() == 115);
        check(label + "Classic restores swords", multi.widget.getOriginalX() == 184 && multi.widget.getOriginalY() == 170);
        check(label + "native hidden swords preserved", multi.widget.isHidden());
    }

    private void resourceStates() throws Exception
    {
        Fixture f = new Fixture(true);
        for (boolean running : new boolean[]{false, true})
        {
            for (boolean stamina : new boolean[]{false, true})
            {
                f.running = running;
                f.stamina = stamina;
                f.hud.update();
                SquareHudController.Snapshot view = f.hud.snapshot();
                check("live run state", view.selected[2] == running && view.stamina == stamina);
                check("run amount unchanged", view.values[2] == 100 && view.maxima[2] == 100);
            }
        }
        Rectangle run = f.hud.snapshot().geometry.block(2);
        f.running = false; f.hud.update();
        int off = f.render().getRGB(run.x + 24, run.y + 3);
        f.running = true; f.hud.update();
        int on = f.render().getRGB(run.x + 24, run.y + 3);
        check("run fill actually changes in renderer", off != on);
        f.stamina = true; f.hud.update();
        check("stamina does not change running fill", f.render().getRGB(run.x + 24, run.y + 3) == on);
        Rectangle spec = f.hud.snapshot().geometry.block(3);
        f.specButton.widget.setHidden(true); f.specSelected = false; f.hud.update();
        int unavailable = f.render().getRGB(spec.x + 24, spec.y + spec.height - 3);
        check("unavailable spec keeps value", f.hud.snapshot().values[3] == 80 && !f.hud.snapshot().available[3]);
        f.specButton.widget.setHidden(false); f.specSelected = false; f.hud.update();
        int ready = f.render().getRGB(spec.x + 24, spec.y + spec.height - 3);
        f.specSelected = true; f.hud.update();
        int armed = f.render().getRGB(spec.x + 24, spec.y + spec.height - 3);
        check("three spec fill states rendered", unavailable != ready && ready != armed && unavailable != armed);
        check("spec native control still available", !f.specButton.widget.isHidden());
        check("resource amounts unchanged", f.hud.snapshot().values[3] == 80 && f.hud.snapshot().maxima[3] == 100);
    }

    private void infoPanelAndCompass(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        f.hud.update();
        Widget map = f.inner.widget.getChildren()[0];
        Rectangle compass = f.hud.snapshot().geometry.compass();
        check("compass default visible", f.hud.snapshot().showCompass && f.hud.snapshot().compassClickable);
        check("compass excludes markers", !f.hud.markerClip().contains(compass.getCenterX(), compass.getCenterY()));
        BufferedImage before = f.render();
        f.showCompass = false;
        f.hud.update();
        check("hidden compass not clickable", !f.hud.snapshot().compassClickable && !f.hud.snapshot().showCompass);
        check("hidden compass gives map its corner back", f.hud.markerClip().contains(compass.getCenterX(), compass.getCenterY()));
        check("map child not recreated for compass", f.inner.widget.getChildren()[0] == map);
        check("hidden compass artwork gone", !samePixels(before, f.render(), compass));
        check("resource bars unaffected", samePixels(before, f.render(), f.hud.snapshot().geometry.block(2)));
        f.showCompass = true;
        f.hud.update();
        check("compass restored", f.hud.snapshot().showCompass && f.hud.snapshot().compassClickable);
        check("map retained after compass toggle", f.inner.widget.getChildren()[0] == map);
        f.infoMode = InfoPanelText.COORDS;
        Field point = SquareHudController.class.getDeclaredField("currentLocation");
        Field initialized = SquareHudController.class.getDeclaredField("locationInitialized");
        point.setAccessible(true);
        initialized.setAccessible(true);
        point.set(f.hud, new net.runelite.api.coords.WorldPoint(3200, 3420, 2));
        initialized.setBoolean(f.hud, true);
        f.hud.update();
        check("Coords used in actual snapshot", f.hud.snapshot().infoText.equals("3200, 3420"));
        check("plane in actual tooltip", f.hud.snapshot().infoTooltip.endsWith("Plane: 2"));
        f.footerMode = SquareHudFooterMode.MOUSEOVER_ONLY;
        point.set(f.hud, new net.runelite.api.coords.WorldPoint(3201, 3421, 1));
        f.hud.update();
        check("text refreshed while panel hidden", !f.hud.snapshot().footerVisible
            && f.hud.snapshot().infoText.equals("3201, 3421"));
        f.infoMode = InfoPanelText.SYSTEM_TIME;
        f.hud.update();
        check("time appears in snapshot without player", f.hud.snapshot().infoText.matches("[0-9]{1,2}:[0-9]{2} [AP]M"));
        f.infoMode = InfoPanelText.COORDS;
        f.hud.refreshLocation(); // no player available during this simulated transition
        f.hud.update();
        check("unknown coordinates not stale", f.hud.snapshot().infoText.isEmpty());
        f.infoMode = InfoPanelText.ZONE;
        Field zone = SquareHudController.class.getDeclaredField("zoneName");
        zone.setAccessible(true);
        zone.set(f.hud, "Varrock");
        f.hud.update();
        check("Zone selection still works", f.hud.snapshot().infoText.equals("Varrock"));
        f.showCompass = false;
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        int drawId = modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP_GRAPHIC6
            : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP_GRAPHIC6;
        int clickId = modern ? InterfaceID.ToplevelPreEoc.COMPASSCLICK
            : InterfaceID.ToplevelOsrsStretch.COMPASSCLICK;
        check("Classic compass drawing hidden", f.widgets.get(drawId).widget.isHidden());
        check("Classic compass input hidden", f.widgets.get(clickId).widget.isHidden());
        check("Classic map remains visible", !f.map.widget.isHidden());
        for (int i = 0; i < 30; i++) { f.hud.update(); }
        f.showCompass = true;
        f.hud.update();
        check("Classic compass restored without churn", !f.widgets.get(drawId).widget.isHidden()
            && !f.widgets.get(clickId).widget.isHidden());
        f.showCompass = false;
        f.hud.update();
        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        f.showCompass = true;
        f.hud.update();
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check("Square/Classic leases do not retain our hidden state", !f.widgets.get(drawId).widget.isHidden()
            && !f.widgets.get(clickId).widget.isHidden());
        f.widgets.get(drawId).widget.setHidden(true);
        f.showCompass = false;
        f.hud.update();
        f.showCompass = true;
        f.hud.update();
        check("naturally hidden compass stays hidden", f.widgets.get(drawId).widget.isHidden());
    }

    private void fixedCompass() throws Exception
    {
        Fixture f = new Fixture(true);
        f.top = InterfaceID.TOPLEVEL;
        TestWidget root = f.add(InterfaceID.Toplevel.UNIVERSE, null, 765, 503);
        TestWidget mapRoot = f.add(InterfaceID.Toplevel.MAPCONTAINER, root, 211, 179);
        TestWidget click = f.add(InterfaceID.Toplevel.COMPASSCLICK, mapRoot, 34, 34);
        Widget draw = mapRoot.widget.createChild(-1, WidgetType.GRAPHIC);
        draw.setContentType(1339);
        Widget terrain = mapRoot.widget.createChild(-1, WidgetType.GRAPHIC);
        terrain.setContentType(1338);
        f.showCompass = false;
        f.hud.update();
        check("fixed square request uses Classic", !f.hud.isActive());
        check("fixed compass art and click both hidden", draw.isHidden() && click.widget.isHidden());
        check("fixed terrain not hidden", !terrain.isHidden());
        f.showCompass = true;
        f.hud.update();
        check("fixed compass restores", !draw.isHidden() && !click.widget.isHidden());
    }

    private void childOwnership(boolean modern) throws Exception
    {
        Fixture f = new Fixture(modern);
        Widget before = f.inner.widget.createChild(-1, WidgetType.RECTANGLE);
        f.map.widget.setBorderType(2);
        SpritePixels originalOverride = (SpritePixels) Proxy.newProxyInstance(SpritePixels.class.getClassLoader(),
            new Class<?>[]{SpritePixels.class}, (p, m, a) -> emptyValue(m.getReturnType()));
        f.overrides.put(1178, originalOverride);
        f.hud.update();
        Widget ours = f.inner.widget.getChildren()[1];
        Widget after = f.inner.widget.createChild(-1, WidgetType.RECTANGLE);
        check("private child uses fresh zero border", ours.getBorderType() == 0);
        check("stock border preserved", f.map.widget.getBorderType() == 2);
        for (int i = 0; i < 120; i++) { f.hud.update(); }
        check("one owned child across frames", f.inner.widget.getChildren().length == 3
            && f.inner.widget.getChildren()[1] == ours);
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check("foreign siblings survive rollback", f.inner.widget.getChildren()[0] == before
            && f.inner.widget.getChildren()[1] == null && f.inner.widget.getChildren()[2] == after);
        check("detached child inert", ours.isSelfHidden() && ours.getContentType() == 0);
        check("foreign stock sprite override preserved", f.overrides.size() == 1 && f.overrides.get(1178) == originalOverride);
        check("original border preserved after rollback", f.map.widget.getBorderType() == 2);

        f.appearance = MinimapAppearance.SQUARE_HUD;
        f.hud.update();
        Widget[] children = f.inner.widget.getChildren();
        Widget current = children[children.length - 1];
        Widget foreignReplacement = new TestWidget(f.inner.id, f.inner, 4, 4, current.getIndex()).widget;
        children[current.getIndex()] = foreignReplacement;
        f.hud.update();
        check("externally removed child recreated once", f.inner.widget.getChildren().length == children.length + 1);
        check("externally inserted child retained", f.inner.widget.getChildren()[current.getIndex()] == foreignReplacement);
        f.appearance = MinimapAppearance.CLASSIC;
        f.hud.update();
        check("cleanup trims only our appended slot", f.inner.widget.getChildren().length == children.length
            && f.inner.widget.getChildren()[current.getIndex()] == foreignReplacement);
    }

    private void failedCreationRestores() throws Exception
    {
        Fixture f = new Fixture(true);
        f.inner.failCreate = true;
        f.hud.update();
        check("failed native child falls back", !f.hud.isActive());
        check("failed creation restores map", f.map.widget.getContentType() == 1338
            && f.map.widget.getType() == WidgetType.GRAPHIC && f.map.widget.getSpriteId() == 1178);
        check("failed creation releases private mask", f.overrides.isEmpty());
        check("failed creation restores geometry", f.block.widget.getOriginalWidth() == 211);
    }

    private void nativeButtonVisibility() throws Exception
    {
        Fixture f = new Fixture(true);
        f.healthButton.widget.setHidden(true);
        f.specButton.widget.setHidden(true);
        f.hud.update();
        SquareHudController.Snapshot view = f.hud.snapshot();
        check("Hidden native actions do not disable HUD", view != null);
        check("HP value remains available", view.values[0] == 73 && view.maxima[0] == 99);
        check("Spec value remains available", view.values[3] == 80 && view.maxima[3] == 100);
        check("Hidden native actions are not enabled", !view.available[0] && !view.available[3]
            && f.healthButton.widget.isSelfHidden() && f.specButton.widget.isSelfHidden());
        BufferedImage unavailable = f.render();
        f.healthButton.widget.setHidden(false);
        f.specButton.widget.setHidden(false);
        f.hud.update();
        BufferedImage available = f.render();
        check("HP rendering independent of click availability", samePixels(unavailable, available, view.geometry.block(0)));
        // dev.10 intentionally changes the fill for an unavailable special attack,
        // but the actual numeric label must remain in the same place.
        Rectangle spec = view.geometry.block(3);
        check("Spec fill reflects click availability", !samePixels(unavailable, available, spec));
        boolean sameLabel = true;
        int labelPixels = 0;
        for (int y = spec.y + 1; y < spec.y + spec.height - 1; y++)
        {
            for (int x = spec.x + 23; x < spec.x + spec.width - 1; x++)
            {
                boolean a = unavailable.getRGB(x, y) == 0xffffedad;
                boolean b = available.getRGB(x, y) == 0xffffedad;
                sameLabel &= a == b;
                if (a) { labelPixels++; }
            }
        }
        check("Spec number still visible and unchanged", sameLabel && labelPixels > 0);
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second, Rectangle region)
    {
        for (int y = region.y; y < region.y + region.height; y++)
        {
            for (int x = region.x; x < region.x + region.width; x++)
            {
                if (first.getRGB(x, y) != second.getRGB(x, y)) { return false; }
            }
        }
        return true;
    }

    private static Object emptyValue(Class<?> type)
    {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        if (type == double.class) { return 0d; }
        if (type == float.class) { return 0f; }
        if (type == byte.class) { return (byte) 0; }
        if (type == short.class) { return (short) 0; }
        if (type == char.class) { return (char) 0; }
        return null;
    }

    private static final class TestWidget
    {
        final int id;
        final TestWidget parent;
        final List<TestWidget> children = new ArrayList<>();
        final Map<String, Object> fields = new HashMap<>();
        final Widget widget;
        Widget[] dynamicChildren = new Widget[0];
        final int index;
        boolean failCreate;

        TestWidget(int id, TestWidget parent, int width, int height)
        {
            this(id, parent, width, height, -1);
        }

        TestWidget(int id, TestWidget parent, int width, int height, int index)
        {
            this.id = id;
            this.parent = parent;
            this.index = index;
            if (parent != null && index < 0) { parent.children.add(this); }
            fields.put("OriginalX", 0);
            fields.put("OriginalY", 0);
            fields.put("OriginalWidth", width);
            fields.put("OriginalHeight", height);
            fields.put("SpriteId", -1);
            fields.put("Type", 5);
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class}, (p, m, a) ->
            {
                String name = m.getName();
                if (name.equals("equals")) { return p == a[0]; }
                if (name.equals("hashCode")) { return System.identityHashCode(p); }
                if (name.equals("toString")) { return "Widget " + id; }
                if (name.equals("getId")) { return id; }
                if (name.equals("getIndex")) { return index; }
                if (name.equals("getChildren") || name.equals("getDynamicChildren")) { return dynamicChildren; }
                if (name.equals("setChildren")) { dynamicChildren = (Widget[]) a[0]; return null; }
                if (name.equals("createChild"))
                {
                    if (failCreate) { return null; }
                    int slot = (int) a[0];
                    if (slot < 0) { slot = dynamicChildren.length; }
                    if (slot >= dynamicChildren.length) { dynamicChildren = Arrays.copyOf(dynamicChildren, slot + 1); }
                    TestWidget created = new TestWidget(id, this, 0, 0, slot);
                    created.widget.setType((int) a[1]);
                    dynamicChildren[slot] = created.widget;
                    return created.widget;
                }
                if (name.equals("getParent")) { return parent == null ? null : parent.widget; }
                if (name.equals("getBounds"))
                {
                    Point origin = parent == null ? new Point(0, 0) : parent.widget.getCanvasLocation();
                    return new Rectangle(origin.getX() + (int) fields.get("OriginalX"),
                        origin.getY() + (int) fields.get("OriginalY"),
                        (int) fields.get("OriginalWidth"), (int) fields.get("OriginalHeight"));
                }
                if (name.equals("getWidth")) { return fields.get("OriginalWidth"); }
                if (name.equals("getHeight")) { return fields.get("OriginalHeight"); }
                if (name.equals("getCanvasLocation"))
                {
                    Point origin = parent == null ? new Point(0, 0) : parent.widget.getCanvasLocation();
                    return new Point(origin.getX() + (int) fields.get("OriginalX"), origin.getY() + (int) fields.get("OriginalY"));
                }
                if (name.equals("isSelfHidden")) { return fields.getOrDefault("Hidden", false); }
                if (name.equals("isHidden"))
                {
                    return (boolean) fields.getOrDefault("Hidden", false) || (parent != null && parent.widget.isHidden());
                }
                if (name.equals("getStaticChildren")) { return children.stream().map(c -> c.widget).toArray(Widget[]::new); }
                if (name.equals("setHidden")) { fields.put("Hidden", a[0]); return null; }
                if (name.startsWith("set") && a != null && a.length == 1)
                {
                    fields.put(name.substring(3), a[0]);
                    return m.getReturnType() == Widget.class ? p : null;
                }
                if (name.startsWith("get") && fields.containsKey(name.substring(3))) { return fields.get(name.substring(3)); }
                return emptyValue(m.getReturnType());
            });
        }
    }

    private static final class Fixture
    {
        final Map<Integer, TestWidget> widgets = new HashMap<>();
        final Map<Integer, SpritePixels> overrides = new HashMap<>();
        final TestWidget root, block, inner, map, ring, host, orbs, healthButton, specButton, worldMap, worldMapWindow, worldMapFrame, worldMapClose;
        final Client client;
        final SquareHudController hud;
        MinimapAppearance appearance = MinimapAppearance.SQUARE_HUD;
        int top;
        int cacheResets;
        boolean menuOpen;
        boolean running, stamina, specSelected, wildernessActive;
        boolean showCompass = true;
        InfoPanelText infoMode = InfoPanelText.ZONE;
        SquareHudFooterMode footerMode = SquareHudFooterMode.ALWAYS_VISIBLE;
        Point mouse = new Point(-1, -1);
        long now;
        MenuEntry[] entries = new MenuEntry[0];

        Fixture(boolean modern) throws Exception
        {
            top = modern ? InterfaceID.TOPLEVEL_PRE_EOC : InterfaceID.TOPLEVEL_OSRS_STRETCH;
            root = add(top << 16, null, 1000, 700);
            block = add(modern ? InterfaceID.ToplevelPreEoc.MAP_CONTAINER : InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, root, 211, 207);
            inner = add(modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP, block, 211, 179);
            map = add(modern ? InterfaceID.ToplevelPreEoc.MINIMAP : InterfaceID.ToplevelOsrsStretch.MINIMAP, inner, 152, 152);
            map.widget.setSpriteId(1178);
            map.widget.setContentType(1338);
            // One object per ID: GRAPHIC9 and child 32 must not be separate fake widgets.
            ring = add(modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP_GRAPHIC9 : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP_GRAPHIC9, inner, 211, 179);
            host = add(modern ? InterfaceID.ToplevelPreEoc.ORBS : InterfaceID.ToplevelOsrsStretch.ORBS, block, 207, 197);
            orbs = add(InterfaceID.Orbs.UNIVERSE, host, 207, 197);
            add(modern ? InterfaceID.ToplevelPreEoc.MAP_MINIMAP_GRAPHIC6 : InterfaceID.ToplevelOsrsStretch.MAP_MINIMAP_GRAPHIC6, inner, 34, 34)
                .widget.setContentType(1339);
            add(modern ? InterfaceID.ToplevelPreEoc.COMPASSCLICK : InterfaceID.ToplevelOsrsStretch.COMPASSCLICK, inner, 34, 34);
            int[] containers = {InterfaceID.Orbs.ORB_HEALTH, InterfaceID.Orbs.ORB_PRAYER, InterfaceID.Orbs.ORB_RUNENERGY, InterfaceID.Orbs.ORB_SPECENERGY};
            int[] buttons = {InterfaceID.Orbs.HEALTHBUTTON, InterfaceID.Orbs.PRAYERBUTTON, InterfaceID.Orbs.RUNBUTTON, InterfaceID.Orbs.SPECBUTTON};
            for (int i = 0; i < containers.length; i++)
            {
                TestWidget orb = add(containers[i], orbs, 57, 34);
                add(buttons[i], orb, 26, 26);
            }
            healthButton = widgets.get(buttons[0]);
            specButton = widgets.get(buttons[3]);
            worldMap = add(InterfaceID.Worldmap.UNIVERSE, root, 1000, 700);
            worldMapWindow = add(InterfaceID.Worldmap.WINDOW, worldMap, 600, 480);
            worldMapWindow.widget.setOriginalX(350);
            worldMapWindow.widget.setOriginalY(200);
            worldMapFrame = add(InterfaceID.Worldmap.FRAME, worldMapWindow, 600, 480);
            worldMapClose = add(InterfaceID.Worldmap.CLOSE, worldMapWindow, 24, 24);
            worldMapClose.widget.setOriginalX(576);
            worldMap.widget.setHidden(true);
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getWidget":
                        TestWidget found = widgets.get((int) a[0]);
                        return found == null ? null : found.widget;
                    case "getTopLevelInterfaceId": return top;
                    case "isMenuOpen": return menuOpen;
                    case "getMouseCanvasPosition": return mouse;
                    case "getMenuEntries": return entries;
                    case "setMenuEntries": entries = (MenuEntry[]) a[0]; return null;
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getSpriteOverrides": return overrides;
                    case "createSpritePixels":
                        return Proxy.newProxyInstance(SpritePixels.class.getClassLoader(), new Class<?>[]{SpritePixels.class},
                            (sp, sm, sa) -> emptyValue(sm.getReturnType()));
                    case "getWidgetSpriteCache":
                        return Proxy.newProxyInstance(m.getReturnType().getClassLoader(), new Class<?>[]{m.getReturnType()}, (cp, cm, ca) ->
                        {
                            if (cm.getName().equals("reset")) { cacheResets++; }
                            return emptyValue(cm.getReturnType());
                        });
                    case "getBoostedSkillLevel": return a[0] == Skill.HITPOINTS ? 73 : 52;
                    case "getRealSkillLevel": return 99;
                    case "getEnergy": return 10000;
                    case "getVarpValue":
                        if ((int) a[0] == VarPlayerID.SA_ENERGY) { return 800; }
                        if ((int) a[0] == VarPlayerID.OPTION_RUN) { return running ? 1 : 0; }
                        if ((int) a[0] == VarPlayerID.SA_ATTACK) { return specSelected ? 1 : 0; }
                        return 0;
                    case "getVarbitValue":
                        if ((int) a[0] == VarbitID.STAMINA_ACTIVE) { return stamina ? 1 : 0; }
                        if ((int) a[0] == VarbitID.INSIDE_WILDERNESS) { return wildernessActive ? 1 : 0; }
                        return 0;
                    default: return emptyValue(m.getReturnType());
                }
            });
            MinimapFilterConfig config = new MinimapFilterConfig()
            {
                @Override public boolean enableSquareMinimap() { return appearance == MinimapAppearance.SQUARE_HUD; }
                @Override public SquareHudFooterMode squareHudFooterMode() { return footerMode; }
                @Override public boolean showCompass() { return showCompass; }
                @Override public InfoPanelText infoPanelText() { return infoMode; }
            };
            hud = new SquareHudController(client, null, config, null, null, () -> now);
            // Exercise update()/restoration without registering real desktop listeners.
            Field started = SquareHudController.class.getDeclaredField("started");
            started.setAccessible(true);
            started.setBoolean(hud, true);
        }

        TestWidget add(int id, TestWidget parent, int width, int height)
        {
            if (widgets.containsKey(id)) { throw new AssertionError("Duplicate widget ID " + id); }
            TestWidget result = new TestWidget(id, parent, width, height);
            widgets.put(id, result);
            return result;
        }

        BufferedImage render()
        {
            BufferedImage image = new BufferedImage(300, 220, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try { new SquareHudOverlay(client, hud, null).render(graphics); }
            finally { graphics.dispose(); }
            return image;
        }
    }
}
