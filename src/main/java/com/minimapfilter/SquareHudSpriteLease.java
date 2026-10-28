package com.minimapfilter;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.SpritePixels;

/** Temporary sprite overrides with identity-checked restoration. */
final class SquareHudSpriteLease
{
    private final Map<Integer, SpritePixels> previous = new HashMap<>();
    private final Map<Integer, SpritePixels> installed = new HashMap<>();

    boolean put(Map<Integer, SpritePixels> overrides, int id, SpritePixels replacement)
    {
        SpritePixels current = overrides.get(id);
        if (installed.containsKey(id) && current == installed.get(id) && current == replacement)
        {
            return false;
        }
        if (!installed.containsKey(id) || current != installed.get(id))
        {
            previous.put(id, current);
        }
        installed.put(id, replacement);
        overrides.put(id, replacement);
        return current != replacement;
    }

    boolean restore(Map<Integer, SpritePixels> overrides)
    {
        boolean changed = false;
        if (overrides != null)
        {
            for (Map.Entry<Integer, SpritePixels> entry : installed.entrySet())
            {
                if (overrides.get(entry.getKey()) == entry.getValue())
                {
                    SpritePixels original = previous.get(entry.getKey());
                    if (original == null)
                    {
                        overrides.remove(entry.getKey());
                    }
                    else
                    {
                        overrides.put(entry.getKey(), original);
                    }
                    changed = true;
                }
            }
        }
        installed.clear();
        previous.clear();
        return changed;
    }
}
