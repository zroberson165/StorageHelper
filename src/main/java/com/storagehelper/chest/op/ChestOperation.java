package com.storagehelper.chest.op;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/**
 * A multi-tick inventory operation driven by the chest screen's tick. Every click goes
 * through {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleContainerInput},
 * i.e. the same code path as a real mouse click, so the server validates everything.
 */
public abstract class ChestOperation {
	protected final Minecraft mc;
	protected final AbstractContainerMenu menu;
	protected final int clicksPerTick;
	protected boolean cancelRequested;
	private boolean finished;
	private Component result = Component.empty();

	protected ChestOperation(Minecraft mc, AbstractContainerMenu menu, int clicksPerTick) {
		this.mc = mc;
		this.menu = menu;
		this.clicksPerTick = Math.max(1, clicksPerTick);
	}

	/** Label shown while the operation runs, e.g. "Sorting…". */
	public abstract Component runningLabel();

	/** Validates preconditions and snapshots state; may finish immediately. */
	public abstract void start();

	/** Performs up to {@link #clicksPerTick} clicks against the live state. */
	public abstract void tick();

	/** Asks the operation to stop as soon as the cursor is empty again. */
	public void requestCancel() {
		cancelRequested = true;
	}

	public boolean isFinished() {
		return finished;
	}

	public Component result() {
		return result;
	}

	protected void finish(Component message) {
		if (!finished) {
			finished = true;
			result = message;
		}
	}

	protected boolean cursorEmpty() {
		return menu.getCarried().isEmpty();
	}

	/**
	 * Sends one click. Returns false (and finishes) if the container is no longer the
	 * player's open menu, so a stale plan never acts on a different container.
	 */
	protected boolean click(int slot, int button, ContainerInput input) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || player.containerMenu != menu) {
			finish(Component.translatable("storagehelper.status.closed"));
			return false;
		}
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
		return true;
	}

	protected static Component t(String key, Object... args) {
		return Component.translatable("storagehelper." + key, args);
	}
}
