package com.storagehelper.chest;

import com.storagehelper.logic.ItemFacts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashSet;
import java.util.Set;

/** Builds game-agnostic {@link ItemFacts} from a live {@link ItemStack}. */
public final class ItemFactsFactory {
	private ItemFactsFactory() {
	}

	public static ItemFacts facts(ItemStack stack) {
		Item item = stack.getItem();
		String id = BuiltInRegistries.ITEM.getKey(item).toString();

		Set<String> tags = new HashSet<>();
		stack.typeHolder().tags().forEach(tag -> tags.add(tag.location().toString()));

		boolean armor = false;
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			EquipmentSlot.Type type = equippable.slot().getType();
			armor = type == EquipmentSlot.Type.HUMANOID_ARMOR || type == EquipmentSlot.Type.ANIMAL_ARMOR;
		}

		return new ItemFacts(
				id,
				item instanceof BlockItem,
				stack.has(DataComponents.TOOL),
				stack.has(DataComponents.WEAPON),
				armor,
				stack.has(DataComponents.FOOD),
				stack.has(DataComponents.CONSUMABLE),
				tags);
	}
}
