package com.storagehelper.chest;

import com.storagehelper.logic.Stack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Converts live {@link ChestMenu} contents into the planner's immutable {@link Stack} lists. */
public final class ChestSnapshots {
	private ChestSnapshots() {
	}

	/** Number of chest (container) slots; they occupy menu indices {@code 0..n-1}. */
	public static int chestSlotCount(ChestMenu menu) {
		return menu.getRowCount() * 9;
	}

	public static List<Stack> chestSlots(ChestMenu menu) {
		int n = chestSlotCount(menu);
		List<Stack> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(toStack(menu.getSlot(i).getItem()));
		}
		return out;
	}

	/** Player main inventory + hotbar as shown in the chest menu (index 0 = menu slot {@code chestSlotCount}). */
	public static List<Stack> playerSlots(ChestMenu menu) {
		int from = chestSlotCount(menu);
		int to = menu.slots.size();
		List<Stack> out = new ArrayList<>(to - from);
		for (int i = from; i < to; i++) {
			out.add(toStack(menu.getSlot(i).getItem()));
		}
		return out;
	}

	public static Stack carried(ChestMenu menu) {
		return toStack(menu.getCarried());
	}

	public static Stack toStack(ItemStack stack) {
		return stack.isEmpty() ? null : new Stack(McStackType.of(stack), stack.getCount());
	}
}
