package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollSettingsTest {
    @Test void freshDefaultIsTwoAndLegacyDefaultMigrates() {
        assertEquals(2, ScrollSettings.DEFAULT_ITEMS_PER_UNIT);
        assertEquals(2, ScrollSettings.resolve(8, true));
    }
    @Test void distinguishableCustomValuesAndVersionedSettingsArePreserved() {
        for (double custom : new double[]{1, 3, 4, 12, 32}) assertEquals(custom, ScrollSettings.resolve(custom, true));
        assertEquals(8, ScrollSettings.resolve(8, false));
        assertEquals(2, ScrollSettings.resolve(Double.NaN, false));
    }
}
