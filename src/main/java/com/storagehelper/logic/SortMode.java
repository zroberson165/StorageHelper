package com.storagehelper.logic;

import java.util.Comparator;

/** The two user-selectable orderings. */
public enum SortMode {
	ALPHABETICAL("alphabetical"),
	BY_TYPE("by_type");

	private final String id;

	SortMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static SortMode fromId(String id, SortMode fallback) {
		for (SortMode m : values()) {
			if (m.id.equalsIgnoreCase(id) || m.name().equalsIgnoreCase(id)) {
				return m;
			}
		}
		return fallback;
	}

	public SortMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/**
	 * Deterministic ordering of stack types for this mode. Ties on display name are broken by
	 * registry id, then by {@link StackType#tieBreaker()}.
	 */
	public Comparator<StackType> comparator() {
		Comparator<StackType> byName = Comparator
				.comparing((StackType t) -> t.displayName().toLowerCase(java.util.Locale.ROOT))
				.thenComparing(StackType::displayName)
				.thenComparing(StackType::registryId)
				.thenComparingLong(StackType::tieBreaker);
		return switch (this) {
			case ALPHABETICAL -> byName;
			case BY_TYPE -> Comparator.comparing(StackType::category).thenComparing(byName);
		};
	}
}
