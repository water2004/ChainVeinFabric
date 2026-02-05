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
import org.edtp.chainveinfabric.Chainveinfabric;
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
    private Text chainVeinLabel;
    private TextFieldWidget searchBox;
    private BlockListWidget blockListWidget;
    private WhitelistWidget whitelistWidget;

    public ChainVeinScreen() {
        super(Text.of("Chain Vein Config"));
    }

    @Override
    protected void init() {
        super.init();
        this.chainVeinLabel = Text.translatable("options.chainveinfabric.chainVein");

        int centerX = this.width / 2;
        int topY = this.height / 4;

        // 连锁采集开关
        this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(Chainveinfabric.CONFIG.isChainVeinEnabled)
                        .omitKeyText()
                        .build(centerX + 10, topY, 60, 20, Text.empty(), (button, value) -> {
                            Chainveinfabric.CONFIG.isChainVeinEnabled = value;
                        }));

        // 搜索框 (横跨两个列表)
        this.searchBox = new TextFieldWidget(this.textRenderer, centerX - 210, topY + 30, 420, 20,
                Text.translatable("options.chainveinfabric.search").copy().formatted(Formatting.GRAY));
        this.searchBox.setMaxLength(256);
        this.searchBox.setDrawsBackground(true);
        this.searchBox.setChangedListener(this::onSearchBoxChanged);
        this.addSelectableChild(this.searchBox);

        // 左侧：所有方块列表
        int listWidth = 200;
        int listTopY = topY + 60;
        int listHeight = this.height - listTopY - 20;

        this.blockListWidget = new BlockListWidget(this.client, listWidth, listHeight, listTopY, 24);
        this.blockListWidget.setX(centerX - 210);
        this.addDrawableChild(this.blockListWidget);

        // 右侧：白名单列表
        this.whitelistWidget = new WhitelistWidget(this.client, listWidth, listHeight, listTopY, 24);
        this.whitelistWidget.setX(centerX + 10);
        this.addDrawableChild(this.whitelistWidget);

        this.refreshLists();
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
        String search = this.searchBox.getText();
        super.resize(width, height);
        this.searchBox.setText(search);
        this.refreshLists();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        super.render(context, mouseX, mouseY, delta);

        this.searchBox.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.allBlocks"),
                this.width / 2 - 210, this.height / 4 + 50, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("options.chainveinfabric.whitelist"),
                this.width / 2 + 10, this.height / 4 + 50, 0xFFFFFFFF);

        int labelX = this.width / 2 - this.textRenderer.getWidth(this.chainVeinLabel) - 20;
        int labelY = this.height / 4 + (20 - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, this.chainVeinLabel, labelX, labelY, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Chainveinfabric.CONFIG.save();
        super.close();
    }

    // --- 白名单列表组件 ---
    class WhitelistWidget extends EntryListWidget<WhitelistWidget.WhitelistEntry> {
        public WhitelistWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.refresh();
        }

        public void refresh() {
            this.clearEntries();
            Chainveinfabric.CONFIG.whitelistedBlocks.stream()
                    .map(id -> Registries.BLOCK.get(Identifier.of(id)))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                    .forEach(block -> this.addEntry(new WhitelistEntry(block)));
        }

        public int getRowWidth() {
            return 180;
        }

        protected int getScrollbarPositionX() {
            return this.getX() + this.width + 6;
        }

        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.USAGE, Text.translatable("narration.whitelist.usage"));
        }

        class WhitelistEntry extends EntryListWidget.Entry<WhitelistEntry> {
            private final Identifier blockIdentifier;
            private final Text blockDisplayName;
            private final ButtonWidget removeButton;

            public WhitelistEntry(Block block) {
                this.blockIdentifier = Registries.BLOCK.getId(block);
                this.blockDisplayName = block.getName();
                this.removeButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.remove"),
                        button -> {
                            Chainveinfabric.CONFIG.whitelistedBlocks.remove(this.blockIdentifier.toString());
                            Chainveinfabric.CONFIG.save();
                            ChainVeinScreen.this.refreshLists();
                        })
                        .dimensions(0, 0, 40, 20).build();
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                // 使用 getX() 和 getY() 获取实际渲染坐标
                int x = this.getX();
                int y = this.getY();
                int entryWidth = this.getWidth();
                int entryHeight = this.getHeight();

                Block block = Registries.BLOCK.get(this.blockIdentifier);
                if (block != null && block.asItem() != null) {
                    context.drawItem(block.asItem().getDefaultStack(), x + 2, y + 2);
                }
                context.drawTextWithShadow(ChainVeinScreen.this.textRenderer, this.blockDisplayName, x + 24,
                        y + (entryHeight - 8) / 2, 0xFFFFFFFF);
                
                this.removeButton.setX(x + entryWidth - 42);
                this.removeButton.setY(y + (entryHeight - 20) / 2);
                this.removeButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (this.removeButton.mouseClicked(click, doubled)) {
                    return true;
                }
                return super.mouseClicked(click, doubled);
            }

            public Text getNarration() {
                return Text.translatable("narrator.entry.block", this.blockDisplayName.getString());
            }
        }
    }

    // --- 所有方块列表组件 ---
    class BlockListWidget extends EntryListWidget<BlockListWidget.BlockEntry> {
        private final List<BlockEntry> allEntries;

        public BlockListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.allEntries = Registries.BLOCK.stream()
                    .map(BlockEntry::new)
                    .sorted(Comparator.comparing(entry -> entry.blockIdentifier.toString()))
                    .collect(Collectors.toList());
            this.filter("");
        }

        private void updateEntries(List<BlockEntry> entries) {
            this.clearEntries();
            entries.forEach(this::addEntry);
        }

        public void filter(String search) {
            List<BlockEntry> filteredEntries;
            if (search.isEmpty()) {
                filteredEntries = this.allEntries;
            } else {
                String lowerCaseSearch = search.toLowerCase(Locale.ROOT);
                filteredEntries = this.allEntries.stream()
                        .filter(entry -> entry.blockIdentifier.toString().toLowerCase(Locale.ROOT).contains(lowerCaseSearch) ||
                                entry.blockDisplayName.getString().toLowerCase(Locale.ROOT).contains(lowerCaseSearch))
                        .collect(Collectors.toList());
            }
            this.updateEntries(filteredEntries);
            this.setScrollY(0);
        }

        public int getRowWidth() {
            return 180;
        }

        protected int getScrollbarPositionX() {
            return this.getX() + this.width + 6;
        }

        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.USAGE, Text.translatable("narration.allblocks.usage"));
        }

        class BlockEntry extends EntryListWidget.Entry<BlockEntry> {
            private final Identifier blockIdentifier;
            private final Text blockDisplayName;
            private final ButtonWidget addButton;

            public BlockEntry(Block block) {
                this.blockIdentifier = Registries.BLOCK.getId(block);
                this.blockDisplayName = block.getName();
                this.addButton = ButtonWidget.builder(Text.translatable("options.chainveinfabric.add"),
                        button -> {
                            Chainveinfabric.CONFIG.whitelistedBlocks.add(this.blockIdentifier.toString());
                            Chainveinfabric.CONFIG.save();
                            ChainVeinScreen.this.refreshLists();
                        })
                        .dimensions(0, 0, 40, 20).build();
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                // 使用 getX() 和 getY() 获取实际渲染坐标
                int x = this.getX();
                int y = this.getY();
                int entryWidth = this.getWidth();
                int entryHeight = this.getHeight();

                Block block = Registries.BLOCK.get(this.blockIdentifier);
                if (block != null && block.asItem() != null) {
                    context.drawItem(block.asItem().getDefaultStack(), x + 2, y + 2);
                }
                context.drawTextWithShadow(ChainVeinScreen.this.textRenderer, this.blockDisplayName, x + 24,
                        y + (entryHeight - 8) / 2, 0xFFFFFFFF);
                
                this.addButton.setX(x + entryWidth - 42);
                this.addButton.setY(y + (entryHeight - 20) / 2);
                this.addButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (this.addButton.mouseClicked(click, doubled)) {
                    return true;
                }
                return super.mouseClicked(click, doubled);
            }

            public Text getNarration() {
                return Text.translatable("narrator.entry.block", this.blockDisplayName.getString());
            }
        }
    }
}