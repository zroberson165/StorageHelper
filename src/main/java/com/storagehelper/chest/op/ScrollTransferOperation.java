package com.storagehelper.chest.op;

import com.storagehelper.chest.ChestSnapshots;
import com.storagehelper.chest.McStackType;
import com.storagehelper.chest.TransferHistory;
import com.storagehelper.logic.BatchTransferPlanner;
import com.storagehelper.logic.ScrollAccumulator;
import com.storagehelper.logic.TransferRequests;
import com.storagehelper.logic.SingleItemTransferPlanner;
import com.storagehelper.logic.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Quantity batches use vanilla whole-stack moves, half-stack pickup, and capped merges.
 * Only the unfinished batch owns the cursor; queued batches expire independently. */
public final class ScrollTransferOperation extends ChestOperation {
    private final int source;
    private final boolean intoContainer;
    private final ItemStack original;
    private final List<Integer> destinations = new ArrayList<>();
    private final TransferRequests requests = new TransferRequests();
    private List<ItemStack> expectedSlots;
    private ItemStack expectedCursor = ItemStack.EMPTY;
    private int activeRemaining;
    private long lastInput;
    private int recoveryTicks;
    private final TransferHistory history;
    private final boolean reversal;
    private final int preferred;
    private int completed;

    public ScrollTransferOperation(Minecraft mc, AbstractContainerMenu menu, int source, boolean intoContainer, int count, int pacing) {
        this(mc, menu, source, intoContainer, count, pacing, null, false, -1);
    }
    public ScrollTransferOperation(Minecraft mc, AbstractContainerMenu menu, int source, boolean intoContainer, int count, int pacing,
                                   TransferHistory history, boolean reversal, int preferred) {
        super(mc, menu, pacing);
        this.history = history; this.reversal = reversal; this.preferred = preferred;
        this.source = source;
        this.intoContainer = intoContainer;
        original = menu.getSlot(source).getItem().copy();
        enqueue(count);
        int boundary = ChestSnapshots.chestSlotCount(menu);
        for (int i = intoContainer ? 0 : boundary; i < (intoContainer ? boundary : menu.slots.size()); i++) destinations.add(i);
    }
    public int source() { return source; }
    public boolean cursorInPlayer() { return intoContainer; }
    public int completed() { return completed; }
    public boolean isCancelling() { return cancelRequested; }
    /** Stop pending and unplaced work before any remembered completed items are reversed. */
    public int cancelForReverse(int requested) {
        requests.clear(); activeRemaining = 0; requestCancel();
        return requested;
    }
    private void transferred(int count) {
        completed += count;
        if (history != null) history.completed(count, reversal);
    }
    public void enqueue(int count) {
        if (!cancelRequested) { requests.add(count); lastInput = System.currentTimeMillis(); }
    }
    @Override public Component runningLabel() { return t("scroll.running"); }
    @Override public void start() {
        if (!cursorEmpty()) { finish(t("status.cursor_not_empty")); return; }
        if (original.isEmpty()) { finish(t("status.stopped")); return; }
        remember();
    }
    private void remember() {
        expectedSlots = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
        expectedCursor = menu.getCarried().copy();
        if (history != null && !history.validate(intoContainer)) cancelRequested = true;
    }
    private boolean unchanged() {
        if (!ItemStack.matches(expectedCursor, menu.getCarried())) return false;
        for (int i = 0; i < expectedSlots.size(); i++)
            if (!ItemStack.matches(expectedSlots.get(i), menu.getSlot(i).getItem())) return false;
        return true;
    }
    private int destination(ItemStack stack) {
        List<Stack> slots = destinations.stream().map(i -> ChestSnapshots.toStack(menu.getSlot(i).getItem())).toList();
        if (preferred >= 0 && destinations.contains(preferred)) {
            var slot = menu.getSlot(preferred);
            var in = slot.getItem();
            if (slot.mayPlace(stack) && (in.isEmpty() || !McStackType.of(stack).fragile() && !McStackType.of(in).fragile()
                    && ItemStack.isSameItemSameComponents(stack, in)) && in.getCount() < slot.getMaxStackSize(stack)) return preferred;
        }
        int index = SingleItemTransferPlanner.destination(slots, ChestSnapshots.toStack(stack),
                i -> menu.getSlot(destinations.get(i)).mayPlace(stack),
                i -> menu.getSlot(destinations.get(i)).getMaxStackSize(stack));
        return index < 0 ? -1 : destinations.get(index);
    }
    @Override public void tick() {
        if (mc.player == null || mc.player.containerMenu != menu) { finish(t("status.closed")); return; }
        if (!unchanged()) { if (history != null) history.clear(); cancelRequested = true; }
        if (cancelRequested) { recover(); return; }
        if (System.currentTimeMillis() - lastInput > ScrollAccumulator.GESTURE_MS) requests.clear();
        for (int clicks = 0; clicks < clicksPerTick; clicks++) {
            var from = menu.getSlot(source);
            ItemStack cursor = menu.getCarried();
            if (cursor.isEmpty()) {
                ItemStack live = from.getItem();
                if (activeRemaining == 0) {
                    if (requests.size() == 0) { finish(t("scroll.done")); return; }
                    activeRemaining = requests.take(live.getCount());
                }
                if (live.isEmpty() || !ItemStack.isSameItemSameComponents(original, live) || !from.mayPickup(mc.player)) {
                    finish(t("status.stopped")); return;
                }
                if (destination(live) < 0) { finish(t("scroll.full")); return; }
                int before = live.getCount();
                if (activeRemaining >= before && !reversal) {
                    // Vanilla QUICK_MOVE fills partial stacks before empty slots, without a cursor.
                    if (!click(source, 0, ContainerInput.QUICK_MOVE)) return;
                    int moved = before - from.getItem().getCount();
                    if (moved <= 0 || moved > activeRemaining) { cancelRequested = true; return; }
                    activeRemaining -= moved;
                    transferred(moved);
                } else {
                    if (McStackType.of(live).fragile() && live.getCount() != 1) { finish(t("status.stopped")); return; }
                    int button = BatchTransferPlanner.pickupButton(before, activeRemaining);
                    if (!click(source, button, ContainerInput.PICKUP)) return;
                    int expected = button == 1 ? (before + 1) / 2 : before;
                    if (menu.getCarried().getCount() != expected || !ItemStack.isSameItemSameComponents(original, menu.getCarried())) {
                        cancelRequested = true; return;
                    }
                }
            } else {
                // Fresh wheel input can use the already-owned cursor stack. Do not
                // return and pick it up again merely because another event arrived.
                if (cursor.getCount() > activeRemaining)
                    activeRemaining += requests.take(cursor.getCount() - activeRemaining);
                if (!ItemStack.isSameItemSameComponents(original, cursor)) { cancelRequested = true; recover(); return; }
                int target = activeRemaining == 0 ? -1 : destination(cursor);
                if (target < 0) {
                    // Batch complete or no more capacity: return the remainder in one click.
                    if (activeRemaining > 0) { activeRemaining = 0; requests.clear(); }
                    if (!from.mayPlace(cursor) || !from.getItem().isEmpty()
                            && !ItemStack.isSameItemSameComponents(from.getItem(), cursor)
                            || from.getItem().getCount() + cursor.getCount() > from.getMaxStackSize(cursor)) {
                        cancelRequested = true; recover(); return;
                    }
                    if (!click(source, 0, ContainerInput.PICKUP)) return;
                    if (!cursorEmpty()) { cancelRequested = true; return; }
                } else {
                    var to = menu.getSlot(target);
                    int room = to.getMaxStackSize(cursor) - to.getItem().getCount();
                    boolean canReturn = from.mayPlace(cursor) && (from.getItem().isEmpty()
                            || ItemStack.isSameItemSameComponents(from.getItem(), cursor))
                            && from.getItem().getCount() < from.getMaxStackSize(cursor);
                    var placement = BatchTransferPlanner.placement(cursor.getCount(), activeRemaining, room, canReturn);
                    int before = cursor.getCount();
                    int button = placement == BatchTransferPlanner.Placement.ALL ? 0 : 1;
                    int slot = placement == BatchTransferPlanner.Placement.TRIM_SOURCE ? source : target;
                    int expected = button == 0 ? Math.min(before, room) : 1;
                    if (!click(slot, button, ContainerInput.PICKUP)) return;
                    int moved = before - menu.getCarried().getCount();
                    if (moved != expected) { cancelRequested = true; return; }
                    if (slot != source) { activeRemaining -= moved; transferred(moved); }
                }
            }
            remember();
        }
    }
    private void recover() {
        requests.clear();
        activeRemaining = 0;
        ItemStack cursor = menu.getCarried();
        if (cursor.isEmpty()) { finish(t("status.stopped")); return; }
        // Never manipulate an unexpected cursor introduced by a correction or another action.
        if (!ItemStack.isSameItemSameComponents(original, cursor) || ++recoveryTicks > 40) {
            finish(t("status.stopped_holding")); return;
        }
        List<Integer> home = new ArrayList<>();
        home.add(source);
        int boundary = ChestSnapshots.chestSlotCount(menu);
        for (int i = intoContainer ? boundary : 0; i < (intoContainer ? menu.slots.size() : boundary); i++) if (i != source) home.add(i);
        List<Stack> slots = home.stream().map(i -> ChestSnapshots.toStack(menu.getSlot(i).getItem())).toList();
        int index = SingleItemTransferPlanner.destination(slots, ChestSnapshots.toStack(cursor),
                i -> menu.getSlot(home.get(i)).mayPlace(cursor), i -> menu.getSlot(home.get(i)).getMaxStackSize(cursor));
        if (index < 0) { finish(t("status.stopped_holding")); return; }
        if (click(home.get(index), 0, ContainerInput.PICKUP)) remember();
    }
}
