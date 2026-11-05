package com.minimapfilter;

public enum AttackableNpcMarkerStyle
{
    NATIVE_DOT("Native dot"),
    SMALL_SWORD_B("Small sword"),
    SMALL_SWORD_C("Med Sword"),
    SWORD("Big sword"),
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
