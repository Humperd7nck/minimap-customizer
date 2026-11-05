package com.minimapfilter;

/** Enum names are persisted by RuneLite; retain the three original keys. */
public enum InfoPanelText
{
    ZONE("Zone"),
    SYSTEM_TIME("Local Time"),
    COORDS("Coords"),
    COORDS_AND_PLANE("Coords + Plane"),
    CURRENT_WORLD("Current World"),
    FRIENDS_ONLINE("Friends Online"),
    CLANMATES_ONLINE("Clanmates Online"),
    FRIENDS_AND_CLANMATES("Friends + Clanmates Online"),
    FPS("FPS"),
    PING("Ping"),
    FPS_AND_PING("FPS + Ping"),
    INVENTORY("Inventory"),
    TOTAL_XP("Total XP"),
    TOTAL_LEVEL("Total Lvl"),
    TIMER("Timer"),
    STOPWATCH("Stopwatch"),
    CALCULATOR("Calculator"),
    VOLUME("Volume"),
    SERVER_TIME("Server Time"),
    NOTES("Notes / Checklist"),
    ALARM("Alarm"),
    NOTHING("-Nothing-"),
    DAY_DATE("Day + Date");

    private final String label;
    InfoPanelText(String label) { this.label = label; }
    boolean isTool() { return this == TIMER || this == STOPWATCH || this == CALCULATOR || this == NOTES || this == ALARM; }
    boolean usesPing() { return this == PING || this == FPS_AND_PING; }
    boolean usesFriends() { return this == FRIENDS_ONLINE || this == FRIENDS_AND_CLANMATES; }
    boolean usesClan() { return this == CLANMATES_ONLINE || this == FRIENDS_AND_CLANMATES; }
    @Override public String toString() { return label; }
}
