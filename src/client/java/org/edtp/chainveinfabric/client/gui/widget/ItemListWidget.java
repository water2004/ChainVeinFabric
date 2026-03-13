package org.edtp.chainveinfabric.client.gui.widget;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import java.util.stream.Collectors;

public class ItemListWidget extends EntryListWidget<ItemListWidget.ItemEntry> {
    private final List<ItemEntry> allEntries;
    private final TextRenderer textRenderer;
    private final Runnable onRefresh;

    public ItemListWidget(MinecraftClient client, int width, int height, int top, int itemHeight, TextRenderer textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.allEntries = Registries.ITEM.stream()
                .filter(this::isPlantable)
                .map(ItemEntry::new)
                .sorted(Comparator.comparing(entry -> entry.itemIdentifier.toString()))
                .collect(Collectors.toList());
        this.filter("");
    }

    private boolean isPlantable(Item item) {
        if (item == Items.NETHER_WART || item == Items.COCOA_BEANS || 
            item == Items.SUGAR_CANE || item == Items.BAMBOO || 
            item == Items.SWEET_BERRIES || item == Items.CHORUS_FRUIT) {
            return true;
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block instanceof PlantBlock || 
                   block instanceof CropBlock || 
                   block instanceof SaplingBlock || 
                   block instanceof StemBlock || 
                   block instanceof AttachedStemBlock ||
                   block instanceof AzaleaBlock ||
                   block instanceof SeaPickleBlock;
        }
        return false;
    }

    public void filter(String search) {
        this.clearEntries();
        String lowerSearch = search.toLowerCase(Locale.ROOT);
        for (ItemEntry entry : allEntries) {
            if (entry.itemIdentifier.toString().toLowerCase(Locale.ROOT).contains(lowerSearch) || 
                entry.itemDisplayName.getString().toLowerCase(Locale.ROOT).contains(lowerSearch)) {
                this.addEntry(entry);
            }
        }
        this.setScrollY(0);
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.USAGE, Text.translatable("narration.allitems.usage"));
    }

    public class ItemEntry extends EntryListWidget.Entry<ItemEntry> {
        public final Identifier itemIdentifier;
        public final Text itemDisplayName;
        private final ButtonWidget addButton;

        public ItemEntry(Item item) {
            this.itemIdentifier = Registries.ITEM.getId(item);
            this.itemDisplayName = item.getName();
            this.addButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.add"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedCrops.add(this.itemIdentifier.toString());
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
