package com.storagehelper.logic;

/**
 * Identity of a kind of stack: the item plus all of its components.
 *
 * <p><b>Contract:</b> {@link Object#equals} and {@link Object#hashCode} must agree with
 * vanilla's "same item and same components" test, because the planners assume two stacks
 * of equal {@code StackType} can be merged by a left-click up to {@link #maxStackSize()}.
 */
public interface StackType {
	/** Full registry id, e.g. {@code minecraft:cobblestone}. */
	String registryId();

	/** Name as displayed to the player (custom names included). */
	String displayName();

	/** Largest count a single stack of this type can hold in a chest slot. */
	int maxStackSize();

	/** Category for "By Type" sorting. */
	ItemCategory category();

	/**
	 * Deterministic tie-breaker for stacks that share both display name and registry id
	 * but differ in components (e.g. two differently enchanted swords with the same name).
	 */
	default long tieBreaker() {
		return 0L;
	}

	/**
	 * True for items that change what a left-click does when they meet another stack
	 * (vanilla bundles absorb the clicked item instead of swapping). The sort planner
	 * never clicks such a stack onto an occupied slot, nor another stack onto it.
	 */
	default boolean fragile() {
		return false;
	}
}
