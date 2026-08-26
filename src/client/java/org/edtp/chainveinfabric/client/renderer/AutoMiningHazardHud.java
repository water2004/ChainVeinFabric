package org.edtp.chainveinfabric.client.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** A deliberately persistent safety indicator for automatic mining. */
public final class AutoMiningHazardHud {
    private static final int EDGE_THICKNESS = 2;
    private static final int CORNER_THICKNESS = 3;

    private AutoMiningHazardHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) return;

        double phase = Util.getMillis() / 320.0;
        float pulse = 0.5F + 0.5F * (float) Math.sin(phase);
        int edgeColor = argb(90 + Math.round(55 * pulse), 255, 28, 20);
        int glowColor = argb(18 + Math.round(18 * pulse), 255, 42, 24);
        int accentColor = argb(190 + Math.round(65 * pulse), 255, 62, 28);

        // A dim full frame makes the dangerous state persistent without
        // covering the world; the glow softens the otherwise harsh red line.
        fillFrame(graphics, width, height, EDGE_THICKNESS, edgeColor);
        fillInnerGlow(graphics, width, height, glowColor);

        int cornerLength = Math.max(14, Math.min(30, Math.min(width, height) / 8));
        fillCorners(graphics, width, height, cornerLength, accentColor);
        drawWarningBadge(graphics, font, width, accentColor);
    }

    private static void fillFrame(GuiGraphicsExtractor graphics, int width, int height,
                                  int thickness, int color) {
        graphics.fill(0, 0, width, thickness, color);
        graphics.fill(0, height - thickness, width, height, color);
        graphics.fill(0, thickness, thickness, height - thickness, color);
        graphics.fill(width - thickness, thickness, width, height - thickness, color);
    }

    private static void fillInnerGlow(GuiGraphicsExtractor graphics, int width, int height, int color) {
        int inner = EDGE_THICKNESS + 4;
        graphics.fill(EDGE_THICKNESS, EDGE_THICKNESS, width - EDGE_THICKNESS, inner, color);
        graphics.fill(EDGE_THICKNESS, height - inner, width - EDGE_THICKNESS,
                height - EDGE_THICKNESS, color);
        graphics.fill(EDGE_THICKNESS, inner, inner, height - inner, color);
        graphics.fill(width - inner, inner, width - EDGE_THICKNESS, height - inner, color);
    }

    private static void fillCorners(GuiGraphicsExtractor graphics, int width, int height,
                                    int length, int color) {
        graphics.fill(0, 0, length, CORNER_THICKNESS, color);
        graphics.fill(0, 0, CORNER_THICKNESS, length, color);

        graphics.fill(width - length, 0, width, CORNER_THICKNESS, color);
        graphics.fill(width - CORNER_THICKNESS, 0, width, length, color);

        graphics.fill(0, height - CORNER_THICKNESS, length, height, color);
        graphics.fill(0, height - length, CORNER_THICKNESS, height, color);

        graphics.fill(width - length, height - CORNER_THICKNESS, width, height, color);
        graphics.fill(width - CORNER_THICKNESS, height - length, width, height, color);
    }

    private static void drawWarningBadge(GuiGraphicsExtractor graphics, Font font,
                                         int screenWidth, int accentColor) {
        Component text = Component.translatable("hud.chainveinfabric.autoMineActive");
        int horizontalMargin = Math.min(6, Math.max(0, screenWidth / 4));
        int badgeWidth = Math.max(1,
                Math.min(screenWidth - horizontalMargin * 2, font.width(text) + 20));
        int left = (screenWidth - badgeWidth) / 2;
        int right = left + badgeWidth;
        int top = 5;
        int bottom = top + 17;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xB0000000);
        graphics.fill(left, top, right, bottom, 0xDB170404);
        graphics.fill(left, top, right, top + 1, accentColor);
        graphics.fill(left, bottom - 1, right, bottom, accentColor);
        graphics.fill(left, top, left + 1, bottom, accentColor);
        graphics.fill(right - 1, top, right, bottom, accentColor);
        graphics.centeredText(font, text, screenWidth / 2, top + 4, 0xFFFFE4D6);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
