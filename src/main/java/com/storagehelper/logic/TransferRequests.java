package com.storagehelper.logic;

/** Bounded FIFO count for identical one-item requests. Returns accepted work to callers. */
public final class TransferRequests {
    private int pending;
    public int add(int requested) {
        int accepted = Math.min(Math.max(0, requested), ScrollAccumulator.MAX_BACKLOG - pending);
        pending += accepted;
        return accepted;
    }
    public int size() { return pending; }
    public void completed() { if (pending > 0) pending--; }
    public void clear() { pending = 0; }
}
