package com.storagehelper.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SortPlannerTest {
	private static final FakeType COBBLE = FakeType.item("cobblestone", 64, ItemCategory.BLOCKS);
	private static final FakeType DIRT = FakeType.item("dirt", 64, ItemCategory.BLOCKS);
	private static final FakeType PEARL = FakeType.item("ender_pearl", 16);
	private static final FakeType SWORD_A = FakeType.named("diamond_sword", "Diamond Sword", 1, 1);
	private static final FakeType SWORD_B = FakeType.named("diamond_sword", "Diamond Sword", 1, 2);
	private static final FakeType APPLE = FakeType.item("apple", 64, ItemCategory.FOOD_AND_DRINKS);

	/** Result of driving the planner against the click simulator until it reports DONE. */
	record Run(List<Stack> finalSlots, Stack finalCursor, int clicks, List<Stack> target) {
	}

	static Run run(List<Stack> start, Comparator<StackType> order) {
		ClickSimulator.State state = new ClickSimulator.State(start, null);
		List<Stack> target = SortPlanner.computeTarget(state.slots, state.cursor, order);
		assertNotNull(target);
		Map<StackType, Integer> before = totals(state.slots, state.cursor);
		int bound = start.size() * 8 + 40;
		int clicks = 0;
		boolean freeingSlot = false;
		while (true) {
			if (freeingSlot && state.cursor == null && !SortPlanner.isFull(state.slots)) {
				freeingSlot = false;
			} else if (!freeingSlot && state.cursor == null && SortPlanner.isFull(state.slots) && SortPlanner.hasFragile(state.slots)) {
				freeingSlot = true;
			}
			if (freeingSlot) {
				int slot = SortPlanner.freeSlotStep(state.slots, state.cursor);
				assertTrue(slot >= 0, "pre-pass could not free a slot although the target has one");
				assertTrue(ClickSimulator.leftClick(state, slot), "pre-pass issued a no-op click");
				clicks++;
				assertTrue(clicks <= bound, "exceeded click bound " + bound + " in pre-pass");
				continue;
			}
			SortPlanner.Step step = SortPlanner.nextStep(state.slots, state.cursor, target);
			if (step.result() == SortPlanner.Result.BLOCKED) {
				fail("planner BLOCKED after " + clicks + " clicks");
			}
			if (step.result() == SortPlanner.Result.DONE) {
				break;
			}
			if (step.result() == SortPlanner.Result.STALE) {
				fail("planner reported STALE without external changes after " + clicks + " clicks");
			}
			Stack inSlot = state.slots.get(step.slot());
			if (state.cursor != null && inSlot != null && (state.cursor.type().fragile() || inSlot.type().fragile())) {
				fail("fragile stack clicked against an occupied slot at click " + clicks);
			}
			if (state.cursor != null) {
				// Invariant: whenever something is on the cursor, an empty slot exists.
				assertTrue(state.slots.contains(null), "no empty slot while cursor is held");
			}
			assertTrue(ClickSimulator.leftClick(state, step.slot()), "planner issued a no-op click");
			clicks++;
			assertTrue(clicks <= bound, "exceeded click bound " + bound);
		}
		assertNull(state.cursor, "cursor must be empty when done");
		assertEquals(before, totals(state.slots, null), "item totals changed");
		assertTrue(SortPlanner.matches(state.slots, target), "final layout differs from target");
		return new Run(state.slots, state.cursor, clicks, target);
	}

	static Map<StackType, Integer> totals(List<Stack> slots, Stack cursor) {
		Map<StackType, Integer> m = new HashMap<>();
		for (Stack s : slots) {
			if (s != null) {
				m.merge(s.type(), s.count(), Integer::sum);
			}
		}
		if (cursor != null) {
			m.merge(cursor.type(), cursor.count(), Integer::sum);
		}
		return m;
	}

	static List<Stack> chest(int size, Stack... stacks) {
		List<Stack> slots = new ArrayList<>(Arrays.asList(stacks));
		while (slots.size() < size) {
			slots.add(null);
		}
		return slots;
	}

	static Stack s(StackType t, int n) {
		return new Stack(t, n);
	}

    @Test void doesNotDismantleAnAlreadyCorrectFullStackToRebuildItLater() {
        var type = FakeType.item("apple",64);
        var slots = java.util.Arrays.asList((Stack)null, new Stack(type,64),new Stack(type,32),new Stack(type,32));
        var target = SortPlanner.computeTarget(slots,null,SortMode.ALPHABETICAL.comparator());
        var state = new ClickSimulator.State(slots,null);
        int clicks = 0;
        while (true) {
            var step = SortPlanner.nextStep(state.slots,state.cursor,target);
            if (step.result() == SortPlanner.Result.DONE) break;
            org.junit.jupiter.api.Assertions.assertEquals(SortPlanner.Result.CLICK,step.result());
            org.junit.jupiter.api.Assertions.assertTrue(ClickSimulator.leftClick(state,step.slot()));
            org.junit.jupiter.api.Assertions.assertTrue(++clicks < 20);
        }
        org.junit.jupiter.api.Assertions.assertEquals(4,clicks); // Previous donor choice took six.
        org.junit.jupiter.api.Assertions.assertNull(state.cursor);
    }

	@Test
	void consolidatesPartialStacksAndPacksLeft() {
		List<Stack> start = chest(27, null, s(COBBLE, 40), null, s(DIRT, 10), s(COBBLE, 40), null, s(DIRT, 5));
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		assertEquals(s(COBBLE, 64), r.finalSlots().get(0));
		assertEquals(s(COBBLE, 16), r.finalSlots().get(1));
		assertEquals(s(DIRT, 15), r.finalSlots().get(2));
		assertNull(r.finalSlots().get(3));
	}

	@Test
	void fullChestSortsWithoutAnyEmptySlot() {
		List<Stack> start = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			StackType t = switch (i % 3) {
				case 0 -> DIRT;
				case 1 -> COBBLE;
				default -> PEARL;
			};
			start.add(s(t, t.maxStackSize()));
		}
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		for (int i = 0; i < 9; i++) {
			assertEquals(COBBLE, r.finalSlots().get(i).type());
			assertEquals(DIRT, r.finalSlots().get(9 + i).type());
			assertEquals(PEARL, r.finalSlots().get(18 + i).type());
		}
	}

	@Test
	void fullChestWithPartialsConsolidatesUsingOnlyTheCursor() {
		List<Stack> start = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			start.add(s(i % 2 == 0 ? COBBLE : DIRT, 20 + i));
		}
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		assertTrue(r.finalSlots().contains(null), "consolidation should free slots");
	}

	@Test
	void unstackableVariantsStayDistinctAndOrderIsDeterministic() {
		List<Stack> start = chest(27, s(SWORD_B, 1), s(APPLE, 3), s(SWORD_A, 1), s(SWORD_B, 1));
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		assertEquals(APPLE, r.finalSlots().get(0).type());
		assertEquals(SWORD_A, r.finalSlots().get(1).type());
		assertEquals(SWORD_B, r.finalSlots().get(2).type());
		assertEquals(SWORD_B, r.finalSlots().get(3).type());
		// Running again on the result changes nothing.
		Run again = run(r.finalSlots(), SortMode.ALPHABETICAL.comparator());
		assertEquals(0, again.clicks());
	}

	@Test
	void byTypePutsCategoriesInOrderThenAlphabetical() {
		FakeType pick = FakeType.item("iron_pickaxe", 1, ItemCategory.TOOLS_AND_WEAPONS);
		FakeType boots = FakeType.item("iron_boots", 1, ItemCategory.ARMOR);
		FakeType ingot = FakeType.item("iron_ingot", 64, ItemCategory.ORES_AND_MINERALS);
		FakeType lever = FakeType.item("lever", 64, ItemCategory.REDSTONE);
		FakeType stick = FakeType.item("stick", 64, ItemCategory.MISC);
		List<Stack> start = chest(27, s(stick, 3), s(lever, 2), s(COBBLE, 9), s(APPLE, 1), s(ingot, 5), s(boots, 1), s(pick, 1), s(DIRT, 1));
		Run r = run(start, SortMode.BY_TYPE.comparator());
		List<StackType> order = r.finalSlots().stream().filter(x -> x != null).map(Stack::type).toList();
		assertEquals(List.of(pick, boots, ingot, APPLE, COBBLE, DIRT, lever, stick), order);
	}

	@Test
	void remainderStackComesAfterFullStacks() {
		List<Stack> start = chest(27, s(COBBLE, 30), s(DIRT, 1), s(COBBLE, 64), s(COBBLE, 50));
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		assertEquals(s(COBBLE, 64), r.finalSlots().get(0));
		assertEquals(s(COBBLE, 64), r.finalSlots().get(1));
		assertEquals(s(COBBLE, 16), r.finalSlots().get(2));
		assertEquals(s(DIRT, 1), r.finalSlots().get(3));
	}

	@Test
	void bundlesAreNeverClickedAgainstOccupiedSlots() {
		FakeType b1 = FakeType.bundle(1);
		FakeType b2 = FakeType.bundle(2);
		// Full chest with bundles scattered: swaps would normally be required.
		List<Stack> start = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			start.add(switch (i % 4) {
				case 0 -> s(DIRT, 64);
				case 1 -> s(b1, 1);
				case 2 -> s(COBBLE, 64);
				default -> s(b2, 1);
			});
		}
		start.set(26, null); // one free slot is enough
		run(start, SortMode.ALPHABETICAL.comparator()); // run() fails on any fragile violation
	}

	@Test
	void fullChestWithBundlesButPartialStacksFreesASlotFirst() {
		FakeType b1 = FakeType.bundle(1);
		List<Stack> start = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			start.add(i % 3 == 0 ? s(b1, 1) : s(i % 3 == 1 ? DIRT : COBBLE, 20));
		}
		Run r = run(start, SortMode.ALPHABETICAL.comparator());
		assertTrue(r.finalSlots().contains(null));
	}

	@Test
	void fullChestWithBundlesAndNothingToConsolidateIsRefusedUpFront() {
		FakeType b1 = FakeType.bundle(1);
		List<Stack> start = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			start.add(i % 2 == 0 ? s(b1, 1) : s(DIRT, 64));
		}
		List<Stack> target = SortPlanner.computeTarget(start, null, SortMode.ALPHABETICAL.comparator());
		assertTrue(SortPlanner.hasFragile(start));
		assertTrue(SortPlanner.isFull(target), "consolidation cannot free a slot here");
		assertEquals(-1, SortPlanner.freeSlotStep(start, null));
	}

	@Test
	void externalChangeIsDetectedAsStaleAndRecoversAfterRecompute() {
		List<Stack> start = chest(27, s(DIRT, 10), s(COBBLE, 64), s(DIRT, 10));
		ClickSimulator.State state = new ClickSimulator.State(start, null);
		Comparator<StackType> order = SortMode.ALPHABETICAL.comparator();
		List<Stack> target = SortPlanner.computeTarget(state.slots, null, order);
		// First click: pick up the cobblestone donor for position 0.
		SortPlanner.Step step = SortPlanner.nextStep(state.slots, state.cursor, target);
		assertEquals(SortPlanner.Result.CLICK, step.result());
		ClickSimulator.leftClick(state, step.slot());
		// Another player drops 5 apples into the chest.
		state.slots.set(20, s(APPLE, 5));
		boolean sawStale = false;
		for (int i = 0; i < 200; i++) {
			step = SortPlanner.nextStep(state.slots, state.cursor, target);
			if (step.result() == SortPlanner.Result.DONE) {
				break;
			}
			if (step.result() == SortPlanner.Result.STALE) {
				sawStale = true;
				target = SortPlanner.computeTarget(state.slots, state.cursor, order);
				continue;
			}
			ClickSimulator.leftClick(state, step.slot());
		}
		assertTrue(sawStale, "the added stack should have invalidated the target");
		assertNull(state.cursor);
		assertEquals(APPLE, state.slots.get(0).type());
	}

	@Test
	void parkCursorPrefersSameTypePartialThenEmpty() {
		List<Stack> slots = chest(9, s(DIRT, 60), null, s(COBBLE, 64));
		assertEquals(0, SortPlanner.parkCursorSlot(slots, s(DIRT, 10)));
		assertEquals(1, SortPlanner.parkCursorSlot(slots, s(COBBLE, 10)));
		assertEquals(1, SortPlanner.parkCursorSlot(slots, s(FakeType.bundle(1), 1)));
		assertEquals(-1, SortPlanner.parkCursorSlot(chest(1, s(COBBLE, 64)), s(DIRT, 1)));
	}

	@Test
	void computeTargetReturnsNullWhenItemsNoLongerFit() {
		List<Stack> slots = chest(2, s(COBBLE, 64), s(DIRT, 64));
		assertNull(SortPlanner.computeTarget(slots, s(APPLE, 1), SortMode.ALPHABETICAL.comparator()));
	}

	@Test
	void randomChestsAlwaysSortAndConserveItems() {
		Random rnd = new Random(20260311);
		FakeType[] pool = {
				COBBLE, DIRT, PEARL, APPLE, SWORD_A, SWORD_B, FakeType.bundle(7), FakeType.bundle(8),
				FakeType.item("stone", 64, ItemCategory.BLOCKS), FakeType.item("bucket", 16),
				FakeType.item("iron_ore", 64, ItemCategory.ORES_AND_MINERALS), FakeType.item("lever", 64, ItemCategory.REDSTONE)
		};
		for (int iter = 0; iter < 600; iter++) {
			int size = rnd.nextBoolean() ? 27 : 54;
			double fill = switch (iter % 4) {
				case 0 -> 1.0;   // completely full
				case 1 -> 0.9;
				case 2 -> 0.5;
				default -> 0.2;
			};
			List<Stack> start = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				if (rnd.nextDouble() < fill) {
					FakeType t = pool[rnd.nextInt(pool.length)];
					int n = t.maxStackSize() == 1 ? 1 : 1 + rnd.nextInt(t.maxStackSize());
					start.add(s(t, n));
				} else {
					start.add(null);
				}
			}
			Comparator<StackType> order = (iter % 2 == 0 ? SortMode.ALPHABETICAL : SortMode.BY_TYPE).comparator();
			try {
				Run r = run(start, order);
				assertNotEquals(-1, r.clicks());
			} catch (AssertionError e) {
				throw new AssertionError("iteration " + iter + ": " + e.getMessage(), e);
			}
		}
	}
}
