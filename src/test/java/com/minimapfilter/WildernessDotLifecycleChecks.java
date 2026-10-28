package com.minimapfilter;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldType;

/** Tests the production native-dot filters with an in-memory client and sprite array. */
public final class WildernessDotLifecycleChecks
{
    private int cases;

    public static void main(String[] args) throws Exception
    {
        System.out.println("Wilderness dot-lifecycle checks passed: " + runAll());
    }

    public static int runAll() throws Exception
    {
        WildernessDotLifecycleChecks t = new WildernessDotLifecycleChecks();
        t.lifecycle();
        return t.cases;
    }

    private void check(String name, boolean ok)
    {
        cases++;
        if (!ok) { throw new AssertionError(name); }
    }

    private void lifecycle() throws Exception
    {
        WildernessSkullChecks.Fixture f = new WildernessSkullChecks.Fixture();
        final SpritePixels[] dots = new SpritePixels[7];
        for (int i = 0; i < dots.length; i++)
        { dots[i] = WildernessSkullChecks.sprite(new int[]{0, 1, 0xffa0b0c0 + i, 0}, 2, 2); }
        SpritePixels[] originals = dots.clone();
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p,m,a) ->
        {
            if (m.getName().equals("getMapDots")) { return dots; }
            return m.invoke(f.client, a);
        });
        MinimapFilterPlugin plugin = new MinimapFilterPlugin();
        set(plugin, "client", client);
        set(plugin, "config", f.config);
        set(plugin, "markerAssets", new MinimapMarkerAssets());
        set(plugin, "wildernessPlayerOverlay", f.overlay);
        call(plugin, "createTransparentSprites");
        call(plugin, "applyDotFilters");
        for (int i = 0; i < dots.length; i++) { check("default native dot " + i, dots[i] == originals[i]); }
        f.config.enabled = true;
        call(plugin, "refreshWildernessPlayerDots");
        Object transparent = get(plugin, "transparentDot");
        check("ordinary dot suppressed for skulls", dots[2] == transparent && f.overlay.isReplacingOtherPlayers());
        for (int i : new int[]{0,1,3,4,5,6}) { check("other category unchanged " + i, dots[i] == originals[i]); }
        for (int i = 0; i < 120; i++) { call(plugin, "refreshWildernessPlayerDots"); }
        check("stable repeated frames", dots[2] == transparent);
        f.wild = 0;
        call(plugin, "refreshWildernessPlayerDots");
        check("exit restores exact native sprite", dots[2] == originals[2] && !f.overlay.isReplacingOtherPlayers());
        f.config.otherColor = Color.CYAN;
        call(plugin, "applyDotFilters");
        SpritePixels colored = dots[2];
        check("configured dot tint outside Wilderness", colored != originals[2]
            && colored.getPixels()[2] == Color.CYAN.getRGB());
        check("native pixel data not mutated", originals[2].getPixels()[2] == 0xffa0b0c2);
        f.wild = 1; call(plugin, "refreshWildernessPlayerDots");
        check("reentry suppresses configured dot", dots[2] == transparent);
        f.wild = 0; call(plugin, "refreshWildernessPlayerDots");
        check("exit restores same configured dot", dots[2] == colored);
        f.wild = 1; call(plugin, "refreshWildernessPlayerDots");
        f.config.show = false; call(plugin, "applyDotFilters");
        check("Other players off stays hidden", dots[2] == transparent && !f.overlay.isReplacingOtherPlayers());
        f.config.show = true; call(plugin, "applyDotFilters");
        check("Other players on returns skulls", dots[2] == transparent && f.overlay.isReplacingOtherPlayers());
        f.config.enabled = false; call(plugin, "applyDotFilters");
        check("feature off restores configured native dot", dots[2] == colored && !f.overlay.isReplacingOtherPlayers());
        f.config.enabled = true; call(plugin, "applyDotFilters");
        f.types = EnumSet.of(WorldType.MEMBERS, WorldType.PVP);
        call(plugin, "refreshWildernessPlayerDots");
        check("special world restores native dot", dots[2] == colored);
        f.types = EnumSet.of(WorldType.MEMBERS); call(plugin, "refreshWildernessPlayerDots");
        f.levelText = ""; call(plugin, "refreshWildernessPlayerDots");
        check("missing bracket restores native dot", dots[2] == colored);
        f.levelText = "Level: 20"; call(plugin, "refreshWildernessPlayerDots");
        check("bracket recovered", dots[2] == transparent);
        f.config.otherColor = NativeDotColors.PLAYER;
        call(plugin, "applyDotFilters");
        SpritePixels external = WildernessSkullChecks.sprite(new int[]{0xff112233}, 1, 1);
        dots[2] = external;
        call(plugin, "refreshWildernessPlayerDots");
        check("native/core sprite rebuild re-suppressed", dots[2] == transparent);
        f.wild = 0; call(plugin, "refreshWildernessPlayerDots");
        check("new external baseline restored", dots[2] == external);
        f.wild = 1; call(plugin, "refreshWildernessPlayerDots");
        call(plugin, "restoreDots");
        check("shutdown restoration", dots[2] == external);
        SpritePixels foreign = WildernessSkullChecks.sprite(new int[]{0xffc0d0e0}, 1, 1);
        dots[2] = foreign;
        call(plugin, "restoreDots");
        check("shutdown does not overwrite a foreign replacement", dots[2] == foreign);
    }

    private static Object get(Object target, String name) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void call(Object target, String name) throws Exception
    {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
}
