package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlLayoutTest {
    @Test void controlsStayOutsideTitlesAndSlotsAcrossContainerSizesAndGuiScales() {
        for (int width : new int[]{320, 427, 640, 854}) for (int height : new int[]{240, 320, 480}) {
            for (int containerHeight : new int[]{166, 222}) {
                int left = (width - 176) / 2, top = (height - containerHeight) / 2;
                var l = ControlLayout.place(width, height, left, top, 176);
                assertTrue(l.searchX() >= 0 && l.searchX() + l.searchWidth() <= width);
                assertTrue(l.searchY() >= 0 && l.searchY() + 20 <= height);
                assertTrue(l.toolsX() >= 0 && l.toolsX() + l.toolsWidth() <= width);
                assertTrue(l.toolsY() + 78 <= height);
                assertTrue(l.toolsX() + l.toolsWidth() <= left || l.toolsX() >= left + 176);
                assertTrue(l.above() ? l.searchY() + 20 <= top - 4 : l.searchX() >= left + 176 || l.searchX() + l.searchWidth() <= left);
            }
        }
    }
    @Test void toolbarUsesLeftSideWhenRightSpaceIsInsufficient() {
        var l = ControlLayout.place(400, 300, 210, 40, 176);
        assertTrue(l.toolsX() + l.toolsWidth() < 210);
        assertTrue(l.toolsY() >= 40);
    }
    @Test void doubleChestAtLargeGuiScaleUsesSideFallback() {
        var l = ControlLayout.place(320,240,72,9,176);
        assertFalse(l.above()); assertEquals(64,l.searchWidth());
        assertTrue(l.toolsY() >= l.searchY() + 22);
    }
}
