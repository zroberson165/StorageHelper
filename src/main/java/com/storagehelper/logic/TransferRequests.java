package com.storagehelper.logic;

/** Bounded quantity accumulator for one source and direction. Returns accepted work to callers. */
public final class TransferRequests {
    private int pending;
    public int add(int requested) {
        int accepted = Math.min(Math.max(0, requested), ScrollAccumulator.MAX_BACKLOG - pending);
        pending += accepted;
        return accepted;
    }
    public int size() { return pending; }
    public int take(int maximum) {
        int count = Math.min(pending, Math.max(0, maximum));
        pending -= count;
        return count;
    }
    public void clear() { pending = 0; }
}
