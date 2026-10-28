package com.minimapfilter;

import java.awt.Color;

/** Inclusive lower bounds for combined ground-item value on one tile. */
enum LootingBagValueTier
{
    GRAY(0L, new Color(0x909090)),
    BROWN(1_000L, new Color(198, 147, 73)),
    GREEN(10_000L, new Color(0x48B85A)),
    BLUE(100_000L, new Color(0x4098EB)),
    RED(1_000_000L, new Color(0xE84C4C)),
    PURPLE(10_000_000L, new Color(0xB45CE8));

    static final long UNKNOWN = -1L;
    private final long minimum;
    private final Color defaultColor;

    LootingBagValueTier(long minimum, Color defaultColor)
    {
        this.minimum = minimum;
        this.defaultColor = defaultColor;
    }

    Color defaultColor()
    {
        return defaultColor;
    }

    /** Null means an unknown total, not the low-value gray tier. */
    static LootingBagValueTier forValue(long total)
    {
        if (total < 0) { return null; }
        if (total >= PURPLE.minimum) { return PURPLE; }
        if (total >= RED.minimum) { return RED; }
        if (total >= BLUE.minimum) { return BLUE; }
        if (total >= GREEN.minimum) { return GREEN; }
        if (total >= BROWN.minimum) { return BROWN; }
        return GRAY;
    }

    /** A saturated total still belongs to the highest tier; never let it wrap. */
    static long addStack(long total, long unitPrice, int quantity)
    {
        if (total < 0 || unitPrice <= 0 || quantity <= 0) { return UNKNOWN; }
        if (unitPrice > (Long.MAX_VALUE - total) / quantity) { return Long.MAX_VALUE; }
        return total + unitPrice * quantity;
    }

    Color selectedColor(MinimapFilterConfig config)
    {
        Color selected;
        switch (this)
        {
            case GRAY: selected = config.lootingBagColorUnder1k(); break;
            case BROWN: selected = config.lootingBagColor1k(); break;
            case GREEN: selected = config.lootingBagColor10k(); break;
            case BLUE: selected = config.lootingBagColor100k(); break;
            case RED: selected = config.lootingBagColor1m(); break;
            default: selected = config.lootingBagColor10m(); break;
        }
        return selected == null ? defaultColor : selected;
    }
}
