package com.storagehelper.logic;

/** Test stand-in for an item type; {@code variant} models differing components. */
record FakeType(String registryId, String displayName, int maxStackSize, ItemCategory category,
				boolean fragile, long variant) implements StackType {

	static FakeType item(String id, int max) {
		return new FakeType("minecraft:" + id, pretty(id), max, ItemCategory.MISC, false, 0);
	}

	static FakeType item(String id, int max, ItemCategory category) {
		return new FakeType("minecraft:" + id, pretty(id), max, category, false, 0);
	}

	static FakeType named(String id, String displayName, int max, long variant) {
		return new FakeType("minecraft:" + id, displayName, max, ItemCategory.MISC, false, variant);
	}

	static FakeType bundle(long variant) {
		return new FakeType("minecraft:bundle", "Bundle", 1, ItemCategory.MISC, true, variant);
	}

	@Override
	public long tieBreaker() {
		return variant;
	}

	private static String pretty(String id) {
		StringBuilder sb = new StringBuilder();
		for (String part : id.split("_")) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}
}
