package com.storagehelper.chest;

import com.storagehelper.chest.op.ChestOperation;
import com.storagehelper.StorageHelperClient;
import com.storagehelper.chest.op.DepositOperation;
import com.storagehelper.chest.op.SortOperation;
import com.storagehelper.chest.op.ScrollTransferOperation;
import com.storagehelper.chest.op.RememberedReturnOperation;
import com.storagehelper.gui.InventoryAreas;
import com.storagehelper.logic.ScrollAccumulator;
import com.storagehelper.logic.ControlLayout;
import com.storagehelper.gui.IconButton;
import com.storagehelper.gui.ScreenSlots;
import com.storagehelper.gui.HoverContents;
import com.storagehelper.config.StorageHelperConfig;
import com.storagehelper.logic.SearchMatcher;
import com.storagehelper.logic.SortMode;
import com.storagehelper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-chest-screen state: the side panel widgets, the live search highlight set and the
 * currently running operation. Created when a chest screen opens, discarded when it closes,
 * so the search query resets for every new chest.
 */
public final class ChestSession {

	private final Minecraft mc;
	private final AbstractContainerScreen<?> screen;
	private final AbstractContainerMenu menu;
	private final StorageHelperConfig config;
	private final List<AbstractWidget> ourWidgets = new ArrayList<>();

	private @Nullable IconButton depositButton;
	private @Nullable IconButton sortButton;
    private @Nullable IconButton playerSortButton;
	private @Nullable IconButton modeButton;
	private @Nullable EditBox searchBox;
	private @Nullable IconButton clearButton;
	private @Nullable StringWidget matchLabel;

	private String query = "";
	private boolean[] matches = new boolean[0];
	private int matchCount = -1;
	private @Nullable ChestOperation operation;
	private boolean closed;
	private final ScrollAccumulator scroll = new ScrollAccumulator();
    private final ScrollAccumulator returnScroll = new ScrollAccumulator();
    private final boolean containerSupported;
    private final TransferHistory history;
    private int pendingReturn;
    private record DeferredForward(int source, boolean deposit, int count, ItemStack identity) {}
    private DeferredForward deferredForward;

	ChestSession(Minecraft mc, AbstractContainerScreen<?> screen, StorageHelperConfig config) {
		this.mc = mc;
		this.screen = screen;
		this.menu = screen.getMenu();
		this.config = config;
        containerSupported = ChestDetector.isChestScreen(screen);
        history = new TransferHistory(menu);
	}

	public AbstractContainerScreen<?> screen() {
		return screen;
	}

	public AbstractContainerMenu menu() {
		return menu;
	}

	// ---------------------------------------------------------------- lifecycle

	void attach() {
		ScreenEvents.remove(screen).register(s -> close());
		ScreenEvents.afterTick(screen).register(s -> tick());
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> allowKeyPress(event));
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> allowMouseClick(event));
		buildWidgets();
		refreshMatches();
	}

	void rebuildWidgets() {
        // Fabric replaces all per-screen events before every resize/init.
        // Reattach input, close, and tick handlers along with the widgets.
        attach();
	}

	void close() {
		if (closed) {
			return;
		}
		closed = true;
		// A running operation simply stops issuing clicks. Anything still on the cursor is
		// handled by vanilla's container-close logic (returned to the inventory).
		operation = null;
        history.clear(); pendingReturn = 0; deferredForward = null;
		ChestSessions.clear(this);
	}

	private void tick() {
		if (closed) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || player.containerMenu != menu) {
			operation = null;
            history.clear(); pendingReturn = 0; deferredForward = null;
			return;
		}
		refreshMatches();
        double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());
        var hovered = ScreenSlots.at(screen, mx, my);
        boolean editing = isSearchFocused() || HoverContents.pointerInside(screen);
        boolean shift = ScreenSlots.shiftDown();
        if (!shift || editing || hovered == null || hovered.index != scroll.source()) {
            scroll.reset();
            if (operation instanceof ScrollTransferOperation transfer) transfer.requestCancel();
        }
        if (!shift || editing || !containerSupported || !InventoryAreas.contains(screen, history, history.deposit(), mx, my)) {
            returnScroll.reset(); pendingReturn = 0;
            if (operation instanceof RememberedReturnOperation reverse) reverse.requestCancel();
        }
        if (deferredForward != null && (!shift || editing || hovered == null || hovered.index != deferredForward.source())) deferredForward = null;
        boolean cursorPlayer = operation instanceof SortOperation sort ? sort.sortsPlayerInventory()
                : operation instanceof ScrollTransferOperation transfer ? transfer.cursorInPlayer()
                : operation instanceof RememberedReturnOperation reverse && reverse.cursorInPlayer();
        if (containerSupported && history.hasIdentity() && !history.validate(cursorPlayer)) {
            pendingReturn = 0;
            if (operation instanceof RememberedReturnOperation reverse) reverse.requestCancel();
        }

		if (operation != null) {
			operation.tick();
			if (operation.isFinished()) {
                logResult(operation);
				operation = null;
				updateButtons();
			}
		}
        if (operation == null && menu.getCarried().isEmpty() && shift && !editing) {
            if (pendingReturn > 0) {
                int count = pendingReturn; pendingReturn = 0;
                startReturn(count);
            } else if (deferredForward != null) {
                var request = deferredForward; deferredForward = null;
                var live = menu.getSlot(request.source()).getItem();
                if (!live.isEmpty() && ItemStack.isSameItemSameComponents(live, request.identity()))
                    startForward(request.source(), request.deposit(), request.count());
            }
        }
	}

	// ---------------------------------------------------------------- widgets

    private void buildWidgets() {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        widgets.removeAll(ourWidgets);
        ourWidgets.clear();
        var acc = (AbstractContainerScreenAccessor) screen;
        var l = ControlLayout.place(screen.width, screen.height, acc.storagehelper$getLeftPos(),
                acc.storagehelper$getTopPos(), acc.storagehelper$getImageWidth());
        Font font = mc.font;
        var playerRegion = InventoryRegion.playerMain(menu, mc.player.getInventory());
        int playerY = playerRegion.slots().stream().mapToInt(i -> menu.getSlot(i).y).min().orElse(84) + acc.storagehelper$getTopPos();
        int playerX = acc.storagehelper$getLeftPos() + acc.storagehelper$getImageWidth() + 4;
        if (playerX + 20 > screen.width - 2) playerX = Math.max(2, acc.storagehelper$getLeftPos() - 24);
        playerY = Math.max(2, Math.min(playerY, screen.height - 46));
        playerSortButton = new IconButton(playerX, playerY, Component.translatable("storagehelper.button.player_sort"),
                IconButton.Icon.PLAYER_SORT, b -> onPlayerSortPressed());
        if (!containerSupported) {
            modeButton = new IconButton(playerX, playerY + 22, Component.empty(), IconButton.Icon.ALPHABETICAL,
                    b -> { config.setSortMode(config.sortMode().next()); updateButtons(); });
            ourWidgets.addAll(List.of(playerSortButton, modeButton)); widgets.addAll(ourWidgets); updateButtons(); return;
        }
        int tx = l.toolsX();
        depositButton = new IconButton(tx, l.toolsY(), Component.translatable("storagehelper.button.deposit"),
                IconButton.Icon.DEPOSIT, b -> onDepositPressed());
        sortButton = new IconButton(tx, l.toolsY() + 22, Component.translatable("storagehelper.button.sort"),
                IconButton.Icon.SORT, b -> onSortPressed());
        modeButton = new IconButton(tx, l.toolsY() + 44, Component.empty(), IconButton.Icon.ALPHABETICAL,
                b -> { config.setSortMode(config.sortMode().next()); updateButtons(); });

        EditBox previous = searchBox;
        EditBox box = new EditBox(font, l.searchX(), l.searchY(), l.searchWidth() - 24, 20, previous,
                Component.translatable("storagehelper.search.narration"));
        box.setMaxLength(64);
        box.setHint(Component.translatable("storagehelper.search.hint"));
        if (previous == null && !query.isEmpty()) box.setValue(query);
        box.setResponder(this::onQueryChanged);
        searchBox = box;
        clearButton = new IconButton(l.searchX() + l.searchWidth() - 20, l.searchY(),
                Component.translatable("storagehelper.search.clear"), IconButton.Icon.CLEAR, b -> clearSearch());
        clearButton.setTooltip(Tooltip.create(Component.translatable("storagehelper.search.clear")));
        matchLabel = new StringWidget(l.toolsX(), Math.min(l.toolsY() + 68, playerY - 12), l.toolsWidth(), 10, Component.empty(), font);
        ourWidgets.addAll(List.of(depositButton, sortButton, modeButton, searchBox, clearButton, matchLabel, playerSortButton));
        widgets.addAll(ourWidgets);
        updateButtons();
        updateMatchLabel();
    }

	private void updateButtons() {
        if (modeButton == null || playerSortButton == null) return;
        boolean idle = operation == null;
        boolean depositing = operation instanceof DepositOperation;
        boolean sorting = operation instanceof SortOperation sort && !sort.sortsPlayerInventory();
        boolean playerSorting = operation instanceof SortOperation sort && sort.sortsPlayerInventory();
        Component cancel = Component.translatable("storagehelper.button.cancel");
        if (depositButton != null) {
            depositButton.active = idle || depositing;
            depositButton.setMessage(depositing ? cancel : Component.translatable("storagehelper.button.deposit"));
            depositButton.setIcon(depositing ? IconButton.Icon.CANCEL : IconButton.Icon.DEPOSIT);
            depositButton.setTooltip(Tooltip.create(depositing ? Component.translatable("storagehelper.button.cancel.tooltip")
                    : Component.translatable("storagehelper.button.deposit").append("\n").append(Component.translatable("storagehelper.button.deposit.tooltip"))));
        }
        if (sortButton != null) {
            sortButton.active = idle || sorting;
            sortButton.setMessage(sorting ? cancel : Component.translatable("storagehelper.button.sort"));
            sortButton.setIcon(sorting ? IconButton.Icon.CANCEL : IconButton.Icon.SORT);
            sortButton.setTooltip(Tooltip.create(sorting ? Component.translatable("storagehelper.button.cancel.tooltip")
                    : Component.translatable("storagehelper.button.sort").append("\n").append(Component.translatable("storagehelper.button.sort.tooltip"))));
        }
        playerSortButton.active = idle || playerSorting;
        playerSortButton.setMessage(playerSorting ? cancel : Component.translatable("storagehelper.button.player_sort"));
        playerSortButton.setIcon(playerSorting ? IconButton.Icon.CANCEL : IconButton.Icon.PLAYER_SORT);
        playerSortButton.setTooltip(Tooltip.create(playerSorting ? Component.translatable("storagehelper.button.cancel.tooltip")
                : Component.translatable("storagehelper.button.player_sort.tooltip")));
        Component mode = Component.translatable("storagehelper.sortmode." + config.sortMode().id());
        modeButton.active = idle;
        modeButton.setMessage(Component.translatable("storagehelper.sortmode.current", mode));
        modeButton.setIcon(config.sortMode() == SortMode.ALPHABETICAL ? IconButton.Icon.ALPHABETICAL : IconButton.Icon.CATEGORY);
        modeButton.setTooltip(Tooltip.create(Component.translatable("storagehelper.sortmode.current", mode).append("\n")
                .append(Component.translatable("storagehelper.sortmode.tooltip." + config.sortMode().id())).append("\n")
                .append(Component.translatable("storagehelper.sortmode.toggle"))));
    }

    private void logResult(ChestOperation op) {
        // Outcomes remain available for diagnosis without HUD, chat, or toast messages.
        StorageHelperClient.LOGGER.debug("[storagehelper] {} ended: {}", op.getClass().getSimpleName(), op.result().getString());
    }

	// ---------------------------------------------------------------- operations

	private void onDepositPressed() {
		if (operation != null) {
			if (operation instanceof DepositOperation) {
				operation.requestCancel();
			}
			return;
		}
		startOperation(new DepositOperation(mc, menu, config.clicksPerTick()));
	}

	private void onSortPressed() {
		if (operation != null) {
			if (operation instanceof SortOperation) {
				operation.requestCancel();
			}
			return;
		}
		startOperation(new SortOperation(mc, menu, config.sortClicksPerTick(), config.sortMode().comparator()));
	}

    private void onPlayerSortPressed() {
        if (operation != null) {
            if (operation instanceof SortOperation sort && sort.sortsPlayerInventory()) sort.requestCancel();
            return;
        }
        startOperation(new SortOperation(mc, InventoryRegion.playerMain(menu, mc.player.getInventory()), config.sortClicksPerTick(), config.sortMode().comparator()));
    }

	private void startOperation(ChestOperation op) {
        pendingReturn = 0; deferredForward = null;
			unfocusSearch();
		op.start();
		if (op.isFinished()) {
			logResult(op);
			return;
		}
		operation = op;
		updateButtons();
	}

	public boolean isOperationRunning() {
		return operation != null;
	}

	// ---------------------------------------------------------------- search

	private void onQueryChanged(String raw) {
		query = SearchMatcher.normalize(raw);
		refreshMatches();
	}

	private void clearSearch() {
		if (searchBox != null) {
			searchBox.setValue("");
		}
		query = "";
		refreshMatches();
	}

    private void unfocusSearch() {
        // Clear the owner before the widget. setFocused() ignores reassignment to the
        // same child, so leaving a stale child pointer makes the next click fail.
        if (screen.getFocused() == searchBox) screen.setFocused(null);
        else if (searchBox != null) searchBox.setFocused(false);
    }

    public boolean isSearchFocused() { return searchBox != null && searchBox.isFocused(); }

    private void startForward(int slot, boolean deposit, int count) {
        if (count <= 0 || !menu.getCarried().isEmpty()) return;
        history.begin(menu.getSlot(slot).getItem(), deposit, slot);
        startOperation(new ScrollTransferOperation(mc, menu, slot, deposit, count, config.scrollClicksPerTick(), history, false, -1));
    }
    private void startReturn(int count) {
        if (count > 0 && history.quantity() > 0 && history.validate(false))
            startOperation(new RememberedReturnOperation(mc, menu, history, Math.min(count, history.quantity()), config.scrollClicksPerTick()));
    }
    public boolean onScroll(double x, double y, double delta) {
        if (!containerSupported) return false;
        if (!ScreenSlots.shiftDown()) { scroll.reset(); returnScroll.reset(); return false; }
        if (isSearchFocused() || !Double.isFinite(delta) || delta == 0) return true;
        for (AbstractWidget widget : ourWidgets) if (widget.isMouseOver(x, y)) { scroll.reset(); return true; }
        boolean playerArea = InventoryAreas.contains(screen, history, true, x, y);
        boolean containerArea = InventoryAreas.contains(screen, history, false, x, y);
        if (!playerArea && !containerArea) { scroll.reset(); returnScroll.reset(); return true; }
        boolean forward = playerArea ? delta < 0 : delta > 0;
        if (!forward) {
            scroll.reset(); deferredForward = null;
            if (!history.hasIdentity() || history.deposit() != playerArea) return true;
            int count = returnScroll.add(playerArea ? -2 : -3, delta, System.currentTimeMillis(), config.scrollItemsPerUnit());
            if (operation instanceof ScrollTransferOperation transfer) {
                count = transfer.cancelForReverse(count);
                pendingReturn = Math.min(ScrollAccumulator.MAX_BACKLOG, pendingReturn + count);
            } else if (operation instanceof RememberedReturnOperation reverse) reverse.enqueue(count);
            else if (operation == null) startReturn(count);
            return true;
        }
        returnScroll.reset(); pendingReturn = 0;
        var source = ScreenSlots.at(screen, x, y);
        if (source == null) { if (operation != null) operation.requestCancel(); return true; }
        int count = scroll.add(source.index, delta, System.currentTimeMillis(), config.scrollItemsPerUnit());
        if (operation instanceof ScrollTransferOperation transfer && !transfer.isCancelling()
                && transfer.source() == source.index && transfer.cursorInPlayer() == playerArea) {
            transfer.enqueue(count); return true;
        }
        if (operation != null) {
            if (operation instanceof ScrollTransferOperation || operation instanceof RememberedReturnOperation) {
                operation.requestCancel();
                ItemStack item = source.hasItem() ? source.getItem() : history.prototype();
                if (!item.isEmpty() && count > 0) {
                    int queued = deferredForward != null && deferredForward.source() == source.index
                            && deferredForward.deposit() == playerArea && ItemStack.isSameItemSameComponents(deferredForward.identity(), item)
                            ? deferredForward.count() : 0;
                    deferredForward = new DeferredForward(source.index, playerArea, Math.min(ScrollAccumulator.MAX_BACKLOG, queued + count), item.copyWithCount(1));
                }
            }
            return true;
        }
        if (source.hasItem()) startForward(source.index, playerArea, count);
        return true;
    }

	private void refreshMatches() {
        if (!containerSupported) return;
		int n = ChestSnapshots.chestSlotCount(menu);
		if (matches.length != n) {
			matches = new boolean[n];
		}
		if (query.isEmpty()) {
			Arrays.fill(matches, false);
			matchCount = -1;
		} else {
			int count = 0;
			for (int i = 0; i < n; i++) {
				ItemStack stack = menu.getSlot(i).getItem();
				boolean match = !stack.isEmpty() && SearchMatcher.matches(
						query,
						stack.getHoverName().getString(),
						BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				matches[i] = match;
				if (match) {
					count++;
				}
			}
			matchCount = count;
		}
		updateMatchLabel();
	}

	private void updateMatchLabel() {
		if (matchLabel == null) {
			return;
		}
		if (matchCount < 0) {
			matchLabel.setMessage(Component.empty());
		} else if (matchCount == 0) {
			matchLabel.setMessage(Component.translatable("storagehelper.search.no_matches").withStyle(ChatFormatting.RED));
		} else if (matchCount == 1) {
			matchLabel.setMessage(Component.translatable("storagehelper.search.one_match").withStyle(ChatFormatting.YELLOW));
		} else {
			matchLabel.setMessage(Component.translatable("storagehelper.search.matches", matchCount).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** True while a non-empty query is active (highlights and dimming are drawn). */
	public boolean isSearchActive() {
		return !query.isEmpty();
	}

	public boolean isMatch(int chestSlotIndex) {
		return chestSlotIndex >= 0 && chestSlotIndex < matches.length && matches[chestSlotIndex];
	}

	public boolean dimNonMatching() {
		return config.dimNonMatching;
	}

	// ---------------------------------------------------------------- input

	/**
	 * While the search box is focused, keys go to the box only: no hotbar swaps, no
	 * inventory-close key, no drop. Escape or Enter leaves the box (a second Escape closes
	 * the chest as usual). While an operation runs, keys that would move items are blocked.
	 */
	private boolean allowKeyPress(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused()) {
			if (event.isEscape() || event.isConfirmation()) {
				unfocusSearch();
				return false;
			}
			searchBox.keyPressed(event);
			return false;
		}
		if (operation != null) {
			return event.isEscape() || mc.options.keyInventory.matches(event);
		}
		return true;
	}

	/** Clicking outside the search box drops its focus; slot clicks are blocked while an operation runs. */
	private boolean allowMouseClick(MouseButtonEvent event) {
		double x = event.x();
		double y = event.y();
        if (searchBox != null && searchBox.isMouseOver(x, y)) {
            screen.setFocused(searchBox);
            searchBox.setFocused(true);
        }
		if (searchBox != null && searchBox.isFocused() && !searchBox.isMouseOver(x, y)) {
			unfocusSearch();
		}
		if (operation != null) {
			for (AbstractWidget w : ourWidgets) {
				if (w.isMouseOver(x, y)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
}
