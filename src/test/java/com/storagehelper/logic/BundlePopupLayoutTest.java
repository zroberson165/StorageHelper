package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundlePopupLayoutTest {
    @Test void twoEntriesDoNotAllocateThreeEmptyRows() {
        var layout = BundlePopupLayout.place(427, 240, 150, 180, 2, 36);
        assertEquals(2, layout.columns()); assertEquals(1, layout.rows());
        assertEquals(40, layout.width()); assertEquals(34, layout.height());
        assertEquals(1, layout.entryAt(layout.x() + 2 + 18 + 8, layout.y() + 12 + 8, 0));
    }
    @Test void boundedAtScreenEdgesAcrossGuiSizes() {
        for (int width : new int[]{320, 427, 640, 854}) for (int count : new int[]{0, 1, 2, 6, 7, 64}) {
            for (int sx : new int[]{1, width - 18}) {
                var l = BundlePopupLayout.place(width, 240, sx, 220, count, 36);
                assertTrue(l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= width && l.y() + l.height() <= 240);
                assertTrue(l.rows() <= 3);
            }
        }
    }
    @Test void onlySmallCorridorCountsAsTransition() {
        var l = BundlePopupLayout.place(427, 240, 100, 100, 2, 36);
        assertTrue(l.transitionContains(117.5, 105));
        assertFalse(l.transitionContains(117.5, 125));
        assertFalse(l.transitionContains(115, 90));
    }
}
