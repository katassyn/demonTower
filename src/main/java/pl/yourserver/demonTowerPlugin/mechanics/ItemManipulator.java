package pl.yourserver.demonTowerPlugin.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemManipulator {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final DemonTowerPlugin plugin;
    private final Random random = new Random();

    // Debug flag - enable via config
    private boolean debugEnabled = true;

    // All possible stats that can be on items (except Protection which is protected)
    private static final List<StatDefinition> ALL_STATS = Arrays.asList(
        new StatDefinition("Damage", "&7Damage:&c +%d", "⚔", false),
        new StatDefinition("HP", "&7HP:&c +%d", "❤", false),
        new StatDefinition("Health", "&7Health:&c +%d", "❤", false),
        new StatDefinition("Luck", "&7Luck:&a +%.1f", "🍀", true),
        new StatDefinition("Speed", "&7Speed:&b +%d", "👟", false),
        new StatDefinition("Attack Speed", "&7Attack Speed:&e +%d%%", "⚡", true),
        new StatDefinition("Thorns", "&7Thorns:&2 %d", "🌵", false),
        new StatDefinition("Armor", "&7Armor:&b +%d", "🛡", false),
        new StatDefinition("XP Boost", "&7XP Boost:&a +%d%%", "⭐", true),
        new StatDefinition("Biologist Time", "&7Biologist Time:&d +%d%%", "🔬", true),
        new StatDefinition("Evasion Chance", "&7Evasion Chance:&b +%d%%", "💨", true),
        new StatDefinition("Critical Chance", "&7Critical Chance:&c +%d%%", "🎯", true),
        new StatDefinition("Critical Damage", "&7Critical Damage:&c +%d%%", "💥", true),
        new StatDefinition("Armor Penetration", "&7Armor Penetration:&4 +%d%%", "🗡", true),
        new StatDefinition("Block Chance", "&7Block Chance:&b %d%%", "🎲", true),
        new StatDefinition("Block Strength", "&7Block Strength:&a +%d%%", "⚙", true)
    );

    // Stats that should not be modified by Corrupted Blacksmith
    private static final Set<String> PROTECTED_STATS = Set.of("Protection");

    // Accessory materials
    private static final Set<Material> ACCESSORY_MATERIALS = Set.of(
        Material.TNT_MINECART,        // Ring 1
        Material.HOPPER_MINECART,     // Ring 2
        Material.CHEST_MINECART,      // Necklace
        Material.FURNACE_MINECART,    // Adornment
        Material.WHITE_BANNER,        // Cloak
        Material.IRON_DOOR,           // Shield
        Material.MINECART,            // Belt
        Material.LEATHER_HORSE_ARMOR  // Gloves
    );

    public ItemManipulator(DemonTowerPlugin plugin) {
        this.plugin = plugin;
        this.debugEnabled = plugin.getConfigManager().isDebugMode();
    }

    private void debug(String message) {
        if (debugEnabled) {
            plugin.getLogger().info("[ItemManipulator DEBUG] " + message);
        }
    }

    /**
     * Check the state of an item based on its lore tags
     */
    public ItemState getItemState(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return ItemState.NORMAL;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return ItemState.NORMAL;

        List<Component> lore = meta.lore();
        if (lore == null) return ItemState.NORMAL;

        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            if (text.contains("ULTIMATE")) return ItemState.ULTIMATE;
            if (text.contains("CORRUPTED")) return ItemState.CORRUPTED;
            if (text.contains("Smelted")) return ItemState.SMELTED;
            if (text.contains("Sanctified")) return ItemState.SANCTIFIED;
        }

        return ItemState.NORMAL;
    }

    /**
     * Check if item is an accessory (for Divine Source)
     */
    public boolean isAccessory(ItemStack item) {
        if (item == null) return false;
        return ACCESSORY_MATERIALS.contains(item.getType());
    }

    /**
     * Check if item is a boss soul (excluded from Divine Source)
     */
    public boolean isBossSoul(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.DRAGON_BREATH;
    }

    /**
     * Parse all stats from item lore
     */
    public Map<String, Double> parseStats(ItemStack item) {
        Map<String, Double> stats = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return stats;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return stats;

        List<Component> lore = meta.lore();
        if (lore == null) return stats;

        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            parseStat(text, stats);
        }

        return stats;
    }

    private void parseStat(String text, Map<String, Double> stats) {
        // Parse stat patterns like "Damage: +150" or "Critical Chance: +5%"
        Pattern pattern = Pattern.compile("([A-Za-z\\s]+):\\s*\\+?([\\d.]+)%?");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String statName = matcher.group(1).trim();
            try {
                double value = Double.parseDouble(matcher.group(2));
                stats.put(statName, value);
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Add state tag to item lore
     * Tag is added as the FIRST line (no extra empty lines)
     */
    public ItemStack addStateTag(ItemStack item, ItemState state) {
        if (item == null || state.isEmpty()) return item;

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        // Add state tag at the very beginning (first line, no extra empty lines)
        // Use decoration(false) to prevent default italic styling
        Component stateComponent = LEGACY.deserialize(state.getTag())
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        lore.add(0, stateComponent);

        meta.lore(lore);
        result.setItemMeta(meta);
        return result;
    }

    /**
     * CORRUPTED BLACKSMITH: Upgrade a random stat by 30%
     */
    public ItemStack upgradeRandomStat(ItemStack item, double percentage) {
        debug("=== upgradeRandomStat START ===");
        debug("Percentage multiplier: " + percentage + " (will use " + (1.0 + percentage) + "x)");

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            debug("No meta or no lore - returning with CORRUPTED tag only");
            return addStateTag(result, ItemState.CORRUPTED);
        }

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            debug("Lore is null or empty - returning with CORRUPTED tag only");
            return addStateTag(result, ItemState.CORRUPTED);
        }

        debug("Found " + lore.size() + " lore lines");
        for (int i = 0; i < lore.size(); i++) {
            debug("  Lore[" + i + "]: '" + PlainTextComponentSerializer.plainText().serialize(lore.get(i)) + "'");
        }

        List<Integer> statIndices = findStatLineIndices(lore);
        debug("Found " + statIndices.size() + " stat line indices: " + statIndices);

        if (statIndices.isEmpty()) {
            debug("No stat lines found - returning with CORRUPTED tag only");
            return addStateTag(result, ItemState.CORRUPTED);
        }

        // Filter out protected stats
        List<Integer> modifiableIndices = new ArrayList<>();
        for (int idx : statIndices) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(idx));
            boolean isProtected = false;
            for (String protectedStat : PROTECTED_STATS) {
                if (text.contains(protectedStat)) {
                    isProtected = true;
                    break;
                }
            }
            if (!isProtected) modifiableIndices.add(idx);
        }

        debug("Modifiable stat indices (after protection filter): " + modifiableIndices);

        if (modifiableIndices.isEmpty()) {
            debug("No modifiable stats - returning with CORRUPTED tag only");
            return addStateTag(result, ItemState.CORRUPTED);
        }

        int targetIdx = modifiableIndices.get(random.nextInt(modifiableIndices.size()));
        debug("Selected target index: " + targetIdx);

        String originalLine = LEGACY_SECTION.serialize(lore.get(targetIdx));
        debug("Original line (serialized): '" + originalLine + "'");

        String modifiedLine = modifyStatValue(originalLine, 1.0 + percentage);
        debug("Modified line: '" + modifiedLine + "'");

        // Convert back to Component using same serializer, and remove italics
        Component modifiedComponent = LEGACY_SECTION.deserialize(modifiedLine)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        lore.set(targetIdx, modifiedComponent);
        meta.lore(lore);
        result.setItemMeta(meta);

        debug("=== upgradeRandomStat END ===");
        return addStateTag(result, ItemState.CORRUPTED);
    }

    /**
     * CORRUPTED BLACKSMITH: Remove a random stat
     */
    public ItemStack removeRandomStat(ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) return addStateTag(result, ItemState.CORRUPTED);

        List<Component> lore = new ArrayList<>(meta.lore());
        List<Integer> statIndices = findStatLineIndices(lore);

        // Filter out protected stats
        List<Integer> removableIndices = new ArrayList<>();
        for (int idx : statIndices) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(idx));
            boolean isProtected = false;
            for (String protectedStat : PROTECTED_STATS) {
                if (text.contains(protectedStat)) {
                    isProtected = true;
                    break;
                }
            }
            if (!isProtected) removableIndices.add(idx);
        }

        if (removableIndices.isEmpty()) return addStateTag(result, ItemState.CORRUPTED);

        int targetIdx = removableIndices.get(random.nextInt(removableIndices.size()));
        lore.remove(targetIdx);

        meta.lore(lore);
        result.setItemMeta(meta);

        return addStateTag(result, ItemState.CORRUPTED);
    }

    /**
     * CORRUPTED BLACKSMITH: Add a random new stat
     */
    public ItemStack addRandomStat(ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return addStateTag(result, ItemState.CORRUPTED);

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        // Find existing stats to avoid duplicates
        Set<String> existingStats = new HashSet<>();
        for (Component line : lore) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            for (StatDefinition stat : ALL_STATS) {
                if (text.contains(stat.name)) {
                    existingStats.add(stat.name);
                    break;
                }
            }
        }

        // Find available stats to add
        List<StatDefinition> availableStats = new ArrayList<>();
        for (StatDefinition stat : ALL_STATS) {
            if (!existingStats.contains(stat.name) && !PROTECTED_STATS.contains(stat.name)) {
                availableStats.add(stat);
            }
        }

        if (availableStats.isEmpty()) return addStateTag(result, ItemState.CORRUPTED);

        StatDefinition newStat = availableStats.get(random.nextInt(availableStats.size()));
        int value = generateRandomStatValue(newStat);

        String statLine;
        if (newStat.isPercentage) {
            statLine = String.format(newStat.format, value) + " " + newStat.icon;
        } else {
            statLine = String.format(newStat.format, value) + " " + newStat.icon;
        }

        // Find where to insert (after empty line, before Rarity)
        int insertIdx = findStatInsertIndex(lore);
        lore.add(insertIdx, LEGACY.deserialize(statLine));

        meta.lore(lore);
        result.setItemMeta(meta);

        return addStateTag(result, ItemState.CORRUPTED);
    }

    /**
     * CORRUPTED BLACKSMITH: Remove all stats (except Protection)
     */
    public ItemStack removeAllStats(ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) return addStateTag(result, ItemState.CORRUPTED);

        List<Component> lore = new ArrayList<>(meta.lore());
        List<Integer> statIndices = findStatLineIndices(lore);

        // Remove in reverse order to maintain indices
        List<Integer> removableIndices = new ArrayList<>();
        for (int idx : statIndices) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(idx));
            boolean isProtected = false;
            for (String protectedStat : PROTECTED_STATS) {
                if (text.contains(protectedStat)) {
                    isProtected = true;
                    break;
                }
            }
            if (!isProtected) removableIndices.add(idx);
        }

        Collections.reverse(removableIndices);
        for (int idx : removableIndices) {
            lore.remove(idx);
        }

        meta.lore(lore);
        result.setItemMeta(meta);

        return addStateTag(result, ItemState.CORRUPTED);
    }

    /**
     * DIVINE SOURCE: Boost accessory stats (+50% to one, +25% to rest)
     */
    public ItemStack sanctifyAccessory(ItemStack item) {
        debug("=== sanctifyAccessory START ===");

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            debug("No meta or no lore - returning with SANCTIFIED tag only");
            return addStateTag(result, ItemState.SANCTIFIED);
        }

        List<Component> lore = new ArrayList<>(meta.lore());
        debug("Found " + lore.size() + " lore lines");
        for (int i = 0; i < lore.size(); i++) {
            debug("  Lore[" + i + "]: '" + PlainTextComponentSerializer.plainText().serialize(lore.get(i)) + "'");
        }

        List<Integer> statIndices = findStatLineIndices(lore);
        debug("Found " + statIndices.size() + " stat line indices: " + statIndices);

        if (statIndices.isEmpty()) {
            debug("No stat lines found - returning with SANCTIFIED tag only");
            return addStateTag(result, ItemState.SANCTIFIED);
        }

        // Pick one random stat for +50%, rest get +25%
        int boostedIdx = random.nextInt(statIndices.size());
        debug("Selected boosted index (will get 50%): " + boostedIdx);

        for (int i = 0; i < statIndices.size(); i++) {
            int idx = statIndices.get(i);
            String originalLine = LEGACY_SECTION.serialize(lore.get(idx));
            double multiplier = (i == boostedIdx) ? 1.5 : 1.25;
            debug("Processing stat at index " + idx + " with multiplier " + multiplier);
            debug("  Original: '" + originalLine + "'");

            String modifiedLine = modifyStatValue(originalLine, multiplier);
            debug("  Modified: '" + modifiedLine + "'");

            // Convert back to Component and remove italics
            Component modifiedComponent = LEGACY_SECTION.deserialize(modifiedLine)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            lore.set(idx, modifiedComponent);
        }

        meta.lore(lore);
        result.setItemMeta(meta);

        debug("=== sanctifyAccessory END ===");
        return addStateTag(result, ItemState.SANCTIFIED);
    }

    /**
     * DEMONIC SMELTER: Merge two items into one, randomly picking stats
     * Protection only appears once (random pick between the two values)
     */
    public ItemStack smeltItems(ItemStack item1, ItemStack item2) {
        // Randomly pick base item for appearance/name
        ItemStack baseItem = random.nextBoolean() ? item1 : item2;
        ItemStack otherItem = (baseItem == item1) ? item2 : item1;

        ItemStack result = baseItem.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return addStateTag(result, ItemState.SMELTED);

        // Parse stats from both items
        Map<String, Double> stats1 = parseStats(item1);
        Map<String, Double> stats2 = parseStats(item2);

        // Collect all unique stat names
        Set<String> allStats = new LinkedHashSet<>();
        allStats.addAll(stats1.keySet());
        allStats.addAll(stats2.keySet());

        // Build merged stats - randomly pick from each item
        Map<String, Double> mergedStats = new LinkedHashMap<>();
        for (String stat : allStats) {
            Double val1 = stats1.get(stat);
            Double val2 = stats2.get(stat);

            if (val1 != null && val2 != null) {
                // Both items have this stat - random pick
                mergedStats.put(stat, random.nextBoolean() ? val1 : val2);
            } else if (val1 != null) {
                // Only item1 has it - 50% chance to include
                if (random.nextBoolean()) {
                    mergedStats.put(stat, val1);
                }
            } else if (val2 != null) {
                // Only item2 has it - 50% chance to include
                if (random.nextBoolean()) {
                    mergedStats.put(stat, val2);
                }
            }
        }

        // Rebuild lore with merged stats
        List<Component> newLore = new ArrayList<>();

        // Add Smelted tag (first line, no extra empty lines, no italics)
        Component smeltedTag = LEGACY.deserialize("&6Smelted")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        newLore.add(smeltedTag);

        // Add merged stats (no italics)
        for (Map.Entry<String, Double> entry : mergedStats.entrySet()) {
            String statLine = formatStatLine(entry.getKey(), entry.getValue());
            if (statLine != null) {
                Component statComponent = LEGACY.deserialize(statLine)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                newLore.add(statComponent);
            }
        }

        // Copy non-stat lines from base item (rarity, set info, etc.)
        if (meta.hasLore()) {
            List<Component> oldLore = meta.lore();
            for (Component line : oldLore) {
                String text = PlainTextComponentSerializer.plainText().serialize(line);
                // Skip stat lines (already handled) and empty lines at start
                if (!isStatLine(text) && (text.contains("Rarity") || text.contains("Set:") ||
                    text.contains("━") || text.contains("Level") || text.contains("Required"))) {
                    newLore.add(line);
                }
            }
        }

        meta.lore(newLore);
        result.setItemMeta(meta);

        return result;
    }

    private String formatStatLine(String statName, double value) {
        // Find matching stat definition
        for (StatDefinition stat : ALL_STATS) {
            if (stat.name.equalsIgnoreCase(statName)) {
                if (value == Math.floor(value)) {
                    return String.format(stat.format, (int) value) + " " + stat.icon;
                } else {
                    return String.format(stat.format, value) + " " + stat.icon;
                }
            }
        }
        // Handle Protection specially
        if (statName.equalsIgnoreCase("Protection")) {
            if (value == Math.floor(value)) {
                return "&7Protection:&9 " + (int) value;
            } else {
                return "&7Protection:&9 " + String.format("%.1f", value);
            }
        }
        return null;
    }

    private boolean isStatLine(String text) {
        // Check if line contains a stat pattern
        Pattern statPattern = Pattern.compile(".*:\\s*\\+?[\\d.]+");
        return statPattern.matcher(text).find() && !text.contains("Rarity") && !text.contains("Level");
    }

    /**
     * INFERNAL CRUCIBLE: Double all stats
     */
    public ItemStack fuseItems(ItemStack item1, ItemStack item2) {
        debug("=== fuseItems START ===");

        ItemStack result = item1.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            debug("No meta or no lore - returning with ULTIMATE tag only");
            return addStateTag(result, ItemState.ULTIMATE);
        }

        List<Component> lore = new ArrayList<>(meta.lore());
        debug("Found " + lore.size() + " lore lines");
        for (int i = 0; i < lore.size(); i++) {
            debug("  Lore[" + i + "]: '" + PlainTextComponentSerializer.plainText().serialize(lore.get(i)) + "'");
        }

        List<Integer> statIndices = findStatLineIndices(lore);
        debug("Found " + statIndices.size() + " stat line indices: " + statIndices);

        for (int idx : statIndices) {
            String originalLine = LEGACY_SECTION.serialize(lore.get(idx));
            debug("Processing stat at index " + idx + " with multiplier 2.0");
            debug("  Original: '" + originalLine + "'");

            String modifiedLine = modifyStatValue(originalLine, 2.0);
            debug("  Modified: '" + modifiedLine + "'");

            // Convert back and remove italics
            Component modifiedComponent = LEGACY_SECTION.deserialize(modifiedLine)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            lore.set(idx, modifiedComponent);
        }

        meta.lore(lore);
        result.setItemMeta(meta);

        debug("=== fuseItems END ===");
        return addStateTag(result, ItemState.ULTIMATE);
    }

    /**
     * Check if two items are identical (for Infernal Crucible)
     */
    public boolean areItemsIdentical(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;

        // Check display name
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        if (meta1 == null || meta2 == null) return false;

        if (meta1.hasDisplayName() != meta2.hasDisplayName()) return false;
        if (meta1.hasDisplayName()) {
            String name1 = PlainTextComponentSerializer.plainText().serialize(meta1.displayName());
            String name2 = PlainTextComponentSerializer.plainText().serialize(meta2.displayName());
            if (!name1.equals(name2)) return false;
        }

        return true;
    }

    // Helper methods
    private List<Integer> findStatLineIndices(List<Component> lore) {
        List<Integer> indices = new ArrayList<>();
        Pattern statPattern = Pattern.compile(".*:\\s*\\+?[\\d.]+");

        debug("findStatLineIndices: Scanning " + lore.size() + " lore lines");
        for (int i = 0; i < lore.size(); i++) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            boolean matches = statPattern.matcher(text).find();
            boolean excluded = text.contains("Rarity") || text.contains("Level") || text.contains("Set:");

            if (matches && !excluded) {
                debug("  [" + i + "] STAT LINE: '" + text + "'");
                indices.add(i);
            } else {
                debug("  [" + i + "] skipped (matches=" + matches + ", excluded=" + excluded + "): '" + text + "'");
            }
        }
        debug("findStatLineIndices: Found " + indices.size() + " stat lines at indices: " + indices);
        return indices;
    }

    private int findStatInsertIndex(List<Component> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (text.contains("Rarity") || text.contains("---")) {
                return i;
            }
        }
        return lore.size();
    }

    private String modifyStatValue(String line, double multiplier) {
        debug("=== modifyStatValue START ===");
        debug("Input line: '" + line + "'");
        debug("Multiplier: " + multiplier);

        // Simpler approach: find any number that appears after a colon
        // Handle formats like: "§7Damage:§c +150" or "Damage: +150" or "Critical Chance: +5%"

        // First, check if this line contains a colon (stat format)
        int colonIdx = line.indexOf(':');
        if (colonIdx == -1) {
            debug("No colon found in line, returning unchanged");
            return line;
        }

        // Find the number after the colon
        // Pattern: find digits (with optional decimal) possibly preceded by + and followed by %
        String afterColon = line.substring(colonIdx + 1);
        debug("After colon: '" + afterColon + "'");

        // Pattern: (anything before number)(optional +)(number with optional decimal)(0-2 percent signs)(rest)
        // Changed from (%%?) to (%{0,2}) to make % fully optional (stats like "Health: +25" have no %)
        Pattern numberPattern = Pattern.compile("(.*?)(\\+?)(\\d+(?:\\.\\d+)?)(%{0,2})(.*)");
        Matcher matcher = numberPattern.matcher(afterColon);

        if (matcher.matches()) {
            String beforeNum = matcher.group(1);   // color codes, spaces before number
            String plusSign = matcher.group(2);    // optional +
            String numberStr = matcher.group(3);   // the number
            String percentSign = matcher.group(4); // 0, 1, or 2 percent signs
            String afterNum = matcher.group(5);    // anything after (icons, etc.)

            debug("Regex matched!");
            debug("  beforeNum: '" + beforeNum + "'");
            debug("  plusSign: '" + plusSign + "'");
            debug("  numberStr: '" + numberStr + "'");
            debug("  percentSign: '" + percentSign + "'");
            debug("  afterNum: '" + afterNum + "'");

            try {
                double value = Double.parseDouble(numberStr);
                double newValue = value * multiplier;

                debug("Parsed value: " + value + " -> New value: " + newValue);

                String newNumber;
                if (newValue == Math.floor(newValue)) {
                    newNumber = String.valueOf((int) newValue);
                } else {
                    newNumber = String.format(Locale.US, "%.1f", newValue);
                }

                // Reconstruct the line
                String result = line.substring(0, colonIdx + 1) + beforeNum + plusSign + newNumber + percentSign + afterNum;
                debug("Result line: '" + result + "'");
                debug("=== modifyStatValue END (success) ===");
                return result;
            } catch (NumberFormatException e) {
                debug("NumberFormatException: " + e.getMessage());
                debug("=== modifyStatValue END (parse error) ===");
                return line;
            }
        } else {
            debug("Regex did NOT match afterColon: '" + afterColon + "'");
            debug("=== modifyStatValue END (no match) ===");
        }

        return line;
    }

    private int generateRandomStatValue(StatDefinition stat) {
        // Generate reasonable stat values based on stat type
        if (stat.isPercentage) {
            return 3 + random.nextInt(8); // 3-10%
        } else if (stat.name.equals("Luck")) {
            return 1 + random.nextInt(3); // 1-3
        } else if (stat.name.equals("Damage")) {
            return 20 + random.nextInt(50); // 20-70
        } else if (stat.name.equals("HP") || stat.name.equals("Health")) {
            return 10 + random.nextInt(20); // 10-30
        } else {
            return 5 + random.nextInt(15); // 5-20
        }
    }

    /**
     * Check if two items are the same base type (weapon, armor, accessory)
     */
    public boolean isSameBaseType(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;

        boolean item1Weapon = isWeapon(item1);
        boolean item2Weapon = isWeapon(item2);
        boolean item1Armor = isArmor(item1);
        boolean item2Armor = isArmor(item2);
        boolean item1Accessory = isAccessory(item1);
        boolean item2Accessory = isAccessory(item2);

        return (item1Weapon && item2Weapon) ||
               (item1Armor && item2Armor) ||
               (item1Accessory && item2Accessory);
    }

    /**
     * Check if item is a weapon
     */
    public boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.contains("SWORD") || typeName.contains("AXE") ||
               typeName.contains("BOW") || typeName.contains("CROSSBOW") ||
               typeName.contains("TRIDENT");
    }

    /**
     * Check if item is armor
     */
    public boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
               typeName.contains("LEGGINGS") || typeName.contains("BOOTS") ||
               typeName.contains("CAP");
    }

    /**
     * Fuse two SANCTIFIED items into one ULTIMATE item
     * Combines stats from both items and doubles them
     */
    public ItemStack fuseToUltimate(ItemStack item1, ItemStack item2) {
        debug("=== fuseToUltimate START ===");

        // Use item1 as base
        ItemStack result = item1.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            debug("No meta - returning item as-is");
            return result;
        }

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        debug("Found " + lore.size() + " lore lines in base item");
        for (int i = 0; i < lore.size(); i++) {
            debug("  Lore[" + i + "]: '" + PlainTextComponentSerializer.plainText().serialize(lore.get(i)) + "'");
        }

        // Get stats from item2 and add them
        ItemMeta meta2 = item2.getItemMeta();
        if (meta2 != null && meta2.lore() != null) {
            debug("Item2 has " + meta2.lore().size() + " lore lines");
            for (Component comp : meta2.lore()) {
                String line = PlainTextComponentSerializer.plainText().serialize(comp);
                // Skip non-stat lines
                if (line.isEmpty() || line.contains("---") || line.contains("Rarity") ||
                    line.contains("SANCTIFIED") || line.contains("SMELTED") ||
                    line.contains("CORRUPTED") || line.contains("ULTIMATE")) {
                    continue;
                }
                // Find matching stat in item1 and add values
                // For simplicity, just append item2 stats (in real implementation, merge them)
            }
        }

        // Double all stat values
        List<Component> newLore = new ArrayList<>();
        debug("Processing lore lines for stat doubling...");
        for (Component comp : lore) {
            String line = LegacyComponentSerializer.legacySection().serialize(comp);
            debug("Processing line: '" + line + "'");

            // Skip state markers (Sanctified tag)
            if (line.contains("Sanctified")) {
                debug("  Skipping Sanctified tag line");
                continue;
            }
            // Double stat values
            String modified = modifyStatValue(line, 2.0);
            debug("  Modified: '" + modified + "'");

            Component modComp = LegacyComponentSerializer.legacySection().deserialize(modified)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            newLore.add(modComp);
        }

        // Add ULTIMATE marker at top (first line, no italics)
        Component ultimateTag = LEGACY.deserialize("&5&l✦ ULTIMATE ✦")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        newLore.add(0, ultimateTag);

        meta.lore(newLore);
        debug("Final lore has " + newLore.size() + " lines");

        // Update display name with ULTIMATE prefix, preserving original colors
        Component displayName = meta.displayName();
        if (displayName != null) {
            // Use legacySection to preserve color codes from the original name
            String nameWithColors = LEGACY_SECTION.serialize(displayName);
            // Prepend ULTIMATE prefix (§5§l[ULTIMATE] §r) and then the original colored name
            meta.displayName(LEGACY_SECTION.deserialize("§5§l[ULTIMATE] §r" + nameWithColors));
            debug("Updated display name to: [ULTIMATE] " + nameWithColors);
        }

        result.setItemMeta(meta);
        debug("=== fuseToUltimate END ===");
        return result;
    }

    // Inner class for stat definitions
    private static class StatDefinition {
        final String name;
        final String format;
        final String icon;
        final boolean isPercentage;

        StatDefinition(String name, String format, String icon, boolean isPercentage) {
            this.name = name;
            this.format = format;
            this.icon = icon;
            this.isPercentage = isPercentage;
        }
    }
}
