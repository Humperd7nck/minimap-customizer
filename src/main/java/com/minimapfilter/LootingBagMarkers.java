package com.minimapfilter;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.TileItem;
import net.runelite.client.game.ItemManager;

/** Values only the same ground-item piles that the existing overlay renders. */
@Singleton
final class LootingBagMarkers
{
    private final MinimapFilterConfig config;
    private final IntToLongFunction prices;
    private final Map<Integer, Long> framePrices = new HashMap<>();
    private final LootingBagIcons icons = new LootingBagIcons();
    private boolean coloring;

    @Inject
    LootingBagMarkers(MinimapFilterConfig config, ItemManager itemManager)
    {
        this(config, itemManager::getItemPrice);
    }

    // Tests use the same aggregator with an in-memory price source.
    LootingBagMarkers(MinimapFilterConfig config, IntToLongFunction prices)
    {
        this.config = config;
        this.prices = prices;
    }

    void beginFrame()
    {
        coloring = config.groundItemMarkerStyle() == GroundItemMarkerStyle.LOOTING_BAG
            && config.colorLootingBagIconsByValue();
        framePrices.clear();
    }

    BufferedImage imageFor(List<TileItem> items)
    {
        if (!coloring) { return LootingBagIcons.original(); }
        LootingBagValueTier tier = LootingBagValueTier.forValue(combinedValue(items));
        // Missing prices never use a customized tier color or an incomplete sum.
        return tier == null ? LootingBagIcons.original() : icons.forTier(tier, tier.selectedColor(config));
    }

    long combinedValue(List<TileItem> items)
    {
        if (items == null || items.isEmpty()) { return LootingBagValueTier.UNKNOWN; }
        long total = 0;
        for (TileItem item : items)
        {
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
            {
                return LootingBagValueTier.UNKNOWN;
            }
            // ItemManager is already backed by RuneLite's cached price table.
            // Within this render pass, look up each distinct item ID at most once.
            long price = framePrices.computeIfAbsent(item.getId(), this::price);
            total = LootingBagValueTier.addStack(total, price, item.getQuantity());
            if (total == LootingBagValueTier.UNKNOWN) { return total; }
            // Keep checking after saturation: a later unknown price still wins.
        }
        return total;
    }

    private long price(int itemId)
    {
        try
        {
            long value = prices.applyAsLong(itemId);
            return value > 0 ? value : LootingBagValueTier.UNKNOWN;
        }
        catch (RuntimeException unavailable)
        {
            // A temporarily missing definition/price must not remove the marker.
            return LootingBagValueTier.UNKNOWN;
        }
    }
}
