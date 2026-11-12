package com.storagehelper.logic;

/** One unit per detent; bounded bursts and no fractional motion carried across gestures. */
public final class ScrollAccumulator {
    public static final int MAX_BACKLOG = 32;
    public static final int MAX_EVENT = 8;
    private int source = -1;
    private int direction;
    private long lastTime;
    private double fraction;

    public int add(int slot, double delta, long now) {
        if (!Double.isFinite(delta) || delta == 0) return 0;
        int sign = delta > 0 ? 1 : -1;
        if (source != slot || direction != sign || now - lastTime > 250) fraction = 0;
        source = slot;
        direction = sign;
        lastTime = now;
        fraction += Math.min(Math.abs(delta), MAX_EVENT);
        int result = Math.min((int) (fraction + 1e-9), MAX_EVENT);
        fraction -= result;
        return result;
    }

    public int source() { return source; }

    public void reset() { source = -1; direction = 0; fraction = 0; }
}
