package com.minimapfilter;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class WildernessSkullRegressionTest
{
    @Test
    public void rangeIconsOverlayAndSettings() throws Exception
    {
        assertTrue(WildernessSkullChecks.runAll() > 80);
    }

    @Test
    public void ordinaryNativeDotRestoresAcrossTransitions() throws Exception
    {
        assertTrue(WildernessDotLifecycleChecks.runAll() > 25);
    }
}
