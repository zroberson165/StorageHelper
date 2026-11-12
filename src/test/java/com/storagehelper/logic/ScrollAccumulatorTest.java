package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollAccumulatorTest {
    @Test void smoothMotionScalesToQuantityWithoutMovingOnTinyInput() {
        var scroll = new ScrollAccumulator();
        int count = 0;
        for (int i = 0; i < 30; i++) count += scroll.add(27, .1, i * 5L, 2);
        assertEquals(6, count);
    }
    @Test void defaultSensitivityAndTinyMovements() {
        var s = new ScrollAccumulator();
        assertEquals(0, s.add(27, .01, 0, 2));
        s.reset();
        assertEquals(2, s.add(27, 1, 1, 2));
        assertEquals(8, s.add(27, 4, 2, 2));
        assertEquals(4, s.add(27, 1, 3, 4));
    }
    @Test void sourceDirectionReleaseAndTimeoutDoNotShareFractions() {
        var s = new ScrollAccumulator();
        assertEquals(0, s.add(27, .8, 0, 1));
        assertEquals(0, s.add(28, .3, 1, 1));
        assertEquals(0, s.add(28, -.8, 2, 1));
        assertEquals(0, s.add(28, -.3, 400, 1));
        s.reset();
        assertEquals(0, s.add(28, -.8, 401, 1));
    }
    @Test void spikesAndInvalidInputCannotCreateAnUnboundedBurst() {
        var s = new ScrollAccumulator();
        assertEquals(0, s.add(0, Double.NaN, 0, 1));
        assertEquals(0, s.add(0, Double.POSITIVE_INFINITY, 0, 1));
        assertEquals(ScrollAccumulator.MAX_EVENT, s.add(0, 1e10, 1, 1));
        assertEquals(0, s.add(0, .1, 2, 1));
    }
    @Test void rapidQueueRetainsExactlyAcceptedRequestsWithinBound() {
        var queue = new TransferRequests();
        int accepted = 0;
        for (int i = 0; i < 100; i++) accepted += queue.add(3);
        assertEquals(ScrollAccumulator.MAX_BACKLOG, accepted);
        assertEquals(accepted, queue.size());
        for (int i = 0; i < 7; i++) queue.take(1);
        assertEquals(7, queue.add(20));
        assertEquals(ScrollAccumulator.MAX_BACKLOG, queue.size());
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(0, queue.add(-1));
    }
}
