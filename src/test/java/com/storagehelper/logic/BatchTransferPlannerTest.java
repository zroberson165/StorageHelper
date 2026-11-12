package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchTransferPlannerTest {
    // A count-only model of actual vanilla primary/secondary PICKUP operations on
    // same-component stacks. Slot 0 is source; slots 1/2 have independent capacities.
    private static final class Inventory {
        int source, cursor, first, second, clicks;
        final int firstLimit, secondLimit, total;
        Inventory(int source, int first, int firstLimit, int secondLimit) {
            this.source = source; this.first = first; this.firstLimit = firstLimit; this.secondLimit = secondLimit;
            total = source + first;
        }
        void pickup(int button) {
            cursor = button == 0 ? source : (source + 1) / 2;
            source -= cursor; clicks++; check();
        }
        void trim() { source++; cursor--; clicks++; check(); }
        int place(int target, int button) {
            int count = target == 1 ? first : second, limit = target == 1 ? firstLimit : secondLimit;
            int moved = Math.min(cursor, Math.min(limit - count, button == 0 ? cursor : 1));
            if (target == 1) first += moved; else second += moved;
            cursor -= moved; clicks++; check(); return moved;
        }
        void recover() { if (cursor > 0) { source += cursor; cursor = 0; clicks++; check(); } }
        void check() {
            assertEquals(total, source + cursor + first + second);
            assertTrue(source >= 0 && cursor >= 0 && first <= firstLimit && second <= secondLimit);
        }
        int move(int requested) {
            int remaining = Math.min(requested, source);
            int before = first + second;
            while (remaining > 0) {
                int dest = first < firstLimit && first > 0 ? 1 : second > 0 && second < secondLimit ? 2
                        : first < firstLimit ? 1 : second < secondLimit ? 2 : -1;
                if (dest < 0) break;
                if (cursor == 0) pickup(BatchTransferPlanner.pickupButton(source, remaining));
                int room = dest == 1 ? firstLimit - first : secondLimit - second;
                switch (BatchTransferPlanner.placement(cursor, remaining, room, true)) {
                    case ALL -> remaining -= place(dest, 0);
                    case ONE -> remaining -= place(dest, 1);
                    case TRIM_SOURCE -> trim();
                }
                assertTrue(clicks < 200);
            }
            recover();
            return first + second - before;
        }
    }
    @Test void arbitraryQuantitiesConserveCountsWithFullPartialAndLimitedDestinations() {
        var full = new Inventory(64, 64, 64, 0);
        assertEquals(0, full.move(32));
        assertEquals(0, full.clicks, "full destinations must not pick up any source items");
        for (int count = 1; count <= 64; count++) for (int requested = 1; requested <= 80; requested++) {
            for (int capacity : new int[]{0, 1, 7, 16, 32, 64}) {
                var inv = new Inventory(count, 64 - capacity, 64, 9);
                assertEquals(Math.min(Math.min(count, requested), capacity + 9), inv.move(requested));
                assertEquals(0, inv.cursor);
            }
        }
    }
    @Test void halfStackRequiresTwoClicksRatherThanNinetySix() {
        var inv = new Inventory(64, 0, 64, 0);
        assertEquals(32, inv.move(32));
        assertEquals(2, inv.clicks);
    }
    @Test void cancellationCanReturnPickedAndPartiallyTransferredStacksWithoutLoss() {
        var inv = new Inventory(64, 55, 64, 64);
        inv.pickup(1);
        inv.place(1, 0);
        inv.recover();
        assertEquals(64, inv.first);
        assertEquals(55, inv.source);
        assertEquals(0, inv.cursor);
    }
}
