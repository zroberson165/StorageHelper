package com.storagehelper.gui;

import com.storagehelper.chest.TransferHistory;
import com.storagehelper.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Includes empty slots and the small row gaps, without treating equipment as storage. */
public final class InventoryAreas {
    private InventoryAreas() {}
    public static boolean contains(AbstractContainerScreen<?> screen, TransferHistory history, boolean player, double x, double y) {
        var a = (AbstractContainerScreenAccessor)screen;
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0;
        for (int index : history.slots(player)) {
            var slot = screen.getMenu().getSlot(index);
            left = Math.min(left, slot.x - 1); right = Math.max(right, slot.x + 17);
            top = Math.min(top, slot.y - 1); bottom = Math.max(bottom, slot.y + 17);
        }
        x -= a.storagehelper$getLeftPos(); y -= a.storagehelper$getTopPos();
        return x >= left && x < right && y >= top && y < bottom;
    }
}
