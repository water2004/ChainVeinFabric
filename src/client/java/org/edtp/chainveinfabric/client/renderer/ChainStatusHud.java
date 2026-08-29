package org.edtp.chainveinfabric.client.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi.ClientMiningProgress;
import org.jetbrains.annotations.Nullable;

/** Renders the normal top-center ChainVein status without occupying the crosshair area. */
public final class ChainStatusHud {
    private static final int TOP = 5;
    private static final int SIDE_MARGIN = 6;
    private static final int MIN_BAR_WIDTH = 48;
    private static final int MAX_BAR_WIDTH = 96;

    private ChainStatusHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font,
                              @Nullable ClientMiningProgress progress) {
        int screenWidth = graphics.guiWidth();
        if (screenWidth <= 0) return;

        Component text = progress != null
                ? Component.translatable(
                        "hud.chainveinfabric.clientMine.progress",
                        progress.currentBlock(), progress.totalBlocks())
                : Component.translatable("hud.chainveinfabric.active");
        text = fitText(font, text, Math.max(1, screenWidth - SIDE_MARGIN * 2), progress);
        graphics.centeredText(font, text, screenWidth / 2, TOP, 0xFFFF402F);

        if (progress == null || screenWidth <= SIDE_MARGIN * 2) return;

        int availableWidth = screenWidth - SIDE_MARGIN * 2;
        int preferredWidth = Math.max(MIN_BAR_WIDTH, font.width(text));
        int barWidth = Math.min(availableWidth, Math.min(MAX_BAR_WIDTH, preferredWidth));
        int left = (screenWidth - barWidth) / 2;
        drawProgressBar(graphics, left, left + barWidth, TOP + font.lineHeight + 1,
                progress.currentBlockProgress(), 0xFFFF4E36);
    }

    static void drawProgressBar(GuiGraphicsExtractor graphics, int left, int right,
                                int top, float progress, int fillColor) {
        if (right <= left) return;

        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        graphics.fill(left, top, right, top + 3, 0xA0000000);
        int filled = Math.round((right - left) * clamped);
        if (filled > 0) {
            graphics.fill(left, top, left + filled, top + 3, fillColor);
        }
    }

    private static Component fitText(Font font, Component text, int maxWidth,
                                     @Nullable ClientMiningProgress progress) {
        if (font.width(text) <= maxWidth) return text;

        if (progress != null) {
            Component compact = Component.literal(
                    progress.currentBlock() + "/" + progress.totalBlocks());
            if (font.width(compact) <= maxWidth) return compact;
            text = compact;
        }

        String ellipsis = "…";
        int contentWidth = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(text.getString(), contentWidth) + ellipsis);
    }
}
