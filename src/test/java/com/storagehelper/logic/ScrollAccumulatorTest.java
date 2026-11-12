package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollAccumulatorTest {
    @Test void smoothMotionProducesOneItemPerWholeUnit() {
        var scroll = new ScrollAccumulator();
        int count = 0;
        for (int i = 0; i < 30; i++) count += scroll.add(27, .1, i * 5L);
        assertEquals(3, count);
    }
    @Test void sourceDirectionReleaseAndTimeoutDoNotShareFractions() {
        var s = new ScrollAccumulator();
        assertEquals(0, s.add(27, .8, 0));
        assertEquals(0, s.add(28, .3, 1));
        assertEquals(0, s.add(28, -.8, 2));
        assertEquals(0, s.add(28, -.3, 400));
        s.reset();
        assertEquals(0, s.add(28, -.8, 401));
    }
    @Test void spikesAndInvalidInputCannotCreateAnUnboundedBurst() {
        var s = new ScrollAccumulator();
        assertEquals(0, s.add(0, Double.NaN, 0));
        assertEquals(0, s.add(0, Double.POSITIVE_INFINITY, 0));
        assertEquals(ScrollAccumulator.MAX_EVENT, s.add(0, 1e10, 1));
        assertEquals(0, s.add(0, .1, 2));
    }
    @Test void rapidQueueRetainsExactlyAcceptedRequestsWithinBound() {
        var queue = new TransferRequests();
        int accepted = 0;
        for (int i = 0; i < 100; i++) accepted += queue.add(3);
        assertEquals(ScrollAccumulator.MAX_BACKLOG, accepted);
        assertEquals(accepted, queue.size());
        for (int i = 0; i < 7; i++) queue.completed();
        assertEquals(7, queue.add(20));
        assertEquals(ScrollAccumulator.MAX_BACKLOG, queue.size());
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(0, queue.add(-1));
    }
}
