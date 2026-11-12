package com.storagehelper.logic;

/** Wheel units become quantities; input gain is independent of packet pacing. */
public final class ScrollAccumulator {
    public static final int MAX_BACKLOG = 64;
    public static final int MAX_EVENT = 64;
    public static final long GESTURE_MS = 200;
    private int source = -1;
    private int direction;
    private long lastTime;
    private double fraction;

    public int add(int slot, double delta, long now, double itemsPerUnit) {
        if (!Double.isFinite(delta) || delta == 0 || !Double.isFinite(itemsPerUnit) || itemsPerUnit <= 0) return 0;
        int sign = delta > 0 ? 1 : -1;
        if (source != slot || direction != sign || now - lastTime > GESTURE_MS) fraction = 0;
        source = slot;
        direction = sign;
        lastTime = now;
        fraction += Math.min(Math.abs(delta) * itemsPerUnit, MAX_EVENT);
        int result = Math.min((int) (fraction + 1e-9), MAX_EVENT);
        fraction -= result;
        return result;
    }
    public int source() { return source; }
    public void reset() { source = -1; direction = 0; fraction = 0; }
}
