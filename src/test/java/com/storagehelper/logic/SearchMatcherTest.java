package com.storagehelper.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMatcherTest {
	@Test
	void normalizeTrimsAndLowercases() {
		assertEquals("cobble", SearchMatcher.normalize("  CoBBle "));
		assertEquals("", SearchMatcher.normalize(null));
		assertEquals("", SearchMatcher.normalize("   "));
	}

	@Test
	void matchesNameOrRegistryIdCaseInsensitively() {
		assertTrue(SearchMatcher.matches("cobble", "Cobblestone", "minecraft:cobblestone"));
		assertTrue(SearchMatcher.matches("STONE", "Cobblestone", "minecraft:cobblestone"));
		assertTrue(SearchMatcher.matches("minecraft:cob", "Cobblestone", "minecraft:cobblestone"));
		assertTrue(SearchMatcher.matches("ender_pearl", "Ender Pearl", "minecraft:ender_pearl"), "registry id with underscore");
		assertTrue(SearchMatcher.matches("bob", "Bob's Rock", "minecraft:cobblestone"), "custom names count");
	}

	@Test
	void emptyQueryAndMismatchesReturnFalse() {
		assertFalse(SearchMatcher.matches("", "Cobblestone", "minecraft:cobblestone"));
		assertFalse(SearchMatcher.matches("diamond", "Cobblestone", "minecraft:cobblestone"));
		assertFalse(SearchMatcher.matches("x", null, null));
	}
}
