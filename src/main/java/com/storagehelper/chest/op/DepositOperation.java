package com.storagehelper.chest.op;

import com.storagehelper.chest.ChestSnapshots;
import com.storagehelper.logic.DepositPlanner;
import com.storagehelper.logic.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * "Deposit Matching": shift-clicks every player stack whose item id is already present in the
 * chest. Vanilla quick-move tops up partial stacks first, then uses empty slots, and leaves
 * overflow in the player's slot. Before each click the slot is re-read and the chest is
 * re-checked for room, so a stale plan is never executed blindly.
 */
public final class DepositOperation extends ChestOperation {
	private final Deque<Integer> queue = new ArrayDeque<>();
	private Set<String> matchIds = Set.of();
	private int startCount;

	public DepositOperation(Minecraft mc, ChestMenu menu, int clicksPerTick) {
		super(mc, menu, clicksPerTick);
	}

	@Override
	public Component runningLabel() {
		return t("status.deposit.running");
	}

	@Override
	public void start() {
		if (!cursorEmpty()) {
			finish(t("status.cursor_not_empty"));
			return;
		}
		List<Stack> chest = ChestSnapshots.chestSlots(menu);
		List<Stack> player = ChestSnapshots.playerSlots(menu);
		matchIds = DepositPlanner.chestItemIds(chest);
		int offset = ChestSnapshots.chestSlotCount(menu);
		for (int i : DepositPlanner.candidates(player, matchIds)) {
			queue.add(i + offset);
		}
		startCount = DepositPlanner.countItems(player, matchIds);
		if (queue.isEmpty()) {
			finish(t("status.deposit.nothing"));
		}
	}

	@Override
	public void tick() {
		if (cancelRequested) {
			finish(t("status.stopped"));
			return;
		}
		if (!cursorEmpty()) {
			finish(t("status.cursor_not_empty"));
			return;
		}
		int clicks = 0;
		while (clicks < clicksPerTick && !queue.isEmpty()) {
			int menuSlot = queue.poll();
			ItemStack live = menu.getSlot(menuSlot).getItem();
			Stack stack = ChestSnapshots.toStack(live);
			if (stack == null || !matchIds.contains(stack.type().registryId())) {
				continue; // changed since planning: skip, never move something unexpected
			}
			if (!DepositPlanner.canAccept(ChestSnapshots.chestSlots(menu), stack)) {
				continue; // no room for this type right now
			}
			if (!click(menuSlot, 0, ContainerInput.QUICK_MOVE)) {
				return;
			}
			clicks++;
		}
		if (queue.isEmpty()) {
			int moved = startCount - DepositPlanner.countItems(ChestSnapshots.playerSlots(menu), matchIds);
			finish(moved > 0 ? t("status.deposit.done", moved) : t("status.deposit.full"));
		}
	}
}
