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

public class WhitelistWidget extends EntryListWidget<WhitelistWidget.WhitelistEntry> {
    private final TextRenderer textRenderer;
    private final Runnable onRefresh;

    public WhitelistWidget(MinecraftClient client, int width, int height, int top, int itemHeight, TextRenderer textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.refresh();
    }

    public void refresh() {
        this.clearEntries();
        ChainveinfabricClient.CONFIG.whitelistedBlocks.stream()
                .map(id -> Registries.BLOCK.get(Identifier.of(id)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                .forEach(block -> this.addEntry(new WhitelistEntry(block)));
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.USAGE, Text.translatable("narration.whitelist.usage"));
    }

    public class WhitelistEntry extends EntryListWidget.Entry<WhitelistEntry> {
        private final Identifier blockIdentifier;
        private final Text blockDisplayName;
        private final ButtonWidget removeButton;

        public WhitelistEntry(Block block) {
            this.blockIdentifier = Registries.BLOCK.getId(block);
            this.blockDisplayName = block.getName();
            this.removeButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.remove"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedBlocks.remove(this.blockIdentifier.toString());
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
            this.removeButton.setX(x + entryWidth - 42);
            this.removeButton.setY(y + (entryHeight - 20) / 2);
            this.removeButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (this.removeButton.mouseClicked(click, doubled)) return true;
            return super.mouseClicked(click, doubled);
        }
    }
}
