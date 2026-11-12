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
			server.runCommand("item replace entity @a hotbar.1 with minecraft:stick 64");
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
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-1-panel");
            verifyControls(ctx);
            ctx.clickScreenButton("Order: A-Z");
            check(ctx.computeOnClient(mc -> StorageHelperClient.config().sortMode()) == SortMode.BY_TYPE, "mode icon must change the saved order");
            ctx.clickScreenButton("Order: By Type");
            double[] modePoint = ctx.computeOnClient(mc -> {
                var modeWidget = Screens.getWidgets(mc.gui.screen()).stream().filter(w -> w.getMessage().getString().equals("Order: A-Z")).findFirst().orElseThrow();
                double unit = mc.getWindow().getGuiScale();
                return new double[]{(modeWidget.getX()+10)*unit,(modeWidget.getY()+10)*unit};
            });
            ctx.getInput().setCursorPos(modePoint[0],modePoint[1]); ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-mode-tooltip");



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
			check(total(player, "minecraft:stick") == 64, "non-matching stick must stay: " + player);
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-2-after-deposit");

			// ---- Sort A-Z ----
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.ALPHABETICAL));
			ctx.clickScreenButton("storagehelper.button.sort");
            ctx.runOnClient(mc -> {
                for (var widget : Screens.getWidgets(mc.gui.screen())) if (widget instanceof com.storagehelper.gui.IconButton icon) {
                    if (icon.icon() == com.storagehelper.gui.IconButton.Icon.ALPHABETICAL || icon.icon() == com.storagehelper.gui.IconButton.Icon.DEPOSIT)
                        check(!icon.active, "other action icons must disable while sorting");
                }
            });
			waitForIdle(ctx, conn);
			List<String> sortedAz = readChest(server, chestPos);
			expectPrefix(sortedAz, "minecraft:apple x3", "minecraft:cobblestone x64", "minecraft:cobblestone x64",
					"minecraft:cobblestone x16", "minecraft:diamond_sword x1", "minecraft:dirt x35", "-");
			check(total(sortedAz, "minecraft:cobblestone") == 144 && total(sortedAz, "minecraft:dirt") == 35, "sort changed item totals: " + sortedAz);
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-3-sorted-az");

			// ---- Sort By Type ----
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.BY_TYPE));
			ctx.clickScreenButton("storagehelper.button.sort");
			waitForIdle(ctx, conn);
			List<String> sortedType = readChest(server, chestPos);
			expectPrefix(sortedType, "minecraft:diamond_sword x1", "minecraft:apple x3", "minecraft:cobblestone x64",
					"minecraft:cobblestone x64", "minecraft:cobblestone x16", "minecraft:dirt x35", "-");
			ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.ALPHABETICAL));
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-4-sorted-type");

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
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-5a-no-matches");
			check(ctx.tryClickScreenButton("storagehelper.search.clear"), "clear button not found");
			ctx.getInput().setCursorPos(box[0], box[1]);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.getInput().typeChars("COBB");
			ctx.waitTicks(2);
			check(countMatches(ctx) == 3, "expected 3 cobblestone matches, got " + countMatches(ctx));
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-5b-search-highlight");

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
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-5c-search-with-tooltip");

			// Escape leaves the box but keeps the query; the clear button resets it.
			ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
			ctx.waitTick();
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "first Escape should only unfocus the search box");
			check(countMatches(ctx) == 3, "query should survive unfocusing");
            // Regression: release focus by clicking away, then edit the existing query.
            ctx.getInput().setCursorPos(4, 4);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10); // Separate clicks: double-click intentionally selects the word.
            ctx.getInput().setCursorPos(box[0], box[1]);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.getInput().typeChars("x");
            ctx.waitTick();
            check(countMatches(ctx) == 0, "refocused search must accept typing without clearing");
            ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            ctx.waitTick();
            check(countMatches(ctx) == 3, "backspace after refocusing must restore the existing query: " + ctx.computeOnClient(mc -> Screens.getWidgets(mc.gui.screen()).stream().filter(w -> w instanceof EditBox).map(w -> ((EditBox)w).getValue() + ":" + ((EditBox)w).getCursorPosition() + ":" + w.isFocused()).toList()));
			check(ctx.tryClickScreenButton("storagehelper.search.clear"), "clear button not found");
			ctx.waitTick();
			check(countMatches(ctx) == 0 && !searchActive(ctx), "clear button should reset the search");

            // Smoother default: four units request 8 items, two units request 4.
            ctx.getInput().setCursorPos(4, 4);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            moveToSlot(ctx, findSlot(ctx, "minecraft:stick"));
            ctx.getInput().holdShift();
            ctx.getInput().scroll(4);
            ctx.waitTicks(20);
            ctx.getInput().releaseShift();
            waitForIdle(ctx, conn);
            check(total(readChest(server, chestPos), "minecraft:stick") == 8, "four wheel units must deposit exactly eight items: " + readChest(server, chestPos));
            moveToSlot(ctx, findSlot(ctx, "minecraft:stick"));
            ctx.getInput().holdShift();
            ctx.getInput().scroll(-2);
            ctx.waitTicks(15);
            ctx.getInput().releaseShift();
            waitForIdle(ctx, conn);
            check(total(readChest(server, chestPos), "minecraft:stick") == 4, "two wheel units must withdraw exactly four items");
            check(ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty()), "wheel transfer must finish with an empty cursor");
            moveToSlot(ctx, ctx.computeOnClient(mc -> mc.player.containerMenu.slots.stream().filter(slot -> slot.container == mc.player.getInventory() && slot.getItem().is(net.minecraft.world.item.Items.STICK)).findFirst().orElseThrow().index));
            ctx.getInput().holdShift();
            ctx.getInput().scroll(32);
            ctx.waitTicks(6);
            ctx.getInput().releaseShift();
            waitForIdle(ctx, conn);
            check(total(readChest(server, chestPos), "minecraft:stick") == 64, "whole-stack request must fill the existing partial stack");
            moveToSlot(ctx, findSlot(ctx, "minecraft:stick"));
            ctx.getInput().holdShift();
            ctx.getInput().scroll(-.5);
            ctx.waitTicks(10);
            ctx.getInput().releaseShift();
            waitForIdle(ctx, conn);
            check(total(readChest(server, chestPos), "minecraft:stick") == 63, "half-unit gesture must withdraw exactly one item");
            moveToSlot(ctx, ctx.computeOnClient(mc -> mc.player.containerMenu.slots.stream().filter(slot -> slot.container == mc.player.getInventory() && slot.getItem().is(net.minecraft.world.item.Items.STICK)).findFirst().orElseThrow().index));
            ctx.getInput().holdShift(); ctx.getInput().scroll(1); ctx.waitTicks(8); ctx.getInput().releaseShift();
            waitForIdle(ctx, conn);
            check(total(readChest(server, chestPos), "minecraft:stick") == 64, "a two-item request with one available item must transfer that one item");
            // A shulker item in a chest uses the same native border hit-test.
            chest(server, chestPos, 26, "minecraft:red_shulker_box[container=[{slot:13,item:{id:'minecraft:diamond',count:5}}]]", 1);
            conn.waitForClientboundPackets();
            int chestGuiScale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
            for (int scale : new int[]{1, 2}) {
                ctx.runOnClient(mc -> mc.options.guiScale().set(scale));
                ctx.waitTick();
                double[] point = ctx.computeOnClient(mc -> {
                    var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
                    var a = (com.storagehelper.mixin.AbstractContainerScreenAccessor)screen;
                    Slot slot = screen.getMenu().getSlot(26);
                    double unit = mc.getWindow().getGuiScale();
                    return new double[]{(a.storagehelper$getLeftPos() + slot.x - .5) * unit, (a.storagehelper$getTopPos() + slot.y + 8) * unit};
                });
                ctx.getInput().setCursorPos(point[0], point[1]);
                ctx.waitTick();
                check(ctx.computeOnClient(mc -> com.storagehelper.gui.ScreenSlots.at((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen(), mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow())).index) == 26, "container slot border must use native hit testing");
                ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-chest-shulker-border-" + scale);
            }
            ctx.runOnClient(mc -> mc.options.guiScale().set(chestGuiScale));
            ctx.waitTick();


			// Second Escape closes the chest.
			ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
			ctx.waitForScreen(null);
			conn.waitForServerboundPackets();
			List<String> finalChest = readChest(server, chestPos);
			check(total(finalChest, "minecraft:cobblestone") == 144 && total(finalChest, "minecraft:dirt") == 35
					&& total(finalChest, "minecraft:apple") == 3 && total(finalChest, "minecraft:diamond_sword") == 1,
					"items changed after closing: " + finalChest);
			ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-6-closed");
            // All vanilla colors share ShulkerBoxScreen; exercise the actual placed menus.
            String[] colors = {"", "white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_", "pink_", "gray_", "light_gray_", "cyan_", "purple_", "blue_", "brown_", "green_", "red_", "black_"};
            for (String color : colors) {
                server.runCommand(fmt("setblock %d %d %d minecraft:%sshulker_box", chestPos.getX(), chestPos.getY(), chestPos.getZ(), color));
                chest(server, chestPos, 0, "minecraft:stone", 3);
                chest(server, chestPos, 3, "minecraft:apple", 2);
                conn.waitForClientboundPackets();
                ctx.getInput().lookAt(chestPos);
                ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                ctx.waitForScreen(net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen.class);
                check(ctx.computeOnClient(mc -> ChestSessions.forScreen(mc.gui.screen()) != null), "missing shulker session: " + color);
                ctx.clickScreenButton("storagehelper.button.sort");
                waitForIdle(ctx, conn);
                expectPrefix(readChest(server, chestPos), "minecraft:apple x2", "minecraft:stone x3", "-");
                if (color.equals("black_")) {
                    server.runCommand("item replace entity @a hotbar.7 with minecraft:purple_shulker_box[container=[{slot:0,item:{id:'minecraft:diamond',count:7}},{slot:26,item:{id:'minecraft:apple',count:2}}]] 1");
                    server.runCommand("item replace entity @a hotbar.8 with minecraft:bundle[bundle_contents=[{id:'minecraft:apple',count:3},{id:'minecraft:stone',count:5}]] 1");
                    conn.waitForClientboundPackets();
                    ctx.waitTicks(2);
                    moveToSlot(ctx, findSlot(ctx, "minecraft:purple_shulker_box"));
                    ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-7-shulker-preview");
                    int shulkerSlot = findSlot(ctx, "minecraft:purple_shulker_box");
                    int previousScale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
                    for (int scale : new int[]{1, 2}) {
                        ctx.runOnClient(mc -> mc.options.guiScale().set(scale));
                        ctx.waitTicks(2);
                        verifyControls(ctx);
                        double[][] borders = {{-.5, 8}, {16.5, 8}, {8, 16.5}, {-.5, -.5}};
                        for (int edge = 0; edge < borders.length; edge++) {
                            double dx = borders[edge][0], dy = borders[edge][1];
                            double[] point = ctx.computeOnClient(mc -> {
                                var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
                                var a = (com.storagehelper.mixin.AbstractContainerScreenAccessor)screen;
                                Slot slot = screen.getMenu().getSlot(shulkerSlot);
                                double unit = mc.getWindow().getGuiScale();
                                return new double[]{(a.storagehelper$getLeftPos() + slot.x + dx) * unit, (a.storagehelper$getTopPos() + slot.y + dy) * unit};
                            });
                            ctx.getInput().setCursorPos(point[0], point[1]);
                            ctx.waitTick();
                            check(ctx.computeOnClient(mc -> com.storagehelper.gui.ScreenSlots.at((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen(), mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow())).index) == shulkerSlot, "native slot border must resolve to shulker at scale " + scale);
                            ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-shulker-border-" + scale + "-" + edge);
                        }
                    }
                    ctx.runOnClient(mc -> mc.options.guiScale().set(previousScale));
                    ctx.waitTicks(2);
                    moveToSlot(ctx, shulkerSlot);

                    // Actual shulker slot rules reject a nested shulker, without picking it up.
                    ctx.getInput().holdShift();
                    ctx.getInput().scroll(1);
                    ctx.waitTicks(4);
                    ctx.getInput().releaseShift();
                    waitForIdle(ctx, conn);
                    check(total(readChest(server, chestPos), "minecraft:purple_shulker_box") == 0, "wheel transfer inserted a nested shulker");
                    check(ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty()), "prohibited transfer changed cursor");
                    int bundleSlot = findSlot(ctx, "minecraft:bundle");
                    moveToSlot(ctx, bundleSlot);
                    ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-8-bundle-popup");
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupSourceIndex((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen())) == bundleSlot, "bundle must open by the next frame");
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupLayout((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen()).height()) == 34, "two entries should use one compact row");
                    ctx.getInput().setCursorPos(4, 4);
                    ctx.waitTick();
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupSourceIndex((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen())) == -1, "popup must dismiss immediately outside source and body");
                    server.runCommand("item replace entity @a hotbar.0 with minecraft:bundle[custom_name='Pocket Kit',lore=['Travel supplies'],bundle_contents=[{id:'minecraft:dirt',count:2}]] 1");
                    conn.waitForClientboundPackets();
                    moveToSlot(ctx, bundleSlot);
                    int otherBundle = ctx.computeOnClient(mc -> mc.player.containerMenu.slots.stream().filter(slot -> slot.index != bundleSlot && slot.getItem().is(net.minecraft.tags.ItemTags.BUNDLES)).findFirst().orElseThrow().index);
                    moveToSlot(ctx, otherBundle);
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupSourceIndex((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen())) == otherBundle, "switching bundles must replace source immediately");
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupLayout((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen()).columns()) == 1, "new bundle must show its own contents");
                    check(ctx.computeOnClient(mc -> mc.player.containerMenu.getSlot(otherBundle).getItem().getHoverName().getString()).equals("Pocket Kit"), "custom bundle title must be preserved");
                    ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-custom-bundle-title");
                    moveToSlot(ctx, bundleSlot);
                    double[] entry = ctx.computeOnClient(mc -> {
                        var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
                        var layout = com.storagehelper.gui.HoverContents.popupLayout(screen);
                        double scale = mc.getWindow().getGuiScale();
                        return new double[]{(layout.x() + 2 + 18 + 8) * scale, (layout.y() + 12 + 8) * scale};
                    });
                    ctx.getInput().setCursorPos(entry[0], entry[1]);
                    ctx.waitTick();
                    ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-9-bundle-selection");
                    ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                    conn.waitForServerboundPackets();
                    conn.waitForClientboundPackets();
                    check(server.computeOnServer(srv -> describe(srv.getPlayerList().getPlayers().getFirst().containerMenu.getCarried())).equals("minecraft:stone x5"), "bundle popup did not extract the selected stack server-side");
                    check(server.computeOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().containerMenu.getSlot(bundleSlot).getItem().get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).itemCopies().map(StorageHelperGameTest::describe).toList()).equals(List.of("minecraft:apple x3")), "bundle remaining contents changed unexpectedly");
                    // Place the extracted stack before closing the screen.
                    moveToSlot(ctx, 27);
                    ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                    moveToSlot(ctx, bundleSlot);
                    check(ctx.computeOnClient(mc -> com.storagehelper.gui.HoverContents.popupLayout((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen()).columns()) == 1, "extraction must invalidate cached entries");
                }

                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitForScreen(null);
            }

            server.runCommand(fmt("setblock %d %d %d minecraft:chest[facing=north,type=left]", chestPos.getX(), chestPos.getY(), chestPos.getZ()));
            server.runCommand(fmt("setblock %d %d %d minecraft:chest[facing=north,type=right]", chestPos.getX() + 1, chestPos.getY(), chestPos.getZ()));
            conn.waitForClientboundPackets();
            ctx.getInput().lookAt(chestPos); ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            ctx.waitForScreen(ContainerScreen.class);
            check(ctx.computeOnClient(mc -> ((ContainerScreen)mc.gui.screen()).getMenu().getRowCount()) == 6, "expected a real double chest");
            for (int scale : new int[]{1,2}) {
                ctx.runOnClient(mc -> mc.options.guiScale().set(scale)); ctx.waitTicks(2);
                verifyControls(ctx);
                double[] search = ctx.computeOnClient(mc -> {
                    EditBox fallbackSearchBox = (EditBox) Screens.getWidgets(mc.gui.screen()).stream().filter(w -> w instanceof EditBox).findFirst().orElseThrow();
                    double unit = mc.getWindow().getGuiScale();
                    return new double[]{(fallbackSearchBox.getX()+5)*unit,(fallbackSearchBox.getY()+8)*unit};
                });
                ctx.getInput().setCursorPos(search[0],search[1]); ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                ctx.getInput().typeChars("stone"); ctx.waitTick();
                check(searchActive(ctx), "top/fallback search must accept typing");
                ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-double-search-" + scale);
                ctx.tryClickScreenButton("storagehelper.search.clear"); ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            }
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); ctx.waitForScreen(null);
		}
	}

	// ------------------------------------------------------------------ helpers

    private static void verifyControls(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
            var a = (com.storagehelper.mixin.AbstractContainerScreenAccessor)screen;
            var actions = Screens.getWidgets(screen).stream()
                    .filter(w -> w instanceof com.storagehelper.gui.IconButton icon && icon.icon() != com.storagehelper.gui.IconButton.Icon.CLEAR).toList();
            check(actions.size() == 3, "all three action icons must remain available");
            for (int i = 1; i < actions.size(); i++) {
                check(actions.get(i).getX() == actions.get(0).getX(), "action icons must form one vertical column");
                check(actions.get(i).getY() == actions.get(0).getY() + i * 22, "action icons must have consistent vertical spacing");
            }
            check(Screens.getWidgets(screen).stream().noneMatch(w -> w instanceof net.minecraft.client.gui.components.MultiLineTextWidget), "operation-status widget must be absent");
            for (var widget : Screens.getWidgets(screen)) {
                if (!(widget instanceof com.storagehelper.gui.IconButton) && !(widget instanceof EditBox)) continue;
                check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX()+widget.getWidth() <= screen.width && widget.getY()+widget.getHeight() <= screen.height, "control is off-screen");
                boolean outside = widget.getX()+widget.getWidth() <= a.storagehelper$getLeftPos()
                        || widget.getX() >= a.storagehelper$getLeftPos()+a.storagehelper$getImageWidth()
                        || widget.getY()+widget.getHeight() <= a.storagehelper$getTopPos();
                check(outside, "control overlaps a title or inventory slot");
                if (widget instanceof com.storagehelper.gui.IconButton) {
                    check(widget.getWidth()==20 && widget.getHeight()==20, "icon hitboxes must stay usable");
                    check(!widget.getMessage().getString().isEmpty(), "icon must have a narration label");
                }
            }
        });
    }
    private static int findSlot(ClientGameTestContext ctx, String id) {
        return ctx.computeOnClient(mc -> {
            for (Slot slot : mc.player.containerMenu.slots)
                if (!slot.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString().equals(id)) return slot.index;
            throw new AssertionError("missing slot: " + id);
        });
    }
    private static void moveToSlot(ClientGameTestContext ctx, int index) {
        double[] point = ctx.computeOnClient(mc -> {
            var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
            var a = (com.storagehelper.mixin.AbstractContainerScreenAccessor)screen;
            Slot slot = screen.getMenu().getSlot(index);
            double scale = mc.getWindow().getGuiScale();
            return new double[]{(a.storagehelper$getLeftPos() + slot.x + 8) * scale, (a.storagehelper$getTopPos() + slot.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(point[0], point[1]);
        ctx.waitTick();
    }

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
			for (int i = 0; i < com.storagehelper.chest.ChestSnapshots.chestSlotCount(s.menu()); i++) {
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
