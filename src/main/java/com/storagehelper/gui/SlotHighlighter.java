package com.storagehelper.gui;

import com.storagehelper.chest.ChestSession;
import com.storagehelper.chest.ChestSessions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Search overlay for chest slots: bright outline + light tint for matches, slight dim otherwise. */
public final class SlotHighlighter {
	private static final int OUTLINE = 0xFFFFD54A;
	private static final int TINT = 0x38FFD54A;
	private static final int DIM = 0x50000000;

	private SlotHighlighter() {
	}

	public static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
		ChestSession session = ChestSessions.forScreen(screen);
		if (session == null || !session.isSearchActive()) {
			return;
		}
		if (session.menu().slots.isEmpty() || slot.container != session.menu().getSlot(0).container) {
			return; // player inventory slots are never highlighted or dimmed
		}
		int x = slot.x;
		int y = slot.y;
		if (session.isMatch(slot.index)) {
			graphics.fill(x - 1, y - 1, x + 17, y, OUTLINE);
			graphics.fill(x - 1, y + 16, x + 17, y + 17, OUTLINE);
			graphics.fill(x - 1, y, x, y + 16, OUTLINE);
			graphics.fill(x + 16, y, x + 17, y + 16, OUTLINE);
			graphics.fill(x, y, x + 16, y + 16, TINT);
		} else if (session.dimNonMatching()) {
			graphics.fill(x, y, x + 16, y + 16, DIM);
		}
	}
}
