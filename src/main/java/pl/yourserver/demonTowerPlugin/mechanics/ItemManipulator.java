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
     */
    public ItemStack addStateTag(ItemStack item, ItemState state) {
        if (item == null || state.isEmpty()) return item;

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        // Add state tag at the beginning (after empty line)
        lore.add(0, Component.empty());
        lore.add(1, LEGACY.deserialize(state.getTag()));

        meta.lore(lore);
        result.setItemMeta(meta);
        return result;
    }

    /**
     * CORRUPTED BLACKSMITH: Upgrade a random stat by 30%
     */
    public ItemStack upgradeRandomStat(ItemStack item, double percentage) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) return addStateTag(result, ItemState.CORRUPTED);

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return addStateTag(result, ItemState.CORRUPTED);

        List<Integer> statIndices = findStatLineIndices(lore);
        if (statIndices.isEmpty()) return addStateTag(result, ItemState.CORRUPTED);

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

        if (modifiableIndices.isEmpty()) return addStateTag(result, ItemState.CORRUPTED);

        int targetIdx = modifiableIndices.get(random.nextInt(modifiableIndices.size()));
        String originalLine = LEGACY_SECTION.serialize(lore.get(targetIdx));
        String modifiedLine = modifyStatValue(originalLine, 1.0 + percentage);

        lore.set(targetIdx, LEGACY.deserialize(modifiedLine.replace('§', '&')));
        meta.lore(lore);
        result.setItemMeta(meta);

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
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) return addStateTag(result, ItemState.SANCTIFIED);

        List<Component> lore = new ArrayList<>(meta.lore());
        List<Integer> statIndices = findStatLineIndices(lore);

        if (statIndices.isEmpty()) return addStateTag(result, ItemState.SANCTIFIED);

        // Pick one random stat for +50%, rest get +25%
        int boostedIdx = random.nextInt(statIndices.size());

        for (int i = 0; i < statIndices.size(); i++) {
            int idx = statIndices.get(i);
            String originalLine = LEGACY_SECTION.serialize(lore.get(idx));
            double multiplier = (i == boostedIdx) ? 1.5 : 1.25;
            String modifiedLine = modifyStatValue(originalLine, multiplier);
            lore.set(idx, LEGACY.deserialize(modifiedLine.replace('§', '&')));
        }

        meta.lore(lore);
        result.setItemMeta(meta);

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

        // Add Smelted tag
        newLore.add(Component.empty());
        newLore.add(LEGACY.deserialize("&6Smelted"));
        newLore.add(Component.empty());

        // Add merged stats
        for (Map.Entry<String, Double> entry : mergedStats.entrySet()) {
            String statLine = formatStatLine(entry.getKey(), entry.getValue());
            if (statLine != null) {
                newLore.add(LEGACY.deserialize(statLine));
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
        ItemStack result = item1.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasLore()) return addStateTag(result, ItemState.ULTIMATE);

        List<Component> lore = new ArrayList<>(meta.lore());
        List<Integer> statIndices = findStatLineIndices(lore);

        for (int idx : statIndices) {
            String originalLine = LEGACY_SECTION.serialize(lore.get(idx));
            String modifiedLine = modifyStatValue(originalLine, 2.0);
            lore.set(idx, LEGACY.deserialize(modifiedLine.replace('§', '&')));
        }

        meta.lore(lore);
        result.setItemMeta(meta);

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

        for (int i = 0; i < lore.size(); i++) {
            String text = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (statPattern.matcher(text).find() && !text.contains("Rarity") && !text.contains("Level") && !text.contains("Set:")) {
                indices.add(i);
            }
        }
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
        Pattern pattern = Pattern.compile("(\\+?)([\\d.]+)(%?)");
        Matcher matcher = pattern.matcher(line);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String prefix = matcher.group(1);
            double value = Double.parseDouble(matcher.group(2));
            String suffix = matcher.group(3);

            double newValue = value * multiplier;
            String replacement;
            if (newValue == Math.floor(newValue)) {
                replacement = prefix + (int) newValue + suffix;
            } else {
                replacement = prefix + String.format("%.1f", newValue) + suffix;
            }
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
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
