package com.minimapfilter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.*;

/** Smoke-test fresh-install defaults and original native dot colors. */
public class MinimapFilterConfigTest
{
    private final MinimapFilterConfig config = new MinimapFilterConfig() {};

    @Test
    public void everyVisibilityCheckboxIsCheckedByDefault() throws Exception
    {
        int count = 0;
        for (java.lang.reflect.Method method : MinimapFilterConfig.class.getDeclaredMethods())
        {
            if (method.isAnnotationPresent(net.runelite.client.config.ConfigItem.class)
                && method.getReturnType() == boolean.class
                && !method.getName().equals("enableSquareMinimap")
                && !method.getName().equals("colorLootingBagIconsByValue"))
            {
                assertTrue("Visibility setting is unchecked: " + method.getName(),
                    (Boolean) method.invoke(config));
                count++;
            }
        }
        assertTrue("Expected all visibility options to be inspected", count >= 20);
    }

    @Test
    public void terrainRenderingAndCaptureAreNotUserFacingSettings()
    {
        for (java.lang.reflect.Method method : MinimapFilterConfig.class.getDeclaredMethods())
        {
            assertFalse("Obsolete diagnostic setting: " + method.getName(),
                method.getName().startsWith("squareHudTerrain")
                    || method.getName().equals("squareHudRenderCapture"));
        }
    }

    @Test
    public void defaultsShowBothTreeGroups()
    {
        assertTrue(config.showFoliage());
        assertTrue(config.showRareTrees());
    }

    @Test
    public void markerDefaultsMatchReleasePreferences()
    {
        assertEquals(PlayerMarkerStyle.DIRECTION_ARROW, config.playerMarkerStyle());
        assertEquals(AttackableNpcMarkerStyle.NATIVE_DOT, config.attackableNpcMarkerStyle());
        assertEquals(GroundItemMarkerStyle.NATIVE_DOT, config.groundItemMarkerStyle());
    }

    @Test
    public void attackableNpcMarkerOrderAndSwordAssetsMatchUi() throws Exception
    {
        AttackableNpcMarkerStyle[] values = AttackableNpcMarkerStyle.values();
        assertArrayEquals(new AttackableNpcMarkerStyle[]
        {
            AttackableNpcMarkerStyle.NATIVE_DOT,
            AttackableNpcMarkerStyle.SMALL_SWORD_B,
            AttackableNpcMarkerStyle.SMALL_SWORD_C,
            AttackableNpcMarkerStyle.SWORD,
            AttackableNpcMarkerStyle.OFF
        }, values);
        assertEquals("Native dot", values[0].toString());
        assertEquals("Small sword", values[1].toString());
        assertEquals("Med Sword", values[2].toString());
        assertEquals("Big sword", values[3].toString());
        assertEquals("Off", values[4].toString());

        BufferedImage big = ImageIO.read(MinimapFilterConfigTest.class.getResourceAsStream(
            "/com/minimapfilter/sword_option_a.png"));
        BufferedImage small = ImageIO.read(MinimapFilterConfigTest.class.getResourceAsStream(
            "/com/minimapfilter/sword_small_b.png"));
        BufferedImage medium = ImageIO.read(MinimapFilterConfigTest.class.getResourceAsStream(
            "/com/minimapfilter/sword_small_c.png"));
        assertNotNull(big);
        assertNotNull(small);
        assertNotNull(medium);
        assertEquals(17, big.getWidth());
        assertEquals(17, big.getHeight());
        assertEquals(9, small.getWidth());
        assertEquals(9, small.getHeight());
        assertEquals(11, medium.getWidth());
        assertEquals(11, medium.getHeight());
        assertFalse("Small and Med sword must remain distinct assets", imagesEqual(small, medium));
        assertNull("Retired Small sword A resource should not remain packaged",
            MinimapFilterConfigTest.class.getResource("/com/minimapfilter/sword_small.png"));
    }

    @Test
    public void retiredSmallSwordSelectionMigratesToNewSmallSword()
    {
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put(AttackableNpcMarkerStyleMigration.KEY, "SMALL_SWORD");
        AttackableNpcMarkerStyleMigration.migrate(saved::get, saved::put);
        assertEquals("SMALL_SWORD_B", saved.get(AttackableNpcMarkerStyleMigration.KEY));

        saved.put(AttackableNpcMarkerStyleMigration.KEY, "SMALL_SWORD_B");
        AttackableNpcMarkerStyleMigration.migrate(saved::get, saved::put);
        assertEquals("SMALL_SWORD_B", saved.get(AttackableNpcMarkerStyleMigration.KEY));

        saved.put(AttackableNpcMarkerStyleMigration.KEY, "SMALL_SWORD_C");
        AttackableNpcMarkerStyleMigration.migrate(saved::get, saved::put);
        assertEquals("SMALL_SWORD_C", saved.get(AttackableNpcMarkerStyleMigration.KEY));

        saved.put(AttackableNpcMarkerStyleMigration.KEY, "SWORD");
        AttackableNpcMarkerStyleMigration.migrate(saved::get, saved::put);
        assertEquals("SWORD", saved.get(AttackableNpcMarkerStyleMigration.KEY));
    }

    @Test
    public void markerColorDefaultsMatchNativeDotColors()
    {
        assertEquals(NativeDotColors.NPC, config.attackableNpcDotColor());
        assertEquals(NativeDotColors.NPC, config.nonCombatNpcDotColor());
        assertEquals(NativeDotColors.ITEM, config.groundItemDotColor());
        assertEquals(NativeDotColors.PLAYER, config.otherPlayerDotColor());
        assertEquals(NativeDotColors.FRIEND, config.friendDotColor());
        assertEquals(NativeDotColors.TEAM, config.teamDotColor());
        assertEquals(NativeDotColors.FRIENDS_CHAT, config.friendsChatDotColor());
        assertEquals(NativeDotColors.CLAN, config.clanDotColor());
    }

    @Test
    public void unrelatedIconCategoriesRemainVisibleByDefault()
    {
        assertTrue(config.showSailing());
        assertTrue(config.showTutors());
        assertTrue(config.showTaskMaster());
        assertTrue(config.showPubs());
        assertTrue(config.showShops());
    }
    private static boolean imagesEqual(BufferedImage a, BufferedImage b)
    {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
        {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++)
        {
            for (int x = 0; x < a.getWidth(); x++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y))
                {
                    return false;
                }
            }
        }
        return true;
    }

}
