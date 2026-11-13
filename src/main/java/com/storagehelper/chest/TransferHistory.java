package com.storagehelper.chest;

import com.storagehelper.logic.TransferLedger;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.stream.IntStream;

/** One menu's reversible balance; layout changes are allowed, cross-inventory corrections invalidate it. */
public final class TransferHistory {
    private final AbstractContainerMenu menu;
    private final TransferLedger<McStackType> ledger = new TransferLedger<>();
    public TransferHistory(AbstractContainerMenu menu) { this.menu = menu; }
    public List<Integer> slots(boolean player) {
        int boundary = ChestSnapshots.chestSlotCount(menu);
        return IntStream.range(player ? boundary : 0, player ? menu.slots.size() : boundary).boxed().toList();
    }
    private int count(boolean player, boolean cursorInPlayer) {
        int count = 0;
        for (int index : slots(player)) if (matches(menu.getSlot(index).getItem())) count += menu.getSlot(index).getItem().getCount();
        if (player == cursorInPlayer && matches(menu.getCarried())) count += menu.getCarried().getCount();
        return count;
    }
    public boolean matches(ItemStack stack) {
        return ledger.identity() != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(ledger.identity().prototype(), stack);
    }
    public void begin(ItemStack stack, boolean deposit, int source) {
        McStackType type = McStackType.of(stack);
        if (!type.equals(ledger.identity()) || ledger.deposit() != deposit) ledger.clear();
        // For a new identity, establish counts using that identity before recording movement.
        int player = 0, container = 0;
        for (int index : slots(true)) if (!menu.getSlot(index).getItem().isEmpty() && ItemStack.isSameItemSameComponents(stack, menu.getSlot(index).getItem())) player += menu.getSlot(index).getItem().getCount();
        for (int index : slots(false)) if (!menu.getSlot(index).getItem().isEmpty() && ItemStack.isSameItemSameComponents(stack, menu.getSlot(index).getItem())) container += menu.getSlot(index).getItem().getCount();
        ledger.begin(type, deposit, source, player, container);
    }
    public boolean validate(boolean cursorInPlayer) { return ledger.verify(count(true, cursorInPlayer), count(false, cursorInPlayer)); }
    public void completed(int count, boolean reverse) { ledger.completed(count, reverse); }
    public boolean hasIdentity() { return ledger.identity() != null; }
    public ItemStack prototype() { return ledger.identity() == null ? ItemStack.EMPTY : ledger.identity().prototype(); }
    public boolean deposit() { return ledger.deposit(); }
    public int quantity() { return ledger.quantity(); }
    public int preferredSource() { return ledger.preferredSource(); }
    public void clear() { ledger.clear(); }
    public int returnSource() {
        boolean donorPlayer = !deposit();
        for (int index : slots(donorPlayer)) if (matches(menu.getSlot(index).getItem())) return index;
        return -1;
    }
}
