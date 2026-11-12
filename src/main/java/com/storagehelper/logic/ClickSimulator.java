package com.storagehelper.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure model of vanilla's {@code PICKUP} left-click on a chest slot with no placement
 * restrictions (chest slots accept anything):
 * <ul>
 *   <li>empty cursor, occupied slot: pick up the whole stack</li>
 *   <li>cursor held, empty slot: place the whole cursor</li>
 *   <li>cursor held, slot of the same type: move as many as fit into the slot; the rest stays on the cursor</li>
 *   <li>cursor held, slot of a different type: swap</li>
 * </ul>
 * This mirrors {@code AbstractContainerMenu#doClick} for {@code ClickType.PICKUP}, button 0.
 */
public final class ClickSimulator {
	private ClickSimulator() {
	}

	/** Mutable simulation state. */
	public static final class State {
		public final List<Stack> slots;
		public Stack cursor;

		public State(List<Stack> slots, Stack cursor) {
			this.slots = new ArrayList<>(slots);
			this.cursor = cursor;
		}

		public State copy() {
			return new State(slots, cursor);
		}
	}

	/**
	 * Applies a left-click to {@code state} in place.
	 *
	 * @return true if anything changed
	 */
	public static boolean leftClick(State state, int slot) {
		Stack inSlot = state.slots.get(slot);
		Stack cursor = state.cursor;

		if (cursor == null) {
			if (inSlot == null) {
				return false;
			}
			state.cursor = inSlot;
			state.slots.set(slot, null);
			return true;
		}

		if (inSlot == null) {
			state.slots.set(slot, cursor);
			state.cursor = null;
			return true;
		}

		if (inSlot.sameType(cursor)) {
			int room = inSlot.type().maxStackSize() - inSlot.count();
			int moved = Math.min(room, cursor.count());
			if (moved <= 0) {
				return false;
			}
			state.slots.set(slot, inSlot.withCount(inSlot.count() + moved));
			state.cursor = cursor.withCount(cursor.count() - moved);
			return true;
		}

		// Different type: swap (vanilla requires cursor.count <= slot max for the cursor stack, always true for legal stacks)
		state.slots.set(slot, cursor);
		state.cursor = inSlot;
		return true;
	}
}
