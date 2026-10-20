package com.minimapfilter;

public enum GroundItemMarkerStyle
{
    NATIVE_DOT("Native dot"),
    LOOTING_BAG("Looting bag"),
    OFF("Off");

    private final String label;

    GroundItemMarkerStyle(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
