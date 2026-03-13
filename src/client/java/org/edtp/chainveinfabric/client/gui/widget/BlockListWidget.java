package org.edtp.chainveinfabric.client.gui.widget;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.Click;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import java.util.stream.Collectors;

public class BlockListWidget extends EntryListWidget<BlockListWidget.BlockEntry> {
    private final List<BlockEntry> allEntries;
    private final TextRenderer textRenderer;
    private final Runnable onRefresh;

    public BlockListWidget(MinecraftClient client, int width, int height, int top, int itemHeight, TextRenderer textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.allEntries = Registries.BLOCK.stream()
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
        this.setScrollY(0);
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.USAGE, Text.translatable("narration.allblocks.usage"));
    }

    public class BlockEntry extends EntryListWidget.Entry<BlockEntry> {
        public final Identifier blockIdentifier;
        public final Text blockDisplayName;
        private final ButtonWidget addButton;

        public BlockEntry(Block block) {
            this.blockIdentifier = Registries.BLOCK.getId(block);
            this.blockDisplayName = block.getName();
            this.addButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.add"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedBlocks.add(this.blockIdentifier.toString());
                        ChainveinfabricClient.CONFIG.save();
                        onRefresh.run();
                    }).dimensions(0, 0, 40, 20).build();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getX();
            int y = getY();
            int entryWidth = getWidth();
            int entryHeight = getHeight();
            Block block = Registries.BLOCK.get(this.blockIdentifier);
            if (block != null && block.asItem() != null) {
                context.drawItem(block.asItem().getDefaultStack(), x + 2, y + 2);
            }
            context.drawTextWithShadow(textRenderer, this.blockDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
            this.addButton.setX(x + entryWidth - 42);
            this.addButton.setY(y + (entryHeight - 20) / 2);
            this.addButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (this.addButton.mouseClicked(click, doubled)) return true;
            return super.mouseClicked(click, doubled);
        }
    }
}
