package com.storagehelper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.inventory.Slot;

/** Read access to the container screen's GUI rectangle, used to place the side panel. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Invoker("getHoveredSlot")
    Slot storagehelper$getHoveredSlot(double x, double y);

	@Accessor("leftPos")
	int storagehelper$getLeftPos();

	@Accessor("topPos")
	int storagehelper$getTopPos();

	@Accessor("imageWidth")
	int storagehelper$getImageWidth();

	@Accessor("imageHeight")
	int storagehelper$getImageHeight();
}
