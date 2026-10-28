package com.minimapfilter;

import java.util.List;

/** Tests the map-key join with deliberately shuffled category keys and sprite IDs. */
public final class NamedMapIconChecks
{
    private int count;

    private void check(String name, boolean value)
    {
        count++;
        if (!value) { throw new AssertionError(name); }
    }

    public static void main(String[] args)
    {
        System.out.println("Named map-key checks passed: " + runAll());
    }

    public static int runAll()
    {
        NamedMapIconChecks c = new NamedMapIconChecks();
        String[] names = {"Junk checker", "Dye trader", "Rope trader", "Garden supplier",
            "Slayer Master", "Wine trader", "Holiday item trader", "Tea trader", "Taxidermist"};
        int[] keys = {710, 711, 712, 713, 714, 715, 716, 717, 718};
        int[] shuffledKeys = {718, 713, 710, 711, 714, 716, 712, 717, 715};
        int[] ids = {2018, 2013, 2010, 2011, 2014, 2016, 2012, 2017, 2015};
        List<NamedMapIconCatalog.Entry> entries = NamedMapIconCatalog.resolve(keys, names, shuffledKeys, ids);
        c.check("all requested names", entries.size() == 9);
        for (int i = 0; i < entries.size(); i++)
        {
            NamedMapIconCatalog.Entry entry = entries.get(i);
            c.check("joined by category not row " + i, entry.spriteId == 2010 + i);
            NamedMapIconCatalog.Category expected = i == 0 || i == 8
                ? NamedMapIconCatalog.Category.OTHER_SERVICE : i == 4
                ? NamedMapIconCatalog.Category.SLAYER_MASTER : NamedMapIconCatalog.Category.SHOP;
            c.check("requested category " + i, entry.category == expected);
            c.check("exact name retained " + i, entry.name.equals(names[i]));
        }
        c.check("case and whitespace", NamedMapIconCatalog.category("  SLAYER MASTER ")
            == NamedMapIconCatalog.Category.SLAYER_MASTER);
        for (String unrelated : new String[]{"Food shop", "Port task board", "Ledger table",
            "Slayer helmet", "Unknown Wine trader", "Garden", "Hunter Tutor", "", "null"})
        {
            c.check("unrelated name rejected: " + unrelated, NamedMapIconCatalog.category(unrelated) == null);
        }
        c.check("null name", NamedMapIconCatalog.category(null) == null);
        c.check("not-yet-loaded enums", NamedMapIconCatalog.resolve(null, null, null, null).isEmpty());
        c.check("mismatched data rejected", NamedMapIconCatalog.resolve(new int[]{1},
            new String[0], new int[]{1}, new int[]{5}).isEmpty());
        c.check("missing sprite not defaulted", NamedMapIconCatalog.resolve(new int[]{1},
            new String[]{"Dye trader"}, new int[]{2}, new int[]{5}).isEmpty());
        c.check("invalid sprite ignored", NamedMapIconCatalog.resolve(new int[]{1},
            new String[]{"Dye trader"}, new int[]{1}, new int[]{-1}).isEmpty());
        c.check("ambiguous sprite rejected", NamedMapIconCatalog.resolve(new int[]{1,2},
            new String[]{"Dye trader","Taxidermist"}, new int[]{1,2}, new int[]{5,5}).isEmpty());
        c.check("duplicate name category rejected", NamedMapIconCatalog.resolve(new int[]{1,1},
            new String[]{"Dye trader","Taxidermist"}, new int[]{1}, new int[]{5}).isEmpty());
        c.check("conflicting sprite key rejected", NamedMapIconCatalog.resolve(new int[]{1},
            new String[]{"Dye trader"}, new int[]{1,1}, new int[]{5,6}).isEmpty());
        c.check("empty enums harmless", NamedMapIconCatalog.resolve(new int[0], new String[0],
            new int[0], new int[0]).isEmpty());
        c.check("only explicit requests", NamedMapIconCatalog.resolve(new int[]{1,2},
            new String[]{"Garden supplier","Port task board"}, new int[]{1,2}, new int[]{5,6}).size() == 1);
        return c.count;
    }
}
