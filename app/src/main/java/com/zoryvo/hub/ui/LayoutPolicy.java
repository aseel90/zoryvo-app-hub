package com.zoryvo.hub.ui;

public final class LayoutPolicy {
    private LayoutPolicy() {}

    public static boolean useGrid(String mode, int widthDp, boolean television) {
        if ("grid".equals(mode)) return true;
        if ("list".equals(mode)) return false;
        return television || widthDp >= 600;
    }

    public static int sidePaddingDp(int widthDp) {
        if (widthDp >= 1200) return 32;
        if (widthDp >= 700) return 24;
        return 14;
    }

    public static int cardGapDp(int widthDp) {
        return widthDp >= 700 ? 12 : 8;
    }

    public static int gridColumns(int widthDp, int sidePaddingDp) {
        int gap = cardGapDp(widthDp);
        int minCard = widthDp >= 600 ? 190 : 160;
        int usable = Math.max(1, widthDp - (sidePaddingDp * 2));
        int columns = Math.max(1, (usable + gap) / (minCard + gap));
        return Math.min(5, columns);
    }

    public static int cardWidthDp(int widthDp, int sidePaddingDp, int columns) {
        int safeColumns = Math.max(1, columns);
        int gap = cardGapDp(widthDp);
        int usable = Math.max(1, widthDp - (sidePaddingDp * 2) - (gap * (safeColumns - 1)));
        return Math.max(1, usable / safeColumns);
    }

    public static int leftIndex(int index, int count, int columns) {
        if (!valid(index, count) || columns <= 0) return -1;
        return index % columns > 0 ? index - 1 : -1;
    }

    public static int rightIndex(int index, int count, int columns) {
        if (!valid(index, count) || columns <= 0) return -1;
        int next = index + 1;
        return index % columns < columns - 1 && next < count ? next : -1;
    }

    public static int upIndex(int index, int count, int columns) {
        if (!valid(index, count) || columns <= 0) return -1;
        int target = index - columns;
        return target >= 0 ? target : -1;
    }

    public static int downIndex(int index, int count, int columns) {
        if (!valid(index, count) || columns <= 0) return -1;
        int target = index + columns;
        return target < count ? target : -1;
    }

    public static int toolbarTargetForColumn(int column, int columns) {
        if (columns <= 1) return 1;
        return column >= (columns / 2) ? 1 : 0;
    }

    private static boolean valid(int index, int count) {
        return count > 0 && index >= 0 && index < count;
    }
}