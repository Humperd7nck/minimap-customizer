package com.minimapfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Joins the game's map-key names and sprite enums by category, not minimap array slot. */
final class NamedMapIconCatalog
{
    // worldmap_init uses these two enums to draw the native map key.
    static final int CATEGORY_SPRITES = 1712;
    static final int CATEGORY_NAMES = 1713;
    static final int REQUESTED_COUNT = 9;

    enum Category { SHOP, OTHER_SERVICE, SLAYER_MASTER }

    static final class Entry
    {
        final String name;
        final int spriteId;
        final Category category;

        Entry(String name, int spriteId, Category category)
        {
            this.name = name;
            this.spriteId = spriteId;
            this.category = category;
        }
    }

    private static final Map<String, Category> REQUESTED;
    static
    {
        Map<String, Category> names = new LinkedHashMap<>();
        names.put("junk checker", Category.OTHER_SERVICE);
        names.put("dye trader", Category.SHOP);
        names.put("rope trader", Category.SHOP);
        names.put("garden supplier", Category.SHOP);
        names.put("slayer master", Category.SLAYER_MASTER);
        names.put("wine trader", Category.SHOP);
        names.put("holiday item trader", Category.SHOP);
        names.put("tea trader", Category.SHOP);
        names.put("taxidermist", Category.OTHER_SERVICE);
        REQUESTED = Collections.unmodifiableMap(names);
    }

    private NamedMapIconCatalog() {}

    static Category category(String name)
    {
        return name == null ? null : REQUESTED.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Missing/default values and ambiguous joins are ignored; similarity is never used. */
    static List<Entry> resolve(int[] nameKeys, String[] names, int[] spriteKeys, int[] sprites)
    {
        if (nameKeys == null || names == null || spriteKeys == null || sprites == null
            || nameKeys.length != names.length || spriteKeys.length != sprites.length)
        {
            return Collections.emptyList();
        }
        Map<Integer, Integer> spriteByCategory = new HashMap<>();
        Set<Integer> badKeys = new HashSet<>();
        for (int i = 0; i < spriteKeys.length; i++)
        {
            if (sprites[i] <= 0) { continue; }
            Integer old = spriteByCategory.putIfAbsent(spriteKeys[i], sprites[i]);
            if (old != null && old != sprites[i]) { badKeys.add(spriteKeys[i]); }
        }
        List<Entry> candidates = new ArrayList<>();
        Map<Integer, Category> groups = new HashMap<>();
        Set<Integer> ambiguousSprites = new HashSet<>();
        Set<Integer> seenNameKeys = new HashSet<>();
        Set<Integer> duplicateNameKeys = new HashSet<>();
        for (int key : nameKeys)
        {
            if (!seenNameKeys.add(key)) { duplicateNameKeys.add(key); }
        }
        for (int i = 0; i < nameKeys.length; i++)
        {
            Category group = category(names[i]);
            Integer sprite = spriteByCategory.get(nameKeys[i]);
            if (group == null || sprite == null || badKeys.contains(nameKeys[i])
                || duplicateNameKeys.contains(nameKeys[i])) { continue; }
            Category old = groups.putIfAbsent(sprite, group);
            if (old != null && old != group) { ambiguousSprites.add(sprite); }
            candidates.add(new Entry(names[i].trim(), sprite, group));
        }
        candidates.removeIf(entry -> ambiguousSprites.contains(entry.spriteId));
        return candidates;
    }
}
