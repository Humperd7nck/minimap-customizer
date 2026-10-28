package com.minimapfilter;

import java.awt.FontMetrics;

final class SquareHudText
{
    private SquareHudText() { }

    static String fit(String text, FontMetrics metrics, int width)
    {
        if (text == null || width <= 0) { return ""; }
        if (metrics.stringWidth(text) <= width) { return text; }
        String suffix = "...";
        if (metrics.stringWidth(suffix) > width) { return ""; }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > width)
        {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).stripTrailing() + suffix;
    }
}
