package org.edtp.chainveinfabric.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class SettingsListWidget extends EntryListWidget<SettingsListWidget.SettingEntry> {
    private final TextRenderer textRenderer;
    private SettingEntry customSelectedEntry;

    public SettingsListWidget(MinecraftClient client, int width, int height, int top, int itemHeight, TextRenderer textRenderer) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.centerListVertically = false;
    }

    public void addControl(Text label, ClickableWidget widget) {
        this.addEntry(new ControlEntry(label, List.of(widget)));
    }

    public void addMultiControl(Text label, List<ClickableWidget> widgets) {
        this.addEntry(new ControlEntry(label, widgets));
    }

    @Override
    public int getRowWidth() {
        return 340;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    public abstract class SettingEntry extends EntryListWidget.Entry<SettingEntry> {
        public abstract void unfocusAll();
    }

    public class ControlEntry extends SettingEntry {
        private final Text label;
        private final List<ClickableWidget> widgets;

        public ControlEntry(Text label, List<ClickableWidget> widgets) {
            this.label = label;
            this.widgets = widgets;
        }

        public List<ClickableWidget> getWidgets() {
            return widgets;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int entryWidth = getWidth();
            int entryHeight = getHeight();
            
            context.drawTextWithShadow(textRenderer, label, x, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
            
            int currentX = x + entryWidth;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                ClickableWidget widget = widgets.get(i);
                currentX -= widget.getWidth();
                widget.setX(currentX);
                widget.setY(y);
                widget.render(context, mouseX, mouseY, tickDelta);
                currentX -= 5; // Gap
            }
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            boolean clickedOnWidget = false;
            for (ClickableWidget widget : widgets) {
                if (widget.mouseClicked(click, doubled)) {
                    if (widget instanceof TextFieldWidget) {
                        ((TextFieldWidget) widget).setFocused(true);
                    }
                    clickedOnWidget = true;
                } else {
                    if (widget instanceof TextFieldWidget) {
                        ((TextFieldWidget) widget).setFocused(false);
                    }
                }
            }
            
            if (clickedOnWidget) {
                SettingsListWidget.this.updateCustomSelection(this);
                return true;
            }
            
            return false;
        }

        @Override
        public void unfocusAll() {
            for (ClickableWidget widget : widgets) {
                if (widget instanceof TextFieldWidget) {
                    ((TextFieldWidget) widget).setFocused(false);
                }
            }
        }
    }

    private void updateCustomSelection(SettingEntry entry) {
        if (customSelectedEntry != null && customSelectedEntry != entry) {
            customSelectedEntry.unfocusAll();
        }
        customSelectedEntry = entry;
    }
}
