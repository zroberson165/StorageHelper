package com.storagehelper.logic;

/** Button decisions for vanilla PICKUP: 0 = whole-stack, 1 = half-stack / single-item. */
public final class BatchTransferPlanner {
    private BatchTransferPlanner() {}
    public static int pickupButton(int sourceCount, int requested) {
        return sourceCount > requested ? 1 : 0;
    }
    public enum Placement { ALL, ONE, TRIM_SOURCE }
    public static Placement placement(int cursor, int requested, int room, boolean canReturnToSource) {
        if (cursor <= requested) return Placement.ALL; // Vanilla caps the merge at destination capacity.
        if (room >= requested && canReturnToSource && cursor - requested < requested) return Placement.TRIM_SOURCE;
        return Placement.ONE;
    }
}
