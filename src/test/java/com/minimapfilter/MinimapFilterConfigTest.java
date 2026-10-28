package com.minimapfilter;

import java.awt.Color;
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
                && !method.getName().equals("colorLootingBagIconsByValue")
                && !method.getName().equals("wildernessPlayerSkulls"))
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
}
