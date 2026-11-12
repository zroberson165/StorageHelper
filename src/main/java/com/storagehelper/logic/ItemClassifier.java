package com.storagehelper.logic;

import java.util.Set;

/**
 * Assigns an {@link ItemCategory} to an item using tags, data components and, as a
 * fallback for unfamiliar (modded) items, registry-id keywords.
 *
 * <p>Precedence, highest first: tools/weapons, armor, ores & minerals, food & drinks,
 * redstone & mechanisms, blocks, misc. This means ore blocks land in "ores", and
 * redstone mechanisms land in "redstone" even though both are also blocks. Redstone dust
 * and redstone blocks are explicitly routed to "redstone" ahead of the mineral rules.
 *
 * <p>Tag rules accept an exact tag id or any sub-tag of it ({@code c:tools} also matches
 * {@code c:tools/shear}). Vanilla {@code minecraft:} tags are available on every server;
 * {@code c:} convention tags only when the server runs Fabric API, so keyword fallbacks
 * cover vanilla items as well.
 */
public final class ItemClassifier {
	private ItemClassifier() {
	}

	private static final Set<String> TOOL_TAGS = Set.of(
			"minecraft:swords", "minecraft:axes", "minecraft:pickaxes", "minecraft:shovels", "minecraft:hoes",
			"minecraft:spears", "minecraft:arrows",
			"minecraft:sword_enchantable", "minecraft:sharp_weapon_enchantable", "minecraft:mining_enchantable",
			"minecraft:bow_enchantable", "minecraft:crossbow_enchantable", "minecraft:trident_enchantable",
			"minecraft:mace_enchantable", "minecraft:fishing_enchantable", "minecraft:fire_aspect_enchantable",
			"c:tools", "c:melee_weapons", "c:ranged_weapons");

	private static final Set<String> ARMOR_TAGS = Set.of(
			"minecraft:head_armor", "minecraft:chest_armor", "minecraft:leg_armor", "minecraft:foot_armor",
			"minecraft:armor_enchantable", "minecraft:harnesses",
			"c:armors");

	private static final Set<String> ORE_TAGS = Set.of(
			"minecraft:coal_ores", "minecraft:iron_ores", "minecraft:copper_ores", "minecraft:gold_ores",
			"minecraft:redstone_ores", "minecraft:emerald_ores", "minecraft:lapis_ores", "minecraft:diamond_ores",
			"minecraft:coals", "minecraft:metal_nuggets",
			"c:ores", "c:raw_materials", "c:ingots", "c:nuggets", "c:gems", "c:dusts", "c:raw_blocks");

	private static final Set<String> FOOD_TAGS = Set.of(
			"minecraft:meat", "minecraft:fishes", "c:foods", "c:drinks", "c:crops");

	private static final Set<String> REDSTONE_TAGS = Set.of(
			"minecraft:rails", "minecraft:buttons", "minecraft:wooden_buttons", "minecraft:stone_buttons",
			"minecraft:wooden_pressure_plates",
			"c:redstone_dusts");

	private static final Set<String> TOOL_EXACT = Set.of(
			"shears", "flint_and_steel", "fishing_rod", "bow", "crossbow", "trident", "mace", "shield",
			"brush", "arrow", "spectral_arrow", "tipped_arrow", "spyglass", "lead", "carrot_on_a_stick",
			"warped_fungus_on_a_stick", "fire_charge", "wind_charge", "spear");
	private static final String[] TOOL_SUFFIXES = {
			"_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_bow", "_crossbow", "_spear", "_dagger", "_hammer",
			"_scythe", "_shears", "_trident", "_mace", "_shield", "_knife", "_sickle", "_paxel"};

	private static final Set<String> ARMOR_EXACT = Set.of("elytra", "turtle_helmet", "saddle");
	private static final String[] ARMOR_SUFFIXES = {
			"_helmet", "_chestplate", "_leggings", "_boots", "_horse_armor", "_wolf_armor", "_harness"};

	/** Minerals that are neither ores nor ingots but belong in the ores/minerals group. */
	private static final Set<String> MINERAL_EXACT = Set.of(
			"coal", "charcoal", "diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "netherite_scrap",
			"ancient_debris",
			"iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "coal_block",
			"netherite_block", "copper_block", "raw_iron_block", "raw_gold_block", "raw_copper_block",
			"glowstone_dust", "prismarine_crystals", "resin_clump", "resin_brick");
	private static final String[] ORE_SUFFIXES = {"_ore", "_ingot", "_nugget", "_gem", "_dust", "_crystal"};

	private static final Set<String> FOOD_EXACT = Set.of(
			"wheat", "sugar", "egg", "cocoa_beans", "cake", "milk_bucket", "honey_bottle",
			"potion", "splash_potion", "lingering_potion", "sweet_berries", "glow_berries");
	private static final String[] FOOD_SUFFIXES = {
			"_stew", "_soup", "_pie", "_bread", "_cookie", "_potion", "_apple", "_berries",
			"_beef", "_porkchop", "_chicken", "_mutton", "_rabbit", "_cod", "_salmon",
			"_juice", "_drink", "_tea", "_coffee", "_milk", "_sandwich"};
	private static final String[] FOOD_PREFIXES = {"cooked_", "baked_"};

	private static final Set<String> REDSTONE_EXACT = Set.of(
			"redstone", "redstone_wire", "redstone_torch", "redstone_block", "repeater", "comparator", "observer",
			"piston", "sticky_piston", "dispenser", "dropper", "hopper", "lever", "tripwire_hook",
			"daylight_detector", "note_block", "target", "redstone_lamp", "tnt", "sculk_sensor",
			"calibrated_sculk_sensor", "crafter", "hopper_minecart", "tnt_minecart", "powered_rail",
			"detector_rail", "activator_rail", "rail", "stone_pressure_plate", "light_weighted_pressure_plate",
			"heavy_weighted_pressure_plate", "polished_blackstone_pressure_plate", "trapped_chest",
			"lightning_rod", "jukebox");
	private static final String[] REDSTONE_SUFFIXES = {
			"_button", "_pressure_plate", "_rail", "_piston", "_bulb", "_sensor", "_comparator",
			"_repeater", "_observer", "_dispenser", "_dropper", "_hopper", "_lever", "_lightning_rod"};
	private static final String[] REDSTONE_CONTAINS = {"redstone"};

	public static ItemCategory classify(ItemFacts f) {
		String path = f.path();

		if (isTool(f, path)) {
			return ItemCategory.TOOLS_AND_WEAPONS;
		}
		if (isArmor(f, path)) {
			return ItemCategory.ARMOR;
		}
		if (isRedstoneOverride(path)) {
			return ItemCategory.REDSTONE;
		}
		if (isOre(f, path)) {
			return ItemCategory.ORES_AND_MINERALS;
		}
		if (isFood(f, path)) {
			return ItemCategory.FOOD_AND_DRINKS;
		}
		if (isRedstone(f, path)) {
			return ItemCategory.REDSTONE;
		}
		if (f.isBlockItem()) {
			return ItemCategory.BLOCKS;
		}
		return ItemCategory.MISC;
	}

	private static boolean isTool(ItemFacts f, String path) {
		return f.hasToolComponent() || f.hasWeaponComponent() || anyTag(f, TOOL_TAGS)
				|| TOOL_EXACT.contains(path) || endsWithAny(path, TOOL_SUFFIXES);
	}

	private static boolean isArmor(ItemFacts f, String path) {
		// Carved pumpkins and heads are "equippable" but are blocks first.
		boolean wearable = f.isArmorEquippable() && !f.isBlockItem();
		return wearable || anyTag(f, ARMOR_TAGS)
				|| ARMOR_EXACT.contains(path) || endsWithAny(path, ARMOR_SUFFIXES);
	}

	private static boolean isRedstoneOverride(String path) {
		return path.equals("redstone") || path.equals("redstone_block") || path.equals("redstone_wire");
	}

	private static boolean isOre(ItemFacts f, String path) {
		if (anyTag(f, ORE_TAGS) || MINERAL_EXACT.contains(path) || endsWithAny(path, ORE_SUFFIXES)) {
			return true;
		}
		// "raw_iron" is a mineral, "raw_bacon" from a mod is food.
		return path.startsWith("raw_") && !f.hasFoodComponent();
	}

	private static boolean isFood(ItemFacts f, String path) {
		return f.hasFoodComponent() || f.hasConsumableComponent() || anyTag(f, FOOD_TAGS)
				|| FOOD_EXACT.contains(path) || endsWithAny(path, FOOD_SUFFIXES) || startsWithAny(path, FOOD_PREFIXES);
	}

	private static boolean isRedstone(ItemFacts f, String path) {
		return anyTag(f, REDSTONE_TAGS) || REDSTONE_EXACT.contains(path)
				|| endsWithAny(path, REDSTONE_SUFFIXES) || containsAny(path, REDSTONE_CONTAINS);
	}

	/** True if any of the item's tags equals a rule tag or is a sub-tag of one ({@code c:tools/shear} under {@code c:tools}). */
	private static boolean anyTag(ItemFacts f, Set<String> rules) {
		for (String tag : f.tags()) {
			if (rules.contains(tag)) {
				return true;
			}
			int slash = tag.indexOf('/');
			if (slash > 0 && rules.contains(tag.substring(0, slash))) {
				return true;
			}
		}
		return false;
	}

	private static boolean endsWithAny(String s, String[] suffixes) {
		for (String x : suffixes) {
			if (s.endsWith(x)) {
				return true;
			}
		}
		return false;
	}

	private static boolean startsWithAny(String s, String[] prefixes) {
		for (String x : prefixes) {
			if (s.startsWith(x)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsAny(String s, String[] parts) {
		for (String x : parts) {
			if (s.contains(x)) {
				return true;
			}
		}
		return false;
	}
}
