package com.storagehelper.chest.op;

import com.storagehelper.chest.ChestSnapshots;
import com.storagehelper.chest.InventoryRegion;
import com.storagehelper.logic.ClickSimulator;
import com.storagehelper.logic.SortPlanner;
import com.storagehelper.logic.Stack;
import com.storagehelper.logic.StackType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.Comparator;
import java.util.List;

/**
 * Physically rearranges the chest with single left-clicks chosen by {@link SortPlanner}.
 *
 * <p>Each tick the live slots and cursor are re-read and the next click is derived from them,
 * so server corrections or another player's changes are absorbed instead of continuing a
 * stale plan. When the contents stop matching the target totals, the target is recomputed
 * (a bounded number of times). After the layout matches, the operation waits a few ticks and
 * re-verifies, so late server corrections are caught and fixed.
 *
 * <p>Cancelling first returns any stack on the cursor to the chest (same-type partial stack,
 * then an empty slot) and only then stops, so the cursor is empty at the end.
 */
public final class SortOperation extends ChestOperation {
	private static final int SETTLE_TICKS = 2;
	private static final int MAX_RECOMPUTES = 8;
	private static final int MAX_VERIFY_ROUNDS = 3;

	private final Comparator<StackType> order;
    private final InventoryRegion region;
	private List<Stack> target;
	private int recomputes;
	private int verifyRounds;
	private int totalClicks;
	private int maxClicks;
	private int settleTicks = -1;
	private boolean freeingSlot;
	private Component stopMessage;

	public SortOperation(Minecraft mc, AbstractContainerMenu menu, int clicksPerTick, Comparator<StackType> order) {
        this(mc, InventoryRegion.container(menu), clicksPerTick, order);
    }
    public SortOperation(Minecraft mc, InventoryRegion region, int clicksPerTick, Comparator<StackType> order) {
        super(mc, region.menu(), clicksPerTick);
        this.region = region;
		this.order = order;
	}

    public boolean sortsPlayerInventory() { return region.playerMain(); }
    private boolean clickRegion(int logical) {
        var slot = menu.getSlot(region.menuSlot(logical));
        if (mc.player == null || !slot.mayPickup(mc.player)
                || !menu.getCarried().isEmpty() && (!slot.mayPlace(menu.getCarried())
                || menu.getCarried().getCount() > slot.getMaxStackSize(menu.getCarried()))) {
            stopMessage = t("status.sort.changed");
            return false;
        }
        return click(region.menuSlot(logical), 0, ContainerInput.PICKUP);
    }

	@Override
	public Component runningLabel() {
		return t("status.sort.running");
	}

	@Override
	public void start() {
		if (!cursorEmpty()) {
			finish(t("status.cursor_not_empty"));
			return;
		}
        if (region.playerMain() && region.slots().size() != 27) { stopSafely(t("status.sort.changed")); return; }
		List<Stack> slots = region.snapshot();
		if (SortPlanner.countStacks(slots) == 0) {
			finish(t("status.sort.empty"));
			return;
		}
		target = SortPlanner.computeTarget(slots, null, order);
		if (target == null) {
			stopSafely(t("status.sort.no_fit"));
			return;
		}
		if (SortPlanner.matches(slots, target)) {
			finish(t("status.sort.already"));
			return;
		}
		if (SortPlanner.hasFragile(slots) && SortPlanner.isFull(target)) {
			// Bundles can only be moved through an empty slot, and consolidation would not free one.
			finish(t("status.sort.bundles_full"));
			return;
		}
		maxClicks = slots.size() * 8 + 40;
	}

	@Override
	public void tick() {
		if (cancelRequested || stopMessage != null) {
			parkCursorThenStop(stopMessage != null ? stopMessage : t("status.stopped"));
			return;
		}
		if (settleTicks > 0) {
			settleTicks--;
			return;
		}
		if (settleTicks == 0) {
			settleTicks = -1;
			List<Stack> slots = region.snapshot();
			if (ChestSnapshots.carried(menu) == null && SortPlanner.matches(slots, target)) {
				finish(t("status.sort.done", SortPlanner.countStacks(target)));
				return;
			}
			if (++verifyRounds > MAX_VERIFY_ROUNDS) {
				stopSafely(t("status.sort.changed"));
				return;
			}
			// A late server correction changed something: keep going from the live state.
		}

		int clicks = 0;
		while (clicks < clicksPerTick) {
			List<Stack> slots = region.snapshot();
			Stack cursor = ChestSnapshots.carried(menu);

			// Full chest with bundles: free a slot by consolidating ordinary stacks first.
			if (freeingSlot && cursor == null && !SortPlanner.isFull(slots)) {
				freeingSlot = false;
			} else if (!freeingSlot && cursor == null && SortPlanner.isFull(slots) && SortPlanner.hasFragile(slots)) {
				freeingSlot = true;
			}
			if (freeingSlot) {
				int slot = SortPlanner.freeSlotStep(slots, cursor);
				if (slot < 0) {
					stopMessage = t("status.sort.bundles_full");
					return;
				}
				if (++totalClicks > maxClicks) {
					stopSafely(t("status.sort.too_many"));
					return;
				}
				if (!clickRegion(slot)) {
					return;
				}
				clicks++;
				continue;
			}

			SortPlanner.Step step = SortPlanner.nextStep(slots, cursor, target);
			switch (step.result()) {
				case DONE -> {
					settleTicks = SETTLE_TICKS;
					return;
				}
				case STALE -> {
					if (!recomputeTarget(slots, cursor)) {
						return;
					}
				}
				case BLOCKED -> {
					stopMessage = t("status.sort.bundles_full");
					return;
				}
				case CLICK -> {
					ClickSimulator.State sim = new ClickSimulator.State(slots, cursor);
					if (!ClickSimulator.leftClick(sim, step.slot())) {
						// The planner never asks for a no-op click; if it would be one the
						// live state disagrees with our model. Re-plan rather than loop.
						if (!recomputeTarget(slots, cursor)) {
							return;
						}
						continue;
					}
					if (++totalClicks > maxClicks) {
						stopSafely(t("status.sort.too_many"));
						return;
					}
					if (!clickRegion(step.slot())) {
						return;
					}
					clicks++;
				}
			}
		}
	}

    private void stopSafely(Component message) {
        if (cursorEmpty()) finish(message); else stopMessage = message;
    }

	private boolean recomputeTarget(List<Stack> slots, Stack cursor) {
		if (++recomputes > MAX_RECOMPUTES) {
			stopSafely(t("status.sort.changed"));
			return false;
		}
		target = SortPlanner.computeTarget(slots, cursor, order);
		if (target == null) {
			stopSafely(t("status.sort.no_fit"));
			return false;
		}
		return true;
	}

	private void parkCursorThenStop(Component doneMessage) {
		Stack cursor = ChestSnapshots.carried(menu);
		if (cursor == null) {
			finish(doneMessage);
			return;
		}
		int slot = SortPlanner.parkCursorSlot(region.snapshot(), cursor);
		if (slot < 0 || ++totalClicks > maxClicks + 20) {
			finish(t("status.stopped_holding"));
			return;
		}
		clickRegion(slot);
		// Re-evaluated next tick: a partial merge may leave a remainder on the cursor.
	}
}
