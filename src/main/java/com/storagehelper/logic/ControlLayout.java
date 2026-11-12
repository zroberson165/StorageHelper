package com.storagehelper.logic;

/** Top search row with a vertical side toolbar; short screens move search to the side. */
public record ControlLayout(int searchX, int searchY, int searchWidth, int toolsX, int toolsY, int toolsWidth, boolean above) {
    public static final int BUTTON = 20, STEP = 22;
    public static ControlLayout place(int screenWidth, int screenHeight, int left, int top, int guiWidth) {
        int rightSpace = screenWidth - left - guiWidth - 8, leftSpace = left - 8;
        boolean right = rightSpace >= 64 || rightSpace >= leftSpace;
        int width = Math.max(64, Math.min(104, right ? rightSpace : leftSpace));
        int sideX = right ? left + guiWidth + 4 : left - width - 4;
        sideX = Math.max(2, Math.min(sideX, screenWidth - width - 2));
        boolean above = top >= 24;
        int sy = above ? top - 24 : Math.max(2, top);
        int toolsY = above ? top : sy + STEP;
        return new ControlLayout(above ? left : sideX, sy, above ? guiWidth : width,
                sideX, toolsY, width, above);
    }
}
