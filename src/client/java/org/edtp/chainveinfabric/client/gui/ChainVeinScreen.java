package org.edtp.chainveinfabric.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.gui.widget.*;

import java.util.Arrays;

public class ChainVeinScreen extends Screen {
    private enum Tab {
        BASIC, SETTINGS
    }

    private Tab currentTab = Tab.BASIC;
    private Text chainVeinLabel;
    private TextFieldWidget searchBox;
    private EntryListWidget<?> allListWidget;
    private EntryListWidget<?> whitelistWidget;
    private DropdownWidget<ChainVeinConfig.ChainMode> modeDropdown;

    // Tab 2 elements
    private TextFieldWidget maxBlocksBox;
    private TextFieldWidget maxRadiusBox;
    private TextFieldWidget packetIntervalBox;
    private CyclingButtonWidget<Boolean> directToInventoryButton;
    private CyclingButtonWidget<Boolean> toolProtectionButton;
    private CyclingButtonWidget<Boolean> diagonalEdgeButton;
    private CyclingButtonWidget<Boolean> diagonalCornerButton;
    private DropdownWidget<ChainVeinConfig.SearchAlgorithm> searchAlgorithmDropdown;
    private TextFieldWidget sphereRadiusBox;
    private TextFieldWidget squareLengthBox;
    private DropdownWidget<ChainVeinConfig.MiningPoint> squarePointDropdown;
    private TextFieldWidget cuboidLBox;
    private TextFieldWidget cuboidWBox;
    private TextFieldWidget cuboidHBox;
    private DropdownWidget<ChainVeinConfig.MiningPoint> cuboidPointDropdown;

    public ChainVeinScreen() {
        super(Text.of("Chain Vein Config"));
    }

    @Override
    protected void init() {
        super.init();
        this.searchAlgorithmDropdown = null;
        this.squarePointDropdown = null;
        this.cuboidPointDropdown = null;
        this.chainVeinLabel = Text.translatable("options.chainveinfabric.chainVein");
        int centerX = this.width / 2;
        int topY = 40;

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("options.chainveinfabric.tab.basic"), button -> {
            this.currentTab = Tab.BASIC;
            this.clearAndInit();
        }).dimensions(centerX - 105, 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("options.chainveinfabric.tab.settings"), button -> {
            this.currentTab = Tab.SETTINGS;
            this.clearAndInit();
        }).dimensions(centerX + 5, 10, 100, 20).build());

        if (this.currentTab == Tab.BASIC) {
            initBasicTab(centerX, topY);
        } else {
            initSettingsTab(centerX, topY);
        }
    }

    private void initBasicTab(int centerX, int topY) {
        // Dropdown added FIRST for click priority
        this.modeDropdown = new DropdownWidget<>(centerX - 210, topY, 150, 20, 
            Text.translatable("options.chainveinfabric.mode"),
            Arrays.asList(ChainVeinConfig.ChainMode.values()),
            ChainveinfabricClient.CONFIG.mode,
            mode -> switch (mode) {
                case CHAIN_MINE -> Text.translatable("options.chainveinfabric.mode.mine");
                case CHAIN_PLANT -> Text.translatable("options.chainveinfabric.mode.plant");
                case CHAIN_UTILITY -> Text.translatable("options.chainveinfabric.mode.utility");
            },
            value -> {
                ChainveinfabricClient.CONFIG.mode = value;
                this.clearAndInit();
            },
            this.textRenderer
        );
        this.addSelectableChild(this.modeDropdown);

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.isChainVeinEnabled)
                .omitKeyText()
                .build(centerX + 150, topY, 60, 20, Text.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.isChainVeinEnabled = value;
                }));

        this.searchBox = new TextFieldWidget(this.textRenderer, centerX - 210, topY + 30, 420, 20,
                Text.translatable("options.chainveinfabric.search").copy().formatted(Formatting.GRAY));
        this.searchBox.setChangedListener(text -> this.refreshLists());
        this.addSelectableChild(this.searchBox);

        int listWidth = 200;
        int listTopY = topY + 65;
        int listHeight = this.height - listTopY - 20;

        if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_MINE) {
            BlockListWidget bList = new BlockListWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedBlocks);
            bList.setX(centerX - 210);
            this.allListWidget = bList;
            this.addDrawableChild(bList);

            WhitelistWidget wList = new WhitelistWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedBlocks);
            wList.setX(centerX + 10);
            this.whitelistWidget = wList;
            this.addDrawableChild(wList);
        } else if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            ItemListWidget iList = new ItemListWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists);
            iList.setX(centerX - 210);
            this.allListWidget = iList;
            this.addDrawableChild(iList);

            CropWhitelistWidget cwList = new CropWhitelistWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists);
            cwList.setX(centerX + 10);
            this.whitelistWidget = cwList;
            this.addDrawableChild(cwList);
        } else {
            BlockListWidget bList = new BlockListWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks);
            bList.setX(centerX - 210);
            this.allListWidget = bList;
            this.addDrawableChild(bList);

            WhitelistWidget wList = new WhitelistWidget(client, listWidth, listHeight, listTopY, 24, textRenderer, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks);
            wList.setX(centerX + 10);
            this.whitelistWidget = wList;
            this.addDrawableChild(wList);
        }
        this.refreshLists();
    }

    private void initSettingsTab(int centerX, int topY) {
        boolean isServerModPresent = ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID);

        // Search Algorithm Dropdown
        this.searchAlgorithmDropdown = new DropdownWidget<>(centerX + 10, topY, 150, 20,
            Text.translatable("options.chainveinfabric.searchAlgorithm"),
            Arrays.asList(ChainVeinConfig.SearchAlgorithm.values()),
            ChainveinfabricClient.CONFIG.searchAlgorithm,
            algo -> switch (algo) {
                case ADJACENT_SAME -> Text.translatable("options.chainveinfabric.searchAlgorithm.adjacent_same");
                case ADJACENT_WHITELIST -> Text.translatable("options.chainveinfabric.searchAlgorithm.adjacent_whitelist");
                case SPHERE -> Text.translatable("options.chainveinfabric.searchAlgorithm.sphere");
                case SQUARE -> Text.translatable("options.chainveinfabric.searchAlgorithm.square");
                case CUBOID -> Text.translatable("options.chainveinfabric.searchAlgorithm.cuboid");
            },
            value -> {
                ChainveinfabricClient.CONFIG.searchAlgorithm = value;
                this.clearAndInit();
            },
            this.textRenderer
        );
        this.addSelectableChild(this.searchAlgorithmDropdown);

        int currentY = topY + 30;

        // Dynamic Algorithm Settings
        if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SPHERE) {
            this.sphereRadiusBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 100, 20, Text.empty());
            this.sphereRadiusBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.sphereRadius));
            this.sphereRadiusBox.setChangedListener(text -> {
                try { ChainveinfabricClient.CONFIG.sphereRadius = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            });
            this.addSelectableChild(this.sphereRadiusBox);
            currentY += 30;
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SQUARE) {
            this.squareLengthBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 100, 20, Text.empty());
            this.squareLengthBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.squareLength));
            this.squareLengthBox.setChangedListener(text -> {
                try { ChainveinfabricClient.CONFIG.squareLength = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            });
            this.addSelectableChild(this.squareLengthBox);
            currentY += 30;

            this.squarePointDropdown = new DropdownWidget<>(centerX + 10, currentY, 100, 20,
                Text.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.values()),
                ChainveinfabricClient.CONFIG.squareMiningPoint,
                p -> switch (p) {
                    case CENTER -> Text.translatable("options.chainveinfabric.miningPoint.center");
                    case TOP_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.top_left");
                    case TOP_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.top_right");
                    case BOTTOM_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_left");
                    case BOTTOM_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_right");
                },
                v -> ChainveinfabricClient.CONFIG.squareMiningPoint = v,
                this.textRenderer
            );
            this.addSelectableChild(this.squarePointDropdown);
            currentY += 30;
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.CUBOID) {
            this.cuboidLBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 50, 20, Text.empty());
            this.cuboidLBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.cuboidL));
            this.cuboidLBox.setChangedListener(text -> {
                try { ChainveinfabricClient.CONFIG.cuboidL = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            });
            this.addSelectableChild(this.cuboidLBox);

            this.cuboidWBox = new TextFieldWidget(textRenderer, centerX + 70, currentY, 50, 20, Text.empty());
            this.cuboidWBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.cuboidW));
            this.cuboidWBox.setChangedListener(text -> {
                try { ChainveinfabricClient.CONFIG.cuboidW = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            });
            this.addSelectableChild(this.cuboidWBox);

            this.cuboidHBox = new TextFieldWidget(textRenderer, centerX + 130, currentY, 50, 20, Text.empty());
            this.cuboidHBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.cuboidH));
            this.cuboidHBox.setChangedListener(text -> {
                try { ChainveinfabricClient.CONFIG.cuboidH = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            });
            this.addSelectableChild(this.cuboidHBox);
            currentY += 30;

            this.cuboidPointDropdown = new DropdownWidget<>(centerX + 10, currentY, 100, 20,
                Text.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.values()),
                ChainveinfabricClient.CONFIG.cuboidMiningPoint,
                p -> switch (p) {
                    case CENTER -> Text.translatable("options.chainveinfabric.miningPoint.center");
                    case TOP_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.top_left");
                    case TOP_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.top_right");
                    case BOTTOM_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_left");
                    case BOTTOM_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_right");
                },
                v -> ChainveinfabricClient.CONFIG.cuboidMiningPoint = v,
                this.textRenderer
            );
            this.addSelectableChild(this.cuboidPointDropdown);
            currentY += 30;
        }

        currentY += 10; // Spacer

        this.maxBlocksBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 100, 20, Text.empty());
        this.maxBlocksBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.maxChainBlocks));
        this.maxBlocksBox.setChangedListener(text -> {
            try { ChainveinfabricClient.CONFIG.maxChainBlocks = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addSelectableChild(this.maxBlocksBox);
        currentY += 30;

        this.maxRadiusBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 100, 20, Text.empty());
        this.maxRadiusBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.maxRadius));
        this.maxRadiusBox.setChangedListener(text -> {
            try { ChainveinfabricClient.CONFIG.maxRadius = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addSelectableChild(this.maxRadiusBox);
        currentY += 30;

        this.directToInventoryButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.directToInventory)
                .omitKeyText().build(centerX + 10, currentY, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.directToInventory = v);
        this.directToInventoryButton.active = isServerModPresent;
        if (!isServerModPresent) {
            this.directToInventoryButton.setTooltip(Tooltip.of(Text.translatable("options.chainveinfabric.directToInventory.disabled")));
        }
        this.addDrawableChild(this.directToInventoryButton);
        currentY += 30;

        this.toolProtectionButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.toolProtection)
                .omitKeyText().build(centerX + 10, currentY, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.toolProtection = v);
        this.addDrawableChild(this.toolProtectionButton);
        currentY += 30;

        this.diagonalEdgeButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalEdge)
                .omitKeyText().build(centerX + 10, currentY, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalEdge = v);
        this.addDrawableChild(this.diagonalEdgeButton);
        currentY += 30;

        this.diagonalCornerButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalCorner)
                .omitKeyText().build(centerX + 10, currentY, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalCorner = v);
        this.addDrawableChild(this.diagonalCornerButton);
        currentY += 30;

        this.packetIntervalBox = new TextFieldWidget(textRenderer, centerX + 10, currentY, 100, 20, Text.empty());
        this.packetIntervalBox.setText(String.valueOf(ChainveinfabricClient.CONFIG.packetInterval));
        this.packetIntervalBox.setEditable(!isServerModPresent);
        if (isServerModPresent) {
            this.packetIntervalBox.setTooltip(Tooltip.of(Text.translatable("options.chainveinfabric.packetInterval.disabled")));
            this.packetIntervalBox.setUneditableColor(0xFF808080);
        }
        this.packetIntervalBox.setChangedListener(text -> {
            try { ChainveinfabricClient.CONFIG.packetInterval = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        });
        this.addSelectableChild(this.packetIntervalBox);
    }

    private void refreshLists() {
        if (allListWidget instanceof BlockListWidget) ((BlockListWidget) allListWidget).filter(searchBox.getText());
        else if (allListWidget instanceof ItemListWidget) ((ItemListWidget) allListWidget).filter(searchBox.getText());

        if (whitelistWidget instanceof WhitelistWidget) ((WhitelistWidget) whitelistWidget).refresh();
        else if (whitelistWidget instanceof CropWhitelistWidget) ((CropWhitelistWidget) whitelistWidget).refresh();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (currentTab == Tab.BASIC && modeDropdown != null && modeDropdown.mouseClicked(click, doubled)) return true;
        if (currentTab == Tab.SETTINGS) {
            if (searchAlgorithmDropdown != null && searchAlgorithmDropdown.mouseClicked(click, doubled)) return true;
            if (squarePointDropdown != null && squarePointDropdown.mouseClicked(click, doubled)) return true;
            if (cuboidPointDropdown != null && cuboidPointDropdown.mouseClicked(click, doubled)) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int topY = 40;

        if (currentTab == Tab.BASIC) {
            searchBox.render(context, mouseX, mouseY, delta);
            String allText = ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT ? "options.chainveinfabric.allCrops" : "options.chainveinfabric.allBlocks";
            String whitelistText = switch (ChainveinfabricClient.CONFIG.mode) {
                case CHAIN_MINE -> "options.chainveinfabric.whitelist";
                case CHAIN_PLANT -> "options.chainveinfabric.cropWhitelist";
                case CHAIN_UTILITY -> "options.chainveinfabric.utilityWhitelist";
            };
            context.drawTextWithShadow(textRenderer, Text.translatable(allText), centerX - 210, topY + 55, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.translatable(whitelistText), centerX + 10, topY + 55, 0xFFFFFFFF);
            int labelX = centerX + 150 - textRenderer.getWidth(chainVeinLabel) - 10;
            context.drawTextWithShadow(textRenderer, chainVeinLabel, labelX, topY + (20 - textRenderer.fontHeight) / 2, 0xFFFFFFFF);
            if (modeDropdown != null) modeDropdown.render(context, mouseX, mouseY, delta);
        } else {
            int currentY = topY + 30;
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SPHERE) {
                context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.sphereRadius"), centerX - 120, currentY + 5, 0xFFFFFFFF);
                if (sphereRadiusBox != null) sphereRadiusBox.render(context, mouseX, mouseY, delta);
                currentY += 30;
            } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SQUARE) {
                context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.squareLength"), centerX - 120, currentY + 5, 0xFFFFFFFF);
                if (squareLengthBox != null) squareLengthBox.render(context, mouseX, mouseY, delta);
                currentY += 30;
                context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.miningPoint"), centerX - 120, currentY + 5, 0xFFFFFFFF);
                // Dropdown render moved down
                currentY += 30;
            } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.CUBOID) {
                String cuboidLabel = Text.translatable("options.chainveinfabric.cuboidL").getString() + "/" +
                        Text.translatable("options.chainveinfabric.cuboidW").getString() + "/" +
                        Text.translatable("options.chainveinfabric.cuboidH").getString();
                context.drawTextWithShadow(textRenderer, Text.literal(cuboidLabel), centerX - 120, currentY + 5, 0xFFFFFFFF);
                if (cuboidLBox != null) cuboidLBox.render(context, mouseX, mouseY, delta);
                if (cuboidWBox != null) cuboidWBox.render(context, mouseX, mouseY, delta);
                if (cuboidHBox != null) cuboidHBox.render(context, mouseX, mouseY, delta);
                currentY += 30;
                context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.miningPoint"), centerX - 120, currentY + 5, 0xFFFFFFFF);
                // Dropdown render moved down
                currentY += 30;
            }

            currentY += 10;
            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.maxBlocks"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (maxBlocksBox != null) maxBlocksBox.render(context, mouseX, mouseY, delta);
            currentY += 30;
            
            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.maxRadius"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (maxRadiusBox != null) maxRadiusBox.render(context, mouseX, mouseY, delta);
            currentY += 30;

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.directToInventory"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (directToInventoryButton != null) directToInventoryButton.render(context, mouseX, mouseY, delta);
            currentY += 30;

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.toolProtection"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (toolProtectionButton != null) toolProtectionButton.render(context, mouseX, mouseY, delta);
            currentY += 30;

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.diagonalEdge"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (diagonalEdgeButton != null) diagonalEdgeButton.render(context, mouseX, mouseY, delta);
            currentY += 30;

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.diagonalCorner"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (diagonalCornerButton != null) diagonalCornerButton.render(context, mouseX, mouseY, delta);
            currentY += 30;

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.packetInterval"), centerX - 120, currentY + 5, 0xFFFFFFFF);
            if (packetIntervalBox != null) packetIntervalBox.render(context, mouseX, mouseY, delta);

            // RENDER DROPDOWNS LAST TO OVERLAY
            // Draw in reverse order of Y position (Top-most drawn LAST)
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SQUARE) {
                if (squarePointDropdown != null) squarePointDropdown.render(context, mouseX, mouseY, delta);
            } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.CUBOID) {
                if (cuboidPointDropdown != null) cuboidPointDropdown.render(context, mouseX, mouseY, delta);
            }
            if (searchAlgorithmDropdown != null) searchAlgorithmDropdown.render(context, mouseX, mouseY, delta);

            context.drawTextWithShadow(textRenderer, Text.translatable("options.chainveinfabric.searchAlgorithm"), centerX - 120, topY + 5, 0xFFFFFFFF);
        }
    }

    @Override
    public void close() {
        ChainveinfabricClient.CONFIG.save();
        super.close();
    }
}
