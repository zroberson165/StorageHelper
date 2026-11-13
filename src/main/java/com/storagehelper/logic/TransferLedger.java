package com.storagehelper.logic;

import java.util.Objects;

/** Completed, component-exact net movement only; requested work is never credited. */
public final class TransferLedger<T> {
    private T identity;
    private boolean deposit;
    private int preferredSource = -1, quantity, expectedPlayer, expectedContainer;
    public void begin(T item, boolean intoContainer, int source, int player, int container) {
        if (!Objects.equals(item, identity) || deposit != intoContainer || !verify(player, container)) {
            identity = item; deposit = intoContainer; preferredSource = source; quantity = 0;
            expectedPlayer = player; expectedContainer = container;
        }
    }
    public void completed(int count, boolean reversing) {
        if (identity == null || count < 0 || reversing && count > quantity) { clear(); return; }
        int signed = reversing ? -count : count;
        quantity += signed;
        expectedPlayer += deposit ? -signed : signed;
        expectedContainer += deposit ? signed : -signed;
    }
    public boolean verify(int player, int container) {
        if (identity == null) return false;
        if (player != expectedPlayer || container != expectedContainer) { clear(); return false; }
        return true;
    }
    public T identity() { return identity; }
    public boolean deposit() { return deposit; }
    public int quantity() { return quantity; }
    public int preferredSource() { return preferredSource; }
    public void clear() { identity = null; quantity = 0; preferredSource = -1; }
}
