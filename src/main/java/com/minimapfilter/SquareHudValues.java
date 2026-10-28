package com.minimapfilter;

/** Conversion and fill arithmetic, independent of the game client. */
final class SquareHudValues
{
    private SquareHudValues() {}

    static int runValue(int energy)
    {
        return Math.max(0, Math.min(10000, energy)) / 100;
    }

    static int specialValue(int energy)
    {
        return Math.max(0, Math.min(1000, energy)) / 10;
    }

    static double fraction(int current, int maximum)
    {
        return maximum <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, current / (double) maximum));
    }

    static int fillHeight(int height, double fraction)
    {
        if (height <= 0 || !Double.isFinite(fraction))
        {
            return 0;
        }
        return (int) Math.round(height * Math.max(0.0, Math.min(1.0, fraction)));
    }
}
