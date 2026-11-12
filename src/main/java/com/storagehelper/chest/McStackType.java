package com.storagehelper.chest;

import com.storagehelper.logic.ItemCategory;
import com.storagehelper.logic.ItemClassifier;
import com.storagehelper.logic.StackType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * {@link StackType} backed by a one-item copy of a real {@link ItemStack}. Equality is
 * vanilla's {@link ItemStack#isSameItemSameComponents}, so two stacks compare equal exactly
 * when a left-click would merge them.
 */
public final class McStackType implements StackType {
	private final ItemStack prototype;
	private final int hash;
	private final String registryId;
	private final boolean fragile;
	private String displayName;
	private ItemCategory category;

	private McStackType(ItemStack stack) {
		this.prototype = stack.copyWithCount(1);
		this.hash = ItemStack.hashItemAndComponents(this.prototype);
		this.registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		this.fragile = this.prototype.has(DataComponents.BUNDLE_CONTENTS)
				|| ClickOverrideDetector.overridesClickBehaviour(stack.getItem());
	}

	public static McStackType of(ItemStack stack) {
		return new McStackType(stack);
	}

	public ItemStack prototype() {
		return prototype;
	}

	@Override
	public String registryId() {
		return registryId;
	}

	@Override
	public String displayName() {
		if (displayName == null) {
			displayName = prototype.getHoverName().getString();
		}
		return displayName;
	}

	@Override
	public int maxStackSize() {
		return Math.max(1, prototype.getMaxStackSize());
	}

	@Override
	public ItemCategory category() {
		if (category == null) {
			category = ItemClassifier.classify(ItemFactsFactory.facts(prototype));
		}
		return category;
	}

	@Override
	public long tieBreaker() {
		return ((long) prototype.getDamageValue() << 32) | (hash & 0xFFFFFFFFL);
	}

	@Override
	public boolean fragile() {
		return fragile;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof McStackType other
				&& hash == other.hash
				&& ItemStack.isSameItemSameComponents(prototype, other.prototype);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return registryId;
	}
}
