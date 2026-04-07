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
import net.minecraft.world.level.block.Block;

public class WhitelistWidget extends AbstractSelectionList<WhitelistWidget.WhitelistEntry> {
    private final Font textRenderer;
    private final Runnable onRefresh;
    private final Set<String> whitelist;

    public WhitelistWidget(Minecraft client, int width, int height, int top, int itemHeight, Font textRenderer, Runnable onRefresh, Set<String> whitelist) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.whitelist = whitelist;
        this.refresh();
    }

    public void refresh() {
        this.clearEntries();
        whitelist.stream()
                .map(id -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                .forEach(block -> this.addEntry(new WhitelistEntry(block)));
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        builder.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE, Component.translatable("narration.whitelist.usage"));
    }

    public class WhitelistEntry extends AbstractSelectionList.Entry<WhitelistEntry> {
        private final Identifier blockIdentifier;
        private final Component blockDisplayName;
        private final Button removeButton;

        public WhitelistEntry(Block block) {
            this.blockIdentifier = BuiltInRegistries.BLOCK.getKey(block);
            this.blockDisplayName = block.getName();
            this.removeButton = Button.builder(Component.translatable("options.chainveinfabric.remove"),
                    button -> {
                        whitelist.remove(this.blockIdentifier.toString());
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
            Block block = BuiltInRegistries.BLOCK.getValue(this.blockIdentifier);
            if (block != null && block.asItem() != null) {
                context.item(block.asItem().getDefaultInstance(), x + 2, y + 2);
            }
            context.text(textRenderer, this.blockDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
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
