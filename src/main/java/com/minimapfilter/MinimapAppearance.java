package com.minimapfilter;

public enum MinimapAppearance
{
    CLASSIC("Classic"),
    SQUARE_HUD("Square HUD");

    private final String label;

    MinimapAppearance(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
