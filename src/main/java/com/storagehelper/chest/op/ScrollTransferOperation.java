package com.storagehelper.chest.op;

import com.storagehelper.chest.ChestSnapshots;
import com.storagehelper.logic.TransferRequests;
import com.storagehelper.logic.SingleItemTransferPlanner;
import com.storagehelper.logic.Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** A bounded queue of one-item transactions: pick up, place one, return remainder.
 * Every intermediate cursor and slot state must equal the last predicted result. */
public final class ScrollTransferOperation extends ChestOperation {
    private final int source;
    private final boolean intoContainer;
    private final ItemStack original;
    private final List<Integer> destinations = new ArrayList<>();
    private final TransferRequests requests = new TransferRequests();
    private int phase;
    private int destination;
    private ItemStack expectedSource = ItemStack.EMPTY;
    private ItemStack expectedCursor = ItemStack.EMPTY;
    private ItemStack expectedDestination = ItemStack.EMPTY;
    private int recoveryTicks;

    public ScrollTransferOperation(Minecraft mc, AbstractContainerMenu menu, int source, boolean intoContainer, int count, int pacing) {
        super(mc, menu, pacing);
        this.source = source;
        this.intoContainer = intoContainer;
        original = menu.getSlot(source).getItem().copy();
        enqueue(count);
        int boundary = ChestSnapshots.chestSlotCount(menu);
        for (int i = intoContainer ? 0 : boundary; i < (intoContainer ? boundary : menu.slots.size()); i++) destinations.add(i);
    }
    public int source() { return source; }
    public void enqueue(int count) { requests.add(count); }
    @Override public Component runningLabel() { return t("scroll.running"); }
    @Override public void start() {
        if (!cursorEmpty()) finish(t("status.cursor_not_empty"));
        if (original.isEmpty()) finish(t("status.stopped"));
    }
    @Override public void tick() {
        if (mc.player == null || mc.player.containerMenu != menu) { finish(t("status.closed")); return; }
        if (cancelRequested) { recover(); return; }
        for (int clicks = 0; clicks < clicksPerTick; clicks++) {
            Slot from = menu.getSlot(source);
            if (phase == 0) {
                if (requests.size() == 0) { finish(t("scroll.done")); return; }
                if (!cursorEmpty()) { finish(t("status.cursor_not_empty")); return; }
                ItemStack live = from.getItem();
                if (live.isEmpty() || !ItemStack.isSameItemSameComponents(original, live) || !from.mayPickup(mc.player)) {
                    finish(t("status.stopped")); return;
                }
                List<Stack> targets = destinations.stream().map(i -> ChestSnapshots.toStack(menu.getSlot(i).getItem())).toList();
                int next = SingleItemTransferPlanner.destination(targets, ChestSnapshots.toStack(live),
                        i -> menu.getSlot(destinations.get(i)).mayPlace(live),
                        i -> menu.getSlot(destinations.get(i)).getMaxStackSize(live));
                if (next < 0) { finish(t("scroll.full")); return; }
                destination = destinations.get(next);
                expectedDestination = menu.getSlot(destination).getItem().copy();
                ItemStack pickedUp = live.copy();
                if (com.storagehelper.chest.McStackType.of(live).fragile() && live.getCount() != 1) {
                    finish(t("status.stopped")); return;
                }
                if (!click(source, 0, ContainerInput.PICKUP)) return;
                phase = 1;
                if (!from.getItem().isEmpty() || !same(menu.getCarried(), pickedUp)) {
                    cancelRequested = true; return;
                }
            } else if (phase == 1) {
                if (!same(from.getItem(), expectedSource) || !same(menu.getCarried(), expectedCursor)
                        || !same(menu.getSlot(destination).getItem(), expectedDestination)
                        || !menu.getSlot(destination).mayPlace(menu.getCarried())) {
                    cancelRequested = true; recover(); return;
                }
                int before = menu.getCarried().getCount();
                // A bundle on the cursor would unload its contents on a secondary click.
                // Unstackable sources instead use a primary click into an empty slot.
                if (!click(destination, before == 1 ? 0 : 1, ContainerInput.PICKUP)) return;
                if (menu.getCarried().getCount() != before - 1) { cancelRequested = true; return; }
                requests.completed();
                phase = menu.getCarried().isEmpty() ? 0 : 2;
            } else {
                if (!same(from.getItem(), expectedSource) || !same(menu.getCarried(), expectedCursor)) {
                    cancelRequested = true; recover(); return;
                }
                if (!from.mayPlace(menu.getCarried()) || from.getMaxStackSize(menu.getCarried()) < menu.getCarried().getCount()) {
                    cancelRequested = true; recover(); return;
                }
                if (!click(source, 0, ContainerInput.PICKUP)) return;
                phase = 0;
            }
            expectedSource = from.getItem().copy();
            expectedCursor = menu.getCarried().copy();
        }
    }
    private static boolean same(ItemStack a, ItemStack b) { return ItemStack.matches(a, b); }
    private void recover() {
        requests.clear();
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
        click(home.get(index), 0, ContainerInput.PICKUP);
    }
}
