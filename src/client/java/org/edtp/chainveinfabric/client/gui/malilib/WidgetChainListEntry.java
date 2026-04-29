package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.world.item.ItemStack;

public class WidgetChainListEntry extends WidgetListEntryBase<ItemStack> {
    private final boolean isOdd;
    private final int buttonsStartX;
    private final boolean isWhitelist;
    private final WidgetChainList parent;

    public WidgetChainListEntry(int x, int y, int width, int height, boolean isOdd, ItemStack stack, int listIndex, WidgetChainList parent, boolean isWhitelist) {
        super(x, y, width, height, stack, listIndex);
        this.isOdd = isOdd;
        this.parent = parent;
        this.isWhitelist = isWhitelist;

        int btnWidth = 50;
        int btnHeight = 20;
        int btnX = x + width - btnWidth - 4;
        int btnY = y + (height - btnHeight) / 2;
        this.buttonsStartX = btnX;

        String btnText = isWhitelist ? "options.chainveinfabric.remove" : "options.chainveinfabric.add";
        ButtonGeneric button = new ButtonGeneric(btnX, btnY, btnWidth, btnHeight, StringUtils.translate(btnText));
        this.addButton(button, new ButtonListener(this));
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (selected || this.isMouseOver(mouseX, mouseY)) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x70FFFFFF);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
        }

        if (this.entry != null && !this.entry.isEmpty()) {
            int iconX = this.x + 4;
            int iconY = this.y + (this.height - 16) / 2;
            ctx.renderItem(this.entry, iconX, iconY);

            String itemName = this.entry.getHoverName().getString();
            int textY = this.y + (this.height - 8) / 2 + 1;
            this.drawString(ctx, iconX + 24, textY, 0xFFFFFFFF, itemName);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }

    @Override
    public boolean canSelectAt(net.minecraft.client.input.MouseButtonEvent click) {
        return super.canSelectAt(click) && click.x() < this.buttonsStartX;
    }

    private static class ButtonListener implements IButtonActionListener {
        private final WidgetChainListEntry widget;

        public ButtonListener(WidgetChainListEntry widget) {
            this.widget = widget;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            if (mouseButton == 0) {
                widget.parent.onButtonAction(widget.entry, widget.isWhitelist);
            }
        }
    }
}
