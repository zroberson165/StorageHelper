package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingleItemTransferPlannerTest {
    private final FakeType stone = FakeType.item("stone", 64);
    @Test void fillsCompatiblePartialsBeforeEarlierEmptySlots() {
        var slots = Arrays.asList(null, new Stack(stone, 63), new Stack(stone, 64));
        assertEquals(1, SingleItemTransferPlanner.destination(slots, new Stack(stone, 8), i -> true, i -> 64));
    }
    @Test void insertionRulesAndSlotLimitsAreAuthoritative() {
        var slots = Arrays.asList(new Stack(stone, 10), null);
        assertEquals(-1, SingleItemTransferPlanner.destination(slots, new Stack(stone, 1), i -> i == 0, i -> 10));
        assertEquals(-1, SingleItemTransferPlanner.destination(Arrays.asList(null, null), new Stack(FakeType.item("shulker_box", 1), 1), i -> false, i -> 64));
    }
    @Test void namesAndComponentsNeverMergeWithDifferentVariants() {
        var named = FakeType.named("stone", "Named Stone", 64, 9);
        assertEquals(1, SingleItemTransferPlanner.destination(Arrays.asList(new Stack(named, 1), null), new Stack(stone, 2), i -> true, i -> 64));
    }
    @Test void neverInsertsIntoABundleOrClicksBundleAgainstOccupiedSlot() {
        var bundle = new Stack(FakeType.bundle(9), 1);
        assertEquals(-1, SingleItemTransferPlanner.destination(List.of(bundle), new Stack(stone, 1), i -> true, i -> 64));
        assertEquals(1, SingleItemTransferPlanner.destination(Arrays.asList(bundle, null), bundle, i -> true, i -> 1));
    }
}
