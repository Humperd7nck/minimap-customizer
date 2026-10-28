package com.minimapfilter;

import org.junit.Test;
import static org.junit.Assert.*;

public class SquareHudRegressionTest
{
    @Test
    public void compactPanelAndCrispNativeIcons() throws Exception
    {
        assertTrue(SquareHudVisualPolishChecks.runAll() > 0);
    }

    @Test
    public void infoPanelSettingsTextAndMigration() throws Exception
    {
        assertTrue(SquareHudInfoPanelChecks.runAll() > 0);
    }

    @Test
    public void stateColorsAndStackedIndicators()
    {
        assertTrue(SquareHudResourceStyleChecks.runAll() > 0);
    }

    @Test
    public void explicitMapKeyCategories()
    {
        assertTrue(NamedMapIconChecks.runAll() > 0);
        assertTrue(new MinimapFilterConfig() {}.showSlayerMasters());
    }

    @Test
    public void footerGeometryVisibilityAndLocationNames() throws Exception
    {
        assertTrue(SquareHudFooterChecks.runAll() > 0);
    }

    @Test
    public void opaqueMapBackdrop()
    {
        assertEquals(14, SquareHudBackdropChecks.runAll());
    }

    @Test
    public void geometryValuesAndRestoration()
    {
        assertEquals(54, SquareHudChecks.runAll());
    }

    @Test
    public void repeatedFramesAndNativeButtonVisibility() throws Exception
    {
        assertTrue(SquareHudControllerChecks.runAll() > 0);
    }

    @Test
    public void classicMinimapContainerDoesNotKeepFullCanvasBounds()
    {
        assertTrue(SquareHudController.needsClassicContainerSizeRepair(1524, 912, 207, 197));
        assertFalse(SquareHudController.needsClassicContainerSizeRepair(207, 197, 207, 197));
        assertFalse(SquareHudController.needsClassicContainerSizeRepair(211, 199, 207, 197));
        assertFalse(SquareHudController.needsClassicContainerSizeRepair(1524, 912, 1524, 912));
        assertFalse(SquareHudController.needsClassicContainerSizeRepair(1000, 900, 0, 0));
    }

    @Test
    public void classicEntryBaselineRejectsFullCanvasButAcceptsNormalResizes()
    {
        assertTrue(SquareHudController.needsClassicBaselineRepair(1524, 912, 213, 296));
        assertFalse(SquareHudController.needsClassicBaselineRepair(213, 296, 213, 296));
        assertFalse(SquareHudController.needsClassicBaselineRepair(235, 312, 213, 296));
        assertFalse(SquareHudController.needsClassicBaselineRepair(1524, 912, 0, 0));
        assertFalse(SquareHudController.needsClassicBaselineRepair(1524, 912, 1524, 912));
    }

    @Test
    public void classicIsTheDefault()
    {
        MinimapFilterConfig config = new MinimapFilterConfig() {};
        assertFalse(config.enableSquareMinimap());
    }
}
