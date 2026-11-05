package com.minimapfilter;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.runelite.client.config.ConfigManager;

/** One-time migration from the retired original Small sword marker to Small sword B. */
final class AttackableNpcMarkerStyleMigration
{
    static final String KEY = "attackableNpcMarkerStyle";
    private static final String RETIRED_SMALL_SWORD = "SMALL_SWORD";
    private static final String CURRENT_SMALL_SWORD = "SMALL_SWORD_B";

    private AttackableNpcMarkerStyleMigration() { }

    static void migrate(ConfigManager manager)
    {
        migrate(key -> manager.getConfiguration(MinimapFilterConfig.GROUP, key),
            (key, value) -> manager.setConfiguration(MinimapFilterConfig.GROUP, key, value));
    }

    static void migrate(Function<String, String> read, BiConsumer<String, String> write)
    {
        String value = read.apply(KEY);
        if (RETIRED_SMALL_SWORD.equals(value))
        {
            write.accept(KEY, CURRENT_SMALL_SWORD);
        }
    }
}
