package com.minimapfilter;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MinimapFilterConfig.GROUP)
public interface MinimapFilterConfig extends Config
{
    String GROUP = "minimapfilter";

    @ConfigSection(
        name = "Entity markers",
        description = "Choose how your player, entities and ground items are shown on the minimap.",
        position = 0
    )
    String ENTITY_DOTS = "entityDots";

    @ConfigSection(
        name = "Marker colors",
        description = "Customize the native minimap dot colors. The direction arrow, sword and looting bag retain their artwork.",
        position = 1,
        closedByDefault = true
    )
    String MARKER_COLORS = "markerColors";

    @ConfigSection(
        name = "Map icons",
        description = "Choose which native map icon categories are visible.",
        position = 2
    )
    String MAP_ICONS = "mapIcons";

    @ConfigItem(
        keyName = "playerMarkerStyle",
        name = "Player (Self)",
        description = "Use the native player marker or overlay it with an arrow showing your current facing direction.",
        section = ENTITY_DOTS,
        position = 0
    )
    default PlayerMarkerStyle playerMarkerStyle()
    {
        return PlayerMarkerStyle.DIRECTION_ARROW;
    }

    @ConfigItem(
        keyName = "attackableNpcMarkerStyle",
        name = "Attackable NPCs",
        description = "Use the native dot, a compact sword icon, or no marker for attackable NPCs.",
        section = ENTITY_DOTS,
        position = 1
    )
    default AttackableNpcMarkerStyle attackableNpcMarkerStyle()
    {
        return AttackableNpcMarkerStyle.NATIVE_DOT;
    }

    @ConfigItem(
        keyName = "showNonCombatNpcs",
        name = "Non-combat NPCs",
        description = "Show native minimap dots for NPCs that do not have an Attack option.",
        section = ENTITY_DOTS,
        position = 2
    )
    default boolean showNonCombatNpcs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "groundItemMarkerStyle",
        name = "Ground items",
        description = "Use the native dot, a looting bag icon, or no marker for ground-item piles.",
        section = ENTITY_DOTS,
        position = 3
    )
    default GroundItemMarkerStyle groundItemMarkerStyle()
    {
        return GroundItemMarkerStyle.NATIVE_DOT;
    }

    @ConfigItem(
        keyName = "showPlayers",
        name = "Other players",
        description = "Show ordinary player dots. Your own center marker is controlled separately above.",
        section = ENTITY_DOTS,
        position = 4
    )
    default boolean showPlayers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showFriends",
        name = "Friends",
        description = "Show friend dots.",
        section = ENTITY_DOTS,
        position = 5
    )
    default boolean showFriends()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showTeam",
        name = "Team",
        description = "Show team-cape dots.",
        section = ENTITY_DOTS,
        position = 6
    )
    default boolean showTeam()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showFriendsChat",
        name = "Friends chat",
        description = "Show friends-chat member dots.",
        section = ENTITY_DOTS,
        position = 7
    )
    default boolean showFriendsChat()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showClan",
        name = "Clan",
        description = "Show clan-member dots.",
        section = ENTITY_DOTS,
        position = 8
    )
    default boolean showClan()
    {
        return true;
    }

    @ConfigItem(
        keyName = "attackableNpcDotColor",
        name = "Attackable NPCs",
        description = "Used only when the Attackable NPCs style is Native dot. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 0
    )
    default Color attackableNpcDotColor()
    {
        return NativeDotColors.NPC;
    }

    @ConfigItem(
        keyName = "nonCombatNpcDotColor",
        name = "Non-combat NPCs",
        description = "Color of the native dots for non-attackable NPCs. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 1
    )
    default Color nonCombatNpcDotColor()
    {
        return NativeDotColors.NPC;
    }

    @ConfigItem(
        keyName = "groundItemDotColor",
        name = "Ground items",
        description = "Used only when the Ground items style is Native dot. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 2
    )
    default Color groundItemDotColor()
    {
        return NativeDotColors.ITEM;
    }

    @ConfigItem(
        keyName = "otherPlayerDotColor",
        name = "Other players",
        description = "Color of ordinary player dots (not Player Self). Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 3
    )
    default Color otherPlayerDotColor()
    {
        return NativeDotColors.PLAYER;
    }

    @ConfigItem(
        keyName = "friendDotColor",
        name = "Friends",
        description = "Color of friend dots. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 4
    )
    default Color friendDotColor()
    {
        return NativeDotColors.FRIEND;
    }

    @ConfigItem(
        keyName = "teamDotColor",
        name = "Team",
        description = "Color of team-cape dots. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 5
    )
    default Color teamDotColor()
    {
        return NativeDotColors.TEAM;
    }

    @ConfigItem(
        keyName = "friendsChatDotColor",
        name = "Friends chat",
        description = "Color of friends chat member dots. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 6
    )
    default Color friendsChatDotColor()
    {
        return NativeDotColors.FRIENDS_CHAT;
    }

    @ConfigItem(
        keyName = "clanDotColor",
        name = "Clan",
        description = "Color of clan member dots. Right-click to reset to the original color.",
        section = MARKER_COLORS,
        position = 7
    )
    default Color clanDotColor()
    {
        return NativeDotColors.CLAN;
    }

    @ConfigItem(
        keyName = "showBanks",
        name = "Banks",
        description = "Show bank and Grand Exchange map icons.",
        section = MAP_ICONS,
        position = 0
    )
    default boolean showBanks()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showAgilityTraining",
        name = "Agility courses",
        description = "Show agility training/course map icons.",
        section = MAP_ICONS,
        position = 1
    )
    default boolean showAgilityTraining()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showAgilityShortcuts",
        name = "Agility shortcuts",
        description = "Show agility shortcut map icons.",
        section = MAP_ICONS,
        position = 2
    )
    default boolean showAgilityShortcuts()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showAltars",
        name = "Altars",
        description = "Show altar map icons.",
        section = MAP_ICONS,
        position = 3
    )
    default boolean showAltars()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showQuestStarts",
        name = "Quests",
        description = "Show quest-start map icons.",
        section = MAP_ICONS,
        position = 4
    )
    default boolean showQuestStarts()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showMinigames",
        name = "Minigames / raids",
        description = "Show minigame and raid-lobby map icons.",
        section = MAP_ICONS,
        position = 5
    )
    default boolean showMinigames()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showTransportation",
        name = "Transportation",
        description = "Show transportation and house-portal map icons.",
        section = MAP_ICONS,
        position = 6
    )
    default boolean showTransportation()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showDungeons",
        name = "Dungeons",
        description = "Show dungeon icons and their associated up/down map-link arrows.",
        section = MAP_ICONS,
        position = 7
    )
    default boolean showDungeons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showFarmingPatches",
        name = "Farming patches",
        description = "Show farming-patch map icons.",
        section = MAP_ICONS,
        position = 8
    )
    default boolean showFarmingPatches()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showFishingSpots",
        name = "Fishing spots",
        description = "Show fishing-spot map icons.",
        section = MAP_ICONS,
        position = 9
    )
    default boolean showFishingSpots()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showMiningSites",
        name = "Mining sites",
        description = "Show mining-site map icons.",
        section = MAP_ICONS,
        position = 10
    )
    default boolean showMiningSites()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showFoliage",
        name = "Foliage (bushes/plants/trees)",
        description = "Show ordinary trees, dead trees, bushes, and small plant/brush minimap icons when checked.",
        section = MAP_ICONS,
        position = 11
    )
    default boolean showFoliage()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showRareTrees",
        name = "Rare trees (oak+)",
        description = "Show the Rare Trees marker and individual oak, willow, maple, yew, and magic minimap symbols when checked.",
        section = MAP_ICONS,
        position = 12
    )
    default boolean showRareTrees()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showSkillFacilities",
        name = "Skilling facilities",
        description = "Show furnaces, anvils, ranges, spinning wheels, sawmills, and similar skill-related icons.",
        section = MAP_ICONS,
        position = 13
    )
    default boolean showSkillFacilities()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showWaterSources",
        name = "Water sources",
        description = "Show regular and stagnant water-source map icons.",
        section = MAP_ICONS,
        position = 14
    )
    default boolean showWaterSources()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showShops",
        name = "Shops",
        description = "Show shop and trader map icons.",
        section = MAP_ICONS,
        position = 15
    )
    default boolean showShops()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showSailing",
        name = "Sailing facilities",
        description = "Show Sailing port-task, ledger, salvaging-station, and docking-point map icons.",
        section = MAP_ICONS,
        position = 16
    )
    default boolean showSailing()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showTutors",
        name = "Tutors",
        description = "Show tutor and new-player guide map icons (including Bank, Combat, Skill, Ironman, and other Tutors).",
        section = MAP_ICONS,
        position = 17
    )
    default boolean showTutors()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showTaskMaster",
        name = "Task Masters",
        description = "Show Task Master map icons.",
        section = MAP_ICONS,
        position = 18
    )
    default boolean showTaskMaster()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showPubs",
        name = "Pubs",
        description = "Show pub and tavern map icons.",
        section = MAP_ICONS,
        position = 19
    )
    default boolean showPubs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showOtherServices",
        name = "Other services",
        description = "Show other utility icons, including Speedrunning shop, Newspaper trader, and Clan Hub.",
        section = MAP_ICONS,
        position = 20
    )
    default boolean showOtherServices()
    {
        return true;
    }
}
