package com.minimapfilter;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ObjectComposition;
import net.runelite.api.SpriteID;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Minimap Customizer",
    description = "Customize native minimap icons, entity markers, dot colors, and player direction with flexible visibility settings",
    tags = {"minimap", "dots", "icons", "filter", "direction", "npcs", "players", "items", "markers"},
    enabledByDefault = false
)
public class MinimapFilterPlugin extends Plugin
{
    private static final int DOT_ITEM = 0;
    private static final int DOT_NPC = 1;
    private static final int DOT_PLAYER = 2;
    private static final int DOT_FRIEND = 3;
    private static final int DOT_TEAM = 4;
    private static final int DOT_FRIENDS_CHAT = 5;
    private static final int DOT_CLAN = 6;
    private static final int DOT_COUNT = 7;

    private static final int[] BANK_ICONS = {
        SpriteID.MAP_ICON_BANK,
        SpriteID.MAP_ICON_GRAND_EXCHANGE
    };
    private static final int[] AGILITY_TRAINING_ICONS = {SpriteID.MAP_ICON_AGILITY_TRAINING};
    private static final int[] AGILITY_SHORTCUT_ICONS = {SpriteID.MAP_ICON_AGILITY_SHORT_CUT};
    private static final int[] ALTAR_ICONS = {SpriteID.MAP_ICON_ALTAR};
    private static final int[] QUEST_ICONS = {SpriteID.MAP_ICON_QUEST_START, SpriteID.MAP_ICON_KOUREND_TASK};
    private static final int[] MINIGAME_ICONS = {SpriteID.MAP_ICON_MINIGAME, SpriteID.MAP_ICON_RAIDS_LOBBY};
    private static final int[] TRANSPORT_ICONS = {
        SpriteID.MAP_ICON_TRANSPORTATION,
        SpriteID.MAP_ICON_HOUSE_PORTAL
    };
    private static final int[] DUNGEON_ICONS = {
        SpriteID.MAP_ICON_DUNGEON,
        SpriteID.MAP_ICON_MAP_LINK_DOWNSTAIRS,
        SpriteID.MAP_ICON_MAP_LINK_UPSTAIRS
    };
    private static final int[] FARMING_PATCH_ICONS = {SpriteID.MAP_ICON_FARMING_PATCH};
    private static final int[] FISHING_SPOT_ICONS = {SpriteID.MAP_ICON_FISHING_SPOT};
    private static final int[] MINING_SITE_ICONS = {SpriteID.MAP_ICON_MINING_SITE};
    private static final int[] RARE_TREE_ICONS = {SpriteID.MAP_ICON_RARE_TREES};
    private static final int[] WATER_SOURCE_ICONS = {
        SpriteID.MAP_ICON_WATER_SOURCE,
        SpriteID.MAP_ICON_STAGNANT_WATER_SOURCE
    };

    /**
     * Representative oak/willow/maple/yew/magic object variants. Their object
     * definitions point at the native MAPSCENE frames used for the individual
     * tree symbols. Additional variants are learned from loaded game objects by
     * name, so this list is only the startup/bootstrap path.
     */
    private static final int[] RARE_TREE_OBJECT_IDS = {
        10820, 20806, 42395, 42831, 51772, // Oak
        10819, 10829, 10831, 10833, 8487,  // Willow
        10832, 8443,                       // Maple
        10822, 36683, 40756, 42391, 42427, 8512, // Yew
        10834, 36685, 8408                 // Magic
    };

    private static final Set<String> RARE_TREE_NAMES = new HashSet<>(Arrays.asList(
        "oak",
        "oak tree",
        "willow",
        "willow tree",
        "maple",
        "maple tree",
        "yew",
        "yew tree",
        "magic",
        "magic tree"
    ));

    /**
     * Ordinary scenery tree names. These are deliberately separate from the
     * five Rare Trees families above. A number of native MAPSCENE frames are
     * reused by both groups, so object identity/name is the authority whenever
     * a frame is shared.
     */
    private static final Set<String> FOLIAGE_TREE_NAMES = new HashSet<>(Arrays.asList(
        "tree",
        "dead tree",
        "evergreen",
        "evergreen tree",
        "pine",
        "pine tree",
        "tropical tree"
    ));

    /**
     * Known foliage object variants confirmed in-game. These objects use MAPSCENE
     * frames rather than the ordinary Client#getMapIcons() layer. Resolving the
     * frame from the object definition is more reliable than silhouette matching
     * alone and also covers visually different bush/plant variants.
     */
    private static final int[] FOLIAGE_OBJECT_IDS = {
        // Common regular-tree variants. Resolving representative object
        // definitions captures the native MAPSCENE frame(s) directly, avoiding
        // the need to loosen the visual matcher around large terrain sprites.
        1276,   // Tree - common double-canopy variant
        1277,   // Tree - young variant
        1278,   // Tree - common variant
        1279,   // Tree - common variant
        1280,   // Tree - young variant

        // Common dead-tree variants. These cover the brown dead-tree MAPSCENE
        // family seen on the minimap while leaving unrelated dock/deck frames alone.
        1282,   // Dead tree
        1283,   // Dead tree
        1284,   // Dead tree
        1285,   // Dead tree - weathered
        1286,   // Dead tree
        1287,   // Dead tree
        1288,   // Dead tree
        1289,   // Dead tree
        1290,   // Dead tree
        1291,   // Dead tree

        // Bush and plant variants observed in-game.
        4729,   // Bush
        33638,  // Bush
        1391,   // Plant
        1392,   // Plant
        34625,  // Plant
        34624,  // Plant
        34622,  // Bush
        34623   // Plant
    };

    // Foliage is not part of Client#getMapIcons(). These symbols are frames in
    // Client#getMapScene() (the MAPSCENE sprite sheet), so they are filtered
    // separately from ordinary bank/agility/etc. map-function icons.

    // Sailing map-function icons do not yet have stable named SpriteID constants
    // in the API used by this plugin. Resolve their Client#getMapIcons() slots
    // from the actual scenery objects instead. These ranges are only a bootstrap;
    // the loaded scene is also scanned so regional/new variants are learned by
    // object identity instead of being accidentally classified by Shop artwork.
    private static final int[][] SAILING_OBJECT_RANGES = {
        {59769, 59827}, // Sailing buoys / docking-point anchor markers
        {60287, 60309}, // Sailing notice / port-task boards
        {60312, 60312}, // Ledger table
        {60250, 60550}  // Fail-closed name scan for salvaging-station variants
    };

    private static final int[] SKILL_FACILITY_ICONS = {
        SpriteID.MAP_ICON_FURNACE,
        SpriteID.MAP_ICON_ANVIL,
        SpriteID.MAP_ICON_COMBAT_TRAINING,
        SpriteID.MAP_ICON_SPINNING_WHEEL,
        SpriteID.MAP_ICON_COOKING_RANGE,
        SpriteID.MAP_ICON_POTTERY_WHEEL,
        SpriteID.MAP_ICON_WINDMILL,
        SpriteID.MAP_ICON_SLAYER_MASTER,
        SpriteID.MAP_ICON_LOOM,
        SpriteID.MAP_ICON_BREWERY,
        SpriteID.MAP_ICON_DAIRY_CHURN,
        SpriteID.MAP_ICON_HUNTER_TRAINING,
        SpriteID.MAP_ICON_SAWMILL,
        SpriteID.MAP_ICON_WOODCUTTING_STUMP,
        SpriteID.MAP_ICON_SANDPIT
    };

    private static final int[] SHOP_ICONS = {
        SpriteID.MAP_ICON_GENERAL_STORE,
        SpriteID.MAP_ICON_SWORD_SHOP,
        SpriteID.MAP_ICON_MAGIC_SHOP,
        SpriteID.MAP_ICON_AXE_SHOP,
        SpriteID.MAP_ICON_HELMET_SHOP,
        SpriteID.MAP_ICON_AMULET_SHOP,
        SpriteID.MAP_ICON_STAFF_SHOP,
        SpriteID.MAP_ICON_PLATEBODY_SHOP,
        SpriteID.MAP_ICON_PLATELEGS_SHOP,
        SpriteID.MAP_ICON_SCIMITAR_SHOP,
        SpriteID.MAP_ICON_ARCHERY_SHOP,
        SpriteID.MAP_ICON_SHIELD_SHOP,
        SpriteID.MAP_ICON_JEWELLERY_SHOP,
        SpriteID.MAP_ICON_GEM_SHOP,
        SpriteID.MAP_ICON_CRAFTING_SHOP,
        SpriteID.MAP_ICON_CANDLE_SHOP,
        SpriteID.MAP_ICON_FISHING_SHOP,
        SpriteID.MAP_ICON_CLOTHES_SHOP,
        SpriteID.MAP_ICON_SILK_TRADER,
        SpriteID.MAP_ICON_FOOD_SHOP_KEBAB,
        SpriteID.MAP_ICON_MACE_SHOP,
        SpriteID.MAP_ICON_FOOD_SHOP,
        SpriteID.MAP_ICON_FOOD_SHOP_CUTLERY,
        SpriteID.MAP_ICON_PLATESKIRT_SHOP,
        SpriteID.MAP_ICON_MINING_SHOP,
        SpriteID.MAP_ICON_CHAINMAIL_SHOP,
        SpriteID.MAP_ICON_SILVER_SHOP,
        SpriteID.MAP_ICON_FUR_TRADER,
        SpriteID.MAP_ICON_SPICE_SHOP,
        SpriteID.MAP_ICON_FOOD_SHOP_FRUIT,
        SpriteID.MAP_ICON_FARMING_SHOP,
        SpriteID.MAP_ICON_HUNTER_SHOP,
        SpriteID.MAP_ICON_PET_SHOP,
        SpriteID.MAP_ICON_APOTHECARY
    };

    private static final int[] TASK_MASTER_ICONS = {SpriteID.MAP_ICON_TASK_MASTER};
    private static final int[] PUB_ICONS = {SpriteID.MAP_ICON_PUB};

    private static final int[] TUTOR_ICONS = {
        SpriteID.MAP_ICON_GUIDE,
        SpriteID.MAP_ICON_IRON_MAN_TUTORS
    };

    private static final int[] OTHER_SERVICE_ICONS = {
        SpriteID.MAP_ICON_HERBALIST,
        SpriteID.MAP_ICON_TANNERY,
        SpriteID.MAP_ICON_HAIRDRESSER,
        SpriteID.MAP_ICON_MAKEOVER_MAGE,
        SpriteID.MAP_ICON_POLL_BOOTH,
        SpriteID.MAP_ICON_ESTATE_AGENT,
        SpriteID.MAP_ICON_STONEMASON,
        SpriteID.MAP_ICON_HOLIDAY_EVENT,
        SpriteID.MAP_ICON_BOUNTY_HUNTER_TRADER,
        SpriteID.MAP_ICON_PRICING_EXPERT_WEAPONS_AND_ARMOUR,
        SpriteID.MAP_ICON_PRICING_EXPERT_LOGS,
        SpriteID.MAP_ICON_PRICING_EXPERT_HERBS,
        SpriteID.MAP_ICON_PRICING_EXPERT_RUNES,
        SpriteID.MAP_ICON_PRICING_EXPERT_ORES_AND_BARS
    };

    private enum IconCategory
    {
        BANK,
        AGILITY_TRAINING,
        AGILITY_SHORTCUT,
        ALTAR,
        QUEST,
        MINIGAME,
        TRANSPORT,
        DUNGEON,
        FARMING_PATCH,
        FISHING_SPOT,
        MINING_SITE,
        FOLIAGE,
        RARE_TREE,
        SKILL_FACILITY,
        WATER_SOURCE,
        SHOP,
        SAILING,
        TUTOR,
        TASK_MASTER,
        PUB,
        OTHER_SERVICE
    }

    /**
     * A value-based fingerprint of a native SpritePixels object.  The client can
     * hand the minimap renderer a different SpritePixels instance than the one
     * returned from the sprite archive, so object identity is not sufficient.
     */
    private static final class IconSignature
    {
        private final int width;
        private final int height;
        private final int maxWidth;
        private final int maxHeight;
        private final int offsetX;
        private final int offsetY;
        private final int pixelHash;

        private IconSignature(SpritePixels sprite)
        {
            width = sprite.getWidth();
            height = sprite.getHeight();
            maxWidth = sprite.getMaxWidth();
            maxHeight = sprite.getMaxHeight();
            offsetX = sprite.getOffsetX();
            offsetY = sprite.getOffsetY();
            pixelHash = Arrays.hashCode(sprite.getPixels());
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof IconSignature))
            {
                return false;
            }
            IconSignature that = (IconSignature) o;
            return width == that.width
                && height == that.height
                && maxWidth == that.maxWidth
                && maxHeight == that.maxHeight
                && offsetX == that.offsetX
                && offsetY == that.offsetY
                && pixelHash == that.pixelHash;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(width, height, maxWidth, maxHeight, offsetX, offsetY, pixelHash);
        }
    }


    /**
     * Fallback matcher for native icons which RuneLite does not currently expose
     * with a named SpriteID constant. References are tiny normalized masks derived
     * from the exact game icons supplied during development.
     */
    private static final class VisualReference
    {
        private static final int SIZE = 16;
        private final String name;
        private final IconCategory category;
        private final boolean[] mask;
        private final int foregroundCount;
        private final int bboxWidth;
        private final int bboxHeight;
        private final double occupancy;
        private final int averageRed;
        private final int averageGreen;
        private final int averageBlue;

        // Separate normalized color representation for Client#getMapIcons().
        // Unlike the foliage MAPSCENE matcher, normal map-function icons often
        // share the same circular outer silhouette, so alpha shape alone is not
        // sufficient to distinguish Garden Supplier from Sailing facilities.
        private final boolean[] mapIconMask;
        private final int[] mapIconRgb;
        private final int mapIconForegroundCount;

        private VisualReference(String name, IconCategory category, BufferedImage image)
        {
            this.name = name;
            this.category = category;
            this.mask = new boolean[SIZE * SIZE];

            int count = 0;
            int minX = SIZE;
            int minY = SIZE;
            int maxX = -1;
            int maxY = -1;
            long red = 0;
            long green = 0;
            long blue = 0;
            for (int y = 0; y < Math.min(SIZE, image.getHeight()); y++)
            {
                for (int x = 0; x < Math.min(SIZE, image.getWidth()); x++)
                {
                    int argb = image.getRGB(x, y);
                    if (((argb >>> 24) & 0xff) > 0)
                    {
                        mask[y * SIZE + x] = true;
                        count++;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        red += (argb >>> 16) & 0xff;
                        green += (argb >>> 8) & 0xff;
                        blue += argb & 0xff;
                    }
                }
            }
            foregroundCount = count;
            bboxWidth = maxX >= minX ? maxX - minX + 1 : 0;
            bboxHeight = maxY >= minY ? maxY - minY + 1 : 0;
            occupancy = bboxWidth > 0 && bboxHeight > 0 ? (double) count / (bboxWidth * bboxHeight) : 0d;
            averageRed = count > 0 ? (int) (red / count) : 0;
            averageGreen = count > 0 ? (int) (green / count) : 0;
            averageBlue = count > 0 ? (int) (blue / count) : 0;

            mapIconMask = new boolean[SIZE * SIZE];
            mapIconRgb = new int[SIZE * SIZE];
            mapIconForegroundCount = normalizeImageColorGrid(image, mapIconMask, mapIconRgb);
        }

        private double score(SpritePixels sprite)
        {
            boolean[] candidateMask = new boolean[SIZE * SIZE];
            int[] candidateRgb = new int[SIZE * SIZE];
            int candidateCount = normalizeSpriteColorGrid(sprite, candidateMask, candidateRgb);

            if (mapIconForegroundCount == 0 || candidateCount == 0)
            {
                return 0d;
            }

            int intersection = 0;
            double weightedPixelSimilarity = 0d;
            double totalWeight = 0d;
            for (int i = 0; i < candidateMask.length; i++)
            {
                int x = i % SIZE;
                int y = i / SIZE;
                double weight = (x >= 4 && x <= 11 && y >= 4 && y <= 11) ? 3.0d : 0.4d;

                boolean referencePixel = mapIconMask[i];
                boolean candidatePixel = candidateMask[i];
                if (!referencePixel && !candidatePixel)
                {
                    continue;
                }

                totalWeight += weight;
                if (!referencePixel || !candidatePixel)
                {
                    continue;
                }

                intersection++;
                int a = mapIconRgb[i];
                int b = candidateRgb[i];
                int dr = ((a >>> 16) & 0xff) - ((b >>> 16) & 0xff);
                int dg = ((a >>> 8) & 0xff) - ((b >>> 8) & 0xff);
                int db = (a & 0xff) - (b & 0xff);
                double distance = Math.sqrt(dr * dr + dg * dg + db * db);
                weightedPixelSimilarity += weight * Math.max(0d, 1d - distance / 441.67295593d);
            }

            if (intersection == 0 || totalWeight == 0d)
            {
                return 0d;
            }

            double silhouette = (2d * intersection) / (mapIconForegroundCount + candidateCount);
            double patternSimilarity = weightedPixelSimilarity / totalWeight;

            // The center artwork is the discriminating feature. All of the new
            // Sailing functions and Garden Supplier share a nearly identical
            // circular outer frame, which is why the old alpha-only matcher
            // incorrectly put Sailing under Shops.
            return 0.15d * silhouette + 0.85d * patternSimilarity;
        }

        private double score(IndexedSprite sprite)
        {
            if (sprite == null || sprite.getPixels() == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0)
            {
                return 0d;
            }

            byte[] pixels = sprite.getPixels();
            int width = sprite.getWidth();
            int height = sprite.getHeight();
            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            int candidateCount = 0;
            long red = 0;
            long green = 0;
            long blue = 0;
            int[] palette = sprite.getPalette();

            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    int paletteIndex = pixels[y * width + x] & 0xff;
                    if (paletteIndex == 0)
                    {
                        continue;
                    }

                    candidateCount++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);

                    if (palette != null && paletteIndex < palette.length)
                    {
                        int rgb = palette[paletteIndex];
                        red += (rgb >>> 16) & 0xff;
                        green += (rgb >>> 8) & 0xff;
                        blue += rgb & 0xff;
                    }
                }
            }

            if (candidateCount == 0 || maxX < minX || maxY < minY)
            {
                return 0d;
            }

            int candidateBboxWidth = maxX - minX + 1;
            int candidateBboxHeight = maxY - minY + 1;

            // Normalizing all MAPSCENE sprites to the same size can make large
            // terrain graphics resemble a
            // small foliage silhouette. Foliage references are exact native
            // icon crops, so reject candidates whose native foreground bounds
            // differ materially before doing any normalized-mask comparison.
            if (Math.abs(candidateBboxWidth - bboxWidth) > 3
                || Math.abs(candidateBboxHeight - bboxHeight) > 3)
            {
                return 0d;
            }

            double candidateOccupancy = (double) candidateCount / (candidateBboxWidth * candidateBboxHeight);
            if (Math.abs(candidateOccupancy - occupancy) > 0.25d)
            {
                return 0d;
            }

            boolean[] candidate = normalizeMask(sprite);
            int intersection = 0;
            for (int i = 0; i < candidate.length; i++)
            {
                if (candidate[i] && mask[i])
                {
                    intersection++;
                }
            }

            if (foregroundCount == 0)
            {
                return 0d;
            }

            double silhouette = (2d * intersection) / (foregroundCount + countTrue(candidate));

            // Use the IndexedSprite palette as an additional discriminator.
            // This keeps a similarly-shaped brown dock/deck sprite from being
            // accepted merely because its alpha silhouette resembles foliage.
            int candidateRed = (int) (red / candidateCount);
            int candidateGreen = (int) (green / candidateCount);
            int candidateBlue = (int) (blue / candidateCount);
            double dr = candidateRed - averageRed;
            double dg = candidateGreen - averageGreen;
            double db = candidateBlue - averageBlue;
            double colorDistance = Math.sqrt(dr * dr + dg * dg + db * db);
            double colorSimilarity = Math.max(0d, 1d - (colorDistance / 441.67295593d));

            if (colorSimilarity < 0.68d)
            {
                return 0d;
            }

            return 0.78d * silhouette + 0.22d * colorSimilarity;
        }

        private static int normalizeImageColorGrid(BufferedImage image, boolean[] outMask, int[] outRgb)
        {
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            {
                return 0;
            }

            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++)
                {
                    int argb = image.getRGB(x, y);
                    if (((argb >>> 24) & 0xff) != 0)
                    {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY)
            {
                return 0;
            }

            int boxWidth = maxX - minX + 1;
            int boxHeight = maxY - minY + 1;
            double scale = Math.min(14d / boxWidth, 14d / boxHeight);
            int targetWidth = Math.max(1, (int) Math.round(boxWidth * scale));
            int targetHeight = Math.max(1, (int) Math.round(boxHeight * scale));
            int offsetX = (SIZE - targetWidth) / 2;
            int offsetY = (SIZE - targetHeight) / 2;
            int count = 0;

            for (int ty = 0; ty < targetHeight; ty++)
            {
                int sy = minY + Math.min(boxHeight - 1, (int) Math.floor((ty + 0.5d) * boxHeight / targetHeight));
                for (int tx = 0; tx < targetWidth; tx++)
                {
                    int sx = minX + Math.min(boxWidth - 1, (int) Math.floor((tx + 0.5d) * boxWidth / targetWidth));
                    int argb = image.getRGB(sx, sy);
                    if (((argb >>> 24) & 0xff) == 0)
                    {
                        continue;
                    }
                    int index = (offsetY + ty) * SIZE + offsetX + tx;
                    outMask[index] = true;
                    outRgb[index] = argb & 0x00ffffff;
                    count++;
                }
            }
            return count;
        }

        private static int normalizeSpriteColorGrid(SpritePixels sprite, boolean[] outMask, int[] outRgb)
        {
            if (sprite == null || sprite.getPixels() == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0)
            {
                return 0;
            }

            int width = sprite.getWidth();
            int height = sprite.getHeight();
            int[] pixels = sprite.getPixels();
            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    if (pixels[y * width + x] != 0)
                    {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY)
            {
                return 0;
            }

            int boxWidth = maxX - minX + 1;
            int boxHeight = maxY - minY + 1;
            double scale = Math.min(14d / boxWidth, 14d / boxHeight);
            int targetWidth = Math.max(1, (int) Math.round(boxWidth * scale));
            int targetHeight = Math.max(1, (int) Math.round(boxHeight * scale));
            int offsetX = (SIZE - targetWidth) / 2;
            int offsetY = (SIZE - targetHeight) / 2;
            int count = 0;

            for (int ty = 0; ty < targetHeight; ty++)
            {
                int sy = minY + Math.min(boxHeight - 1, (int) Math.floor((ty + 0.5d) * boxHeight / targetHeight));
                for (int tx = 0; tx < targetWidth; tx++)
                {
                    int sx = minX + Math.min(boxWidth - 1, (int) Math.floor((tx + 0.5d) * boxWidth / targetWidth));
                    int rgb = pixels[sy * width + sx];
                    if (rgb == 0)
                    {
                        continue;
                    }
                    int index = (offsetY + ty) * SIZE + offsetX + tx;
                    outMask[index] = true;
                    outRgb[index] = rgb & 0x00ffffff;
                    count++;
                }
            }
            return count;
        }

        private static int countTrue(boolean[] values)
        {
            int count = 0;
            for (boolean value : values)
            {
                if (value)
                {
                    count++;
                }
            }
            return count;
        }

        private static boolean[] normalizeMask(SpritePixels sprite)
        {
            boolean[] out = new boolean[SIZE * SIZE];
            if (sprite == null || sprite.getPixels() == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0)
            {
                return out;
            }

            int width = sprite.getWidth();
            int height = sprite.getHeight();
            int[] pixels = sprite.getPixels();

            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    if (pixels[y * width + x] != 0)
                    {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY)
            {
                return out;
            }

            int boxWidth = maxX - minX + 1;
            int boxHeight = maxY - minY + 1;
            double scale = Math.min(14d / boxWidth, 14d / boxHeight);
            int targetWidth = Math.max(1, (int) Math.round(boxWidth * scale));
            int targetHeight = Math.max(1, (int) Math.round(boxHeight * scale));
            int offsetX = (SIZE - targetWidth) / 2;
            int offsetY = (SIZE - targetHeight) / 2;

            for (int ty = 0; ty < targetHeight; ty++)
            {
                int sy = minY + Math.min(boxHeight - 1, (int) Math.floor((ty + 0.5d) * boxHeight / targetHeight));
                for (int tx = 0; tx < targetWidth; tx++)
                {
                    int sx = minX + Math.min(boxWidth - 1, (int) Math.floor((tx + 0.5d) * boxWidth / targetWidth));
                    if (pixels[sy * width + sx] != 0)
                    {
                        out[(offsetY + ty) * SIZE + offsetX + tx] = true;
                    }
                }
            }
            return out;
        }

        private static boolean[] normalizeMask(IndexedSprite sprite)
        {
            boolean[] out = new boolean[SIZE * SIZE];
            if (sprite == null || sprite.getPixels() == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0)
            {
                return out;
            }

            int width = sprite.getWidth();
            int height = sprite.getHeight();
            byte[] pixels = sprite.getPixels();

            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    if (pixels[y * width + x] != 0)
                    {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY)
            {
                return out;
            }

            int boxWidth = maxX - minX + 1;
            int boxHeight = maxY - minY + 1;
            double scale = Math.min(14d / boxWidth, 14d / boxHeight);
            int targetWidth = Math.max(1, (int) Math.round(boxWidth * scale));
            int targetHeight = Math.max(1, (int) Math.round(boxHeight * scale));
            int offsetX = (SIZE - targetWidth) / 2;
            int offsetY = (SIZE - targetHeight) / 2;

            for (int ty = 0; ty < targetHeight; ty++)
            {
                int sy = minY + Math.min(boxHeight - 1, (int) Math.floor((ty + 0.5d) * boxHeight / targetHeight));
                for (int tx = 0; tx < targetWidth; tx++)
                {
                    int sx = minX + Math.min(boxWidth - 1, (int) Math.floor((tx + 0.5d) * boxWidth / targetWidth));
                    if (pixels[sy * width + sx] != 0)
                    {
                        out[(offsetY + ty) * SIZE + offsetX + tx] = true;
                    }
                }
            }
            return out;
        }
    }

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private MinimapFilterConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PlayerDirectionOverlay playerDirectionOverlay;

    @Inject
    private EntityMarkerOverlay entityMarkerOverlay;

    @Inject
    private MinimapMarkerAssets markerAssets;

    @Inject
    private ConfigManager configManager;

    private TreeMapSceneOverlay treeMapSceneOverlay;

    private final SpritePixels[] visibleDotSprites = new SpritePixels[DOT_COUNT];
    // Track our own replacements by identity so they never become the new
    // "original" when RuneLite rebuilds or recolors native dots.
    private final SpritePixels[] recoloredDotSprites = new SpritePixels[DOT_COUNT];
    private final SpritePixels[] recoloredDotBases = new SpritePixels[DOT_COUNT];
    private final int[] recoloredDotColors = new int[DOT_COUNT];
    private SpritePixels transparentDot;
    private SpritePixels transparentIcon;

    /** Native sprite fingerprint -> filter category. */
    private final Map<IconSignature, IconCategory> iconCatalog = new HashMap<>();

    /** Live Client#getMapIcons() slot -> category when object identity is needed. */
    private final Map<Integer, IconCategory> iconIndexOverrides = new HashMap<>();

    /** Fallback results for unnamed native icon sprites. */
    private final Map<IconSignature, IconCategory> visualMatchCache = new HashMap<>();
    private final Set<IconSignature> visualMissCache = new HashSet<>();
    private final java.util.List<VisualReference> visualReferences = new java.util.ArrayList<>();

    /** Original live minimap icon per slot while that slot is hidden by us. */
    private final Map<Integer, SpritePixels> hiddenIconOriginals = new HashMap<>();

    /** The current Client#getMapIcons() array. A replacement array invalidates slot snapshots. */
    private SpritePixels[] lastLiveIconArray;

    /** Original pixel data for map-scene sprite frames identified as foliage. */
    private final Map<Integer, byte[]> foliageMapSceneOriginals = new HashMap<>();

    /** Original pixel data for individual rare-tree MAPSCENE frames. */
    private final Map<Integer, byte[]> rareTreeMapSceneOriginals = new HashMap<>();

    /** MAPSCENE frames genuinely used by both ordinary foliage trees and Rare Trees. */
    private final Set<Integer> sharedTreeMapSceneFrames = new HashSet<>();

    /** Native images used to redraw a shared frame on a per-object basis. */
    private final Map<Integer, BufferedImage> sharedTreeFrameImages = new ConcurrentHashMap<>();

    /** Live game objects with native MAPSCENE graphics; maintained for the separation overlay. */
    private final Map<Long, GameObject> mapSceneGameObjects = new ConcurrentHashMap<>();

    private IndexedSprite[] lastMapSceneArray;

    @Provides
    MinimapFilterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(MinimapFilterConfig.class);
    }

    /**
     * Keep the existing foliage choices when replacing negative checkboxes
     * with positive visibility checkboxes. RuneLite configuration is persisted
     * by key, so we must migrate rather than reuse the inverse keys.
     */
    private void migrateTreeVisibilitySettings()
    {
        migrateTreeVisibilitySetting("hideFoliage", "showFoliage");
        migrateTreeVisibilitySetting("hideRareTrees", "showRareTrees");
    }

    private void migrateTreeVisibilitySetting(String legacyKey, String visibilityKey)
    {
        String legacyValue = configManager.getConfiguration(MinimapFilterConfig.GROUP, legacyKey);
        if (legacyValue == null)
        {
            return;
        }

        // RuneLite may have already written the new ConfigItem default (true)
        // before startUp() executes. The presence of the legacy key is our
        // upgrade signal; its actual stored choice must win over that default.
        // The legacy key is removed immediately so this only runs once.
        if ("true".equalsIgnoreCase(legacyValue) || "false".equalsIgnoreCase(legacyValue))
        {
            configManager.setConfiguration(MinimapFilterConfig.GROUP, visibilityKey,
                !Boolean.parseBoolean(legacyValue));
        }
        configManager.unsetConfiguration(MinimapFilterConfig.GROUP, legacyKey);
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        migrateTreeVisibilitySettings();
        clientThread.invokeLater(this::applyFilters);
    }

    @Override
    protected void startUp()
    {
        migrateTreeVisibilitySettings();
        treeMapSceneOverlay = new TreeMapSceneOverlay(this);
        overlayManager.add(treeMapSceneOverlay);
        overlayManager.add(entityMarkerOverlay);
        overlayManager.add(playerDirectionOverlay);
        clientThread.invokeLater(() ->
        {
            createTransparentSprites();
            refreshVisibleDotSprites();
            loadVisualReferences();
            rebuildIconCatalog();
            applyFilters();
        });
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(playerDirectionOverlay);
        overlayManager.remove(entityMarkerOverlay);
        if (treeMapSceneOverlay != null)
        {
            overlayManager.remove(treeMapSceneOverlay);
            treeMapSceneOverlay = null;
        }
        clientThread.invokeLater(() ->
        {
            restoreDots();
            restoreLiveIcons();
            restoreMapSceneFilters();
            Arrays.fill(visibleDotSprites, null);
            clearRecoloredDots();
            transparentDot = null;
            transparentIcon = null;
            iconCatalog.clear();
            iconIndexOverrides.clear();
            visualMatchCache.clear();
            visualMissCache.clear();
            visualReferences.clear();
            hiddenIconOriginals.clear();
            lastLiveIconArray = null;
            foliageMapSceneOriginals.clear();
            rareTreeMapSceneOriginals.clear();
            sharedTreeMapSceneFrames.clear();
            sharedTreeFrameImages.clear();
            mapSceneGameObjects.clear();
            lastMapSceneArray = null;
            markerAssets.clear();
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (MinimapFilterConfig.GROUP.equals(event.getGroup()))
        {
            clientThread.invokeLater(this::applyFilters);
            return;
        }

        // RuneLite's built-in Minimap plugin can recolor these same dot sprites.
        if ("minimap".equals(event.getGroup()))
        {
            clientThread.invokeLater(() ->
            {
                refreshVisibleDotSprites();
                applyDotFilters();
            });
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.STARTING)
        {
            Arrays.fill(visibleDotSprites, null);
            clearRecoloredDots();
            markerAssets.clear();
            hiddenIconOriginals.clear();
            iconIndexOverrides.clear();
            lastLiveIconArray = null;
            foliageMapSceneOriginals.clear();
            rareTreeMapSceneOriginals.clear();
            sharedTreeMapSceneFrames.clear();
            sharedTreeFrameImages.clear();
            mapSceneGameObjects.clear();
            lastMapSceneArray = null;
            return;
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() ->
            {
                createTransparentSprites();
                refreshVisibleDotSprites();
                loadVisualReferences();
                rebuildIconCatalog();
                applyFilters();
            });
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        GameObject gameObject = event.getGameObject();
        if (gameObject == null)
        {
            return;
        }

        // Sailing map icons can be attached to ordinary scene objects. Learn
        // their map-icon slot as objects enter the scene so Sailing has higher
        // authority than the generic artwork-based Shop classifier.
        registerSailingMapIconFromObjectId(gameObject.getId());

        IndexedSprite[] mapScene = client.getMapScene();
        if (mapScene == null)
        {
            return;
        }

        trackMapSceneGameObject(gameObject);
        captureTreeFramesFromObject(mapScene, gameObject);
        updateSharedTreeFrames();
        applyMapSceneVisibility(mapScene);
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        if (client.getGameState() == GameState.LOGGED_IN && event.getDecorativeObject() != null)
        {
            registerSailingMapIconFromObjectId(event.getDecorativeObject().getId());
        }
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        if (client.getGameState() == GameState.LOGGED_IN && event.getGroundObject() != null)
        {
            registerSailingMapIconFromObjectId(event.getGroundObject().getId());
        }
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        if (client.getGameState() == GameState.LOGGED_IN && event.getWallObject() != null)
        {
            registerSailingMapIconFromObjectId(event.getWallObject().getId());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject gameObject = event.getGameObject();
        if (gameObject != null)
        {
            mapSceneGameObjects.remove(gameObject.getHash());
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        // The vanilla client can rebuild the live icon array as regions/objects
        // change. Re-apply only to that native array; no overlay/input work is done.
        applyIconFilters();
        applyMapSceneFilters();
    }

    private void createTransparentSprites()
    {
        if (transparentDot == null)
        {
            transparentDot = client.createSpritePixels(new int[4 * 5], 4, 5);
        }

        if (transparentIcon == null)
        {
            transparentIcon = client.createSpritePixels(new int[1], 1, 1);
        }
    }

    private void applyFilters()
    {
        createTransparentSprites();
        applyDotFilters();
        applyIconFilters();
        applyMapSceneFilters();
    }

    private void clearRecoloredDots()
    {
        Arrays.fill(recoloredDotSprites, null);
        Arrays.fill(recoloredDotBases, null);
        Arrays.fill(recoloredDotColors, 0);
    }

    private void refreshVisibleDotSprites()
    {
        SpritePixels[] mapDots = client.getMapDots();
        if (mapDots == null)
        {
            return;
        }

        int count = Math.min(DOT_COUNT, mapDots.length);
        for (int i = 0; i < count; i++)
        {
            SpritePixels sprite = mapDots[i];
            if (sprite != null && sprite != transparentDot && sprite != recoloredDotSprites[i])
            {
                // A new client/RuneLite Minimap sprite replaces our prior
                // baseline. Never cache one of our own tinted copies as native.
                if (sprite != visibleDotSprites[i])
                {
                    visibleDotSprites[i] = sprite;
                    recoloredDotSprites[i] = null;
                    recoloredDotBases[i] = null;
                }
            }
        }
    }

    private void refreshNpcMarkerAssets()
    {
        SpritePixels nativeDot = visibleDotSprites[DOT_NPC];
        if (nativeDot == null)
        {
            markerAssets.clear();
            return;
        }

        BufferedImage attackImage = getNpcDotImage(nativeDot,
            config.attackableNpcDotColor());
        BufferedImage nonCombatImage = getNpcDotImage(nativeDot,
            config.nonCombatNpcDotColor());
        markerAssets.setNpcDots(attackImage, nonCombatImage);
    }

    private BufferedImage getNpcDotImage(SpritePixels original, Color desired)
    {
        if (!NativeDotColors.isCustomized(desired, NativeDotColors.NPC))
        {
            return original.toBufferedImage();
        }
        SpritePixels tinted = client.createSpritePixels(
            NativeDotColors.recolor(original.getPixels(), desired),
            original.getWidth(), original.getHeight());
        return tinted.toBufferedImage();
    }

    private void applyDotFilters()
    {
        SpritePixels[] mapDots = client.getMapDots();
        if (mapDots == null || transparentDot == null)
        {
            return;
        }

        refreshVisibleDotSprites();
        refreshNpcMarkerAssets();

        // The native client has one global NPC dot sprite for all NPCs.
        // Separate rendering is only required when hiding a subgroup,
        // selecting the sword, or choosing distinct NPC dot colors.
        boolean nativeNpcDots = config.attackableNpcMarkerStyle() == AttackableNpcMarkerStyle.NATIVE_DOT
            && config.showNonCombatNpcs()
            && !NativeDotColors.npcColorsDiffer(config);

        setDot(mapDots, DOT_ITEM,
            config.groundItemMarkerStyle() == GroundItemMarkerStyle.NATIVE_DOT,
            config.groundItemDotColor(), NativeDotColors.ITEM);
        setDot(mapDots, DOT_NPC, nativeNpcDots,
            config.attackableNpcDotColor(), NativeDotColors.NPC);
        setDot(mapDots, DOT_PLAYER, config.showPlayers(),
            config.otherPlayerDotColor(), NativeDotColors.PLAYER);
        setDot(mapDots, DOT_FRIEND, config.showFriends(),
            config.friendDotColor(), NativeDotColors.FRIEND);
        setDot(mapDots, DOT_TEAM, config.showTeam(),
            config.teamDotColor(), NativeDotColors.TEAM);
        setDot(mapDots, DOT_FRIENDS_CHAT, config.showFriendsChat(),
            config.friendsChatDotColor(), NativeDotColors.FRIENDS_CHAT);
        setDot(mapDots, DOT_CLAN, config.showClan(),
            config.clanDotColor(), NativeDotColors.CLAN);
    }

    private void setDot(SpritePixels[] mapDots, int index, boolean visible,
        Color desired, Color defaultColor)
    {
        if (index >= mapDots.length)
        {
            return;
        }

        if (!visible)
        {
            mapDots[index] = transparentDot;
            return;
        }

        SpritePixels original = visibleDotSprites[index];
        if (original == null)
        {
            return;
        }

        // Keeping the original color selected never modifies RuneLite's
        // native or Minimap-plugin generated sprite.
        if (!NativeDotColors.isCustomized(desired, defaultColor))
        {
            mapDots[index] = original;
            return;
        }

        int colorRgb = desired.getRGB();
        if (recoloredDotSprites[index] == null || recoloredDotBases[index] != original
            || recoloredDotColors[index] != colorRgb)
        {
            recoloredDotSprites[index] = client.createSpritePixels(
                NativeDotColors.recolor(original.getPixels(), desired),
                original.getWidth(), original.getHeight());
            recoloredDotBases[index] = original;
            recoloredDotColors[index] = colorRgb;
        }
        mapDots[index] = recoloredDotSprites[index];
    }

    private void restoreDots()
    {
        SpritePixels[] mapDots = client.getMapDots();
        if (mapDots == null)
        {
            return;
        }

        int count = Math.min(DOT_COUNT, mapDots.length);
        for (int i = 0; i < count; i++)
        {
            // Do not overwrite another plugin's newly installed sprite.
            if ((mapDots[i] == transparentDot || mapDots[i] == recoloredDotSprites[i])
                && visibleDotSprites[i] != null)
            {
                mapDots[i] = visibleDotSprites[i];
            }
        }
    }

    private void rebuildIconCatalog()
    {
        iconCatalog.clear();
        visualMatchCache.clear();
        visualMissCache.clear();
        addIconGroup(BANK_ICONS, IconCategory.BANK);
        addIconGroup(AGILITY_TRAINING_ICONS, IconCategory.AGILITY_TRAINING);
        addIconGroup(AGILITY_SHORTCUT_ICONS, IconCategory.AGILITY_SHORTCUT);
        addIconGroup(ALTAR_ICONS, IconCategory.ALTAR);
        addIconGroup(QUEST_ICONS, IconCategory.QUEST);
        addIconGroup(MINIGAME_ICONS, IconCategory.MINIGAME);
        addIconGroup(TRANSPORT_ICONS, IconCategory.TRANSPORT);
        addIconGroup(DUNGEON_ICONS, IconCategory.DUNGEON);
        addIconGroup(FARMING_PATCH_ICONS, IconCategory.FARMING_PATCH);
        addIconGroup(FISHING_SPOT_ICONS, IconCategory.FISHING_SPOT);
        addIconGroup(MINING_SITE_ICONS, IconCategory.MINING_SITE);
        addIconGroup(RARE_TREE_ICONS, IconCategory.RARE_TREE);
        addIconGroup(SKILL_FACILITY_ICONS, IconCategory.SKILL_FACILITY);
        addIconGroup(WATER_SOURCE_ICONS, IconCategory.WATER_SOURCE);
        addIconGroup(SHOP_ICONS, IconCategory.SHOP);
        addIconGroup(TUTOR_ICONS, IconCategory.TUTOR);
        addIconGroup(TASK_MASTER_ICONS, IconCategory.TASK_MASTER);
        addIconGroup(PUB_ICONS, IconCategory.PUB);
        addIconGroup(OTHER_SERVICE_ICONS, IconCategory.OTHER_SERVICE);
        rebuildSailingIconIndexOverrides();

        log.debug("Loaded {} native minimap icon signatures and {} index overrides",
            iconCatalog.size(), iconIndexOverrides.size());
    }


    private void loadVisualReferences()
    {
        if (!visualReferences.isEmpty())
        {
            return;
        }

        // Garden Supplier is an unnamed normal map icon. Foliage references
        // are used only by the separate MAPSCENE classifier below. The foliage
        // matcher is deliberately stricter than the live-icon matcher because
        // MAPSCENE also contains terrain/deck graphics.
        addVisualReference("garden_supplier.png", "Garden supplier", IconCategory.SHOP);

        // Reference artwork for unconfirmed Tutor icons. These images are not
        // used by the visual classifier.
        addVisualReference("tutor_fishing_tutor.png", "Fishing Tutor", IconCategory.TUTOR);
        addVisualReference("tutor_mining_tutor.png", "Mining Tutor", IconCategory.TUTOR);
        addVisualReference("tutor_bond_tutor.png", "Bond Tutor", IconCategory.TUTOR);
        addVisualReference("tutor_hunter_tutor.png", "Hunter Tutor", IconCategory.TUTOR);

        // Tutor filtering uses exact native sprite hashes and named SpriteID
        // constants. Screenshot-cropped references
        // were removed from classification because the common circular surround
        // falsely resembles unrelated map icons.

        // Current Sailing map-function artwork supplied from the in-game map key.
        // These are intentionally checked before the generic Shop catalogue because
        // the old silhouette-only Garden Supplier fallback claimed every circular
        // Sailing icon as a Shop icon.
        addVisualReference("sailing_port_task_board.png", "Port task board", IconCategory.SAILING);
        addVisualReference("sailing_ledger_table.png", "Ledger table", IconCategory.SAILING);
        addVisualReference("sailing_salvaging_station.png", "Salvaging station", IconCategory.SAILING);
        addVisualReference("sailing_salvaging_spot.png", "Salvaging spot", IconCategory.SAILING);
        addVisualReference("sailing_docking_point.png", "Docking point", IconCategory.SAILING);

        addVisualReference("foliage_dead_tree.png", "Dead tree", IconCategory.FOLIAGE);
        addVisualReference("foliage_green_tree.png", "Green tree", IconCategory.FOLIAGE);
        addVisualReference("foliage_plant.png", "Plant", IconCategory.FOLIAGE);
    }

    private void addVisualReference(String resource, String name, IconCategory category)
    {
        try (InputStream in = MinimapFilterPlugin.class.getResourceAsStream(resource))
        {
            if (in == null)
            {
                log.warn("Missing minimap reference resource {}", resource);
                return;
            }

            BufferedImage image = ImageIO.read(in);
            if (image != null)
            {
                visualReferences.add(new VisualReference(name, category, image));
            }
        }
        catch (IOException ex)
        {
            log.warn("Unable to load minimap reference resource {}", resource, ex);
        }
    }

    private IconCategory matchVisualCategory(IconSignature signature, SpritePixels sprite)
    {
        IconCategory cached = visualMatchCache.get(signature);
        if (cached != null)
        {
            return cached;
        }
        if (visualMissCache.contains(signature))
        {
            return null;
        }

        VisualReference best = null;
        VisualReference second = null;
        double bestScore = 0d;
        double secondScore = 0d;
        for (VisualReference reference : visualReferences)
        {
            // Foliage belongs to the separate MAPSCENE layer. The normal
            // live-icon fallback is intentionally limited to unnamed Shop and
            // Sailing artwork.
            if (reference.category == IconCategory.FOLIAGE || reference.category == IconCategory.TUTOR)
            {
                continue;
            }

            double score = reference.score(sprite);
            if (score > bestScore)
            {
                second = best;
                secondScore = bestScore;
                best = reference;
                bestScore = score;
            }
            else if (score > secondScore)
            {
                second = reference;
                secondScore = score;
            }
        }

        if (best == null)
        {
            visualMissCache.add(signature);
            return null;
        }

        // These references come from rendered 16px map-key artwork, whereas
        // Client#getMapIcons() contains the native sprite. Small palette/edge
        // differences are expected. Exact named SpriteID categories are
        // resolved before this fallback, so a known Food shop (or any other
        // named Shop icon) can never be stolen by the Sailing matcher.
        double minimumScore;
        if (best.category == IconCategory.SAILING)
        {
            if (best.name.equals("Ledger table") || best.name.equals("Salvaging station"))
            {
                minimumScore = 0.58d;
            }
            else
            {
                minimumScore = 0.64d;
            }
        }
        else
        {
            // Garden Supplier is the only unnamed Shop visual fallback. Keep it
            // stricter than Sailing so unrelated circular map icons do not fall
            // into Shops merely because they share the standard outer frame.
            minimumScore = 0.72d;
        }

        double margin = bestScore - secondScore;
        boolean sufficientlyDistinct = second == null || margin >= 0.035d || bestScore >= 0.86d;
        if (bestScore >= minimumScore && sufficientlyDistinct)
        {
            visualMatchCache.put(signature, best.category);
            log.debug("Matched native minimap icon {} as {} (score {}, runner-up {}, margin {})",
                best.name, best.category, bestScore, secondScore, margin);
            return best.category;
        }

        visualMissCache.add(signature);
        return null;
    }

    private void addIconGroup(int[] spriteIds, IconCategory category)
    {
        for (int spriteId : spriteIds)
        {
            SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
            if (sprites == null || sprites.length == 0 || sprites[0] == null)
            {
                continue;
            }

            iconCatalog.put(new IconSignature(sprites[0]), category);
        }
    }

    private void rebuildSailingIconIndexOverrides()
    {
        iconIndexOverrides.clear();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        // Bootstrap known live Sailing scenery. This catches icons even if the
        // plugin is enabled after the object-spawn events already happened.
        for (int[] range : SAILING_OBJECT_RANGES)
        {
            for (int objectId = range[0]; objectId <= range[1]; objectId++)
            {
                registerSailingMapIconFromObjectId(objectId);
            }
        }

        // Also scan the loaded scene: object ID ranges alone can miss Sailing
        // facilities, leaving them without an
        // override and fell through to the Shop sprite fingerprint.
        scanLoadedSceneForSailingMapIcons();
    }

    private void scanLoadedSceneForSailingMapIcons()
    {
        if (client.getTopLevelWorldView() == null
            || client.getTopLevelWorldView().getScene() == null)
        {
            return;
        }

        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
        if (tiles == null)
        {
            return;
        }

        for (Tile[][] plane : tiles)
        {
            if (plane == null)
            {
                continue;
            }

            for (Tile[] column : plane)
            {
                if (column == null)
                {
                    continue;
                }

                for (Tile tile : column)
                {
                    if (tile == null)
                    {
                        continue;
                    }

                    GameObject[] gameObjects = tile.getGameObjects();
                    if (gameObjects != null)
                    {
                        for (GameObject gameObject : gameObjects)
                        {
                            if (gameObject != null)
                            {
                                registerSailingMapIconFromObjectId(gameObject.getId());
                            }
                        }
                    }

                    if (tile.getDecorativeObject() != null)
                    {
                        registerSailingMapIconFromObjectId(tile.getDecorativeObject().getId());
                    }
                    if (tile.getGroundObject() != null)
                    {
                        registerSailingMapIconFromObjectId(tile.getGroundObject().getId());
                    }
                    if (tile.getWallObject() != null)
                    {
                        registerSailingMapIconFromObjectId(tile.getWallObject().getId());
                    }
                }
            }
        }
    }

    private void registerSailingMapIconFromObjectId(int objectId)
    {
        if (objectId < 0)
        {
            return;
        }

        ObjectComposition composition = client.getObjectDefinition(objectId);
        if (composition == null)
        {
            return;
        }

        registerSailingMapIconComposition(composition);

        int[] impostorIds = composition.getImpostorIds();
        if (impostorIds != null)
        {
            for (int impostorId : impostorIds)
            {
                if (impostorId < 0)
                {
                    continue;
                }
                ObjectComposition impostor = client.getObjectDefinition(impostorId);
                if (impostor != null)
                {
                    registerSailingMapIconComposition(impostor);
                }
            }
        }
    }

    private void registerSailingMapIconComposition(ObjectComposition composition)
    {
        if (!isSailingMapIconObject(composition))
        {
            return;
        }

        int mapIconId = composition.getMapIconId();
        if (mapIconId >= 0)
        {
            iconIndexOverrides.put(mapIconId, IconCategory.SAILING);
            log.debug("Sailing icon override: object {} '{}' -> map icon slot {}",
                composition.getId(), composition.getName(), mapIconId);
        }
    }

    private static boolean isSailingMapIconObject(ObjectComposition composition)
    {
        if (composition == null)
        {
            return false;
        }

        int id = composition.getId();

        // Current live Sailing facility IDs. Keeping these explicit prevents a
        // generic non-Sailing notice board elsewhere in Gielinor from being
        // swept into this category merely because of its display name.
        if ((id >= 59769 && id <= 59827)
            || (id >= 60287 && id <= 60309)
            || id == 60312)
        {
            return true;
        }

        String name = composition.getName();
        if (name == null)
        {
            return false;
        }

        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        return n.equals("port task board")
            || n.equals("ledger table")
            || n.contains("salvaging station")
            || n.equals("docking point")
            || n.equals("mooring point")
            || n.equals("buoy")
            || n.equals("gangplank");
    }

    /**
     * Exact native 15x15 map-icon fingerprints. These are restricted to verified
     * native images, never similarity/slot-only assumptions.
     *
     * Bond Tutor is a strong visual match to the map-key image, and needs
     * live confirmation; Fishing, Mining and Hunter Tutor still need their
     * actual sprite signatures. No broader tutor similarity matching occurs.
     */
    private static IconCategory classifyCapturedMapIcon(SpritePixels sprite)
    {
        if (sprite == null
            || sprite.getWidth() != 15 || sprite.getHeight() != 15
            || sprite.getMaxWidth() != 15 || sprite.getMaxHeight() != 15
            || sprite.getOffsetX() != 0 || sprite.getOffsetY() != 0
            || sprite.getPixels() == null || sprite.getPixels().length != 225)
        {
            return null;
        }

        switch (Arrays.hashCode(sprite.getPixels()))
        {
            case 0xe26c3fb9: // Task Master: green star
                return IconCategory.TASK_MASTER;
            case 0x9c85fe5b: // Pub: beer mug
                return IconCategory.PUB;
            case 0x6e324d4b: // Speedrunning shop: blue clock
            case 0x8705e33e: // Newspaper trader: newsprint
            case 0x73a667fc: // Clan Hub: gray building
                return IconCategory.OTHER_SERVICE;
            case 0x6c70232f: // Apothecary: purple flask
                return IconCategory.SHOP;
            case 0x7dcd096a: // Bond Tutor: blue-rimmed green/gold badge
                return IconCategory.TUTOR;
            default:
                return null;
        }
    }

    private void applyIconFilters()
    {
        if (transparentIcon == null)
        {
            return;
        }

        // The sprite archive is not guaranteed to be ready at plugin startup.
        if (iconCatalog.isEmpty())
        {
            rebuildIconCatalog();
            if (iconCatalog.isEmpty())
            {
                return;
            }
        }

        SpritePixels[] liveIcons = client.getMapIcons();
        if (liveIcons == null)
        {
            return;
        }


        if (liveIcons != lastLiveIconArray)
        {
            hiddenIconOriginals.clear();
            lastLiveIconArray = liveIcons;
            // Keep the existing Sailing scene-object overrides current.
            rebuildSailingIconIndexOverrides();
        }

        for (int i = 0; i < liveIcons.length; i++)
        {
            SpritePixels current = liveIcons[i];
            if (current == null)
            {
                hiddenIconOriginals.remove(i);
                continue;
            }

            SpritePixels nativeSprite;
            if (current == transparentIcon)
            {
                nativeSprite = hiddenIconOriginals.get(i);
                if (nativeSprite == null)
                {
                    continue;
                }
            }
            else
            {
                nativeSprite = current;
                hiddenIconOriginals.put(i, nativeSprite);
            }

            IconSignature signature = new IconSignature(nativeSprite);
            // Confirmed capture fingerprints take priority over approximate
            // Sailing/Shop matching and scene-slot overrides. In particular,
            // the Bond Tutor must never inherit an unrelated Sailing match.
            IconCategory category = classifyCapturedMapIcon(nativeSprite);
            if (category == null)
            {
                category = iconIndexOverrides.get(i);
            }
            if (category == null)
            {
                // Exact native SpriteID fingerprints always win. This is
                // especially important for Food shop: it must remain a Shop
                // even if its circular artwork resembles a Sailing reference.
                category = iconCatalog.get(signature);
            }
            if (category == null && TutorIconFingerprints.matches(
                nativeSprite.getWidth(), nativeSprite.getHeight(),
                nativeSprite.getMaxWidth(), nativeSprite.getMaxHeight(),
                nativeSprite.getOffsetX(), nativeSprite.getOffsetY(),
                nativeSprite.getPixels()))
            {
                // An exact sprite fingerprint. Unlike local slot
                // indices or world-map categories, this follows the artwork
                // if RuneLite rearranges its map-icon array.
                category = IconCategory.TUTOR;
            }
            if (category == null)
            {
                if (isCapturedLedgerSprite(nativeSprite))
                {
                    category = IconCategory.SAILING;
                }
                else
                {
                    category = matchVisualCategory(signature, nativeSprite);
                }
            }
            if (category == null)
            {
                // Not one of the icon families managed by this plugin.
                if (current == transparentIcon)
                {
                    liveIcons[i] = nativeSprite;
                }
                hiddenIconOriginals.remove(i);
                continue;
            }

            if (isCategoryVisible(category))
            {
                if (current == transparentIcon)
                {
                    liveIcons[i] = nativeSprite;
                }
                hiddenIconOriginals.remove(i);
            }
            else
            {
                liveIcons[i] = transparentIcon;
            }
        }
    }

    /**
     * Pixel-exact fallback for the Ledger table icon.
     * Native SpriteID matches retain precedence, protecting Food shop and all
     * other recognized icons. Unlike approximate Sailing visual matching,
     * this cannot claim unrelated icons with similar circular artwork.
     */
    private boolean isCapturedLedgerSprite(SpritePixels sprite)
    {
        if (sprite.getWidth() != 15 || sprite.getHeight() != 15)
        {
            return false;
        }
        int[] pixels = sprite.getPixels();
        if (pixels == null || pixels.length != 225)
        {
            return false;
        }
        return Arrays.hashCode(pixels) == 0x92e45667;
    }

    private void applyMapSceneFilters()
    {
        IndexedSprite[] mapScene = client.getMapScene();
        if (mapScene == null)
        {
            return;
        }

        if (mapScene != lastMapSceneArray)
        {
            foliageMapSceneOriginals.clear();
            rareTreeMapSceneOriginals.clear();
            sharedTreeMapSceneFrames.clear();
            sharedTreeFrameImages.clear();
            mapSceneGameObjects.clear();
            lastMapSceneArray = mapScene;

            // Build an object-aware view of the currently loaded scene. This is
            // important because Jagex reuses some MAPSCENE frames between
            // ordinary trees and rare woodcutting trees (notably oak-style
            // frames). Frame identity alone therefore cannot separate them.
            scanLoadedSceneForTreeFrames(mapScene);
        }

        // Bootstrap known variants even if none happen to be in the loaded
        // scene yet. Runtime scene scanning fills in regional variants by name.
        resolveKnownRareTreeFrames(mapScene);
        resolveKnownFoliageFrames(mapScene);

        // Discover additional foliage-only MAPSCENE frames with the strict
        // native-size/color matcher. Do not classify a rare-tree frame as
        // foliage based only on appearance; overlap is established only when an
        // actual ordinary-tree object in the loaded scene uses that frame.
        for (int i = 0; i < mapScene.length; i++)
        {
            IndexedSprite sprite = mapScene[i];
            if (sprite == null
                || foliageMapSceneOriginals.containsKey(i)
                || rareTreeMapSceneOriginals.containsKey(i))
            {
                continue;
            }

            VisualReference best = null;
            double bestScore = 0d;
            for (VisualReference reference : visualReferences)
            {
                if (reference.category != IconCategory.FOLIAGE)
                {
                    continue;
                }

                double score = reference.score(sprite);
                if (score > bestScore)
                {
                    best = reference;
                    bestScore = score;
                }
            }

            if (best != null && bestScore >= 0.74d)
            {
                foliageMapSceneOriginals.put(i, sprite.getPixels().clone());
                log.debug("Matched MAPSCENE frame {} as foliage {} (strict score {})", i, best.name, bestScore);
            }
        }

        updateSharedTreeFrames();
        applyMapSceneVisibility(mapScene);
    }

    /**
     * Apply native MAPSCENE visibility while treating frames shared by ordinary
     * trees and Rare Trees specially. A shared frame is left native only when
     * both categories are visible. If either category is hidden the frame is
     * suppressed globally and TreeMapSceneOverlay redraws only the individual
     * objects which are meant to remain visible.
     */
    private void applyMapSceneVisibility(IndexedSprite[] mapScene)
    {
        for (Map.Entry<Integer, byte[]> entry : foliageMapSceneOriginals.entrySet())
        {
            if (!sharedTreeMapSceneFrames.contains(entry.getKey()))
            {
                setMapSceneFrameVisible(mapScene, entry.getKey(), entry.getValue(), config.showFoliage());
            }
        }

        for (Map.Entry<Integer, byte[]> entry : rareTreeMapSceneOriginals.entrySet())
        {
            if (!sharedTreeMapSceneFrames.contains(entry.getKey()))
            {
                setMapSceneFrameVisible(mapScene, entry.getKey(), entry.getValue(), config.showRareTrees());
            }
        }

        // With a shared frame, native rendering is safe only when both groups
        // are visible. In every other state we blank it and selectively redraw
        // visible objects in the overlay.
        boolean showSharedNatively = config.showFoliage() && config.showRareTrees();
        for (int index : sharedTreeMapSceneFrames)
        {
            byte[] original = foliageMapSceneOriginals.get(index);
            if (original == null)
            {
                original = rareTreeMapSceneOriginals.get(index);
            }
            if (original != null)
            {
                setMapSceneFrameVisible(mapScene, index, original, showSharedNatively);
            }
        }
    }

    private void setMapSceneFrameVisible(IndexedSprite[] mapScene, int index, byte[] original, boolean visible)
    {
        if (index < 0 || index >= mapScene.length || mapScene[index] == null || original == null)
        {
            return;
        }

        IndexedSprite sprite = mapScene[index];
        if (visible)
        {
            if (isAllZero(sprite.getPixels()))
            {
                sprite.setPixels(original.clone());
            }
        }
        else if (!isAllZero(sprite.getPixels()))
        {
            sprite.setPixels(new byte[original.length]);
        }
    }

    private void resolveKnownRareTreeFrames(IndexedSprite[] mapScene)
    {
        for (int objectId : RARE_TREE_OBJECT_IDS)
        {
            ObjectComposition composition = client.getObjectDefinition(objectId);
            captureRareTreeFrameFromComposition(mapScene, objectId, composition);

            if (composition != null && composition.getImpostorIds() != null)
            {
                ObjectComposition transformed = composition.getImpostor();
                if (transformed != null && transformed != composition)
                {
                    captureRareTreeFrameFromComposition(mapScene, objectId, transformed);
                }
            }
        }
    }

    private void resolveKnownFoliageFrames(IndexedSprite[] mapScene)
    {
        for (int objectId : FOLIAGE_OBJECT_IDS)
        {
            ObjectComposition composition = client.getObjectDefinition(objectId);
            captureFoliageFrameFromComposition(mapScene, objectId, composition);

            if (composition != null && composition.getImpostorIds() != null)
            {
                ObjectComposition transformed = composition.getImpostor();
                if (transformed != null && transformed != composition)
                {
                    captureFoliageFrameFromComposition(mapScene, objectId, transformed);
                }
            }
        }
    }

    private void scanLoadedSceneForTreeFrames(IndexedSprite[] mapScene)
    {
        if (client.getScene() == null || client.getScene().getTiles() == null)
        {
            return;
        }

        Tile[][][] tiles = client.getScene().getTiles();
        for (Tile[][] plane : tiles)
        {
            if (plane == null)
            {
                continue;
            }

            for (Tile[] column : plane)
            {
                if (column == null)
                {
                    continue;
                }

                for (Tile tile : column)
                {
                    if (tile == null || tile.getGameObjects() == null)
                    {
                        continue;
                    }

                    for (GameObject gameObject : tile.getGameObjects())
                    {
                        if (gameObject == null)
                        {
                            continue;
                        }

                        trackMapSceneGameObject(gameObject);
                        captureTreeFramesFromObject(mapScene, gameObject);
                    }
                }
            }
        }
    }

    private void trackMapSceneGameObject(GameObject gameObject)
    {
        if (gameObject == null)
        {
            return;
        }

        ObjectComposition composition = getResolvedComposition(gameObject);
        if (composition != null && composition.getMapSceneId() >= 0)
        {
            mapSceneGameObjects.put(gameObject.getHash(), gameObject);
        }
        else
        {
            mapSceneGameObjects.remove(gameObject.getHash());
        }
    }

    private void captureTreeFramesFromObject(IndexedSprite[] mapScene, GameObject gameObject)
    {
        if (gameObject == null)
        {
            return;
        }

        int objectId = gameObject.getId();
        ObjectComposition composition = client.getObjectDefinition(objectId);
        captureTreeFrameByIdentity(mapScene, objectId, composition);

        if (composition != null && composition.getImpostorIds() != null)
        {
            ObjectComposition transformed = composition.getImpostor();
            if (transformed != null && transformed != composition)
            {
                captureTreeFrameByIdentity(mapScene, objectId, transformed);
            }
        }
    }

    private void captureTreeFrameByIdentity(IndexedSprite[] mapScene, int objectId, ObjectComposition composition)
    {
        if (composition == null)
        {
            return;
        }

        if (isRareTreeComposition(composition))
        {
            captureRareTreeFrameFromComposition(mapScene, objectId, composition);
        }
        else if (isFoliageTreeComposition(objectId, composition))
        {
            captureFoliageFrameFromComposition(mapScene, objectId, composition);
        }
    }

    private ObjectComposition getResolvedComposition(GameObject gameObject)
    {
        if (gameObject == null)
        {
            return null;
        }

        ObjectComposition composition = client.getObjectDefinition(gameObject.getId());
        if (composition != null && composition.getImpostorIds() != null)
        {
            ObjectComposition transformed = composition.getImpostor();
            if (transformed != null)
            {
                return transformed;
            }
        }
        return composition;
    }

    private static String normalizedObjectName(ObjectComposition composition)
    {
        if (composition == null || composition.getName() == null)
        {
            return "";
        }
        return composition.getName().trim().toLowerCase();
    }

    private static boolean isRareTreeComposition(ObjectComposition composition)
    {
        return RARE_TREE_NAMES.contains(normalizedObjectName(composition));
    }

    private static boolean isFoliageTreeComposition(int objectId, ObjectComposition composition)
    {
        if (isKnownFoliageObjectId(objectId))
        {
            return true;
        }
        return FOLIAGE_TREE_NAMES.contains(normalizedObjectName(composition));
    }

    private static boolean isKnownFoliageObjectId(int objectId)
    {
        for (int id : FOLIAGE_OBJECT_IDS)
        {
            if (id == objectId)
            {
                return true;
            }
        }
        return false;
    }

    private void captureRareTreeFrameFromComposition(IndexedSprite[] mapScene, int objectId, ObjectComposition composition)
    {
        if (composition == null)
        {
            return;
        }

        int mapSceneId = composition.getMapSceneId();
        if (mapSceneId < 0 || mapSceneId >= mapScene.length || mapScene[mapSceneId] == null)
        {
            return;
        }

        if (!rareTreeMapSceneOriginals.containsKey(mapSceneId))
        {
            // If foliage already captured this shared frame, use its preserved
            // native pixels instead of accidentally snapshotting a currently
            // blanked sprite.
            byte[] pixels = foliageMapSceneOriginals.get(mapSceneId);
            if (pixels == null)
            {
                pixels = mapScene[mapSceneId].getPixels();
            }

            if (pixels != null && !isAllZero(pixels))
            {
                rareTreeMapSceneOriginals.put(mapSceneId, pixels.clone());
                log.debug("Resolved rare-tree object {} ({}) to MAPSCENE frame {}",
                    objectId, composition.getName(), mapSceneId);
            }
        }
    }

    private void captureFoliageFrameFromComposition(IndexedSprite[] mapScene, int objectId, ObjectComposition composition)
    {
        if (composition == null)
        {
            return;
        }

        int mapSceneId = composition.getMapSceneId();
        if (mapSceneId < 0 || mapSceneId >= mapScene.length || mapScene[mapSceneId] == null)
        {
            return;
        }

        if (!foliageMapSceneOriginals.containsKey(mapSceneId))
        {
            // A MAPSCENE frame may legitimately be shared with a Rare Tree.
            // Preserve the same native source pixels in both categories and let
            // object-aware separation handle the overlap later.
            byte[] pixels = rareTreeMapSceneOriginals.get(mapSceneId);
            if (pixels == null)
            {
                pixels = mapScene[mapSceneId].getPixels();
            }

            if (pixels != null && !isAllZero(pixels))
            {
                foliageMapSceneOriginals.put(mapSceneId, pixels.clone());
                log.debug("Resolved foliage object {} ({}) to MAPSCENE frame {}",
                    objectId, composition.getName(), mapSceneId);
            }
        }
    }

    private void updateSharedTreeFrames()
    {
        Set<Integer> next = new HashSet<>(foliageMapSceneOriginals.keySet());
        next.retainAll(rareTreeMapSceneOriginals.keySet());

        if (!next.equals(sharedTreeMapSceneFrames))
        {
            sharedTreeMapSceneFrames.clear();
            sharedTreeMapSceneFrames.addAll(next);
            sharedTreeFrameImages.clear();
            if (!sharedTreeMapSceneFrames.isEmpty())
            {
                log.debug("Separating shared ordinary/rare tree MAPSCENE frames {}", sharedTreeMapSceneFrames);
            }
        }
    }

    /**
     * Draw only the visible category back over a native frame which is shared by
     * ordinary trees and Rare Trees. This is used only when the two hide toggles
     * differ; when both categories are shown the native frame is left untouched,
     * and when both are hidden nothing is redrawn.
     */
    void renderSeparatedTreeObjects(Graphics2D graphics)
    {
        if (graphics == null
            || sharedTreeMapSceneFrames.isEmpty()
            || config.showFoliage() == config.showRareTrees())
        {
            return;
        }

        IndexedSprite[] mapScene = client.getMapScene();
        if (mapScene == null || mapScene != lastMapSceneArray)
        {
            return;
        }

        for (GameObject gameObject : mapSceneGameObjects.values())
        {
            if (gameObject == null)
            {
                continue;
            }

            ObjectComposition composition = getResolvedComposition(gameObject);
            if (composition == null)
            {
                continue;
            }

            int mapSceneId = composition.getMapSceneId();
            if (!sharedTreeMapSceneFrames.contains(mapSceneId))
            {
                continue;
            }

            boolean rareTree = isRareTreeComposition(composition);
            boolean foliageTree = !rareTree && isFoliageTreeComposition(gameObject.getId(), composition);

            // Unknown objects which happen to reuse a shared tree frame are
            // always preserved. The separation logic is intentionally allowed
            // to hide only objects positively classified into one of our two
            // tree groups.
            boolean shouldDraw = rareTree
                ? config.showRareTrees()
                : foliageTree ? config.showFoliage() : true;

            if (!shouldDraw)
            {
                continue;
            }

            BufferedImage image = getSharedTreeFrameImage(mapSceneId, mapScene);
            net.runelite.api.Point center = gameObject.getMinimapLocation();
            if (image == null || center == null)
            {
                continue;
            }

            double scale = client.getMinimapZoom() / 4.0d;
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = center.getX() - width / 2;
            int y = center.getY() - height / 2;
            graphics.drawImage(image, x, y, width, height, null);
        }
    }

    private BufferedImage getSharedTreeFrameImage(int mapSceneId, IndexedSprite[] mapScene)
    {
        BufferedImage cached = sharedTreeFrameImages.get(mapSceneId);
        if (cached != null)
        {
            return cached;
        }

        if (mapSceneId < 0 || mapSceneId >= mapScene.length || mapScene[mapSceneId] == null)
        {
            return null;
        }

        byte[] pixels = foliageMapSceneOriginals.get(mapSceneId);
        if (pixels == null)
        {
            pixels = rareTreeMapSceneOriginals.get(mapSceneId);
        }
        if (pixels == null || isAllZero(pixels))
        {
            return null;
        }

        IndexedSprite sprite = mapScene[mapSceneId];
        int sourceWidth = sprite.getWidth();
        int sourceHeight = sprite.getHeight();
        int offsetX = Math.max(0, sprite.getOffsetX());
        int offsetY = Math.max(0, sprite.getOffsetY());
        int width = Math.max(sourceWidth + offsetX, sprite.getOriginalWidth());
        int height = Math.max(sourceHeight + offsetY, sprite.getOriginalHeight());
        if (width <= 0 || height <= 0 || pixels.length < sourceWidth * sourceHeight)
        {
            return null;
        }

        int[] palette = sprite.getPalette();
        if (palette == null)
        {
            return null;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sourceHeight; y++)
        {
            for (int x = 0; x < sourceWidth; x++)
            {
                int paletteIndex = pixels[y * sourceWidth + x] & 0xff;
                if (paletteIndex == 0 || paletteIndex >= palette.length)
                {
                    continue;
                }

                int dx = x + offsetX;
                int dy = y + offsetY;
                if (dx >= 0 && dy >= 0 && dx < width && dy < height)
                {
                    image.setRGB(dx, dy, 0xff000000 | (palette[paletteIndex] & 0x00ffffff));
                }
            }
        }

        sharedTreeFrameImages.put(mapSceneId, image);
        return image;
    }

    private static boolean isAllZero(byte[] pixels)
    {
        if (pixels == null)
        {
            return true;
        }

        for (byte pixel : pixels)
        {
            if (pixel != 0)
            {
                return false;
            }
        }
        return true;
    }

    private void restoreMapSceneFilters()
    {
        IndexedSprite[] mapScene = client.getMapScene();
        if (mapScene != null && mapScene == lastMapSceneArray)
        {
            restoreMapSceneFrames(mapScene, foliageMapSceneOriginals);
            restoreMapSceneFrames(mapScene, rareTreeMapSceneOriginals);
        }

        foliageMapSceneOriginals.clear();
        rareTreeMapSceneOriginals.clear();
        sharedTreeMapSceneFrames.clear();
        sharedTreeFrameImages.clear();
        mapSceneGameObjects.clear();
        lastMapSceneArray = null;
    }

    private void restoreMapSceneFrames(IndexedSprite[] mapScene, Map<Integer, byte[]> originals)
    {
        for (Map.Entry<Integer, byte[]> entry : originals.entrySet())
        {
            int index = entry.getKey();
            if (index >= 0 && index < mapScene.length && mapScene[index] != null)
            {
                mapScene[index].setPixels(entry.getValue().clone());
            }
        }
    }

    private boolean isCategoryVisible(IconCategory category)
    {
        switch (category)
        {
            case BANK:
                return config.showBanks();
            case AGILITY_TRAINING:
                return config.showAgilityTraining();
            case AGILITY_SHORTCUT:
                return config.showAgilityShortcuts();
            case ALTAR:
                return config.showAltars();
            case QUEST:
                return config.showQuestStarts();
            case MINIGAME:
                return config.showMinigames();
            case TRANSPORT:
                return config.showTransportation();
            case DUNGEON:
                return config.showDungeons();
            case FARMING_PATCH:
                return config.showFarmingPatches();
            case FISHING_SPOT:
                return config.showFishingSpots();
            case MINING_SITE:
                return config.showMiningSites();
            case FOLIAGE:
                return config.showFoliage();
            case RARE_TREE:
                return config.showRareTrees();
            case SKILL_FACILITY:
                return config.showSkillFacilities();
            case WATER_SOURCE:
                return config.showWaterSources();
            case SHOP:
                return config.showShops();
            case SAILING:
                return config.showSailing();
            case TUTOR:
                return config.showTutors();
            case TASK_MASTER:
                return config.showTaskMaster();
            case PUB:
                return config.showPubs();
            case OTHER_SERVICE:
                return config.showOtherServices();
            default:
                return true;
        }
    }

    private void restoreLiveIcons()
    {
        SpritePixels[] liveIcons = client.getMapIcons();
        if (liveIcons == null)
        {
            hiddenIconOriginals.clear();
            lastLiveIconArray = null;
            return;
        }

        for (Map.Entry<Integer, SpritePixels> entry : hiddenIconOriginals.entrySet())
        {
            int index = entry.getKey();
            if (index >= 0 && index < liveIcons.length && liveIcons[index] == transparentIcon)
            {
                liveIcons[index] = entry.getValue();
            }
        }

        hiddenIconOriginals.clear();
        lastLiveIconArray = null;
    }
}
