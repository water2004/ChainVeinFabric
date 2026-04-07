package org.edtp.chainveinfabric.client.gui.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class DropdownWidget<T> extends AbstractWidget {
    private final List<T> values;
    private final Function<T, Component> textGetter;
    private final Consumer<T> onSelect;
    private final Font textRenderer;
    private boolean expanded = false;
    private T selected;

    public DropdownWidget(int x, int y, int width, int height, Component message, List<T> values, T initialValue, Function<T, Component> textGetter, Consumer<T> onSelect, Font textRenderer) {
        super(x, y, width, height, message);
        this.values = values;
        this.selected = initialValue;
        this.textGetter = textGetter;
        this.onSelect = onSelect;
        this.textRenderer = textRenderer;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        
        context.fill(x, y, x + width, y + height, 0xFF000000);
        drawRectBorder(context, x, y, width, height, 0xFFFFFFFF);
        
        context.text(textRenderer, textGetter.apply(selected), x + 5, y + (height - 8) / 2, 0xFFFFFFFF);
        context.text(textRenderer, expanded ? "▲" : "▼", x + width - 15, y + (height - 8) / 2, 0xFFFFFFFF);
    }

    public void renderOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        
        int x = getX();
        int y = getY();
        int optionY = y + height;
        for (T value : values) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= optionY && mouseY < optionY + height;
            context.fill(x, optionY, x + width, optionY + height, hovered ? 0xFF444444 : 0xFF222222);
            drawRectBorder(context, x, optionY, width, height, 0xFF888888);
            context.text(textRenderer, textGetter.apply(value), x + 5, optionY + (height - 8) / 2, 0xFFFFFFFF);
            optionY += height;
        }
    }

    private void drawRectBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    public boolean isMouseOverOverlay(double mouseX, double mouseY) {
        if (!this.visible || !expanded) return false;
        int x = getX();
        int y = getY();
        int overlayHeight = (values.size() * height);
        return mouseX >= x && mouseX < x + width && mouseY >= y + height && mouseY < y + height + overlayHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!this.active || !this.visible) return false;

        double mx = click.x();
        double my = click.y();
        int x = getX();
        int y = getY();

        if (expanded) {
            int optionY = y + height;
            for (T value : values) {
                if (mx >= x && mx < x + width && my >= optionY && my < optionY + height) {
                    this.selected = value;
                    this.onSelect.accept(value);
                    this.expanded = false;
                    return true;
                }
                optionY += height;
            }

            if (mx >= x && mx < x + width && my >= y && my < y + height) {
                this.expanded = false;
                return true;
            }

            this.expanded = false;
            return false; // Clicked outside, closed but didn't consume click
        } else {
            if (mx >= x && mx < x + width && my >= y && my < y + height) {
                this.expanded = true;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;
        int x = getX();
        int y = getY();
        int currentHeight = expanded ? height + (values.size() * height) : height;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + currentHeight;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, getMessage());
    }
}
