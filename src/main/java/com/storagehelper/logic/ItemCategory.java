package com.storagehelper.logic;

/**
 * Sort categories for "By Type" mode, in display order.
 * The ordinal is the primary sort key, so the declaration order matters.
 */
public enum ItemCategory {
	TOOLS_AND_WEAPONS("tools"),
	ARMOR("armor"),
	ORES_AND_MINERALS("ores"),
	FOOD_AND_DRINKS("food"),
	BLOCKS("blocks"),
	REDSTONE("redstone"),
	MISC("misc");

	private final String key;

	ItemCategory(String key) {
		this.key = key;
	}

	/** Short key used for translation lookups ({@code storagehelper.category.<key>}). */
	public String key() {
		return key;
	}
}
