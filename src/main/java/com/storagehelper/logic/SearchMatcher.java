package com.storagehelper.logic;

import java.util.Locale;

/** Case-insensitive substring matching against display names and registry ids. */
public final class SearchMatcher {
	private SearchMatcher() {
	}

	/** Normalises a raw query: trims and lower-cases. Returns "" for blank input. */
	public static String normalize(String query) {
		return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * @param normalizedQuery output of {@link #normalize}
	 * @return true when the query is non-empty and is a substring of the display name or of the registry id
	 */
	public static boolean matches(String normalizedQuery, String displayName, String registryId) {
		if (normalizedQuery == null || normalizedQuery.isEmpty()) {
			return false;
		}
		String q = normalizedQuery.toLowerCase(Locale.ROOT);
		if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		return registryId != null && registryId.toLowerCase(Locale.ROOT).contains(q);
	}
}
