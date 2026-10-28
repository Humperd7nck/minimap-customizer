package com.minimapfilter;

public enum SquareHudFooterMode
{
    ALWAYS_VISIBLE("Always"),
    MOUSEOVER_ONLY("Mouseover only");

    private final String label;
    SquareHudFooterMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
