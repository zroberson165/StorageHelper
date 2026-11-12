package com.storagehelper.logic;

/**
 * A non-empty stack: a type and a positive count. Empty slots are represented by {@code null}.
 */
public record Stack(StackType type, int count) {
	public Stack {
		if (type == null) {
			throw new IllegalArgumentException("type");
		}
		if (count <= 0) {
			throw new IllegalArgumentException("count must be positive: " + count);
		}
	}

	public boolean sameType(Stack other) {
		return other != null && type.equals(other.type);
	}

	public Stack withCount(int newCount) {
		return newCount <= 0 ? null : new Stack(type, newCount);
	}

	public boolean isFull() {
		return count >= type.maxStackSize();
	}

	/** True when both are null, or both non-null with equal type and count. */
	public static boolean same(Stack a, Stack b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.count == b.count && a.type.equals(b.type);
	}

	public static boolean isEmpty(Stack s) {
		return s == null;
	}
}
