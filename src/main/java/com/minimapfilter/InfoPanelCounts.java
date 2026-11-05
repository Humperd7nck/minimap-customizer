package com.minimapfilter;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Social counting rules without live client access, for deterministic regression tests. */
final class InfoPanelCounts
{
    static final class Member
    {
        final String name;
        final int world;
        Member(String name, int world) { this.name = name; this.world = world; }
    }
    static int friends(List<Member> members)
    {
        if (members == null) { return -1; }
        Set<String> online = new HashSet<>();
        for (Member member : members)
        {
            if (member != null && member.world > 0)
            {
                String name = normalize(member.name);
                if (!name.isEmpty()) { online.add(name); }
            }
        }
        return online.size();
    }
    static int clanmates(List<String> roster, List<Member> channel, String localName)
    {
        String self = normalize(localName);
        if (roster == null || channel == null || self.isEmpty()) { return -1; }
        Set<String> clan = new HashSet<>();
        for (String name : roster)
        {
            String normalized = normalize(name);
            if (!normalized.isEmpty()) { clan.add(normalized); }
        }
        Set<String> online = new HashSet<>();
        for (Member member : channel)
        {
            if (member == null || member.world <= 0) { continue; }
            String name = normalize(member.name);
            if (!name.equals(self) && clan.contains(name)) { online.add(name); }
        }
        return online.size();
    }
    static String normalize(String name)
    {
        return name == null ? "" : name.replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }
    private InfoPanelCounts() { }
}
