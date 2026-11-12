package com.storagehelper.gui;

import com.storagehelper.chest.ChestSession;
import com.storagehelper.logic.BundlePopupLayout;
import static com.storagehelper.logic.BundlePopupLayout.*;
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
    private static BundlePopupLayout layout;
    private static List<ItemStack> cachedItems = List.of();
    private static double wheel;
    private static Screen releaseOwner;

    private HoverContents() {}

    public static void reset(Screen screen) {
        if (owner == screen) { owner = null; source = null; snapshot = ItemStack.EMPTY; wheel = 0; cachedItems = List.of(); layout = null; }
        if (releaseOwner == screen) releaseOwner = null;
    }
    private static boolean supported(AbstractContainerScreen<?> screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || ChestSessions.forScreen(screen) != null;
    }
    private static boolean inside(double mx, double my) {
        return source != null && layout != null && layout.contains(mx, my);
    }
    public static boolean pointerInside(AbstractContainerScreen<?> screen) {
        var mc = Minecraft.getInstance();
        return owner == screen && inside(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }
    /** Current presentation for screen integration/diagnostics; null when dismissed. */
    public static BundlePopupLayout popupLayout(AbstractContainerScreen<?> screen) {
        return valid(screen) ? layout : null;
    }
    public static int popupSourceIndex(AbstractContainerScreen<?> screen) {
        return valid(screen) ? source.index : -1;
    }
    private static boolean valid(AbstractContainerScreen<?> screen) {
        var mc = Minecraft.getInstance();
        return owner == screen && source != null && mc.player != null && mc.gui.screen() == screen
                && source.isActive() && ItemStack.matches(snapshot, source.getItem());
    }
    private static void open(AbstractContainerScreen<?> screen, Slot slot) {
        owner = screen; source = slot; snapshot = slot.getItem().copy(); firstRow = 0; wheel = 0;
        BundleContents contents = slot.getItem().get(DataComponents.BUNDLE_CONTENTS);
        cachedItems = contents == null ? List.of() : contents.itemCopies().toList();
        var a = (AbstractContainerScreenAccessor) screen;
        int sx = a.storagehelper$getLeftPos() + slot.x, sy = a.storagehelper$getTopPos() + slot.y;
        layout = BundlePopupLayout.place(screen.width, screen.height, sx, sy, cachedItems.size(),
                Minecraft.getInstance().font.width(slot.getItem().getHoverName()));
        x = layout.x(); y = layout.y(); width = layout.width(); height = layout.height();
        columns = layout.columns(); rows = layout.rows();
    }

    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my) {
        if (!supported(screen)) return false;
        var mc = Minecraft.getInstance();
        Slot hovered = ScreenSlots.at(screen, mx, my);
        if (!valid(screen)) reset(owner);
        if (source != null && owner == screen && !inside(mx, my) && hovered != source) {
            // Another actual slot wins immediately. Only the one-pixel corridor can
            // keep a popup alive between its source and its body; no dismissal timer.
            if (!layout.transitionContains(mx, my)) reset(screen);
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
    private static List<ItemStack> items() { return cachedItems; }
    private static int entryAt(double mx, double my) {
        return layout == null ? -1 : layout.entryAt(mx, my, firstRow);
    }
    private static void drawBundle(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mx, int my) {
        var mc = Minecraft.getInstance();
        List<ItemStack> items = items();
        int selected = BundleItem.getSelectedItemIndex(source.getItem());
        int hovered = entryAt(mx, my);
        g.nextStratum();
        g.fill(x, y, x + width, y + height, 0xF0181228);
        Component title = source.getItem().getHoverName();
        int overflow = Math.max(0, mc.font.width(title) - (width - 4));
        int offset = overflow == 0 ? 0 : (int)((Math.sin(net.minecraft.util.Util.getMillis() / 1800.0) + 1) * .5 * overflow);
        g.enableScissor(x + 2, y + 1, x + width - 2, y + HEADER);
        g.text(mc.font, title, x + 2 - offset, y + 2, -1, false);
        g.disableScissor();
        for (int cell = 0; cell < rows * columns; cell++) {
            int index = firstRow * columns + cell;
            int cx = x + PAD + cell % columns * CELL, cy = y + HEADER + cell / columns * CELL;
            if (index < items.size()) {
                g.fill(cx, cy, cx + 18, cy + 18, index == hovered ? 0xFF8A7840 : index == selected ? 0xFF555577 : 0xFF332D40);
                g.item(items.get(index), cx + 1, cy + 1);
                g.itemDecorations(mc.font, items.get(index), cx + 1, cy + 1);
            }
        }
        if (items.isEmpty()) g.text(mc.font, Component.translatable("storagehelper.bundle.empty"), x + PAD, y + HEADER, -1, false);
        if (hovered >= 0 && hovered < items.size()) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(mc, items.get(hovered)));
            tooltip.add(Component.translatable(screen instanceof CreativeModeInventoryScreen ? "storagehelper.bundle.readonly"
                    : hovered < BundleItem.getNumberOfItemsToShow(source.getItem()) ? "storagehelper.bundle.extract" : "storagehelper.bundle.limited"));
            if (!screen.getMenu().getCarried().isEmpty()) tooltip.add(Component.translatable("storagehelper.status.cursor_not_empty"));
            var lines = tooltip.stream().flatMap(c -> mc.font.split(c, Math.max(80, Math.min(240, screen.width - 24))).stream()).toList();
            g.setTooltipForNextFrame(mc.font, lines, DefaultTooltipPositioner.INSTANCE, mx, my, true);
        } else if (ScreenSlots.at(screen, mx, my) == source) {
            // Keep lore, capacity, names, registry IDs, and other ordinary tooltip text.
            var extra = Screen.getTooltipFromItem(mc, source.getItem()).stream().skip(1)
                    .flatMap(c -> mc.font.split(c, Math.min(240, screen.width - 24)).stream())
                    .map(ClientTooltipComponent::create).toList();
            if (!extra.isEmpty()) g.tooltip(mc.font, extra,
                    x, y + height + 12, DefaultTooltipPositioner.INSTANCE, source.getItem().get(DataComponents.TOOLTIP_STYLE), false);
        }
    }
    public static boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (owner != screen || !inside(event.x(), event.y())) return false;
        releaseOwner = screen;
        if (!valid(screen)) { reset(screen); releaseOwner = screen; return true; }
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
