package org.edtp.chainveinfabric.client.gui;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChainVeinScreen extends Screen {
    private enum Tab {
        WHITELIST, SETTINGS
    }

    private Tab currentTab = Tab.WHITELIST;

    private Text chainVeinLabel;
    private TextFieldWidget searchBox;
    private BlockListWidget blockListWidget;
    private WhitelistWidget whitelistWidget;

    // Tab 2 elements
    private TextFieldWidget maxBlocksBox;
    private TextFieldWidget maxRadiusBox;
    private CyclingButtonWidget<Boolean> directToInventoryButton;
    private CyclingButtonWidget<Boolean> toolProtectionButton;
    private CyclingButtonWidget<Boolean> diagonalEdgeButton;
    private CyclingButtonWidget<Boolean> diagonalCornerButton;

    public ChainVeinScreen() {
        super(Text.of("Chain Vein Config"));
    }

    @Override
    protected void init() {
        super.init();
        this.chainVeinLabel = Text.translatable("options.chainveinfabric.chainVein");

        int centerX = this.width / 2;
        int topY = 40;

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("options.chainveinfabric.tab.whitelist"), button -> {
            this.currentTab = Tab.WHITELIST;
            this.clearAndInit();
        }).dimensions(centerX - 105, 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("options.chainveinfabric.tab.settings"), button -> {
            this.currentTab = Tab.SETTINGS;
            this.clearAndInit();
        }).dimensions(centerX + 5, 10, 100, 20).build());

        if (this.currentTab == Tab.WHITELIST) {
            initWhitelistTab(centerX, topY);
        } else {
            initSettingsTab(centerX, topY);
        }
    }

    private void initWhitelistTab(int centerX, int topY) {
        // Chain Mining Toggle
        this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.isChainVeinEnabled)
                        .omitKeyText()
                        .build(centerX + 10, topY, 60, 20, Text.empty(), (button, value) -> {
                            ChainveinfabricClient.CONFIG.isChainVeinEnabled = value;
                        }));

        // Search Box
        this.searchBox = new TextFieldWidget(this.textRenderer, centerX - 210, topY + 30, 420, 20,
                Text.translatable("options.chainveinfabric.search").copy().formatted(Formatting.GRAY));
        this.searchBox.setMaxLength(256);
        this.searchBox.setDrawsBackground(true);
        this.searchBox.setChangedListener(this::onSearchBoxChanged);
        this.addSelectableChild(this.searchBox);

        // Lists
        int listWidth = 200;
        int listTopY = topY + 65;
        int listHeight = this.height - listTopY - 20;

        this.blockListWidget = new BlockListWidget(this.client, listWidth, listHeight, listTopY, 24);
        this.blockListWidget.setX(centerX - 210);
        this.addDrawableChild(this.blockListWidget);

        this.whitelistWidget = new WhitelistWidget(this.client, listWidth, listHeight, listTopY, 24);
        this.whitelistWidget.setX(centerX + 10);
        this.addDrawableChild(this.whitelistWidget);

        this.refreshLists();
    }

    private void initSettingsTab(int centerX, int topY) {
        // Max Blocks Input
        this.maxBlocksBox = new TextFieldWidget(this.textRenderer, centerX + 10, topY, 100, 20, Text.of(String.valueOf(ChainveinfabricClient.CONFIG.maxChainBlocks)));
        this.maxBlocksBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.maxChainBlocks));
        this.maxBlocksBox.setChangedListener(text -> {
            try {
                ChainveinfabricClient.CONFIG.maxChainBlocks = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {}
        });
        this.addSelectableChild(this.maxBlocksBox);

        // Max Radius Input
        this.maxRadiusBox = new TextFieldWidget(this.textRenderer, centerX + 10, topY + 30, 100, 20, Text.of(String.valueOf(ChainveinfabricClient.CONFIG.maxRadius)));
        this.maxRadiusBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.maxRadius));
        this.maxRadiusBox.setChangedListener(text -> {
            try {
                ChainveinfabricClient.CONFIG.maxRadius = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {}
        });
        this.addSelectableChild(this.maxRadiusBox);

        // Direct to Inventory Toggle
        this.directToInventoryButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.directToInventory)
                .omitKeyText()
                .build(centerX + 10, topY + 60, 100, 20, Text.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.directToInventory = value;
                });
        this.addDrawableChild(this.directToInventoryButton);

        // Tool Protection Toggle
        this.toolProtectionButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.toolProtection)
                .omitKeyText()
                .build(centerX + 10, topY + 90, 100, 20, Text.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.toolProtection = value;
                });
        this.addDrawableChild(this.toolProtectionButton);

        // Diagonal Edge Toggle
        this.diagonalEdgeButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalEdge)
                .omitKeyText()
                .build(centerX + 10, topY + 120, 100, 20, Text.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.diagonalEdge = value;
                });
        this.addDrawableChild(this.diagonalEdgeButton);

        // Diagonal Corner Toggle
        this.diagonalCornerButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalCorner)
                .omitKeyText()
                .build(centerX + 10, topY + 150, 100, 20, Text.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.diagonalCorner = value;
                });
        this.addDrawableChild(this.diagonalCornerButton);
    }

    private void onSearchBoxChanged(String search) {
        this.refreshLists();
    }

    private void refreshLists() {
        if (this.blockListWidget != null) {
            this.blockListWidget.filter(this.searchBox.getText());
        }
        if (this.whitelistWidget != null) {
            this.whitelistWidget.refresh();
        }
    }

    @Override
    public void resize(int width, int height) {
        String search = (this.searchBox != null) ? this.searchBox.getText() : "";
        super.resize(width, height);
        if (this.searchBox != null) this.searchBox.setText(search);
        this.refreshLists();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int topY = 40;

        if (this.currentTab == Tab.WHITELIST) {
            this.searchBox.render(context, mouseX, mouseY, delta);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.allBlocks"), centerX - 210, topY + 55, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.whitelist"), centerX + 10, topY + 55, 0xFFFFFFFF);
            
            int labelX = centerX - this.textRenderer.getWidth(this.chainVeinLabel) - 20;
            int labelY = topY + (20 - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer, this.chainVeinLabel, labelX, labelY, 0xFFFFFFFF);
        } else {
            this.maxBlocksBox.render(context, mouseX, mouseY, delta);
            this.maxRadiusBox.render(context, mouseX, mouseY, delta);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.maxBlocks"), centerX - 120, topY + 5, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.maxRadius"), centerX - 120, topY + 35, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.directToInventory"), centerX - 120, topY + 65, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.toolProtection"), centerX - 120, topY + 95, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.diagonalEdge"), centerX - 120, topY + 125, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.diagonalCorner"), centerX - 120, topY + 155, 0xFFFFFFFF);
        }
    }

    @Override
    public void close() {
        ChainveinfabricClient.CONFIG.save();
        super.close();
    }

    // --- Inner classes (WhitelistWidget and BlockListWidget) remain exactly as before ---
    class WhitelistWidget extends EntryListWidget<WhitelistWidget.WhitelistEntry> {
        public WhitelistWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
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
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { builder.put(NarrationPart.USAGE, Text.translatable("narration.whitelist.usage")); }

        class WhitelistEntry extends EntryListWidget.Entry<WhitelistEntry> {
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
                            ChainVeinScreen.this.refreshLists();
                        }).dimensions(0, 0, 40, 20).build();
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = this.getX(); int y = this.getY();
                int entryWidth = this.getWidth(); int entryHeight = this.getHeight();
                Block block = Registries.BLOCK.get(this.blockIdentifier);
                if (block != null && block.asItem() != null) {
                    context.drawItem(block.asItem().getDefaultStack(), x + 2, y + 2);
                }
                context.drawTextWithShadow(ChainVeinScreen.this.textRenderer, this.blockDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
                this.removeButton.setX(x + entryWidth - 42);
                this.removeButton.setY(y + (entryHeight - 20) / 2);
                this.removeButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (this.removeButton.mouseClicked(click, doubled)) return true;
                return super.mouseClicked(click, doubled);
            }

            public Text getNarration() { return Text.translatable("narrator.entry.block", this.blockDisplayName.getString()); }
        }
    }

    class BlockListWidget extends EntryListWidget<BlockListWidget.BlockEntry> {
        private final List<BlockEntry> allEntries;

        public BlockListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.allEntries = Registries.BLOCK.stream().map(BlockEntry::new).sorted(Comparator.comparing(entry -> entry.blockIdentifier.toString())).collect(Collectors.toList());
            this.filter("");
        }

        private void updateEntries(List<BlockEntry> entries) { this.clearEntries(); entries.forEach(this::addEntry); }

        public void filter(String search) {
            List<BlockEntry> filteredEntries;
            if (search.isEmpty()) { filteredEntries = this.allEntries; } else {
                String lowerCaseSearch = search.toLowerCase(Locale.ROOT);
                filteredEntries = this.allEntries.stream().filter(entry -> entry.blockIdentifier.toString().toLowerCase(Locale.ROOT).contains(lowerCaseSearch) || entry.blockDisplayName.getString().toLowerCase(Locale.ROOT).contains(lowerCaseSearch)).collect(Collectors.toList());
            }
            this.updateEntries(filteredEntries);
            this.setScrollY(0);
        }

        public int getRowWidth() { return 180; }
        protected int getScrollbarPositionX() { return this.getX() + this.width + 6; }
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { builder.put(NarrationPart.USAGE, Text.translatable("narration.allblocks.usage")); }

        class BlockEntry extends EntryListWidget.Entry<BlockEntry> {
            private final Identifier blockIdentifier;
            private final Text blockDisplayName;
            private final ButtonWidget addButton;

            public BlockEntry(Block block) {
                this.blockIdentifier = Registries.BLOCK.getId(block);
                this.blockDisplayName = block.getName();
                this.addButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.add"),
                        button -> {
                            ChainveinfabricClient.CONFIG.whitelistedBlocks.add(this.blockIdentifier.toString());
                            ChainveinfabricClient.CONFIG.save();
                            ChainVeinScreen.this.refreshLists();
                        }).dimensions(0, 0, 40, 20).build();
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = this.getX(); int y = this.getY();
                int entryWidth = this.getWidth(); int entryHeight = this.getHeight();
                Block block = Registries.BLOCK.get(this.blockIdentifier);
                if (block != null && block.asItem() != null) {
                    context.drawItem(block.asItem().getDefaultStack(), x + 2, y + 2);
                }
                context.drawTextWithShadow(ChainVeinScreen.this.textRenderer, this.blockDisplayName, x + 24, y + (entryHeight - 8) / 2, 0xFFFFFFFF);
                this.addButton.setX(x + entryWidth - 42);
                this.addButton.setY(y + (entryHeight - 20) / 2);
                this.addButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (this.addButton.mouseClicked(click, doubled)) return true;
                return super.mouseClicked(click, doubled);
            }

            public Text getNarration() { return Text.translatable("narrator.entry.block", this.blockDisplayName.getString()); }
        }
    }
}
