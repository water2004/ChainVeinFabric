package org.edtp.chainveinfabric.client.gui.widget;

import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class BlockListWidget extends AbstractSelectionList<BlockListWidget.BlockEntry> {
    private final List<BlockEntry> allEntries;
    private final Font textRenderer;
    private final Runnable onRefresh;
    private final Set<String> whitelist;

    public BlockListWidget(Minecraft client, int width, int height, int top, int itemHeight, Font textRenderer, Runnable onRefresh, Set<String> whitelist) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.whitelist = whitelist;
        this.allEntries = BuiltInRegistries.BLOCK.stream()
                .map(BlockEntry::new)
                .sorted(Comparator.comparing(entry -> entry.blockIdentifier.toString()))
                .collect(Collectors.toList());
        this.filter("");
    }

    public void filter(String search) {
        this.clearEntries();
        String lowerSearch = search.toLowerCase(Locale.ROOT);
        for (BlockEntry entry : allEntries) {
            if (entry.blockIdentifier.toString().toLowerCase(Locale.ROOT).contains(lowerSearch) || 
                entry.blockDisplayName.getString().toLowerCase(Locale.ROOT).contains(lowerSearch)) {
                this.addEntry(entry);
            }
        }
        this.setScrollAmount(0);
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        builder.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE, Component.translatable("narration.allblocks.usage"));
    }

    public class BlockEntry extends AbstractSelectionList.Entry<BlockEntry> {
        public final Identifier blockIdentifier;
        public final Component blockDisplayName;
        private final Button addButton;

        public BlockEntry(Block block) {
            this.blockIdentifier = BuiltInRegistries.BLOCK.getKey(block);
            this.blockDisplayName = block.getName();
            this.addButton = Button.builder(Component.translatable("options.chainveinfabric.add"),
                    button -> {
                        whitelist.add(this.blockIdentifier.toString());
                        ChainveinfabricClient.CONFIG.save();
                        onRefresh.run();
                    }).bounds(0, 0, 40, 20).build();
        }

        @Override
        public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int entryWidth = getWidth();
            int entryHeight = getHeight();
            Block block = BuiltInRegistries.BLOCK.getValue(this.blockIdentifier);
            if (block != null && block.asItem() != null) {
                context.renderItem(block.asItem().getDefaultInstance(), x + 2, y + 2);
            }
            context.drawString(textRenderer, this.blockDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
            this.addButton.setX(x + entryWidth - 42);
            this.addButton.setY(y + (entryHeight - 20) / 2);
            this.addButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (this.addButton.mouseClicked(click, doubled)) return true;
            return super.mouseClicked(click, doubled);
        }
    }
}
