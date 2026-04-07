package org.edtp.chainveinfabric.client.gui.widget;

import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class CropWhitelistWidget extends AbstractSelectionList<CropWhitelistWidget.CropWhitelistEntry> {
    private final Font textRenderer;
    private final Runnable onRefresh;

    public CropWhitelistWidget(Minecraft client, int width, int height, int top, int itemHeight, Font textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.refresh();
    }

    public void refresh() {
        this.clearEntries();
        ChainveinfabricClient.CONFIG.whitelistedCrops.stream()
                .map(id -> BuiltInRegistries.ITEM.getValue(Identifier.parse(id)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .forEach(item -> this.addEntry(new CropWhitelistEntry(item)));
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        builder.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE, Component.translatable("narration.cropwhitelist.usage"));
    }

    public class CropWhitelistEntry extends AbstractSelectionList.Entry<CropWhitelistEntry> {
        private final Identifier itemIdentifier;
        private final Component itemDisplayName;
        private final Button removeButton;

        public CropWhitelistEntry(Item item) {
            this.itemIdentifier = BuiltInRegistries.ITEM.getKey(item);
            this.itemDisplayName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId());
            this.removeButton = Button.builder(Component.translatable("options.chainveinfabric.remove"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedCrops.remove(this.itemIdentifier.toString());
                        ChainveinfabricClient.CONFIG.save();
                        onRefresh.run();
                    }).bounds(0, 0, 40, 20).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int entryWidth = getWidth();
            int entryHeight = getHeight();
            Item item = BuiltInRegistries.ITEM.getValue(this.itemIdentifier);
            if (item != null) {
                context.item(item.getDefaultInstance(), x + 2, y + 2);
            }
            context.text(textRenderer, this.itemDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
            this.removeButton.setX(x + entryWidth - 42);
            this.removeButton.setY(y + (entryHeight - 20) / 2);
            this.removeButton.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (this.removeButton.mouseClicked(click, doubled)) return true;
            return super.mouseClicked(click, doubled);
        }
    }
}
