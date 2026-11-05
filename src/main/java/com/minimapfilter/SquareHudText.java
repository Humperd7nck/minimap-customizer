package com.minimapfilter;

import java.awt.FontMetrics;

final class SquareHudText
{
    private SquareHudText() { }

    static String[] wrapTwoLines(String text, FontMetrics metrics, int width)
    {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) { return new String[]{"", ""}; }
        int separator = value.indexOf(" | ");
        if (separator > 0)
        {
            String a = value.substring(0, separator).trim();
            String b = value.substring(separator + 3).trim();
            if (!a.isEmpty() && !b.isEmpty() && metrics.stringWidth(a) <= width && metrics.stringWidth(b) <= width)
            {
                return new String[]{a, b};
            }
        }
        String[] words = value.split(" +");
        int best = -1, bestBalance = Integer.MAX_VALUE;
        for (int i = 1; i < words.length; i++)
        {
            String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i));
            String b = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            int aw = metrics.stringWidth(a), bw = metrics.stringWidth(b);
            if (aw <= width && bw <= width && Math.abs(aw - bw) < bestBalance)
            { best = i; bestBalance = Math.abs(aw - bw); }
        }
        if (best > 0)
        {
            return new String[]{String.join(" ", java.util.Arrays.copyOfRange(words, 0, best)),
                String.join(" ", java.util.Arrays.copyOfRange(words, best, words.length))};
        }
        int split = Math.max(1, value.length() / 2);
        int space = value.lastIndexOf(' ', split);
        if (space <= 0) { space = value.indexOf(' ', split); }
        if (space <= 0) { return new String[]{fit(value, metrics, width), ""}; }
        return new String[]{fit(value.substring(0, space).trim(), metrics, width),
            fit(value.substring(space + 1).trim(), metrics, width)};
    }

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
