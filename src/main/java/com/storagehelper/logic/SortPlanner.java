package com.storagehelper.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans a chest sort as a sequence of single left-clicks, one step at a time.
 *
 * <p>The planner is <em>stateless</em>: every call to {@link #nextStep} looks only at the
 * current live contents (slots + cursor) and the desired {@code target}. That makes it robust
 * to server corrections and to other players touching the chest: whatever the live state is,
 * the next click is derived from it. If the live contents stop matching the target's item
 * totals (someone added or removed items) the planner reports {@link Result#STALE} and the
 * caller recomputes the target from the live state with {@link #computeTarget}.
 *
 * <p>Strategy: positions are finalised left to right. For position {@code p} with target
 * stack {@code T}:
 * <ul>
 *   <li>If the cursor holds {@code T}'s type, click {@code p} (place, top up, or swap out a
 *       foreign stack).</li>
 *   <li>If the cursor is empty, pick up a donor stack of {@code T}'s type from some slot after
 *       {@code p}.</li>
 *   <li>If the cursor holds a foreign stack, click a donor slot after {@code p}: vanilla swaps,
 *       so the foreign stack is parked there and the donor is now on the cursor.</li>
 * </ul>
 * Every click either increases the count at {@code p} or completes it, so the procedure
 * terminates, needs no free slot, and ends with an empty cursor because the target holds
 * exactly the chest's items.
 *
 * <p>{@linkplain StackType#fragile() Fragile} stacks (bundles) are never clicked onto an
 * occupied slot and never have another stack clicked onto them, because vanilla would move
 * items into the bundle instead of swapping. Whenever such a swap would be needed, the
 * cursor stack is parked in an empty slot first. An empty slot always exists while the
 * cursor is non-empty, because the cursor's contents came out of the chest.
 */
public final class SortPlanner {
	private SortPlanner() {
	}

	public enum Result {
		/** {@link Step#slot} should be left-clicked. */
		CLICK,
		/** Slots already equal the target and the cursor is empty. */
		DONE,
		/** Live contents are inconsistent with the target; recompute the target. */
		STALE,
		/**
		 * A fragile stack must be moved but no empty slot is available to park around it.
		 * Cannot happen after {@link #freeSlotStep} has produced an empty slot.
		 */
		BLOCKED
	}

	public record Step(Result result, int slot) {
		public static final Step DONE = new Step(Result.DONE, -1);
		public static final Step STALE = new Step(Result.STALE, -1);
		public static final Step BLOCKED = new Step(Result.BLOCKED, -1);

		static Step click(int slot) {
			return new Step(Result.CLICK, slot);
		}
	}

	/**
	 * Computes the desired final layout for the given contents: stacks of equal type are
	 * consolidated into full stacks plus at most one remainder, types are ordered by
	 * {@code order}, full stacks precede the remainder, and empty slots come last.
	 *
	 * @param slots  current chest slots ({@code null} = empty)
	 * @param cursor stack currently on the cursor, counted as chest content ({@code null} = none)
	 * @return list of the same size as {@code slots}, or {@code null} if the items no longer fit
	 *         (only possible when the chest changed underneath a running operation)
	 */
	public static List<Stack> computeTarget(List<Stack> slots, Stack cursor, Comparator<StackType> order) {
		Map<StackType, Integer> totals = new LinkedHashMap<>();
		for (Stack s : slots) {
			if (s != null) {
				totals.merge(s.type(), s.count(), Integer::sum);
			}
		}
		if (cursor != null) {
			totals.merge(cursor.type(), cursor.count(), Integer::sum);
		}

		List<StackType> types = new ArrayList<>(totals.keySet());
		types.sort(order);

		List<Stack> target = new ArrayList<>(slots.size());
		for (StackType type : types) {
			int total = totals.get(type);
			int max = Math.max(1, type.maxStackSize());
			while (total >= max) {
				target.add(new Stack(type, max));
				total -= max;
			}
			if (total > 0) {
				target.add(new Stack(type, total));
			}
		}
		if (target.size() > slots.size()) {
			return null;
		}
		while (target.size() < slots.size()) {
			target.add(null);
		}
		return target;
	}

	/** True when every slot already equals the target. */
	public static boolean matches(List<Stack> slots, List<Stack> target) {
		for (int i = 0; i < slots.size(); i++) {
			if (!Stack.same(slots.get(i), target.get(i))) {
				return false;
			}
		}
		return true;
	}

	/** Number of non-empty stacks. */
	public static int countStacks(List<Stack> slots) {
		int n = 0;
		for (Stack s : slots) {
			if (s != null) {
				n++;
			}
		}
		return n;
	}

	/** Decides the next left-click from the live state. See the class docs. */
	public static Step nextStep(List<Stack> slots, Stack cursor, List<Stack> target) {
		int n = slots.size();
		int p = -1;
		for (int i = 0; i < n; i++) {
			if (!Stack.same(slots.get(i), target.get(i))) {
				p = i;
				break;
			}
		}
		if (p < 0) {
			return cursor == null ? Step.DONE : Step.STALE;
		}

		Stack t = target.get(p);
		Stack s = slots.get(p);
		if (t == null) {
			// Occupied slot in the empty tail: more items than the target accounts for.
			return Step.STALE;
		}
		boolean foreignHere = s != null && !s.sameType(t);
		int haveHere = (s != null && s.sameType(t)) ? s.count() : 0;
		if (haveHere > t.count()) {
			return Step.STALE;
		}

		if (cursor != null) {
			if (cursor.sameType(t)) {
				if (foreignHere && (fragile(cursor) || fragile(s))) {
					// A swap would trigger bundle behaviour: park the cursor first.
					int e = findEmpty(slots, p + 1);
					return e < 0 ? Step.BLOCKED : Step.click(e);
				}
				// Placing/merging/swapping onto p. Check we cannot overshoot a remainder target.
				int wouldBe = s == null || foreignHere
						? cursor.count()
						: Math.min(t.type().maxStackSize(), s.count() + cursor.count());
				if (wouldBe > t.count()) {
					return Step.STALE;
				}
				return Step.click(p);
			}
			// Cursor holds a foreign stack: swap it into a donor slot of t's type ...
			int q = findDonor(slots, p, t, haveHere);
			if (q < 0) {
				return Step.STALE;
			}
			if (fragile(cursor) || fragile(slots.get(q))) {
				// ... unless a bundle is involved; then park it in an empty slot instead.
				int e = findEmpty(slots, p + 1);
				return e < 0 ? Step.BLOCKED : Step.click(e);
			}
			return Step.click(q);
		}

		// Empty cursor: pick up a donor for t.
		int q = findDonor(slots, p, t, haveHere);
		if (q < 0) {
			return Step.STALE;
		}
		if (foreignHere && (fragile(s) || fragile(slots.get(q)))) {
			// The later swap at p would involve a bundle: move the foreign stack out first.
			return Step.click(p);
		}
		return Step.click(q);
	}

	private static boolean fragile(Stack s) {
		return s != null && s.type().fragile();
	}

	/** True if any slot holds a {@linkplain StackType#fragile() fragile} stack (bundle). */
	public static boolean hasFragile(List<Stack> slots) {
		for (Stack s : slots) {
			if (fragile(s)) {
				return true;
			}
		}
		return false;
	}

	/** True if the chest has no empty slot. */
	public static boolean isFull(List<Stack> slots) {
		return findEmpty(slots, 0) < 0;
	}

	/**
	 * Pre-pass for a completely full chest that contains fragile stacks: fragile stacks can
	 * only be moved via an empty slot, so first consolidate partial stacks of some ordinary
	 * type until a slot becomes free. With an empty cursor, picks up the smaller of two partial
	 * stacks of the same type; with a loaded cursor, merges it into the fullest partial stack
	 * of its type, or places it into an empty slot.
	 *
	 * @return slot to left-click, or -1 if no consolidation is possible
	 */
	public static int freeSlotStep(List<Stack> slots, Stack cursor) {
		if (cursor != null) {
			if (!cursor.type().fragile()) {
				int best = -1;
				int bestCount = -1;
				for (int i = 0; i < slots.size(); i++) {
					Stack s = slots.get(i);
					if (s != null && s.sameType(cursor) && !s.isFull() && s.count() > bestCount) {
						best = i;
						bestCount = s.count();
					}
				}
				if (best >= 0) {
					return best;
				}
			}
			return findEmpty(slots, 0);
		}
		Map<StackType, Integer> firstPartial = new LinkedHashMap<>();
		for (int i = 0; i < slots.size(); i++) {
			Stack s = slots.get(i);
			if (s == null || s.isFull() || s.type().fragile()) {
				continue;
			}
			Integer first = firstPartial.get(s.type());
			if (first == null) {
				firstPartial.put(s.type(), i);
				continue;
			}
			return slots.get(first).count() <= s.count() ? first : i;
		}
		return -1;
	}

	private static int findEmpty(List<Stack> slots, int from) {
		for (int i = from; i < slots.size(); i++) {
			if (slots.get(i) == null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Finds a slot after {@code p} holding {@code t}'s type. Prefers a donor whose count is
	 * exactly what position {@code p} still needs (one click finishes it), otherwise the
	 * largest donor.
	 */
	private static int findDonor(List<Stack> slots, int p, Stack t, int haveHere) {
		int need = t.count() - haveHere;
		int best = -1;
		int bestCount = -1;
		for (int q = p + 1; q < slots.size(); q++) {
			Stack c = slots.get(q);
			if (c == null || !c.sameType(t)) {
				continue;
			}
			if (c.count() == need) {
				return q;
			}
			if (c.count() > bestCount) {
				best = q;
				bestCount = c.count();
			}
		}
		return best;
	}

	/**
	 * When an operation is being cancelled with a stack still on the cursor, choose a chest
	 * slot to drop it into: a partial stack of the same type first, then an empty slot.
	 * Fragile stacks only go into empty slots.
	 *
	 * @return slot index, or -1 if the chest cannot take it
	 */
	public static int parkCursorSlot(List<Stack> slots, Stack cursor) {
		if (cursor == null) {
			return -1;
		}
		if (!cursor.type().fragile()) {
			for (int i = 0; i < slots.size(); i++) {
				Stack s = slots.get(i);
				if (s != null && s.sameType(cursor) && s.count() < s.type().maxStackSize()) {
					return i;
				}
			}
		}
		return findEmpty(slots, 0);
	}
}
