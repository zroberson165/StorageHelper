package com.storagehelper.logic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemClassifierTest {
	private static ItemFacts facts(String id, boolean block, Set<String> tags) {
		return new ItemFacts(id, block, false, false, false, false, false, tags);
	}

	private static ItemCategory classify(ItemFacts f) {
		return ItemClassifier.classify(f);
	}

	@Test
	void vanillaPrecedenceRules() {
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("minecraft:redstone_ore", true, Set.of("minecraft:redstone_ores"))),
				"ore blocks belong to ores even though they are blocks");
		assertEquals(ItemCategory.REDSTONE, classify(facts("minecraft:redstone_block", true, Set.of())),
				"redstone block is a mechanism, not a mineral");
		assertEquals(ItemCategory.REDSTONE, classify(facts("minecraft:redstone", false, Set.of("c:dusts/redstone"))));
		assertEquals(ItemCategory.REDSTONE, classify(facts("minecraft:piston", true, Set.of())),
				"mechanisms win over blocks");
		assertEquals(ItemCategory.REDSTONE, classify(facts("minecraft:oak_button", true, Set.of("minecraft:buttons", "minecraft:wooden_buttons"))));
		assertEquals(ItemCategory.BLOCKS, classify(facts("minecraft:stone", true, Set.of())));
		assertEquals(ItemCategory.BLOCKS, classify(facts("minecraft:iron_door", true, Set.of())));
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("minecraft:iron_block", true, Set.of())));
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("minecraft:raw_copper", false, Set.of())));
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("minecraft:gold_nugget", false, Set.of("minecraft:metal_nuggets"))));
		assertEquals(ItemCategory.MISC, classify(facts("minecraft:stick", false, Set.of())));
		assertEquals(ItemCategory.MISC, classify(facts("minecraft:string", false, Set.of())));
	}

	@Test
	void componentsDriveToolsArmorAndFood() {
		assertEquals(ItemCategory.TOOLS_AND_WEAPONS, classify(new ItemFacts("minecraft:diamond_pickaxe", false, true, false, false, false, false, Set.of())));
		assertEquals(ItemCategory.TOOLS_AND_WEAPONS, classify(new ItemFacts("mod:laser", false, false, true, false, false, false, Set.of())));
		assertEquals(ItemCategory.ARMOR, classify(new ItemFacts("minecraft:iron_boots", false, false, false, true, false, false, Set.of())));
		assertEquals(ItemCategory.BLOCKS, classify(new ItemFacts("minecraft:carved_pumpkin", true, false, false, true, false, false, Set.of())),
				"wearable blocks stay blocks");
		assertEquals(ItemCategory.FOOD_AND_DRINKS, classify(new ItemFacts("minecraft:golden_apple", false, false, false, false, true, false, Set.of())));
		assertEquals(ItemCategory.FOOD_AND_DRINKS, classify(new ItemFacts("minecraft:potion", false, false, false, false, false, true, Set.of())));
		assertEquals(ItemCategory.FOOD_AND_DRINKS, classify(new ItemFacts("mod:raw_bacon", false, false, false, false, true, false, Set.of())),
				"food beats the raw_ mineral heuristic");
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("minecraft:raw_iron", false, Set.of())));
	}

	@Test
	void unfamiliarModdedItemsFallBackSensibly() {
		assertEquals(ItemCategory.TOOLS_AND_WEAPONS, classify(facts("mod:thing", false, Set.of("c:tools/shear"))), "sub-tags of c:tools count");
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("mod:tin_ingot", false, Set.of())));
		assertEquals(ItemCategory.ORES_AND_MINERALS, classify(facts("mod:tin_ore", true, Set.of("c:ores/tin"))));
		assertEquals(ItemCategory.ARMOR, classify(facts("mod:tin_chestplate", false, Set.of())));
		assertEquals(ItemCategory.TOOLS_AND_WEAPONS, classify(facts("mod:tin_sword", false, Set.of())));
		assertEquals(ItemCategory.FOOD_AND_DRINKS, classify(facts("mod:berry_pie", false, Set.of())));
		assertEquals(ItemCategory.REDSTONE, classify(facts("mod:fancy_hopper", true, Set.of())));
		assertEquals(ItemCategory.BLOCKS, classify(facts("mod:weird_block", true, Set.of())));
		assertEquals(ItemCategory.MISC, classify(facts("mod:gizmo", false, Set.of())));
	}
}
