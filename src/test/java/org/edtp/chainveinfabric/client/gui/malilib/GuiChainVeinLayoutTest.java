package org.edtp.chainveinfabric.client.gui.malilib;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiChainVeinLayoutTest {
    @Test
    void headerUsesNormalCompactAndCollapsedFormsWithoutAddingRows() {
        GuiChainVeinLayout.HeaderLayout normal = GuiChainVeinLayout.header(
                1000, 80, List.of(70, 90, 80, 80));
        assertFalse(normal.hideTitle);
        assertFalse(normal.collapsed);
        assertEquals(40, normal.contentY);

        GuiChainVeinLayout.HeaderLayout compact = GuiChainVeinLayout.header(
                600, 200, List.of(70, 90, 80, 80));
        assertTrue(compact.hideTitle);
        assertFalse(compact.collapsed);
        assertEquals(40, compact.contentY);

        GuiChainVeinLayout.HeaderLayout collapsed = GuiChainVeinLayout.header(
                300, 80, List.of(70, 90, 80, 80));
        assertTrue(collapsed.hideTitle);
        assertTrue(collapsed.collapsed);
        assertEquals(40, collapsed.contentY);
    }

    @Test
    void narrowBasicControlsStayOnOneRowInsideThePageMargins() {
        GuiChainVeinLayout.BasicLayout layout = GuiChainVeinLayout.basic(
                320, 240, 40, true, true,
                List.of(120, 90, 60, 90, 50));

        List<GuiChainVeinLayout.Rect> controls = List.of(
                layout.mode, layout.outline, layout.importButton,
                layout.renderLayer, layout.toggle);
        int previousRight = GuiChainVeinLayout.PAGE_MARGIN;
        for (GuiChainVeinLayout.Rect control : controls) {
            assertEquals(40, control.y);
            assertTrue(control.width > 0);
            assertTrue(control.x >= previousRight);
            assertTrue(control.x + control.width <= 320 - GuiChainVeinLayout.PAGE_MARGIN);
            previousRight = control.x + control.width;
        }
        assertEquals(GuiChainVeinLayout.ControlDensity.ICON, layout.controlDensity);
        assertTrue(layout.singlePane);
        assertTrue(layout.leftList.height > 0);
    }

    @Test
    void wideBasicLayoutKeepsBothListsVisible() {
        GuiChainVeinLayout.BasicLayout layout = GuiChainVeinLayout.basic(
                900, 500, 40, false, false, List.of(160, 100, 50));

        assertEquals(GuiChainVeinLayout.ControlDensity.FULL, layout.controlDensity);
        assertFalse(layout.singlePane);
        assertTrue(layout.leftList.width > 0);
        assertTrue(layout.rightList.width > 0);
        assertTrue(layout.leftList.x + layout.leftList.width < layout.rightList.x);
        assertFalse(layout.importButton.isVisible());
        assertFalse(layout.renderLayer.isVisible());
    }
}
