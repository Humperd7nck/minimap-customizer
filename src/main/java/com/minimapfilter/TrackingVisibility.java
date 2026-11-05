package com.minimapfilter;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Persistent master visibility overrides for the Tracker popup.
 * These never mutate the detailed marker/icon settings underneath them.
 */
@Singleton
final class TrackingVisibility implements TrackingPopup.Model
{
    static final String ALL_KEY = "trackingAll";
    static final String ENTITIES_KEY = "trackingEntities";
    static final String GROUND_ITEMS_KEY = "trackingGroundItems";
    static final String MAP_ICONS_KEY = "trackingMapIcons";
    static final String FOLIAGE_KEY = "trackingFoliage";

    private final ConfigManager config;
    private boolean localAll = true;
    private boolean localEntities = true;
    private boolean localGroundItems = true;
    private boolean localMapIcons = true;
    private boolean localFoliage = true;

    @Inject
    TrackingVisibility(ConfigManager config)
    {
        this.config = config;
    }

    static boolean isTrackingKey(String key)
    {
        return ALL_KEY.equals(key) || ENTITIES_KEY.equals(key) || GROUND_ITEMS_KEY.equals(key)
            || MAP_ICONS_KEY.equals(key) || FOLIAGE_KEY.equals(key);
    }

    @Override public boolean all() { return value(ALL_KEY, localAll); }
    @Override public boolean stored(TrackingPopup.Category category)
    {
        switch (category)
        {
            case ENTITIES: return value(ENTITIES_KEY, localEntities);
            case GROUND_ITEMS: return value(GROUND_ITEMS_KEY, localGroundItems);
            case MAP_ICONS: return value(MAP_ICONS_KEY, localMapIcons);
            case FOLIAGE: return value(FOLIAGE_KEY, localFoliage);
            default: return true;
        }
    }

    boolean entities() { return all() && stored(TrackingPopup.Category.ENTITIES); }
    boolean groundItems() { return all() && stored(TrackingPopup.Category.GROUND_ITEMS); }
    boolean mapIcons() { return all() && stored(TrackingPopup.Category.MAP_ICONS); }
    boolean foliage() { return all() && stored(TrackingPopup.Category.FOLIAGE); }
    boolean anyEffective() { return entities() || groundItems() || mapIcons() || foliage(); }

    @Override public void toggleAll() { set(ALL_KEY, !all()); }

    @Override public void toggle(TrackingPopup.Category category)
    {
        if (!all() || category == null) { return; }
        boolean next = !stored(category);
        switch (category)
        {
            case ENTITIES: localEntities = next; set(ENTITIES_KEY, next); break;
            case GROUND_ITEMS: localGroundItems = next; set(GROUND_ITEMS_KEY, next); break;
            case MAP_ICONS: localMapIcons = next; set(MAP_ICONS_KEY, next); break;
            case FOLIAGE: localFoliage = next; set(FOLIAGE_KEY, next); break;
            default: break;
        }
    }

    private boolean value(String key, boolean fallback)
    {
        if (config == null) { return fallback; }
        String raw = config.getConfiguration(MinimapFilterConfig.GROUP, key);
        return raw == null ? true : Boolean.parseBoolean(raw);
    }

    private void set(String key, boolean value)
    {
        if (ALL_KEY.equals(key)) { localAll = value; }
        if (config != null) { config.setConfiguration(MinimapFilterConfig.GROUP, key, value); }
    }
}
