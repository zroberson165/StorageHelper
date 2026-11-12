package com.storagehelper.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.storagehelper.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.sdl.SDLScancode;

public final class ScreenSlots {
    private ScreenSlots() {}
    public static boolean shiftDown() {
        return InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_LSHIFT)
                || InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_RSHIFT);
    }
    public static Slot at(AbstractContainerScreen<?> screen, double x, double y) {
        var a = (AbstractContainerScreenAccessor) screen;
        for (Slot slot : screen.getMenu().slots) {
            int sx = a.storagehelper$getLeftPos() + slot.x, sy = a.storagehelper$getTopPos() + slot.y;
            if (slot.isActive() && x >= sx && x < sx + 16 && y >= sy && y < sy + 16) return slot;
        }
        return null;
    }
}
