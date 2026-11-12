package com.storagehelper.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortModeTest {
	@Test
	void alphabeticalIsCaseInsensitiveWithDeterministicTieBreaks() {
		FakeType a = FakeType.named("stone", "apple", 64, 0);
		FakeType b = FakeType.named("dirt", "Apple", 64, 0);
		FakeType c = FakeType.named("cobblestone", "Apple", 64, 0);
		FakeType d = FakeType.named("cobblestone", "Apple", 64, 5);
		List<StackType> list = new ArrayList<>(List.of(a, b, c, d));
		Collections.shuffle(list, new java.util.Random(1));
		list.sort(SortMode.ALPHABETICAL.comparator());
		// "Apple" (upper) sorts before "apple" on the case-sensitive tie-break; ids then variants break the rest.
		assertEquals(List.of(c, d, b, a), list);
	}

	@Test
	void byTypeOrdersCategoriesFirst() {
		FakeType zTool = FakeType.item("zzz_axe", 1, ItemCategory.TOOLS_AND_WEAPONS);
		FakeType aBlock = FakeType.item("aaa", 64, ItemCategory.BLOCKS);
		List<StackType> list = new ArrayList<>(List.of(aBlock, zTool));
		list.sort(SortMode.BY_TYPE.comparator());
		assertEquals(List.of(zTool, aBlock), list);
	}

	@Test
	void idsRoundTripAndCycle() {
		assertEquals(SortMode.BY_TYPE, SortMode.fromId("by_type", SortMode.ALPHABETICAL));
		assertEquals(SortMode.BY_TYPE, SortMode.fromId("BY_TYPE", SortMode.ALPHABETICAL));
		assertEquals(SortMode.ALPHABETICAL, SortMode.fromId("nonsense", SortMode.ALPHABETICAL));
		assertEquals(SortMode.BY_TYPE, SortMode.ALPHABETICAL.next());
		assertEquals(SortMode.ALPHABETICAL, SortMode.BY_TYPE.next());
	}
}
