package com.minimapfilter;

/** Immutable client-thread sample. Negative values mean unavailable, not zero. */
final class InfoPanelMetrics
{
    static final InfoPanelMetrics EMPTY = new InfoPanelMetrics(-1, -1, -1, -1, -1, -1, -1);
    final int world, friends, clanmates, fps, inventory, totalLevel;
    final long totalXp;

    InfoPanelMetrics(int world, int friends, int clanmates, int fps, int inventory,
        long totalXp, int totalLevel)
    {
        this.world = world;
        this.friends = friends;
        this.clanmates = clanmates;
        this.fps = fps;
        this.inventory = inventory;
        this.totalXp = totalXp;
        this.totalLevel = totalLevel;
    }
}
