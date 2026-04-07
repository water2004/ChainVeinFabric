package org.edtp.chainveinfabric.client.gui.widget;

//import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import java.util.stream.Collectors;

public class ItemListWidget extends AbstractSelectionList<ItemListWidget.ItemEntry> {
    private final List<ItemEntry> allEntries;
    private final Font textRenderer;
    private final Runnable onRefresh;

    public ItemListWidget(Minecraft client, int width, int height, int top, int itemHeight, Font textRenderer, Runnable onRefresh) {
        super(client, width, height, top, itemHeight);
        this.textRenderer = textRenderer;
        this.onRefresh = onRefresh;
        this.allEntries = BuiltInRegistries.ITEM.stream()
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
            return block instanceof VegetationBlock || 
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
        this.setScrollAmount(0);
    }

    public int getRowWidth() { return 180; }
    protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        builder.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE, Component.translatable("narration.allitems.usage"));
    }

    public class ItemEntry extends AbstractSelectionList.Entry<ItemEntry> {
        public final Identifier itemIdentifier;
        public final Component itemDisplayName;
        private final Button addButton;

        public ItemEntry(Item item) {
            this.itemIdentifier = BuiltInRegistries.ITEM.getKey(item);
            this.itemDisplayName = item.getName();
            this.addButton = Button.builder(Component.translatable("options.chainveinfabric.add"),
                    button -> {
                        ChainveinfabricClient.CONFIG.whitelistedCrops.add(this.itemIdentifier.toString());
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
            Item item = BuiltInRegistries.ITEM.getValue(this.itemIdentifier);
            if (item != null) {
                context.renderItem(item.getDefaultInstance(), x + 2, y + 2);
            }
            context.drawString(textRenderer, this.itemDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
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
