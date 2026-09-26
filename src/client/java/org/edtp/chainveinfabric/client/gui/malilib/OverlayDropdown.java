package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.interfaces.IStringRetriever;
import fi.dy.masa.malilib.util.MathUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Dropdown that renders its open menu above the rest of the screen widgets. */
abstract class OverlayDropdown<T> extends WidgetDropDownList<T> {
    private long lastDrawn;

    OverlayDropdown(int x, int y, int width, int height, int maxHeight,
                    int maxVisibleEntries, List<T> entries,
                    IStringRetriever<T> stringRetriever) {
        super(x, y, width, height, maxHeight, maxVisibleEntries, entries, stringRetriever);
    }

    boolean isMenuOpen() {
        return this.isOpen;
    }

    long getLastDrawn() {
        return this.lastDrawn;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        if (this.isOpen) {
            int visible = Math.min(this.maxVisibleEntries, this.filteredEntries.size());
            int dropHeight = this.height + visible * this.height + 4;
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + dropHeight;
        }
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        double clickY = mouseY;
        if (this.isOpen && clickY > this.y + this.height) {
            int visible = Math.min(this.maxVisibleEntries, this.filteredEntries.size());
            int dropHeight = this.height + visible * this.height + 4;

            if (mouseX >= this.x && mouseX < this.x + this.width
                    && clickY >= this.y && clickY < this.y + dropHeight
                    && mouseX < this.x + this.width - this.scrollbarWidth) {
                int relativeIndex = (int) ((clickY - this.y - this.height - 1) / this.height);
                relativeIndex = MathUtils.clamp(relativeIndex, 0, visible - 1);
                this.setSelectedEntry(this.scrollBar.getValue() + relativeIndex);
                this.isOpen = false;
                if (this.searchBar != null && this.searchBar.getTextField() != null) {
                    this.searchBar.getTextField().setValue("");
                }
                this.updateFilteredEntries();
                return true;
            }
        }
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void render(int mouseX, int mouseY, boolean selected, GuiGraphics context) {
        boolean wasOpen = this.isOpen;
        this.isOpen = false;
        super.render(mouseX, mouseY, selected, context);
        this.isOpen = wasOpen;
        this.lastDrawn = System.currentTimeMillis();
    }

    void renderOverlay(GuiGraphics context, int mouseX, int mouseY, boolean selected) {
        boolean wasOpen = this.isOpen;
        this.isOpen = true;
        super.render(mouseX, mouseY, selected, context);
        this.isOpen = wasOpen;
    }
}
