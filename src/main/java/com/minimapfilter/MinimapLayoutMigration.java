package com.minimapfilter;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.runelite.client.config.ConfigManager;

/** One-time migration from the Classic/Square dropdown to the checkbox. */
final class MinimapLayoutMigration
{
    static final String LEGACY = "minimapAppearance";
    static final String ENABLED = "enableSquareMinimap";

    private MinimapLayoutMigration() { }

    static void migrate(ConfigManager manager)
    {
        migrate(key -> manager.getConfiguration(MinimapFilterConfig.GROUP, key),
            (key, value) -> manager.setConfiguration(MinimapFilterConfig.GROUP, key, value),
            key -> manager.unsetConfiguration(MinimapFilterConfig.GROUP, key));
    }

    static void migrate(Function<String, String> read, BiConsumer<String, String> write,
        Consumer<String> remove)
    {
        String legacy = read.apply(LEGACY);
        if (!"SQUARE_HUD".equals(legacy) && !"CLASSIC".equals(legacy)) { return; }
        // Core may have already seeded the new false default on profile load.
        // The old key is the one-time signal, just like our foliage migration.
        write.accept(ENABLED, Boolean.toString("SQUARE_HUD".equals(legacy)));
        remove.accept(LEGACY);
        // Keep the existing Info panel view key and enum constants unchanged.
    }
}
