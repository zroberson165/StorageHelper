package com.storagehelper.logic;

/** Versioned default migration without changing distinguishable custom sensitivities. */
public final class ScrollSettings {
    public static final double DEFAULT_ITEMS_PER_UNIT = 2;
    private ScrollSettings() {}
    public static double resolve(double value, boolean legacy) {
        if (legacy && value == 8) value = DEFAULT_ITEMS_PER_UNIT;
        return Double.isFinite(value) ? Math.max(1, Math.min(32, value)) : DEFAULT_ITEMS_PER_UNIT;
    }
}
