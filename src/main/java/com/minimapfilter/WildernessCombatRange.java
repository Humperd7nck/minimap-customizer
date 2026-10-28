package com.minimapfilter;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.WorldType;

/** The local player's normal-world level bracket, not an attack-permission test. */
final class WildernessCombatRange
{
    enum Marker { RED, WHITE, DOT }

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern LEVEL = Pattern.compile("^\\s*Level:\\s*(\\d{1,3})(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    final int low;
    final int high;

    private WildernessCombatRange(int combat, int wilderness)
    {
        low = Math.max(3, combat - wilderness);
        high = Math.min(126, combat + wilderness);
    }

    static WildernessCombatRange from(int combat, String nativeLevelText)
    {
        if (!validCombatLevel(combat) || nativeLevelText == null) { return null; }
        Matcher match = LEVEL.matcher(TAGS.matcher(nativeLevelText).replaceAll(" "));
        if (!match.find()) { return null; }
        int wilderness = Integer.parseInt(match.group(1));
        if (wilderness < 1 || wilderness > 100) { return null; }
        return new WildernessCombatRange(combat, wilderness);
    }

    Marker marker(int combat)
    {
        if (!validCombatLevel(combat)) { return Marker.DOT; }
        return combat >= low && combat <= high ? Marker.RED : Marker.WHITE;
    }

    static boolean validCombatLevel(int combat)
    {
        return combat >= 3 && combat <= 126;
    }

    static boolean standardWorld(Set<WorldType> types)
    {
        if (types == null) { return false; }
        // Opt in only to ordinary world flags. PvP, Deadman, seasonal, beta,
        // Bounty Hunter and other special modes retain normal dots for now.
        for (WorldType type : types)
        {
            if (type != WorldType.MEMBERS && type != WorldType.SKILL_TOTAL
                && type != WorldType.HIGH_RISK && type != WorldType.FRESH_START_WORLD
                && type != WorldType.LEGACY_ONLY)
            {
                return false;
            }
        }
        return true;
    }
}
