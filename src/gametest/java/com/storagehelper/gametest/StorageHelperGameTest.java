package com.storagehelper.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.storagehelper.StorageHelperClient;
import com.storagehelper.chest.ChestSession;
import com.storagehelper.chest.ChestSessions;
import com.storagehelper.logic.SortMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Drives the real client: creates a world, places and fills a chest, opens it by right-clicking,
 * then exercises Deposit Matching, both sort orders, search highlighting and key routing, and
 * verifies the <em>server-side</em> chest contents after each step. Screenshots are written to
 * {@code run/clientGameTest/screenshots}.
 */
public final class StorageHelperGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerConnection conn = sp.getConnection();
			TestServerContext server = sp.getServer();
			conn.waitForChunksRender();

			BlockPos base = ctx.computeOnClient(mc -> Objects.requireNonNull(mc.player).blockPosition());
			int x = base.getX();
			int y = base.getY();
			int z = base.getZ();
			BlockPos chestPos = new BlockPos(x, y, z + 2);

			// Flat stage, a chest two blocks south of the player, player facing south.
			server.runCommand("gamemode creative @a");
			server.runCommand(fmt("fill %d %d %d %d %d %d minecraft:stone", x - 4, y - 1, z - 4, x + 4, y - 1, z + 6));
			server.runCommand(fmt("fill %d %d %d %d %d %d minecraft:air", x - 4, y, z - 4, x + 4, y + 3, z + 6));
			server.runCommand(fmt("setblock %d %d %d minecraft:chest[facing=north]", chestPos.getX(), chestPos.getY(), chestPos.getZ()));
			chest(server, chestPos, 0, "minecraft:cobblestone", 40);
			chest(server, chestPos, 3, "minecraft:dirt", 10);
			chest(server, chestPos, 5, "minecraft:cobblestone", 40);
			chest(server, chestPos, 7, "minecraft:dirt", 5);
			chest(server, chestPos, 9, "minecraft:diamond_sword", 1);
			chest(server, chestPos, 12, "minecraft:apple", 3);
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 64");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:stick 7");
			server.runCommand("item replace entity @a inventory.0 with minecraft:dirt 20");
			server.runCommand(fmt("tp @a %d %d %d 0 0", x, y, z));
			conn.waitForClientboundPackets();
			ctx.waitTicks(5);

			// Open the chest exactly like a player would.
			ctx.getInput().lookAt(chestPos);
			ctx.waitTick();
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitForScreen(ContainerScreen.class);
			conn.waitForClientboundPackets();
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> ChestSessions.forScreen(mc.gui.screen()) != null), "Storage Helper panel was not attached to the chest screen");
			ctx.takeScreenshot("storagehelper-1-panel");

			// ---- Deposit Matching ----
			ctx.clickScreenButton("storagehelper.button.deposit");
			waitForIdle(ctx, conn);
			List<String> chestAfterDeposit = readChest(server, chestPos);
			check(total(chestAfterDeposit, "minecraft:cobblestone") == 144, "cobblestone not fully deposited: " + chestAfterDeposit);
			check(total(chestAfterDeposit, "minecraft:dirt") == 35, "dirt not fully deposited: " + chestAfterDeposit);
			check(chestAfterDeposit.get(0).equals("minecraft:cobblestone x64"), "partial stack should be topped up first: " + chestAfterDeposit);
			check(chestAfterDeposit.get(5).equals("minecraft:cobblestone x64"), "second partial stack should be topped up: " + chestAfterDeposit);
			check(chestAfterDeposit.get(3).equals("minecraft:dirt x30"), "dirt should merge into existing stack: " + chestAfterDeposit);
			List<String> player = readPlayer(ctx);
			check(total(player, "minecraft:cobblestone") == 0 && total(player, "minecraft:dirt") == 0, "matching items left in inventory: " + player);
			check(total(player, "minecraft:stick") == 7, "non-matching stick must stay: " + player);
			ctx.takeScreenshot("storagehelper-2-after-deposit");

			// ---- Sort A-Z ----
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.ALPHABETICAL));
			ctx.clickScreenButton("storagehelper.button.sort");
			waitForIdle(ctx, conn);
			List<String> sortedAz = readChest(server, chestPos);
			expectPrefix(sortedAz, "minecraft:apple x3", "minecraft:cobblestone x64", "minecraft:cobblestone x64",
					"minecraft:cobblestone x16", "minecraft:diamond_sword x1", "minecraft:dirt x35", "-");
			check(total(sortedAz, "minecraft:cobblestone") == 144 && total(sortedAz, "minecraft:dirt") == 35, "sort changed item totals: " + sortedAz);
			ctx.takeScreenshot("storagehelper-3-sorted-az");

			// ---- Sort By Type ----
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.BY_TYPE));
			ctx.clickScreenButton("storagehelper.button.sort");
			waitForIdle(ctx, conn);
			List<String> sortedType = readChest(server, chestPos);
			expectPrefix(sortedType, "minecraft:diamond_sword x1", "minecraft:apple x3", "minecraft:cobblestone x64",
					"minecraft:cobblestone x64", "minecraft:cobblestone x16", "minecraft:dirt x35", "-");
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.ALPHABETICAL));
			ctx.takeScreenshot("storagehelper-4-sorted-type");

			// ---- Search + key routing ----
			double[] box = ctx.computeOnClient(mc -> {
				for (AbstractWidget w : Screens.getWidgets(Objects.requireNonNull(mc.gui.screen()))) {
					if (w instanceof EditBox e) {
						double scale = mc.getWindow().getGuiScale();
						return new double[]{(e.getX() + e.getWidth() / 2.0) * scale, (e.getY() + e.getHeight() / 2.0) * scale};
					}
				}
				return null;
			});
			check(box != null, "search box not found");
			ctx.getInput().setCursorPos(box[0], box[1]);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.getInput().typeChars("zzz");
			ctx.waitTicks(2);
			check(searchActive(ctx) && countMatches(ctx) == 0, "a query with no hits must show the no-match state");
			ctx.takeScreenshot("storagehelper-5a-no-matches");
			check(ctx.tryClickScreenButton("\u2715"), "clear button not found");
			ctx.getInput().setCursorPos(box[0], box[1]);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.getInput().typeChars("COBB");
			ctx.waitTicks(2);
			check(countMatches(ctx) == 3, "expected 3 cobblestone matches, got " + countMatches(ctx));
			ctx.takeScreenshot("storagehelper-5b-search-highlight");

			// Hover a chest slot and press the hotbar-1 key and the inventory key: with the box focused
			// nothing may move and the screen must stay open.
			double[] slot0 = ctx.computeOnClient(mc -> {
				ContainerScreen s = (ContainerScreen) Objects.requireNonNull(mc.gui.screen());
				Slot slot = s.getMenu().getSlot(0);
				int left = (s.width - 176) / 2;
				int top = (s.height - (114 + s.getMenu().getRowCount() * 18)) / 2;
				double scale = mc.getWindow().getGuiScale();
				return new double[]{(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
			});
			ctx.getInput().setCursorPos(slot0[0], slot0[1]);
			ctx.waitTick();
			ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(2);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "inventory key must not close the chest while typing");
			conn.waitForServerboundPackets();
			conn.waitForClientboundPackets();
			List<String> afterKeys = readChest(server, chestPos);
			check(afterKeys.equals(sortedType), "hotbar key swapped a slot while the search box was focused: " + afterKeys);
			ctx.takeScreenshot("storagehelper-5c-search-with-tooltip");

			// Escape leaves the box but keeps the query; the clear button resets it.
			ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
			ctx.waitTick();
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "first Escape should only unfocus the search box");
			check(countMatches(ctx) == 3, "query should survive unfocusing");
			check(ctx.tryClickScreenButton("✕"), "clear button not found");
			ctx.waitTick();
			check(countMatches(ctx) == 0 && !searchActive(ctx), "clear button should reset the search");

			// Second Escape closes the chest.
			ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
			ctx.waitForScreen(null);
			conn.waitForServerboundPackets();
			List<String> finalChest = readChest(server, chestPos);
			check(total(finalChest, "minecraft:cobblestone") == 144 && total(finalChest, "minecraft:dirt") == 35
					&& total(finalChest, "minecraft:apple") == 3 && total(finalChest, "minecraft:diamond_sword") == 1,
					"items changed after closing: " + finalChest);
			ctx.takeScreenshot("storagehelper-6-closed");
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void chest(TestServerContext server, BlockPos pos, int slot, String item, int count) {
		server.runCommand(fmt("item replace block %d %d %d container.%d with %s %d", pos.getX(), pos.getY(), pos.getZ(), slot, item, count));
	}

	private static String fmt(String f, Object... args) {
		return String.format(java.util.Locale.ROOT, f, args);
	}

	private static void waitForIdle(ClientGameTestContext ctx, TestServerConnection conn) {
		ctx.waitTicks(2);
		ctx.waitFor(mc -> {
			ChestSession s = ChestSessions.forScreen(mc.gui.screen());
			return s == null || !s.isOperationRunning();
		}, 600);
		conn.waitForServerboundPackets();
		conn.waitForClientboundPackets();
		ctx.waitTicks(2);
	}

	private static List<String> readChest(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> {
			BlockEntity be = s.overworld().getBlockEntity(pos);
			check(be instanceof Container, "no chest at " + pos);
			Container c = (Container) be;
			List<String> out = new ArrayList<>();
			for (int i = 0; i < c.getContainerSize(); i++) {
				out.add(describe(c.getItem(i)));
			}
			return out;
		});
	}

	private static List<String> readPlayer(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			ContainerScreen s = (ContainerScreen) Objects.requireNonNull(mc.gui.screen());
			List<String> out = new ArrayList<>();
			for (int i = s.getMenu().getRowCount() * 9; i < s.getMenu().slots.size(); i++) {
				out.add(describe(s.getMenu().getSlot(i).getItem()));
			}
			return out;
		});
	}

	private static String describe(ItemStack stack) {
		return stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
	}

	private static int total(List<String> slots, String id) {
		int n = 0;
		for (String s : slots) {
			if (s.startsWith(id + " x")) {
				n += Integer.parseInt(s.substring(id.length() + 2));
			}
		}
		return n;
	}

	private static void expectPrefix(List<String> actual, String... expected) {
		for (int i = 0; i < expected.length; i++) {
			check(actual.get(i).equals(expected[i]), "slot " + i + ": expected " + expected[i] + " but chest is " + actual);
		}
	}

	private static int countMatches(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();
			ChestSession s = ChestSessions.forScreen(screen);
			if (s == null) {
				return -1;
			}
			int n = 0;
			for (int i = 0; i < s.menu().getRowCount() * 9; i++) {
				if (s.isMatch(i)) {
					n++;
				}
			}
			return n;
		});
	}

	private static boolean searchActive(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			ChestSession s = ChestSessions.forScreen(mc.gui.screen());
			return s != null && s.isSearchActive();
		});
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
