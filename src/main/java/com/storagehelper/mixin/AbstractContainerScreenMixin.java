package com.storagehelper.mixin;

import com.storagehelper.gui.SlotHighlighter;
import com.storagehelper.gui.HoverContents;
import com.storagehelper.chest.ChestSessions;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
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
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void storagehelper$contents(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        if (HoverContents.render((AbstractContainerScreen<?>) (Object) this, graphics, x, y)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void storagehelper$popupClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (HoverContents.click((AbstractContainerScreen<?>) (Object) this, event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void storagehelper$popupRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (HoverContents.release((AbstractContainerScreen<?>) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void storagehelper$scroll(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        if (HoverContents.scroll(screen, x, y, vertical)) { cir.setReturnValue(true); return; }
        var session = ChestSessions.forScreen(screen);
        if (session != null && session.onScroll(x, y, vertical)) cir.setReturnValue(true);
    }
}
