package com.storagehelper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the container screen's GUI rectangle, used to place the side panel. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int storagehelper$getLeftPos();

	@Accessor("topPos")
	int storagehelper$getTopPos();

	@Accessor("imageWidth")
	int storagehelper$getImageWidth();

	@Accessor("imageHeight")
	int storagehelper$getImageHeight();
}
