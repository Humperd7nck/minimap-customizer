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
        name = "Looting bag colors (ground item icon)",
        description = "To enable: Entity markers > Ground items > Looting bag. Colors use the combined value of every stack on a tile.",
        position = 1,
        closedByDefault = true
    )
    String LOOTING_BAG_COLORS = "lootingBagColors";

    @ConfigItem(
        keyName = "colorLootingBagIconsByValue",
        name = "Color Looting bag icons by value",
        description = "Sum unit price times quantity for all ground items on each tile, using RuneLite's configured price source. Any missing price keeps the original brown/gold bag.",
        section = LOOTING_BAG_COLORS,
        position = 0
    )
    default boolean colorLootingBagIconsByValue()
    {
        return false;
    }

    @ConfigItem(
        keyName = "lootingBagColorUnder1k",
        name = "0–999 gp",
        description = "Bag cloth color for a combined tile value of 0–999 gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 1
    )
    default Color lootingBagColorUnder1k()
    {
        return LootingBagValueTier.GRAY.defaultColor();
    }

    @ConfigItem(
        keyName = "lootingBagColor1k",
        name = "1k–9,999 gp",
        description = "Bag cloth color for a combined tile value of 1,000–9,999 gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 2
    )
    default Color lootingBagColor1k()
    {
        return LootingBagValueTier.BROWN.defaultColor();
    }

    @ConfigItem(
        keyName = "lootingBagColor10k",
        name = "10k–99,999 gp",
        description = "Bag cloth color for a combined tile value of 10,000–99,999 gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 3
    )
    default Color lootingBagColor10k()
    {
        return LootingBagValueTier.GREEN.defaultColor();
    }

    @ConfigItem(
        keyName = "lootingBagColor100k",
        name = "100k–999,999 gp",
        description = "Bag cloth color for a combined tile value of 100,000–999,999 gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 4
    )
    default Color lootingBagColor100k()
    {
        return LootingBagValueTier.BLUE.defaultColor();
    }

    @ConfigItem(
        keyName = "lootingBagColor1m",
        name = "1m–9,999,999 gp",
        description = "Bag cloth color for a combined tile value of 1,000,000–9,999,999 gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 5
    )
    default Color lootingBagColor1m()
    {
        return LootingBagValueTier.RED.defaultColor();
    }

    @ConfigItem(
        keyName = "lootingBagColor10m",
        name = "10m+ gp",
        description = "Bag cloth color for a combined tile value of 10,000,000+ gp. Right-click to reset. Outline, tie and gold coin are retained.",
        section = LOOTING_BAG_COLORS,
        position = 6
    )
    default Color lootingBagColor10m()
    {
        return LootingBagValueTier.PURPLE.defaultColor();
    }

    @ConfigSection(
        name = "Marker colors",
        description = "Customize only native minimap dots. Looting bag value colors have their own section.",
        position = 2,
        closedByDefault = true
    )
    String MARKER_COLORS = "markerColors";

    @ConfigSection(
        name = "Map icons",
        description = "Choose which native map icon categories are visible.",
        position = 3
    )
    String MAP_ICONS = "mapIcons";

    @ConfigSection(
        name = "Layout",
        description = "Compass, square minimap and Info panel.",
        position = -1
    )
    String LAYOUT = "layout";

    @ConfigItem(
        keyName = "showCompass",
        name = "Show compass",
        description = "Show the native compass in Classic or the diamond in Square HUD. Hidden compasses do not accept clicks.",
        section = LAYOUT,
        position = 0
    )
    default boolean showCompass()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableSquareMinimap",
        name = "Enable square minimap",
        description = "Use a square map with resource blocks and an Info panel. Resizable layouts only; fixed mode keeps Classic.",
        section = LAYOUT,
        position = 1
    )
    default boolean enableSquareMinimap()
    {
        return false;
    }

    @ConfigItem(
        keyName = "squareHudFooterMode",
        name = "Info panel view",
        description = "Always show the Info panel, or reveal it on mouseover. Requires Enable square minimap; the selection is kept while disabled.",
        section = LAYOUT,
        position = 2
    )
    default SquareHudFooterMode squareHudFooterMode()
    {
        return SquareHudFooterMode.ALWAYS_VISIBLE;
    }

    @ConfigItem(
        keyName = "infoPanelText",
        name = "Info panel text",
        description = "Zone, your computer's local time, or world tile coordinates. Requires Enable square minimap. Hover for details.",
        section = LAYOUT,
        position = 3
    )
    default InfoPanelText infoPanelText()
    {
        return InfoPanelText.ZONE;
    }

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
        keyName = "wildernessPlayerSkulls",
        name = "Wilderness player skulls",
        description = "Replace Other players dots in normal-world Wilderness: red inside your combat-level bracket, white outside. Not actual PvP skulls or an attack/safety guarantee. Friends, clan and team keep their own markers. Special worlds or unknown range keep normal dots. Requires Other players.",
        section = ENTITY_DOTS,
        position = 5
    )
    default boolean wildernessPlayerSkulls()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showFriends",
        name = "Friends",
        description = "Show friend dots.",
        section = ENTITY_DOTS,
        position = 6
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
        position = 7
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
        position = 8
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
        position = 9
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
        keyName = "showSlayerMasters",
        name = "Slayer Masters",
        description = "Show Slayer Master map icons, independently of Skilling facilities.",
        section = MAP_ICONS,
        position = 19
    )
    default boolean showSlayerMasters()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showPubs",
        name = "Pubs",
        description = "Show pub and tavern map icons.",
        section = MAP_ICONS,
        position = 20
    )
    default boolean showPubs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showOtherServices",
        name = "Other services",
        description = "Show other utility icons, including Junk checker, Taxidermist, Speedrunning shop, Newspaper trader, and Clan Hub.",
        section = MAP_ICONS,
        position = 21
    )
    default boolean showOtherServices()
    {
        return true;
    }
}
