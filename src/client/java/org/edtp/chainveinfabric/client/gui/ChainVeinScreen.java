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
import java.util.List;
import java.util.function.Consumer;

public class ChainVeinScreen extends Screen {
    private enum Tab {
        BASIC, SETTINGS
    }

    private Tab currentTab = Tab.BASIC;
    private Text chainVeinLabel;
    private TextFieldWidget searchBox;
    private EntryListWidget<?> allListWidget;
    private EntryListWidget<?> whitelistWidget;
    private SettingsListWidget settingsList;
    private DropdownWidget<ChainVeinConfig.ChainMode> modeDropdown;

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

    private TextFieldWidget createNumBox(int width, int initialValue, Consumer<Integer> onUpdate) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        box.setText(String.valueOf(initialValue));
        box.setTextPredicate(text -> text.isEmpty() || text.matches("^-?[0-9]*$"));
        box.setChangedListener(text -> {
            if (!text.isEmpty() && !text.equals("-")) {
                try { 
                    onUpdate.accept(Integer.parseInt(text)); 
                } catch (NumberFormatException ignored) {}
            }
        });
        return box;
    }

    private void initSettingsTab(int centerX, int topY) {
        boolean isServerModPresent = ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID);
        int listWidth = 360;
        int listHeight = this.height - topY - 30;

        settingsList = new SettingsListWidget(client, listWidth, listHeight, topY, 24, textRenderer);
        settingsList.setX(centerX - 180);
        this.addDrawableChild(settingsList);

        // Algorithm
        DropdownWidget<ChainVeinConfig.SearchAlgorithm> algoDropdown = new DropdownWidget<>(0, 0, 150, 20,
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
        settingsList.addControl(Text.translatable("options.chainveinfabric.searchAlgorithm"), algoDropdown);

        if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SPHERE) {
            settingsList.addControl(Text.translatable("options.chainveinfabric.sphereRadius"),
                createNumBox(50, ChainveinfabricClient.CONFIG.sphereRadius, v -> ChainveinfabricClient.CONFIG.sphereRadius = v));
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SQUARE) {
            settingsList.addControl(Text.translatable("options.chainveinfabric.squareLength"),
                createNumBox(50, ChainveinfabricClient.CONFIG.squareLength, v -> ChainveinfabricClient.CONFIG.squareLength = v));

            DropdownWidget<ChainVeinConfig.MiningPoint> squarePointDropdown = new DropdownWidget<>(0, 0, 150, 20,
                Text.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.CENTER, ChainVeinConfig.MiningPoint.FRONT_TOP_LEFT, ChainVeinConfig.MiningPoint.FRONT_TOP_RIGHT, ChainVeinConfig.MiningPoint.BACK_BOTTOM_LEFT, ChainVeinConfig.MiningPoint.BACK_BOTTOM_RIGHT),
                ChainveinfabricClient.CONFIG.squareMiningPoint,
                p -> switch (p) {
                    case CENTER -> Text.translatable("options.chainveinfabric.miningPoint.center");
                    case FRONT_TOP_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.top_left");
                    case FRONT_TOP_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.top_right");
                    case BACK_BOTTOM_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_left");
                    case BACK_BOTTOM_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.bottom_right");
                    default -> Text.literal(p.name());
                },
                v -> ChainveinfabricClient.CONFIG.squareMiningPoint = v,
                this.textRenderer
            );
            settingsList.addControl(Text.translatable("options.chainveinfabric.miningPoint"), squarePointDropdown);
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.CUBOID) {
            TextFieldWidget cuboidLBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidL, v -> ChainveinfabricClient.CONFIG.cuboidL = v);
            TextFieldWidget cuboidWBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidW, v -> ChainveinfabricClient.CONFIG.cuboidW = v);
            TextFieldWidget cuboidHBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidH, v -> ChainveinfabricClient.CONFIG.cuboidH = v);
            settingsList.addMultiControl(Text.translatable("options.chainveinfabric.cuboidL").append("/").append(Text.translatable("options.chainveinfabric.cuboidW")).append("/").append(Text.translatable("options.chainveinfabric.cuboidH")), 
                List.of(cuboidLBox, cuboidWBox, cuboidHBox));

            DropdownWidget<ChainVeinConfig.MiningPoint> cuboidPointDropdown = new DropdownWidget<>(0, 0, 150, 20,
                Text.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.values()),
                ChainveinfabricClient.CONFIG.cuboidMiningPoint,
                p -> switch (p) {
                    case CENTER -> Text.translatable("options.chainveinfabric.miningPoint.center");
                    case FRONT_TOP_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.front_top_left");
                    case FRONT_TOP_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.front_top_right");
                    case FRONT_BOTTOM_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.front_bottom_left");
                    case FRONT_BOTTOM_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.front_bottom_right");
                    case BACK_TOP_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.back_top_left");
                    case BACK_TOP_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.back_top_right");
                    case BACK_BOTTOM_LEFT -> Text.translatable("options.chainveinfabric.miningPoint.back_bottom_left");
                    case BACK_BOTTOM_RIGHT -> Text.translatable("options.chainveinfabric.miningPoint.back_bottom_right");
                },
                v -> ChainveinfabricClient.CONFIG.cuboidMiningPoint = v,
                this.textRenderer
            );
            settingsList.addControl(Text.translatable("options.chainveinfabric.miningPoint"), cuboidPointDropdown);
        }

        // Common settings
        settingsList.addControl(Text.translatable("options.chainveinfabric.maxBlocks"),
            createNumBox(50, ChainveinfabricClient.CONFIG.maxChainBlocks, v -> ChainveinfabricClient.CONFIG.maxChainBlocks = v));

        settingsList.addControl(Text.translatable("options.chainveinfabric.maxRadius"),
            createNumBox(50, ChainveinfabricClient.CONFIG.maxRadius, v -> ChainveinfabricClient.CONFIG.maxRadius = v));

        CyclingButtonWidget<Boolean> directToInventoryButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.directToInventory)
                .omitKeyText().build(0, 0, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.directToInventory = v);
        directToInventoryButton.active = isServerModPresent;
        if (!isServerModPresent) {
            directToInventoryButton.setTooltip(Tooltip.of(Text.translatable("options.chainveinfabric.directToInventory.disabled")));
        }
        settingsList.addControl(Text.translatable("options.chainveinfabric.directToInventory"), directToInventoryButton);

        CyclingButtonWidget<Boolean> toolProtectionButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.toolProtection)
                .omitKeyText().build(0, 0, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.toolProtection = v);
        settingsList.addControl(Text.translatable("options.chainveinfabric.toolProtection"), toolProtectionButton);

        CyclingButtonWidget<Boolean> diagonalEdgeButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalEdge)
                .omitKeyText().build(0, 0, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalEdge = v);
        settingsList.addControl(Text.translatable("options.chainveinfabric.diagonalEdge"), diagonalEdgeButton);

        CyclingButtonWidget<Boolean> diagonalCornerButton = CyclingButtonWidget.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalCorner)
                .omitKeyText().build(0, 0, 100, 20, Text.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalCorner = v);
        settingsList.addControl(Text.translatable("options.chainveinfabric.diagonalCorner"), diagonalCornerButton);

        TextFieldWidget packetIntervalBox = createNumBox(50, ChainveinfabricClient.CONFIG.packetInterval, v -> ChainveinfabricClient.CONFIG.packetInterval = v);
        packetIntervalBox.active = !isServerModPresent;
        packetIntervalBox.setEditable(!isServerModPresent);
        if (isServerModPresent) {
            packetIntervalBox.setTooltip(Tooltip.of(Text.translatable("options.chainveinfabric.packetInterval.disabled")));
        }
        settingsList.addControl(Text.translatable("options.chainveinfabric.packetInterval"), packetIntervalBox);
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
        
        // Priority for expanded dropdown overlays in SETTINGS tab
        if (currentTab == Tab.SETTINGS && settingsList != null) {
            for (SettingsListWidget.SettingEntry entry : settingsList.children()) {
                if (entry instanceof SettingsListWidget.ControlEntry) {
                    for (var widget : ((SettingsListWidget.ControlEntry) entry).getWidgets()) {
                        if (widget instanceof DropdownWidget && ((DropdownWidget<?>) widget).isMouseOverOverlay(click.x(), click.y())) {
                            if (widget.mouseClicked(click, doubled)) return true;
                        }
                    }
                }
            }
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
            if (modeDropdown != null) {
                modeDropdown.render(context, mouseX, mouseY, delta);
                modeDropdown.renderOverlay(context, mouseX, mouseY, delta);
            }
        } else {
            // Render Settings Tab (list is already drawn by super.render)
            if (settingsList != null) {
                for (SettingsListWidget.SettingEntry entry : settingsList.children()) {
                    if (entry instanceof SettingsListWidget.ControlEntry) {
                        for (var widget : ((SettingsListWidget.ControlEntry) entry).getWidgets()) {
                            if (widget instanceof DropdownWidget) {
                                ((DropdownWidget<?>) widget).renderOverlay(context, mouseX, mouseY, delta);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        ChainveinfabricClient.CONFIG.save();
        super.close();
    }
}
