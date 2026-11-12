package com.storagehelper.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans "Deposit Matching": every player stack whose item (by registry id) already appears in
 * the chest is shift-clicked into the chest. Vanilla's quick-move fills compatible partial
 * stacks first and then empty slots, and leaves any overflow in the player's slot, which is
 * exactly the behaviour required.
 */
public final class DepositPlanner {
	private DepositPlanner() {
	}

	/** Registry ids of all item types currently in the chest. */
	public static Set<String> chestItemIds(List<Stack> chestSlots) {
		Set<String> ids = new HashSet<>();
		for (Stack s : chestSlots) {
			if (s != null) {
				ids.add(s.type().registryId());
			}
		}
		return ids;
	}

	/**
	 * Indices (into {@code playerSlots}) of stacks that match one of {@code chestIds}, in order.
	 */
	public static List<Integer> candidates(List<Stack> playerSlots, Set<String> chestIds) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < playerSlots.size(); i++) {
			Stack s = playerSlots.get(i);
			if (s != null && chestIds.contains(s.type().registryId())) {
				out.add(i);
			}
		}
		return out;
	}

	/**
	 * Whether a quick-move of {@code stack} into the chest would move at least one item:
	 * there is a non-full stack of the same type, or an empty slot.
	 */
	public static boolean canAccept(List<Stack> chestSlots, Stack stack) {
		if (stack == null) {
			return false;
		}
		for (Stack s : chestSlots) {
			if (s == null) {
				return true;
			}
			if (s.sameType(stack) && s.count() < s.type().maxStackSize()) {
				return true;
			}
		}
		return false;
	}

	/** Total number of items of the given registry ids across the given slots. */
	public static int countItems(List<Stack> slots, Set<String> ids) {
		int total = 0;
		for (Stack s : slots) {
			if (s != null && ids.contains(s.type().registryId())) {
				total += s.count();
			}
		}
		return total;
	}
}
