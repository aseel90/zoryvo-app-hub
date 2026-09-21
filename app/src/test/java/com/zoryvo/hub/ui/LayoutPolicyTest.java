package com.zoryvo.hub.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LayoutPolicyTest {
    @Test
    public void autoModeUsesListOnPhonesAndGridOnLargeScreens() {
        assertFalse(LayoutPolicy.useGrid("auto", 360, false));
        assertFalse(LayoutPolicy.useGrid("auto", 599, false));
        assertTrue(LayoutPolicy.useGrid("auto", 600, false));
        assertTrue(LayoutPolicy.useGrid("auto", 540, true));
    }

    @Test
    public void explicitModeAlwaysWins() {
        assertTrue(LayoutPolicy.useGrid("grid", 320, false));
        assertFalse(LayoutPolicy.useGrid("list", 1600, true));
    }

    @Test
    public void gridNeverOverpacksNarrowWindows() {
        int pad320 = LayoutPolicy.sidePaddingDp(320);
        assertEquals(1, LayoutPolicy.gridColumns(320, pad320));

        int pad360 = LayoutPolicy.sidePaddingDp(360);
        assertEquals(2, LayoutPolicy.gridColumns(360, pad360));

        int pad600 = LayoutPolicy.sidePaddingDp(600);
        assertEquals(2, LayoutPolicy.gridColumns(600, pad600));

        int pad960 = LayoutPolicy.sidePaddingDp(960);
        assertEquals(4, LayoutPolicy.gridColumns(960, pad960));
    }

    @Test
    public void gridNeighborsStayInsideTheirRowsAndColumns() {
        int count = 7;
        int columns = 3;

        assertEquals(-1, LayoutPolicy.leftIndex(0, count, columns));
        assertEquals(0, LayoutPolicy.leftIndex(1, count, columns));
        assertEquals(-1, LayoutPolicy.rightIndex(2, count, columns));
        assertEquals(4, LayoutPolicy.rightIndex(3, count, columns));
        assertEquals(1, LayoutPolicy.upIndex(4, count, columns));
        assertEquals(6, LayoutPolicy.downIndex(3, count, columns));
        assertEquals(-1, LayoutPolicy.downIndex(4, count, columns));
    }

    @Test
    public void cardWidthFitsWindowBudget() {
        int width = 960;
        int padding = LayoutPolicy.sidePaddingDp(width);
        int columns = LayoutPolicy.gridColumns(width, padding);
        int card = LayoutPolicy.cardWidthDp(width, padding, columns);
        int gaps = LayoutPolicy.cardGapDp(width) * (columns - 1);

        assertTrue((card * columns) + gaps + (padding * 2) <= width);
    }
}