package com.minimapfilter;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class LootingBagRegressionTest
{
    @Test
    public void combinedValueColorsAndMissingPrices() throws Exception
    {
        assertTrue(LootingBagChecks.runAll() > 0);
    }

    @Test
    public void inactiveSectionAndRestoration() throws Exception
    {
        assertTrue(LootingBagControlsChecks.runAll() > 0);
    }

}
