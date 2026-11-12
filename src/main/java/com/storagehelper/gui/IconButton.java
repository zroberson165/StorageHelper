package com.storagehelper.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native button hitboxes, focus, tooltip and narration with game-rendered pixel icons. */
public final class IconButton extends Button {
    public enum Icon { DEPOSIT, SORT, ALPHABETICAL, CATEGORY, CLEAR, CANCEL }
    private Icon icon;
    public IconButton(int x, int y, Component label, Icon icon, OnPress press) {
        super(x, y, 20, 20, label, press, DEFAULT_NARRATION);
        this.icon = icon;
    }
    public void setIcon(Icon icon) { this.icon = icon; }
    public Icon icon() { return icon; }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractDefaultSprite(g);
        boolean pressed = active && isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed();
        int ox = getX() + 3 + (pressed ? 1 : 0), oy = getY() + 3 + (pressed ? 1 : 0);
        int color = active ? 0xFFF2EDD9 : 0xFF777777;
        if (pressed) g.fill(getX() + 2, getY() + 2, getX() + 18, getY() + 18, 0x60443624);
        switch (icon) {
            case DEPOSIT -> {
                rect(g, ox, oy, 1, 7, 2, 6, color); rect(g, ox, oy, 1, 11, 12, 2, color); rect(g, ox, oy, 11, 7, 2, 6, color);
                rect(g, ox, oy, 6, 0, 2, 9, color);
                for (int i = 0; i < 4; i++) rect(g, ox, oy, 3 + i, 5 + i, 8 - i * 2, 1, color);
            }
            case SORT -> {
                for (int i = 0; i < 3; i++) rect(g, ox, oy, 1, 1 + i * 4, 4 + i * 2, 2, color);
                rect(g, ox, oy, 11, 1, 1, 9, color);
                for (int i = 0; i < 3; i++) rect(g, ox, oy, 9 + i, 9 + i, 5 - i * 2, 1, color);
            }
            case ALPHABETICAL -> {
                pixels(g, ox, oy, 0, 0, new String[]{"010", "101", "111", "101", "101"}, color);
                pixels(g, ox, oy, 9, 8, new String[]{"111", "001", "010", "100", "111"}, color);
                rect(g, ox, oy, 5, 5, 3, 2, color);
            }
            case CATEGORY -> {
                int[] colors = active ? new int[]{0xFF91C9EC, 0xFFE8C575, 0xFFA9CE83, 0xFFD898BC} : new int[]{color,color,color,color};
                for (int i = 0; i < 4; i++) rect(g, ox, oy, 1 + i % 2 * 7, 1 + i / 2 * 7, 5, 5, colors[i]);
            }
            case CLEAR -> { for (int i = 0; i < 9; i++) { rect(g, ox, oy, 2 + i, 2 + i, 1, 1, color); rect(g, ox, oy, 10 - i, 2 + i, 1, 1, color); } }
            case CANCEL -> rect(g, ox, oy, 3, 3, 8, 8, active ? 0xFFEEA18B : color);
        }
    }
    private static void rect(GuiGraphicsExtractor g, int x, int y, int dx, int dy, int w, int h, int color) { g.fill(x + dx, y + dy, x + dx + w, y + dy + h, color); }
    private static void pixels(GuiGraphicsExtractor g, int x, int y, int dx, int dy, String[] rows, int color) {
        for (int j = 0; j < rows.length; j++) for (int i = 0; i < rows[j].length(); i++) if (rows[j].charAt(i) == '1') rect(g,x,y,dx+i,dy+j,1,1,color);
    }
}
