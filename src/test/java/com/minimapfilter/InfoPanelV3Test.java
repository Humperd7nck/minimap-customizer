package com.minimapfilter;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class InfoPanelV3Test
{
    @Test
    public void formatsTimingArithmeticPingSocialAndSwing() throws Exception
    {
        assertTrue(InfoPanelQuickControlsChecks.runAll() > 0);
        assertTrue(InfoPanelToolDrawerChecks.runAll() > 0);
        assertTrue(TrackingPopupChecks.runAll() > 0);
        assertTrue(InfoPanelV3Checks.runAll() > 0);
        assertTrue(InfoPanelReminderChecks.runAll() > 0);
        assertTrue(InfoPanelReminderBannerChecks.runAll() > 0);
        assertTrue(InfoPanelLayoutChecks.runAll() > 0);
        assertTrue(InfoPanelDropdownChecks.runAll() > 0);
        assertTrue(InfoPanelVolumeChecks.runAll() > 0);
        assertTrue(InfoPanelVolumeBarChecks.runAll() > 0);
    }
}
