package com.minimapfilter;

public enum PlayerMarkerStyle
{
    NATIVE("Native marker"),
    DIRECTION_ARROW("Direction arrow");

    private final String displayName;

    PlayerMarkerStyle(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
