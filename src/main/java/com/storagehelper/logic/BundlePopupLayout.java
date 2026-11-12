package com.storagehelper.logic;

/** Compact GUI-space popup geometry; only the tiny gap is a valid transition region. */
public record BundlePopupLayout(int x, int y, int width, int height, int columns, int rows, int sourceX, int sourceY) {
    public static final int CELL = 18, HEADER = 12, PAD = 2;
    public static BundlePopupLayout place(int screenWidth, int screenHeight, int sx, int sy, int count, int nameWidth) {
        int columns = Math.max(1, Math.min(Math.min(6, Math.max(1, count)), (screenWidth - 12) / CELL));
        int rows = count == 0 ? 0 : Math.min((count + columns - 1) / columns, Math.max(1, Math.min(3, (screenHeight - 28) / CELL)));
        int width = Math.max(count == 0 ? 72 : columns * CELL + PAD * 2, Math.min(112, nameWidth + PAD * 2));
        int height = HEADER + (count == 0 ? 12 : rows * CELL) + PAD * 2;
        int x = sx + 18;
        if (x + width > screenWidth - 4) x = sx - width - 2;
        x = Math.max(4, Math.min(x, screenWidth - width - 4));
        int y = Math.max(4, Math.min(sy - 1, screenHeight - height - 4));
        return new BundlePopupLayout(x, y, width, height, columns, rows, sx, sy);
    }
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
    public boolean transitionContains(double mx, double my) {
        int left = x >= sourceX + 17 ? sourceX + 17 : x + width;
        int right = x >= sourceX + 17 ? x : sourceX - 1;
        return right - left <= 4 && mx >= left && mx < right
                && my >= Math.max(y, sourceY - 1) && my < Math.min(y + height, sourceY + 17);
    }
    public int entryAt(double mx, double my, int firstRow) {
        if (!contains(mx, my) || mx < x + PAD || mx >= x + PAD + columns * CELL
                || my < y + HEADER || my >= y + HEADER + rows * CELL) return -1;
        return (firstRow + (int)(my - y - HEADER) / CELL) * columns + (int)(mx - x - PAD) / CELL;
    }
}
