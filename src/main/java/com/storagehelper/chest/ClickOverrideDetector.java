package com.storagehelper.chest;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects items that override vanilla's click-on-stack behaviour ({@code Item#overrideStackedOnOther}
 * / {@code Item#overrideOtherStackedOnMe}), such as bundles and modded backpacks. The sort
 * planner treats those as {@linkplain com.storagehelper.logic.StackType#fragile() fragile}.
 * Minecraft 26.x ships unobfuscated, so the method names are stable at runtime.
 */
public final class ClickOverrideDetector {
	private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

	private ClickOverrideDetector() {
	}

	public static boolean overridesClickBehaviour(Item item) {
		return CACHE.computeIfAbsent(item.getClass(), ClickOverrideDetector::compute);
	}

	private static boolean compute(Class<?> cls) {
		try {
			Method onOther = cls.getMethod("overrideStackedOnOther",
					ItemStack.class, Slot.class, ClickAction.class, Player.class);
			Method onMe = cls.getMethod("overrideOtherStackedOnMe",
					ItemStack.class, ItemStack.class, Slot.class, ClickAction.class, Player.class, SlotAccess.class);
			return onOther.getDeclaringClass() != Item.class || onMe.getDeclaringClass() != Item.class;
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Unknown shape: be conservative and treat it as fragile.
			return true;
		}
	}
}
