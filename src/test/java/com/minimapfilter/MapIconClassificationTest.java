package com.minimapfilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.SpriteID;
import net.runelite.api.SpritePixels;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the production catalog/filter methods using in-memory enum and sprite data. */
public class MapIconClassificationTest
{
    @Test
    public void requestedServicesIgnoreSailingSlotGuesses() throws Exception
    {
        Fixture f = new Fixture();
        f.sailing = false;
        f.filter();
        for (int i = 0; i < 9; i++) { assertSame("explicit icon " + i, f.original[i], f.live[i]); }
        f.shops = false;
        f.filter();
        for (int i : new int[]{1,2,3,5,6,7}) { assertSame("shop " + i, f.transparent, f.live[i]); }
        for (int i : new int[]{0,4,8}) { assertSame("not a shop " + i, f.original[i], f.live[i]); }
        f.shops = true;
        f.other = false;
        f.filter();
        assertSame(f.transparent, f.live[0]);
        assertSame(f.transparent, f.live[8]);
        for (int i : new int[]{1,2,3,4,5,6,7}) { assertSame(f.original[i], f.live[i]); }
        f.other = true;
        f.filter();
        assertArrayEquals(f.original, f.live);
    }

    @Test
    public void slayerToggleIsIndependentAndEnabledByDefault() throws Exception
    {
        Fixture f = new Fixture();
        assertTrue(new MinimapFilterConfig() {}.showSlayerMasters());
        f.skills = false;
        f.filter();
        assertSame(f.original[4], f.live[4]);
        f.skills = true;
        f.slayer = false;
        f.filter();
        assertSame(f.transparent, f.live[4]);
        f.slayer = true;
        f.filter();
        assertSame(f.original[4], f.live[4]);
    }

    @Test
    public void joinsUseCategoryKeysAndOnlyRequestedNames() throws Exception
    {
        Fixture f = new Fixture();
        assertEquals(9, f.catalog().size());
        assertEquals(Boolean.TRUE, get(f.plugin, "namedIconCatalogComplete"));
        f.original[0] = sprite(9000);
        f.live[0] = f.original[0];
        f.overrides().remove(0);
        f.other = false;
        f.filter();
        assertSame("unknown artwork not classified by a local slot", f.original[0], f.live[0]);
    }

    @Test
    public void slayerNativeIdWorksBeforeLegendLoads() throws Exception
    {
        Fixture f = new Fixture();
        f.enumsAvailable = false;
        f.catalog().clear();
        invoke(f.plugin, "rebuildNamedIconCatalog");
        assertEquals(1, f.catalog().size());
        assertEquals(Boolean.FALSE, get(f.plugin, "namedIconCatalogComplete"));
        f.slayer = false;
        f.filter();
        assertSame(f.transparent, f.live[4]);
    }

    static Object get(Object instance, String name) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void set(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    static void invoke(Object instance, String name) throws Exception
    {
        Method method = instance.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(instance);
    }

    static Object empty(Class<?> type)
    {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        if (type == float.class) { return 0f; }
        if (type == double.class) { return 0d; }
        return null;
    }

    static SpritePixels sprite(int seed)
    {
        int[] pixels = new int[225];
        for (int i = 0; i < pixels.length; i++) { pixels[i] = (seed * 1103515245 + i * 677) & 0xffffff; }
        return (SpritePixels) Proxy.newProxyInstance(SpritePixels.class.getClassLoader(),
            new Class<?>[]{SpritePixels.class}, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getWidth": case "getHeight": case "getMaxWidth": case "getMaxHeight": return 15;
                    case "getPixels": return pixels;
                    default: return empty(method.getReturnType());
                }
            });
    }

    static final class Fixture
    {
        final MinimapFilterPlugin plugin = new MinimapFilterPlugin();
        final SpritePixels[] original = new SpritePixels[9];
        final SpritePixels[] live;
        final SpritePixels transparent = sprite(10000);
        boolean shops = true, other = true, slayer = true, skills = true, sailing = true, enumsAvailable = true;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Fixture() throws Exception
        {
            String[] names = {"Junk checker", "Dye trader", "Rope trader", "Garden supplier", "Slayer Master",
                "Wine trader", "Holiday item trader", "Tea trader", "Taxidermist"};
            int[] keys = new int[9], spriteKeys = new int[9], spriteIds = new int[9];
            Map<Integer, SpritePixels> sprites = new HashMap<>();
            for (int i = 0; i < 9; i++)
            {
                original[i] = sprite(i + 1);
                keys[i] = 810 + i;
                spriteKeys[8-i] = keys[i];
                spriteIds[8-i] = 3010 + i;
                sprites.put(3010+i, original[i]);
            }
            sprites.put(SpriteID.MAP_ICON_SLAYER_MASTER, original[4]);
            live = Arrays.copyOf(original, original.length);
            EnumComposition nameEnum = (EnumComposition) Proxy.newProxyInstance(EnumComposition.class.getClassLoader(),
                new Class<?>[]{EnumComposition.class}, (p,m,a) -> m.getName().equals("getKeys") ? keys
                    : m.getName().equals("getStringVals") ? names : empty(m.getReturnType()));
            EnumComposition spriteEnum = (EnumComposition) Proxy.newProxyInstance(EnumComposition.class.getClassLoader(),
                new Class<?>[]{EnumComposition.class}, (p,m,a) -> m.getName().equals("getKeys") ? spriteKeys
                    : m.getName().equals("getIntVals") ? spriteIds : empty(m.getReturnType()));
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (p,m,a) ->
                {
                    switch (m.getName())
                    {
                        case "getEnum": return !enumsAvailable ? null
                            : (int)a[0] == NamedMapIconCatalog.CATEGORY_NAMES ? nameEnum : spriteEnum;
                        case "getSprites":
                            SpritePixels found = sprites.get((int)a[1]);
                            return found == null ? null : new SpritePixels[]{found};
                        case "getMapIcons": return live;
                        default: return empty(m.getReturnType());
                    }
                });
            MinimapFilterConfig config = new MinimapFilterConfig()
            {
                @Override public boolean showShops() { return shops; }
                @Override public boolean showOtherServices() { return other; }
                @Override public boolean showSlayerMasters() { return slayer; }
                @Override public boolean showSkillFacilities() { return skills; }
                @Override public boolean showSailing() { return sailing; }
            };
            set(plugin, "client", client);
            set(plugin, "config", config);
            set(plugin, "transparentIcon", transparent);
            set(plugin, "lastLiveIconArray", live);
            invoke(plugin, "rebuildNamedIconCatalog");
            ((Map)get(plugin, "iconCatalog")).putAll(catalog());
            Class<? extends Enum> category = (Class<? extends Enum>)Class.forName("com.minimapfilter.MinimapFilterPlugin$IconCategory");
            Object sailingCategory = Enum.valueOf(category, "SAILING");
            for (int i = 0; i < 9; i++) { overrides().put(i, sailingCategory); }
        }

        @SuppressWarnings("unchecked")
        Map<Object,Object> catalog() throws Exception { return (Map<Object,Object>)get(plugin, "namedIconCatalog"); }
        @SuppressWarnings("unchecked")
        Map<Object,Object> overrides() throws Exception { return (Map<Object,Object>)get(plugin, "iconIndexOverrides"); }
        void filter() throws Exception { invoke(plugin, "applyIconFilters"); }
    }
}
