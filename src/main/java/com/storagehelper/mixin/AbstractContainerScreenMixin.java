package com.storagehelper.mixin;

import com.storagehelper.gui.SlotHighlighter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the search highlight / dim overlay right after each slot's item has been extracted,
 * so it sits above the item but below tooltips and the carried item. Rendering only; no
 * inventory state is touched here.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void storagehelper$afterExtractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotHighlighter.draw((AbstractContainerScreen<?>) (Object) this, graphics, slot);
	}
}
