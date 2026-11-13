package com.storagehelper.chest;

import com.storagehelper.logic.Stack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.List;
import java.util.stream.IntStream;

/** Explicit menu-slot mapping: player sort excludes hotbar/equipment/crafting by backing index. */
public record InventoryRegion(AbstractContainerMenu menu, List<Integer> slots, boolean playerMain) {
    public static InventoryRegion container(AbstractContainerMenu menu) {
        return new InventoryRegion(menu, IntStream.range(0, ChestSnapshots.chestSlotCount(menu)).boxed().toList(), false);
    }
    public static InventoryRegion playerMain(AbstractContainerMenu menu, Inventory inventory) {
        return new InventoryRegion(menu, menu.slots.stream().filter(s -> s.container == inventory
                && s.getContainerSlot() >= 9 && s.getContainerSlot() < 36).map(s -> s.index).toList(), true);
    }
    public List<Stack> snapshot() { return slots.stream().map(i -> ChestSnapshots.toStack(menu.getSlot(i).getItem())).toList(); }
    public int menuSlot(int logicalSlot) { return slots.get(logicalSlot); }
}
