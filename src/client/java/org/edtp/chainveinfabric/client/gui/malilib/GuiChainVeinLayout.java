package org.edtp.chainveinfabric.client.gui.malilib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure responsive layout calculations for the ChainVein configuration screen. */
final class GuiChainVeinLayout {
    static final int PAGE_MARGIN = 12;
    static final int BASIC_BODY_MAX_WIDTH = 400;
    static final int CONTROL_HEIGHT = 20;
    static final int CONTROL_GAP = 5;

    private static final int BASIC_MAX_WIDTH = 445;
    private static final int DUAL_LIST_MIN_WIDTH = 360;
    private static final int HEADER_TAB_Y = 10;
    private static final int HEADER_CONTENT_Y = 40;
    private static final int HEADER_TAB_GAP = 10;
    private static final int COMPACT_HEADER_TAB_GAP = 4;
    private static final int CONFIG_SWITCHER_WIDTH = 155;
    private static final int COLLAPSED_TAB_WIDTH = 130;

    private GuiChainVeinLayout() {
    }

    static HeaderLayout header(int screenWidth, int titleWidth, List<Integer> tabWidths) {
        int buttonWidth = tabWidths.stream().mapToInt(Integer::intValue).sum();
        int titleRight = 20 + titleWidth + 12;
        int switcherLeft = screenWidth - CONFIG_SWITCHER_WIDTH - 8;
        int firstRowWidth = Math.max(0, switcherLeft - titleRight);
        int normalWidth = buttonWidth + HEADER_TAB_GAP * Math.max(0, tabWidths.size() - 1);

        if (normalWidth <= firstRowWidth) {
            return new HeaderLayout(titleRight, HEADER_TAB_Y, firstRowWidth,
                    HEADER_TAB_GAP, HEADER_CONTENT_Y, false, false);
        }

        int compactRowWidth = Math.max(0, switcherLeft - PAGE_MARGIN);
        int compactWidth = buttonWidth + COMPACT_HEADER_TAB_GAP * Math.max(0, tabWidths.size() - 1);
        if (compactWidth <= compactRowWidth) {
            return new HeaderLayout(PAGE_MARGIN, HEADER_TAB_Y, compactRowWidth,
                    COMPACT_HEADER_TAB_GAP, HEADER_CONTENT_Y, true, false);
        }

        int dropdownWidth = Math.min(COLLAPSED_TAB_WIDTH, compactRowWidth);
        return new HeaderLayout(
                PAGE_MARGIN + Math.max(0, (compactRowWidth - dropdownWidth) / 2),
                HEADER_TAB_Y, Math.max(1, dropdownWidth), 0,
                HEADER_CONTENT_Y, true, true);
    }

    static BasicLayout basic(int screenWidth, int screenHeight, int topY,
                             boolean showImport, boolean showRenderLayer,
                             List<Integer> compactWidths) {
        int contentWidth = Math.max(1, Math.min(BASIC_MAX_WIDTH, screenWidth - PAGE_MARGIN * 2));
        int contentX = (screenWidth - contentWidth) / 2;

        List<Integer> fullWidths = fullControlWidths(showImport, showRenderLayer);
        ControlDensity density;
        List<Integer> controlWidths;
        if (rowWidth(fullWidths) <= contentWidth) {
            density = ControlDensity.FULL;
            controlWidths = fullWidths;
        } else if (rowWidth(compactWidths) <= contentWidth) {
            density = ControlDensity.COMPACT;
            controlWidths = compactWidths;
        } else {
            density = ControlDensity.ICON;
            controlWidths = iconControlWidths(contentWidth, showImport, showRenderLayer);
        }

        Rect[] row = centeredRow(contentX, topY, contentWidth, controlWidths);
        int index = 0;
        Rect mode = row[index++];
        Rect outline = row[index++];
        Rect importButton = showImport ? row[index++] : Rect.hidden();
        Rect renderLayer = showRenderLayer ? row[index++] : Rect.hidden();
        Rect toggle = row[index];

        int bodyWidth = Math.min(BASIC_BODY_MAX_WIDTH, contentWidth);
        int bodyX = (screenWidth - bodyWidth) / 2;
        boolean singlePane = bodyWidth < DUAL_LIST_MIN_WIDTH;
        Rect availablePane = Rect.hidden();
        Rect whitelistPane = Rect.hidden();
        int searchY = topY + CONTROL_HEIGHT + 10;
        int titleY = -1;
        int listY;

        if (singlePane) {
            int leftPaneWidth = (bodyWidth - CONTROL_GAP) / 2;
            int rightPaneWidth = bodyWidth - CONTROL_GAP - leftPaneWidth;
            availablePane = new Rect(bodyX, searchY, leftPaneWidth, CONTROL_HEIGHT);
            whitelistPane = new Rect(
                    bodyX + leftPaneWidth + CONTROL_GAP, searchY,
                    rightPaneWidth, CONTROL_HEIGHT);
            searchY += CONTROL_HEIGHT + CONTROL_GAP;
            listY = searchY + CONTROL_HEIGHT + CONTROL_GAP;
        } else {
            titleY = searchY + CONTROL_HEIGHT + 5;
            listY = titleY + 12;
        }

        Rect search = new Rect(bodyX, searchY, bodyWidth, CONTROL_HEIGHT);
        int listHeight = Math.max(CONTROL_HEIGHT, screenHeight - listY - 20);
        Rect leftList;
        Rect rightList;
        if (singlePane) {
            leftList = new Rect(bodyX, listY, bodyWidth, listHeight);
            rightList = leftList;
        } else {
            int leftWidth = (bodyWidth - CONTROL_GAP) / 2;
            int rightWidth = bodyWidth - CONTROL_GAP - leftWidth;
            leftList = new Rect(bodyX, listY, leftWidth, listHeight);
            rightList = new Rect(
                    bodyX + leftWidth + CONTROL_GAP, listY,
                    rightWidth, listHeight);
        }

        return new BasicLayout(
                mode, toggle, outline, importButton, renderLayer,
                availablePane, whitelistPane, search, leftList, rightList,
                titleY, singlePane, density);
    }

    private static List<Integer> fullControlWidths(boolean showImport, boolean showRenderLayer) {
        List<Integer> widths = new ArrayList<>();
        widths.add(170);
        widths.add(80);
        if (showImport) widths.add(50);
        if (showRenderLayer) widths.add(85);
        widths.add(40);
        return widths;
    }

    private static List<Integer> iconControlWidths(int contentWidth,
                                                   boolean showImport,
                                                   boolean showRenderLayer) {
        List<Integer> widths = new ArrayList<>();
        int fixedWidth = 28 + 40;
        int fixedCount = 2;
        if (showImport) {
            fixedWidth += 28;
            fixedCount++;
        }
        if (showRenderLayer) {
            fixedWidth += 28;
            fixedCount++;
        }
        int gapWidth = fixedCount * CONTROL_GAP;
        widths.add(Math.max(40, contentWidth - fixedWidth - gapWidth));
        widths.add(28);
        if (showImport) widths.add(28);
        if (showRenderLayer) widths.add(28);
        widths.add(40);
        return widths;
    }

    private static int rowWidth(List<Integer> widths) {
        return widths.stream().mapToInt(Integer::intValue).sum()
                + Math.max(0, widths.size() - 1) * CONTROL_GAP;
    }

    private static Rect[] centeredRow(int contentX, int y, int contentWidth,
                                      List<Integer> preferredWidths) {
        int count = preferredWidths.size();
        int gapWidth = Math.max(0, count - 1) * CONTROL_GAP;
        int availableForControls = Math.max(count, contentWidth - gapWidth);
        int preferredTotal = preferredWidths.stream().mapToInt(Integer::intValue).sum();
        int[] widths = new int[count];
        int assigned = 0;

        for (int i = 0; i < count; i++) {
            int width = preferredWidths.get(i);
            if (preferredTotal > availableForControls) {
                width = Math.max(1, width * availableForControls / preferredTotal);
            }
            widths[i] = width;
            assigned += width;
        }
        if (assigned < availableForControls && preferredTotal > availableForControls) {
            widths[0] += availableForControls - assigned;
        }

        int actualWidth = Arrays.stream(widths).sum() + gapWidth;
        int x = contentX + Math.max(0, (contentWidth - actualWidth) / 2);
        Rect[] row = new Rect[count];
        for (int i = 0; i < count; i++) {
            row[i] = new Rect(x, y, widths[i], CONTROL_HEIGHT);
            x += widths[i] + CONTROL_GAP;
        }
        return row;
    }

    enum ControlDensity { FULL, COMPACT, ICON }

    static final class Rect {
        final int x;
        final int y;
        final int width;
        final int height;

        Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        static Rect hidden() {
            return new Rect(0, 0, 0, 0);
        }

        boolean isVisible() {
            return this.width > 0 && this.height > 0;
        }
    }

    static final class BasicLayout {
        final Rect mode;
        final Rect toggle;
        final Rect outline;
        final Rect importButton;
        final Rect renderLayer;
        final Rect availablePane;
        final Rect whitelistPane;
        final Rect search;
        final Rect leftList;
        final Rect rightList;
        final int titleY;
        final boolean singlePane;
        final ControlDensity controlDensity;

        BasicLayout(Rect mode, Rect toggle, Rect outline, Rect importButton,
                    Rect renderLayer, Rect availablePane, Rect whitelistPane,
                    Rect search, Rect leftList, Rect rightList, int titleY,
                    boolean singlePane, ControlDensity controlDensity) {
            this.mode = mode;
            this.toggle = toggle;
            this.outline = outline;
            this.importButton = importButton;
            this.renderLayer = renderLayer;
            this.availablePane = availablePane;
            this.whitelistPane = whitelistPane;
            this.search = search;
            this.leftList = leftList;
            this.rightList = rightList;
            this.titleY = titleY;
            this.singlePane = singlePane;
            this.controlDensity = controlDensity;
        }
    }

    static final class HeaderLayout {
        final int tabX;
        final int tabY;
        final int tabWidth;
        final int tabGap;
        final int contentY;
        final boolean hideTitle;
        final boolean collapsed;

        HeaderLayout(int tabX, int tabY, int tabWidth, int tabGap,
                     int contentY, boolean hideTitle, boolean collapsed) {
            this.tabX = tabX;
            this.tabY = tabY;
            this.tabWidth = tabWidth;
            this.tabGap = tabGap;
            this.contentY = contentY;
            this.hideTitle = hideTitle;
            this.collapsed = collapsed;
        }
    }
}
