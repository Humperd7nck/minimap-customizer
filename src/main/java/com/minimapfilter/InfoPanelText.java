package com.minimapfilter;

public enum InfoPanelText
{
    ZONE("Zone"),
    SYSTEM_TIME("System Time"),
    COORDS("Coords");

    private final String label;

    InfoPanelText(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
