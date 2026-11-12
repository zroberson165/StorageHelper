package com.storagehelper.logic;

import java.util.Set;

/**
 * Game-agnostic facts about an item type that the {@link ItemClassifier} needs.
 * The Minecraft adapter fills this from tags, data components and item classes;
 * tests build it directly.
 *
 * @param registryId      full registry id, e.g. {@code minecraft:iron_ore}
 * @param isBlockItem     true when the item places a block
 * @param hasToolComponent  item carries the vanilla {@code minecraft:tool} component
 * @param hasWeaponComponent item carries the vanilla {@code minecraft:weapon} component
 * @param isArmorEquippable  item is equippable in an armor slot (head/chest/legs/feet/body)
 * @param hasFoodComponent  item carries {@code minecraft:food}
 * @param hasConsumableComponent item carries {@code minecraft:consumable} (potions, milk, ...)
 * @param tags            item tag ids the item belongs to, e.g. {@code minecraft:swords}, {@code c:ingots}
 */
public record ItemFacts(
		String registryId,
		boolean isBlockItem,
		boolean hasToolComponent,
		boolean hasWeaponComponent,
		boolean isArmorEquippable,
		boolean hasFoodComponent,
		boolean hasConsumableComponent,
		Set<String> tags
) {
	public ItemFacts {
		tags = Set.copyOf(tags);
	}

	/** The path part of the registry id (after the namespace). */
	public String path() {
		int idx = registryId.indexOf(':');
		return idx < 0 ? registryId : registryId.substring(idx + 1);
	}

	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}
}
