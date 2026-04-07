package org.edtp.chainveinfabric.client.gui.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SettingsListWidget extends AbstractSelectionList<SettingsListWidget.SettingEntry> {
    private final Font textRenderer;
    private SettingEntry customSelectedEntry;

    public SettingsListWidget(Minecraft client, int width, int height, int top, int itemHeight, Font textRenderer) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.centerListVertically = false;
    }

    public void addControl(Component label, AbstractWidget widget) {
        this.addEntry(new ControlEntry(label, List.of(widget)));
    }

    public void addMultiControl(Component label, List<AbstractWidget> widgets) {
        this.addEntry(new ControlEntry(label, widgets));
    }

    @Override
    public int getRowWidth() {
        return 340;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }

    public abstract class SettingEntry extends AbstractSelectionList.Entry<SettingEntry> {
        public abstract void unfocusAll();
    }

    public class ControlEntry extends SettingEntry {
        private final Component label;
        private final List<AbstractWidget> widgets;

        public ControlEntry(Component label, List<AbstractWidget> widgets) {
            this.label = label;
            this.widgets = widgets;
        }

        public List<AbstractWidget> getWidgets() {
            return widgets;
        }

        @Override
        public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int entryWidth = getWidth();
            int entryHeight = getHeight();
            
            context.drawString(textRenderer, label, x, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
            
            int currentX = x + entryWidth;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                currentX -= widget.getWidth();
                widget.setX(currentX);
                widget.setY(y);
                widget.render(context, mouseX, mouseY, tickDelta);
                currentX -= 5; // Gap
            }
        }

        @Override
        public void setFocused(boolean focused) {
            if (!focused) {
                unfocusAll();
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            boolean clickedOnWidget = false;
            for (AbstractWidget widget : widgets) {
                if (widget.mouseClicked(click, doubled)) {
                    if (widget instanceof EditBox) {
                        ((EditBox) widget).setFocused(true);
                        ((EditBox) widget).moveCursorToEnd(false);
                    }
                    clickedOnWidget = true;
                } else {
                    if (widget instanceof EditBox) {
                        ((EditBox) widget).setFocused(false);
                    }
                }
            }
            
            if (clickedOnWidget) {
                SettingsListWidget.this.updateCustomSelection(this);
                return true;
            }
            
            return false;
        }

        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> selectableChildren() {
            return widgets;
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
            for (AbstractWidget widget : widgets) {
                if (widget.keyPressed(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
            for (AbstractWidget widget : widgets) {
                if (widget.charTyped(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean keyReleased(net.minecraft.client.input.KeyEvent input) {
            for (AbstractWidget widget : widgets) {
                if (widget.keyReleased(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void unfocusAll() {
            for (AbstractWidget widget : widgets) {
                if (widget instanceof EditBox) {
                    ((EditBox) widget).setFocused(false);
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
