package com.storagehelper.gui;

import com.storagehelper.chest.ChestSession;
import com.storagehelper.chest.ChestSessions;
import com.storagehelper.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import java.util.ArrayList;
import java.util.List;

/** Read-only shulker tooltips and a screen-local bundle popup. No container data is edited. */
public final class HoverContents {
    private static AbstractContainerScreen<?> owner;
    private static Slot source;
    private static ItemStack snapshot = ItemStack.EMPTY;
    private static int x, y, width, height, rows, columns, firstRow;
    private static long lastInside;
    private static double wheel;
    private static Screen releaseOwner;
    private static final int CELL = 20, HEADER = 18, GRACE_MS = 300;
    private HoverContents() {}

    public static void reset(Screen screen) {
        if (owner == screen) { owner = null; source = null; snapshot = ItemStack.EMPTY; wheel = 0; }
        if (releaseOwner == screen) releaseOwner = null;
    }
    private static boolean supported(AbstractContainerScreen<?> screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || ChestSessions.forScreen(screen) != null;
    }
    private static boolean inside(double mx, double my) {
        return source != null && mx >= x && mx < x + width && my >= y && my < y + height;
    }
    public static boolean pointerInside(AbstractContainerScreen<?> screen) {
        var mc = Minecraft.getInstance();
        return owner == screen && inside(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }
    private static boolean valid(AbstractContainerScreen<?> screen) {
        var mc = Minecraft.getInstance();
        return owner == screen && source != null && mc.player != null && mc.gui.screen() == screen
                && source.isActive() && ItemStack.matches(snapshot, source.getItem());
    }
    private static void open(AbstractContainerScreen<?> screen, Slot slot) {
        owner = screen; source = slot; snapshot = slot.getItem().copy(); firstRow = 0; wheel = 0;
        columns = Math.max(1, Math.min(6, (screen.width - 20) / CELL));
        rows = Math.max(1, Math.min(3, (screen.height - 90) / CELL));
        width = columns * CELL + 8;
        height = HEADER + rows * CELL + 30;
        var a = (AbstractContainerScreenAccessor) screen;
        int sx = a.storagehelper$getLeftPos() + slot.x, sy = a.storagehelper$getTopPos() + slot.y;
        x = sx + 20;
        if (x + width > screen.width - 4) x = sx - width - 4;
        x = Math.max(4, Math.min(x, screen.width - width - 4));
        y = Math.max(4, Math.min(sy, screen.height - height - 4));
        lastInside = System.currentTimeMillis();
    }
    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my) {
        if (!supported(screen)) return false;
        var mc = Minecraft.getInstance();
        Slot hovered = ScreenSlots.at(screen, mx, my);
        if (!valid(screen)) reset(owner);
        if (source != null && owner == screen) {
            if (hovered == source || inside(mx, my)) lastInside = System.currentTimeMillis();
            else if (System.currentTimeMillis() - lastInside > GRACE_MS) reset(screen);
        }
        if (source == null && hovered != null && hovered.getItem().is(ItemTags.BUNDLES)) open(screen, hovered);
        if (owner == screen && source != null) {
            drawBundle(screen, graphics, mx, my);
            // The popup replaces the vanilla bundle image; suppress underlying tooltips too.
            return true;
        }
        if (hovered != null && hovered.getItem().getItem() instanceof BlockItem block
                && block.getBlock() instanceof ShulkerBoxBlock && screen.getMenu().getCarried().isEmpty()) {
            ItemStack item = hovered.getItem();
            ItemStack textOnly = item.copy();
            textOnly.remove(DataComponents.CONTAINER);
            List<ClientTooltipComponent> lines = new ArrayList<>();
            for (Component line : Screen.getTooltipFromItem(mc, textOnly)) {
                for (var wrapped : mc.font.split(line, Math.max(162, Math.min(240, screen.width - 24))))
                    lines.add(ClientTooltipComponent.create(wrapped));
            }
            lines.add(new ShulkerGrid(item.get(DataComponents.CONTAINER)));
            graphics.nextStratum();
            graphics.tooltip(mc.font, lines, mx, my, DefaultTooltipPositioner.INSTANCE, item.get(DataComponents.TOOLTIP_STYLE), true);
            return true;
        }
        return false;
    }
    private static List<ItemStack> items() {
        BundleContents contents = source.getItem().get(DataComponents.BUNDLE_CONTENTS);
        return contents == null ? List.of() : contents.itemCopies().toList();
    }
    private static int entryAt(double mx, double my) {
        if (!inside(mx, my) || mx < x + 4 || mx >= x + 4 + columns * CELL || my < y + HEADER || my >= y + HEADER + rows * CELL) return -1;
        return (firstRow + (int)(my - y - HEADER) / CELL) * columns + (int)(mx - x - 4) / CELL;
    }
    private static void drawBundle(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mx, int my) {
        var mc = Minecraft.getInstance();
        List<ItemStack> items = items();
        int selected = BundleItem.getSelectedItemIndex(source.getItem());
        int hovered = entryAt(mx, my);
        g.nextStratum();
        g.fill(x, y, x + width, y + height, 0xF0181228);
        g.text(mc.font, mc.font.plainSubstrByWidth(source.getItem().getHoverName().getString(), width - 8), x + 4, y + 4, -1, false);
        for (int cell = 0; cell < rows * columns; cell++) {
            int index = firstRow * columns + cell;
            int cx = x + 4 + cell % columns * CELL, cy = y + HEADER + cell / columns * CELL;
            g.fill(cx, cy, cx + 19, cy + 19, index == hovered ? 0xFF8A7840 : index == selected ? 0xFF555577 : 0xFF332D40);
            if (index < items.size()) {
                g.item(items.get(index), cx + 1, cy + 1);
                g.itemDecorations(mc.font, items.get(index), cx + 1, cy + 1);
            }
        }
        Component footer = Component.translatable(items.isEmpty() ? "storagehelper.bundle.empty" : "storagehelper.bundle.hint");
        var wrapped = mc.font.split(footer, width - 8);
        for (int i = 0; i < Math.min(2, wrapped.size()); i++) g.text(mc.font, wrapped.get(i), x + 4, y + HEADER + rows * CELL + 3 + i * 10, -1, false);
        if (hovered >= 0 && hovered < items.size()) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(mc, items.get(hovered)));
            tooltip.add(Component.translatable(screen instanceof CreativeModeInventoryScreen ? "storagehelper.bundle.readonly"
                    : hovered < BundleItem.getNumberOfItemsToShow(source.getItem()) ? "storagehelper.bundle.extract" : "storagehelper.bundle.limited"));
            if (!screen.getMenu().getCarried().isEmpty()) tooltip.add(Component.translatable("storagehelper.status.cursor_not_empty"));
            var lines = tooltip.stream().flatMap(c -> mc.font.split(c, Math.max(80, Math.min(240, screen.width - 24))).stream()).toList();
            g.setTooltipForNextFrame(mc.font, lines, DefaultTooltipPositioner.INSTANCE, mx, my, true);
        } else if (ScreenSlots.at(screen, mx, my) == source) {
            // Keep lore, capacity, names, registry IDs, and other ordinary tooltip text.
            g.tooltip(mc.font, Screen.getTooltipFromItem(mc, source.getItem()).stream()
                    .map(c -> ClientTooltipComponent.create(c.getVisualOrderText())).toList(),
                    x, y + height + 12, DefaultTooltipPositioner.INSTANCE, source.getItem().get(DataComponents.TOOLTIP_STYLE), true);
        }
    }
    public static boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (owner != screen || !inside(event.x(), event.y())) return false;
        releaseOwner = screen;
        if (!valid(screen)) { reset(screen); return true; }
        var mc = Minecraft.getInstance();
        ChestSession session = ChestSessions.forScreen(screen);
        int index = entryAt(event.x(), event.y());
        if (event.button() != 1 || index < 0 || index >= BundleItem.getNumberOfItemsToShow(source.getItem())
                || !screen.getMenu().getCarried().isEmpty() || session != null && (session.isOperationRunning() || session.isSearchFocused())
                || mc.gameMode == null || mc.getConnection() == null || screen instanceof CreativeModeInventoryScreen
                || mc.player.containerMenu != screen.getMenu() || mc.player.isSpectator() || !source.allowModification(mc.player)) return true;
        // Match vanilla's prediction plus its vanilla selection packet. The following click
        // performs actual extraction on the server; never remove data locally ourselves.
        if (BundleItem.getSelectedItemIndex(source.getItem()) != index) {
            BundleItem.toggleSelectedItem(source.getItem(), index);
            mc.getConnection().send(new ServerboundSelectBundleItemPacket(source.index, index));
        }
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, source.index, 1, ContainerInput.PICKUP, mc.player);
        reset(screen);
        releaseOwner = screen;
        return true;
    }
    public static boolean release(AbstractContainerScreen<?> screen) { boolean result = releaseOwner == screen; if (result) releaseOwner = null; return result; }
    public static boolean scroll(AbstractContainerScreen<?> screen, double mx, double my, double delta) {
        if (owner != screen || !inside(mx, my)) return false;
        if (!valid(screen)) { reset(screen); return true; }
        if (!Double.isFinite(delta)) return true;
        wheel += Math.max(-8, Math.min(8, delta));
        int steps = (int) wheel; wheel -= steps;
        int max = Math.max(0, (items().size() + columns - 1) / columns - rows);
        firstRow = Math.max(0, Math.min(max, firstRow - steps));
        lastInside = System.currentTimeMillis();
        return true;
    }
    private static final class ShulkerGrid implements ClientTooltipComponent {
        private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        private final ItemContainerContents contents;
        ShulkerGrid(ItemContainerContents contents) { this.contents = contents; if (contents != null) contents.copyInto(items); }
        private Component message() {
            return contents == null ? Component.translatable("storagehelper.preview.unavailable")
                    : items.stream().allMatch(ItemStack::isEmpty) ? Component.translatable("storagehelper.preview.empty") : null;
        }
        @Override public int getHeight(Font font) { return 58 + (message() == null ? 0 : font.split(message(), 162).size() * 9); }
        @Override public int getWidth(Font font) { return 162; }
        @Override public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
            for (int i = 0; i < 27; i++) {
                int cx = x + i % 9 * 18, cy = y + i / 9 * 18;
                g.fill(cx, cy, cx + 17, cy + 17, 0xFF383040);
                g.item(items.get(i), cx, cy);
                g.itemDecorations(font, items.get(i), cx, cy);
            }
            if (message() != null)
                for (var line : font.split(message(), 162)) {
                    g.text(font, line, x, y + 56, -1, false); y += 9;
                }
        }
    }
}
