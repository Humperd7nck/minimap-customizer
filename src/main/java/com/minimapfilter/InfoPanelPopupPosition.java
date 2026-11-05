package com.minimapfilter;

import java.awt.Point;

/** Hidden per-profile persistence format for user-positioned Info/tool popups. */
final class InfoPanelPopupPosition
{
    private static final String PREFIX = "popupPosition";
    static final String SELECTOR_KEY = PREFIX + "InfoSelector";
    static final String TRACKING_KEY = PREFIX + "Tracking";

    private InfoPanelPopupPosition() { }

    static boolean isPositionKey(String key)
    {
        return key != null && key.startsWith(PREFIX);
    }

    static String key(InfoPanelText mode)
    {
        return mode == null ? PREFIX + "Unknown" : PREFIX + camel(mode);
    }

    private static String camel(InfoPanelText mode)
    {
        String[] parts = mode.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts)
        {
            if (!part.isEmpty()) { out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); }
        }
        return out.toString();
    }

    static String encode(Point point)
    {
        return point == null ? null : point.x + "," + point.y;
    }

    static Point decode(String value)
    {
        if (value == null) { return null; }
        int comma = value.indexOf(',');
        if (comma <= 0 || comma == value.length() - 1 || value.indexOf(',', comma + 1) >= 0) { return null; }
        try
        {
            int x = Integer.parseInt(value.substring(0, comma).trim());
            int y = Integer.parseInt(value.substring(comma + 1).trim());
            return new Point(x, y);
        }
        catch (NumberFormatException ignored) { return null; }
    }
}
