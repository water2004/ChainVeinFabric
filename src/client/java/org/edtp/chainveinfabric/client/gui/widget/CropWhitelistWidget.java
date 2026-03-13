package org.edtp.chainveinfabric.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.Click;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;

public class CropWhitelistWidget extends EntryListWidget<CropWhitelistWidget.CropWhitelistEntry> {
    private final TextRenderer textRenderer;
    private final Runnable onRefresh;

    public CropWhitelistWidget(MinecraftClient client, int width, int height, int top, int itemHeight, TextRenderer textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.refresh();
    }

    public void refresh() {
        this.clearEntries();
        ChainveinfabricClient.CONFIG.whitelistedCrops.stream()
                .map(id -> Registries.ITEM.get(Identifier.of(id)))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()))
                .forEach(item -> this.addEntry(new CropWhitelistEntry(item)));
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.USAGE, Text.translatable("narration.cropwhitelist.usage"));
    }

    public class CropWhitelistEntry extends EntryListWidget.Entry<CropWhitelistEntry> {
        private final Identifier itemIdentifier;
        private final Text itemDisplayName;
        private final ButtonWidget removeButton;

        public CropWhitelistEntry(Item item) {
            this.itemIdentifier = Registries.ITEM.getId(item);
            this.itemDisplayName = item.getName();
            this.removeButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.remove"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedCrops.remove(this.itemIdentifier.toString());
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
            Item item = Registries.ITEM.get(this.itemIdentifier);
            if (item != null) {
                context.drawItem(item.getDefaultStack(), x + 2, y + 2);
            }
            context.drawTextWithShadow(textRenderer, this.itemDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
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
