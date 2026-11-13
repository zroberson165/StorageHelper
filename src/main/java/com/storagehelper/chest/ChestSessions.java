package com.storagehelper.chest;

import com.storagehelper.config.StorageHelperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jspecify.annotations.Nullable;

/** Tracks the single active chest session (only one container screen can be open at a time). */
public final class ChestSessions {
	private static @Nullable ChestSession current;

	private ChestSessions() {
	}

	/** Called from {@code ScreenEvents.AFTER_INIT}: fires on open and again on every resize. */
	public static void onScreenInit(Minecraft mc, Screen screen, StorageHelperConfig config) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}
		if (current != null && current.screen() == screen) {
			current.rebuildWidgets(); // resize: vanilla cleared the widgets, add ours again
			return;
		}
		if (!ChestDetector.isChestScreen(containerScreen) && !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
			return;
		}
		if (current != null) {
			current.close();
		}
		current = new ChestSession(mc, containerScreen, config);
		current.attach();
	}

	public static @Nullable ChestSession forScreen(Screen screen) {
		ChestSession s = current;
		return s != null && s.screen() == screen ? s : null;
	}

	static void clear(ChestSession session) {
		if (current == session) {
			current = null;
		}
	}
}
