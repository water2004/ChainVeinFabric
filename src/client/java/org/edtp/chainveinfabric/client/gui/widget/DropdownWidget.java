package org.edtp.chainveinfabric.client.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DropdownWidget<T> extends ClickableWidget {
    private final List<T> values;
    private final Function<T, Text> textGetter;
    private final Consumer<T> onSelect;
    private final TextRenderer textRenderer;
    private boolean expanded = false;
    private T selected;

    public DropdownWidget(int x, int y, int width, int height, Text message, List<T> values, T initialValue, Function<T, Text> textGetter, Consumer<T> onSelect, TextRenderer textRenderer) {
        super(x, y, width, height, message);
        this.values = values;
        this.selected = initialValue;
        this.textGetter = textGetter;
        this.onSelect = onSelect;
        this.textRenderer = textRenderer;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        
        context.fill(x, y, x + width, y + height, 0xFF000000);
        drawRectBorder(context, x, y, width, height, 0xFFFFFFFF);
        
        context.drawTextWithShadow(textRenderer, textGetter.apply(selected), x + 5, y + (height - 8) / 2, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, expanded ? "▲" : "▼", x + width - 15, y + (height - 8) / 2, 0xFFFFFFFF);

        if (expanded) {
            int optionY = y + height;
            for (T value : values) {
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= optionY && mouseY < optionY + height;
                context.fill(x, optionY, x + width, optionY + height, hovered ? 0xFF444444 : 0xFF222222);
                drawRectBorder(context, x, optionY, width, height, 0xFF888888);
                context.drawTextWithShadow(textRenderer, textGetter.apply(value), x + 5, optionY + (height - 8) / 2, 0xFFFFFFFF);
                optionY += height;
            }
        }
    }

    private void drawRectBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
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
            return false;
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
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }
}
