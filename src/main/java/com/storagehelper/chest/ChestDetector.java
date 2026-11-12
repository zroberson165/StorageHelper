package com.storagehelper.chest;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Set;

/**
 * Decides whether a generic container screen is a real chest (single or double, including
 * trapped and copper variants) rather than a barrel, ender chest, or a server plugin menu
 * that happens to use the 9x3 / 9x6 layout.
 *
 * <p>Two independent signals are accepted:
 * <ul>
 *   <li>The screen title is vanilla's untranslated chest name ({@code container.chest} or
 *       {@code container.chestDouble}) and the row count matches.</li>
 *   <li>The player right-clicked a chest block a moment ago and the opened menu has the
 *       row count that block implies (covers renamed chests).</li>
 * </ul>
 */
public final class ChestDetector {
	private static final long CLICK_WINDOW_MS = 4000;
	private static final Set<String> SINGLE_KEYS = Set.of("container.chest");
	private static final Set<String> DOUBLE_KEYS = Set.of("container.chestDouble");

	private static volatile int lastClickedRows = -1;
	private static volatile long lastClickedAtMs;

	private ChestDetector() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				BlockState state = level.getBlockState(hit.getBlockPos());
				if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
					lastClickedRows = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? 3 : 6;
					lastClickedAtMs = System.currentTimeMillis();
				}
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean isChestScreen(AbstractContainerScreen<?> screen) {
		if (screen instanceof ShulkerBoxScreen && screen.getMenu() instanceof ShulkerBoxMenu) return true;
		if (!(screen.getMenu() instanceof ChestMenu menu)) return false;
		int rows = menu.getRowCount();
		if (rows != 3 && rows != 6) {
			return false;
		}
		if (screen.getTitle().getContents() instanceof TranslatableContents tc) {
			String key = tc.getKey();
			if ((rows == 3 && SINGLE_KEYS.contains(key)) || (rows == 6 && DOUBLE_KEYS.contains(key))) {
				return true;
			}
		}
		return lastClickedRows == rows && System.currentTimeMillis() - lastClickedAtMs <= CLICK_WINDOW_MS;
	}
}
