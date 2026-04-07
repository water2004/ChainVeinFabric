package org.edtp.chainveinfabric.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
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
    private Component chainVeinLabel;
    private EditBox searchBox;
    private AbstractSelectionList<?> allListWidget;
    private AbstractSelectionList<?> whitelistWidget;
    private SettingsListWidget settingsList;
    private DropdownWidget<ChainVeinConfig.ChainMode> modeDropdown;

    public ChainVeinScreen() {
        super(Component.nullToEmpty("Chain Vein Config"));
    }

    @Override
    protected void init() {
        super.init();
        this.chainVeinLabel = Component.translatable("options.chainveinfabric.chainVein");
        int centerX = this.width / 2;
        int topY = 40;

        // Tab buttons
        this.addRenderableWidget(Button.builder(Component.translatable("options.chainveinfabric.tab.basic"), button -> {
            this.currentTab = Tab.BASIC;
            this.rebuildWidgets();
        }).bounds(centerX - 105, 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("options.chainveinfabric.tab.settings"), button -> {
            this.currentTab = Tab.SETTINGS;
            this.rebuildWidgets();
        }).bounds(centerX + 5, 10, 100, 20).build());

        if (this.currentTab == Tab.BASIC) {
            initBasicTab(centerX, topY);
        } else {
            initSettingsTab(centerX, topY);
        }
    }

    private void initBasicTab(int centerX, int topY) {
        // Dropdown added FIRST for click priority
        this.modeDropdown = new DropdownWidget<>(centerX - 210, topY, 150, 20, 
            Component.translatable("options.chainveinfabric.mode"),
            Arrays.asList(ChainVeinConfig.ChainMode.values()),
            ChainveinfabricClient.CONFIG.mode,
            mode -> switch (mode) {
                case CHAIN_MINE -> Component.translatable("options.chainveinfabric.mode.mine");
                case CHAIN_PLANT -> Component.translatable("options.chainveinfabric.mode.plant");
                case CHAIN_UTILITY -> Component.translatable("options.chainveinfabric.mode.utility");
            },
            value -> {
                ChainveinfabricClient.CONFIG.mode = value;
                this.rebuildWidgets();
            },
            this.font
        );
        this.addWidget(this.modeDropdown);

        this.addRenderableWidget(CycleButton.onOffBuilder(ChainveinfabricClient.CONFIG.isChainVeinEnabled)
                .displayOnlyValue()
                .create(centerX + 150, topY, 60, 20, Component.empty(), (button, value) -> {
                    ChainveinfabricClient.CONFIG.isChainVeinEnabled = value;
                }));

        this.searchBox = new EditBox(this.font, centerX - 210, topY + 30, 420, 20,
                Component.translatable("options.chainveinfabric.search").copy().withStyle(ChatFormatting.GRAY));
        this.searchBox.setResponder(text -> this.refreshLists());
        this.addWidget(this.searchBox);

        int listWidth = 200;
        int listTopY = topY + 65;
        int listHeight = this.height - listTopY - 20;

        if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_MINE) {
            BlockListWidget bList = new BlockListWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedBlocks);
            bList.setX(centerX - 210);
            this.allListWidget = bList;
            this.addRenderableWidget(bList);

            WhitelistWidget wList = new WhitelistWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedBlocks);
            wList.setX(centerX + 10);
            this.whitelistWidget = wList;
            this.addRenderableWidget(wList);
        } else if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            ItemListWidget iList = new ItemListWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists);
            iList.setX(centerX - 210);
            this.allListWidget = iList;
            this.addRenderableWidget(iList);

            CropWhitelistWidget cwList = new CropWhitelistWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists);
            cwList.setX(centerX + 10);
            this.whitelistWidget = cwList;
            this.addRenderableWidget(cwList);
        } else {
            BlockListWidget bList = new BlockListWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks);
            bList.setX(centerX - 210);
            this.allListWidget = bList;
            this.addRenderableWidget(bList);

            WhitelistWidget wList = new WhitelistWidget(minecraft, listWidth, listHeight, listTopY, 24, font, this::refreshLists, ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks);
            wList.setX(centerX + 10);
            this.whitelistWidget = wList;
            this.addRenderableWidget(wList);
        }
        this.refreshLists();
    }

    private EditBox createNumBox(int width, int initialValue, Consumer<Integer> onUpdate) {
        EditBox box = new EditBox(font, 0, 0, width, 20, Component.empty());
        box.setValue(String.valueOf(initialValue));
        box.setFilter(text -> text.isEmpty() || text.matches("^-?[0-9]*$"));
        box.setResponder(text -> {
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

        settingsList = new SettingsListWidget(minecraft, listWidth, listHeight, topY, 24, font);
        settingsList.setX(centerX - 180);
        this.addRenderableWidget(settingsList);

        // Algorithm
        DropdownWidget<ChainVeinConfig.SearchAlgorithm> algoDropdown = new DropdownWidget<>(0, 0, 150, 20,
            Component.translatable("options.chainveinfabric.searchAlgorithm"),
            Arrays.asList(ChainVeinConfig.SearchAlgorithm.values()),
            ChainveinfabricClient.CONFIG.searchAlgorithm,
            algo -> switch (algo) {
                case ADJACENT_SAME -> Component.translatable("options.chainveinfabric.searchAlgorithm.adjacent_same");
                case ADJACENT_WHITELIST -> Component.translatable("options.chainveinfabric.searchAlgorithm.adjacent_whitelist");
                case SPHERE -> Component.translatable("options.chainveinfabric.searchAlgorithm.sphere");
                case SQUARE -> Component.translatable("options.chainveinfabric.searchAlgorithm.square");
                case CUBOID -> Component.translatable("options.chainveinfabric.searchAlgorithm.cuboid");
            },
            value -> {
                ChainveinfabricClient.CONFIG.searchAlgorithm = value;
                this.rebuildWidgets();
            },
            this.font
        );
        settingsList.addControl(Component.translatable("options.chainveinfabric.searchAlgorithm"), algoDropdown);

        if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SPHERE) {
            settingsList.addControl(Component.translatable("options.chainveinfabric.sphereRadius"),
                createNumBox(50, ChainveinfabricClient.CONFIG.sphereRadius, v -> ChainveinfabricClient.CONFIG.sphereRadius = v));
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.SQUARE) {
            settingsList.addControl(Component.translatable("options.chainveinfabric.squareLength"),
                createNumBox(50, ChainveinfabricClient.CONFIG.squareLength, v -> ChainveinfabricClient.CONFIG.squareLength = v));

            DropdownWidget<ChainVeinConfig.MiningPoint> squarePointDropdown = new DropdownWidget<>(0, 0, 150, 20,
                Component.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.CENTER, ChainVeinConfig.MiningPoint.FRONT_TOP_LEFT, ChainVeinConfig.MiningPoint.FRONT_TOP_RIGHT, ChainVeinConfig.MiningPoint.BACK_BOTTOM_LEFT, ChainVeinConfig.MiningPoint.BACK_BOTTOM_RIGHT),
                ChainveinfabricClient.CONFIG.squareMiningPoint,
                p -> switch (p) {
                    case CENTER -> Component.translatable("options.chainveinfabric.miningPoint.center");
                    case FRONT_TOP_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.top_left");
                    case FRONT_TOP_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.top_right");
                    case BACK_BOTTOM_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.bottom_left");
                    case BACK_BOTTOM_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.bottom_right");
                    default -> Component.literal(p.name());
                },
                v -> ChainveinfabricClient.CONFIG.squareMiningPoint = v,
                this.font
            );
            settingsList.addControl(Component.translatable("options.chainveinfabric.miningPoint"), squarePointDropdown);
        } else if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.CUBOID) {
            EditBox cuboidLBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidL, v -> ChainveinfabricClient.CONFIG.cuboidL = v);
            EditBox cuboidWBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidW, v -> ChainveinfabricClient.CONFIG.cuboidW = v);
            EditBox cuboidHBox = createNumBox(40, ChainveinfabricClient.CONFIG.cuboidH, v -> ChainveinfabricClient.CONFIG.cuboidH = v);
            settingsList.addMultiControl(Component.translatable("options.chainveinfabric.cuboidL").append("/").append(Component.translatable("options.chainveinfabric.cuboidW")).append("/").append(Component.translatable("options.chainveinfabric.cuboidH")), 
                List.of(cuboidLBox, cuboidWBox, cuboidHBox));

            DropdownWidget<ChainVeinConfig.MiningPoint> cuboidPointDropdown = new DropdownWidget<>(0, 0, 150, 20,
                Component.translatable("options.chainveinfabric.miningPoint"),
                Arrays.asList(ChainVeinConfig.MiningPoint.values()),
                ChainveinfabricClient.CONFIG.cuboidMiningPoint,
                p -> switch (p) {
                    case CENTER -> Component.translatable("options.chainveinfabric.miningPoint.center");
                    case FRONT_TOP_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.front_top_left");
                    case FRONT_TOP_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.front_top_right");
                    case FRONT_BOTTOM_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.front_bottom_left");
                    case FRONT_BOTTOM_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.front_bottom_right");
                    case BACK_TOP_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.back_top_left");
                    case BACK_TOP_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.back_top_right");
                    case BACK_BOTTOM_LEFT -> Component.translatable("options.chainveinfabric.miningPoint.back_bottom_left");
                    case BACK_BOTTOM_RIGHT -> Component.translatable("options.chainveinfabric.miningPoint.back_bottom_right");
                },
                v -> ChainveinfabricClient.CONFIG.cuboidMiningPoint = v,
                this.font
            );
            settingsList.addControl(Component.translatable("options.chainveinfabric.miningPoint"), cuboidPointDropdown);
        }

        // Common settings
        settingsList.addControl(Component.translatable("options.chainveinfabric.maxBlocks"),
            createNumBox(50, ChainveinfabricClient.CONFIG.maxChainBlocks, v -> ChainveinfabricClient.CONFIG.maxChainBlocks = v));

        settingsList.addControl(Component.translatable("options.chainveinfabric.maxRadius"),
            createNumBox(50, ChainveinfabricClient.CONFIG.maxRadius, v -> ChainveinfabricClient.CONFIG.maxRadius = v));

        CycleButton<Boolean> directToInventoryButton = CycleButton.onOffBuilder(ChainveinfabricClient.CONFIG.directToInventory)
                .displayOnlyValue().create(0, 0, 100, 20, Component.empty(), (b, v) -> ChainveinfabricClient.CONFIG.directToInventory = v);
        directToInventoryButton.active = isServerModPresent;
        if (!isServerModPresent) {
            directToInventoryButton.setTooltip(Tooltip.create(Component.translatable("options.chainveinfabric.directToInventory.disabled")));
        }
        settingsList.addControl(Component.translatable("options.chainveinfabric.directToInventory"), directToInventoryButton);

        CycleButton<Boolean> toolProtectionButton = CycleButton.onOffBuilder(ChainveinfabricClient.CONFIG.toolProtection)
                .displayOnlyValue().create(0, 0, 100, 20, Component.empty(), (b, v) -> ChainveinfabricClient.CONFIG.toolProtection = v);
        settingsList.addControl(Component.translatable("options.chainveinfabric.toolProtection"), toolProtectionButton);

        CycleButton<Boolean> diagonalEdgeButton = CycleButton.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalEdge)
                .displayOnlyValue().create(0, 0, 100, 20, Component.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalEdge = v);
        settingsList.addControl(Component.translatable("options.chainveinfabric.diagonalEdge"), diagonalEdgeButton);

        CycleButton<Boolean> diagonalCornerButton = CycleButton.onOffBuilder(ChainveinfabricClient.CONFIG.diagonalCorner)
                .displayOnlyValue().create(0, 0, 100, 20, Component.empty(), (b, v) -> ChainveinfabricClient.CONFIG.diagonalCorner = v);
        settingsList.addControl(Component.translatable("options.chainveinfabric.diagonalCorner"), diagonalCornerButton);

        EditBox packetIntervalBox = createNumBox(50, ChainveinfabricClient.CONFIG.packetInterval, v -> ChainveinfabricClient.CONFIG.packetInterval = v);
        packetIntervalBox.active = !isServerModPresent;
        packetIntervalBox.setEditable(!isServerModPresent);
        if (isServerModPresent) {
            packetIntervalBox.setTooltip(Tooltip.create(Component.translatable("options.chainveinfabric.packetInterval.disabled")));
        }
        settingsList.addControl(Component.translatable("options.chainveinfabric.packetInterval"), packetIntervalBox);
    }

    private void refreshLists() {
        if (allListWidget instanceof BlockListWidget) ((BlockListWidget) allListWidget).filter(searchBox.getValue());
        else if (allListWidget instanceof ItemListWidget) ((ItemListWidget) allListWidget).filter(searchBox.getValue());

        if (whitelistWidget instanceof WhitelistWidget) ((WhitelistWidget) whitelistWidget).refresh();
        else if (whitelistWidget instanceof CropWhitelistWidget) ((CropWhitelistWidget) whitelistWidget).refresh();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(context);
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
            context.drawString(font, Component.translatable(allText), centerX - 210, topY + 55, 0xFFFFFFFF);
            context.drawString(font, Component.translatable(whitelistText), centerX + 10, topY + 55, 0xFFFFFFFF);
            int labelX = centerX + 150 - font.width(chainVeinLabel) - 10;
            context.drawString(font, chainVeinLabel, labelX, topY + (20 - font.lineHeight) / 2, 0xFFFFFFFF);
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
    public void onClose() {
        ChainveinfabricClient.CONFIG.save();
        super.onClose();
    }
}
