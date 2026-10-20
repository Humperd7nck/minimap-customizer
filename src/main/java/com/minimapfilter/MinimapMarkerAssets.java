package com.minimapfilter;

import java.awt.image.BufferedImage;
import javax.inject.Singleton;

@Singleton
public class MinimapMarkerAssets
{
    private volatile BufferedImage attackableNpcDot;
    private volatile BufferedImage nonCombatNpcDot;

    public BufferedImage getAttackableNpcDot()
    {
        return attackableNpcDot;
    }

    public BufferedImage getNonCombatNpcDot()
    {
        return nonCombatNpcDot;
    }

    public void setNpcDots(BufferedImage attackableNpcDot,
        BufferedImage nonCombatNpcDot)
    {
        this.attackableNpcDot = attackableNpcDot;
        this.nonCombatNpcDot = nonCombatNpcDot;
    }

    public void clear()
    {
        attackableNpcDot = null;
        nonCombatNpcDot = null;
    }
}
