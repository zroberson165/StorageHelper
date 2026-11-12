package com.storagehelper.logic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositPlannerTest {
	private static final FakeType COBBLE = FakeType.item("cobblestone", 64);
	private static final FakeType DIRT = FakeType.item("dirt", 64);
	private static final FakeType NAMED_COBBLE = FakeType.named("cobblestone", "Bob's Rock", 64, 9);
	private static final FakeType SWORD = FakeType.item("diamond_sword", 1);

	private static Stack s(StackType t, int n) {
		return new Stack(t, n);
	}

	@Test
	void candidatesMatchByRegistryIdRegardlessOfComponents() {
		List<Stack> chest = SortPlannerTest.chest(27, s(COBBLE, 64), s(SWORD, 1));
		Set<String> ids = DepositPlanner.chestItemIds(chest);
		assertEquals(Set.of("minecraft:cobblestone", "minecraft:diamond_sword"), ids);

		List<Stack> player = Arrays.asList(s(DIRT, 5), s(NAMED_COBBLE, 3), null, s(COBBLE, 64), s(SWORD, 1));
		assertEquals(List.of(1, 3, 4), DepositPlanner.candidates(player, ids));
	}

	@Test
	void canAcceptNeedsPartialSameTypeOrEmptySlot() {
		assertTrue(DepositPlanner.canAccept(SortPlannerTest.chest(27, s(COBBLE, 64)), s(COBBLE, 10)), "empty slots available");
		List<Stack> full = SortPlannerTest.chest(1, s(COBBLE, 60));
		assertTrue(DepositPlanner.canAccept(full, s(COBBLE, 10)), "partial stack of same type");
		assertFalse(DepositPlanner.canAccept(full, s(NAMED_COBBLE, 10)), "different components cannot merge");
		assertFalse(DepositPlanner.canAccept(SortPlannerTest.chest(1, s(COBBLE, 64)), s(COBBLE, 1)), "full stack, no empty slot");
	}

	@Test
	void countItemsSumsOnlyMatchingIds() {
		List<Stack> player = List.of(s(DIRT, 5), s(NAMED_COBBLE, 3), s(COBBLE, 64));
		assertEquals(67, DepositPlanner.countItems(player, Set.of("minecraft:cobblestone")));
		assertEquals(0, DepositPlanner.countItems(player, Set.of()));
	}
}
