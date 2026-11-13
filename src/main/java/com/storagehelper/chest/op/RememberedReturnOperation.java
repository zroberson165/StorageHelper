package com.storagehelper.chest.op;

import com.storagehelper.chest.TransferHistory;
import com.storagehelper.logic.ScrollAccumulator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Reuses paced batch transfers while resolving each donor from the current layout. */
public final class RememberedReturnOperation extends ChestOperation {
    private final TransferHistory history;
    private int pending, accounted;
    private long lastInput;
    private ScrollTransferOperation delegate;
    public RememberedReturnOperation(Minecraft mc, AbstractContainerMenu menu, TransferHistory history, int count, int pacing) {
        super(mc, menu, pacing); this.history = history; enqueue(count);
    }
    public void enqueue(int count) {
        if (!cancelRequested) { pending = Math.min(ScrollAccumulator.MAX_BACKLOG, pending + Math.max(0,count)); lastInput = System.currentTimeMillis(); }
    }
    public boolean cursorInPlayer() { return !history.deposit(); }
    @Override public Component runningLabel() { return t("scroll.running"); }
    @Override public void start() { if (!cursorEmpty()) finish(t("status.cursor_not_empty")); }
    @Override public void requestCancel() { super.requestCancel(); pending = 0; if (delegate != null) delegate.requestCancel(); }
    @Override public void tick() {
        if (mc.player == null || mc.player.containerMenu != menu) { finish(t("status.closed")); return; }
        if (delegate == null) {
            if (cancelRequested || pending == 0 || history.quantity() == 0
                    || System.currentTimeMillis() - lastInput > ScrollAccumulator.GESTURE_MS || !history.validate(false)) {
                finish(t("status.stopped")); return;
            }
            int source = history.returnSource();
            if (source < 0) { finish(t("status.stopped")); return; }
            int quantity = Math.min(pending, Math.min(history.quantity(), menu.getSlot(source).getItem().getCount()));
            delegate = new ScrollTransferOperation(mc, menu, source, !history.deposit(), quantity, clicksPerTick,
                    history, true, history.preferredSource());
            accounted = 0;
            delegate.start();
        }
        if (!delegate.isFinished()) delegate.tick();
        int moved = delegate.completed() - accounted;
        pending = Math.max(0, pending - moved); accounted += moved;
        if (delegate.isFinished()) {
            if (cancelRequested || accounted == 0 || history.quantity() == 0) { finish(delegate.result()); return; }
            delegate = null;
        }
    }
}
