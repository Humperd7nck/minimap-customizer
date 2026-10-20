package com.minimapfilter;

public enum AttackableNpcMarkerStyle
{
    NATIVE_DOT("Native dot"),
    SWORD("Sword"),
    OFF("Off");

    private final String label;

    AttackableNpcMarkerStyle(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
