package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;
import org.edtp.chainveinfabric.client.logic.PlantingItems;
import org.edtp.chainveinfabric.client.logic.WhitelistImportService;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;

import static org.edtp.chainveinfabric.client.gui.malilib.GuiChainVeinLayout.BASIC_BODY_MAX_WIDTH;
import static org.edtp.chainveinfabric.client.gui.malilib.GuiChainVeinLayout.CONTROL_HEIGHT;
import static org.edtp.chainveinfabric.client.gui.malilib.GuiChainVeinLayout.PAGE_MARGIN;

public class GuiChainVein extends GuiConfigsBase {

    enum Tab { BASIC, SETTINGS, HOTKEYS, PRESETS }
    private enum BasicPane { AVAILABLE, WHITELIST }

    private Tab currentTab = Tab.BASIC;
    private BasicPane compactPane = BasicPane.AVAILABLE;
    private final List<OverlayDropdown<?>> activeDropdowns = new ArrayList<>();
    private ChainVeinConfig.ChainMode presetWhitelistMode = ChainVeinConfig.ChainMode.CHAIN_MINE;
    private boolean controlClick;

    private WidgetChainList leftList;
    private WidgetChainList rightList;
    private WidgetSearchBar searchBar;
    private PresetListWidget presetList;
    private GuiChainVeinLayout.BasicLayout basicLayout;
    private boolean compactHeader;

    public GuiChainVein() {
        super(20, 40, "chainveinfabric", null, "options.chainveinfabric.chainVein");
        this.setConfigWidth(108);
        this.setHoverInfoProvider(config -> {
            String key = config.getName() + ".comment";
            String comment = StringUtils.translate(key);
            return comment.equals(key) ? null : comment;
        });
        ConfigProxies.setAlgorithmChangeListener(() -> {
            if (this.currentTab == Tab.SETTINGS) {
                this.reCreateListWidget();
                this.initGui();
            }
        });
        ConfigProxies.load();
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 40;
    }

    @Override
    protected int getBrowserHeight() {
        return Math.max(40, this.height - this.getListY() - 40);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        if (!this.compactHeader) {
            super.drawTitle(ctx, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY) {
        if (this.currentTab != Tab.SETTINGS && this.currentTab != Tab.HOTKEYS) {
            return null;
        }
        return new ResponsiveConfigList(
                listX, listY, this.getBrowserWidth(), this.getBrowserHeight(),
                this.getConfigWidth(), this.useKeybindSearch(), this,
                this.activeDropdowns::add);
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<fi.dy.masa.malilib.config.IConfigBase> configs = new ArrayList<>();

        if (this.currentTab == Tab.HOTKEYS) {
            configs.addAll(ConfigProxies.getAvailableHotkeys());
            configs.add(ConfigProxies.ENABLE_CHAIN_VEIN_ON_MODE_HOTKEY);
            return this.createTranslatedConfigWrappers(configs);
        }

        configs.add(ConfigProxies.ALGO);
        boolean automatic = ChainveinfabricClient.isAutoMiningArmed();

        switch ((ConfigProxies.MAlgo) ConfigProxies.ALGO.getOptionListValue()) {
            case SPHERE:
                configs.add(ConfigProxies.SPHERE_RADIUS);
                break;
            case SQUARE:
                configs.add(ConfigProxies.SQUARE_LENGTH);
                if (!automatic) configs.add(ConfigProxies.SQUARE_POINT);
                break;
            case CUBOID:
                configs.add(ConfigProxies.CUBOID_L);
                configs.add(ConfigProxies.CUBOID_W);
                configs.add(ConfigProxies.CUBOID_H);
                if (!automatic) configs.add(ConfigProxies.CUBOID_POINT);
                break;
            default:
                break;
        }

        configs.add(ConfigProxies.MAX_BLOCKS);
        configs.add(ConfigProxies.MAX_RADIUS);
        configs.add(ConfigProxies.DIRECT_INV);
        if (QuickShulkerIntegration.isAvailable()) {
            configs.add(ConfigProxies.QUICK_SHULKER_OVERFLOW);
        }
        configs.add(ConfigProxies.TOOL_PROT);
        configs.add(ConfigProxies.DIAG_EDGE);
        configs.add(ConfigProxies.DIAG_CORNER);
        configs.add(ConfigProxies.PACKET_INV);
        if (ChainveinfabricClient.CONFIG.mode.isMiningMode()) {
            configs.add(ConfigProxies.AUTO_MINE_COOLDOWN);
        }

        return this.createTranslatedConfigWrappers(configs);
    }

    private List<ConfigOptionWrapper> createTranslatedConfigWrappers(
        List<fi.dy.masa.malilib.config.IConfigBase> configs) {
        for (fi.dy.masa.malilib.config.IConfigBase config : configs) {
            config.setTranslatedName(StringUtils.translate(config.getName()));
        }
        return ConfigOptionWrapper.createFor(configs);
    }

    @Override
    public void initGui() {
        GuiChainVeinLayout.HeaderLayout headerLayout = this.createHeaderLayout();
        this.compactHeader = headerLayout.hideTitle;
        this.setListPosition(20, headerLayout.contentY);
        super.initGui();
        this.activeDropdowns.clear();
        this.leftList = null;
        this.rightList = null;
        this.searchBar = null;
        this.presetList = null;
        this.basicLayout = null;

        this.addTabNavigation(headerLayout);

        int topY = headerLayout.contentY;
        if (this.currentTab == Tab.BASIC) {
            initBasicTab(topY);
        } else if (this.currentTab == Tab.PRESETS) {
            initPresetTab(topY);
        }
    }

    private GuiChainVeinLayout.HeaderLayout createHeaderLayout() {
        List<Tab> tabs = List.of(Tab.BASIC, Tab.SETTINGS, Tab.HOTKEYS, Tab.PRESETS);
        List<Integer> tabWidths = tabs.stream()
                .map(tab -> "options.chainveinfabric.tab." + tab.name().toLowerCase(Locale.ROOT))
                .map(StringUtils::translate)
                .map(label -> this.getStringWidth(label) + 10)
                .toList();
        return GuiChainVeinLayout.header(
                this.width, this.getStringWidth(this.getTitleString()), tabWidths);
    }

    private void addTabNavigation(GuiChainVeinLayout.HeaderLayout layout) {
        if (layout.collapsed) {
            this.addCollapsedTabDropdown(layout);
        } else {
            this.addTabButtons(layout);
        }
    }

    private void addCollapsedTabDropdown(GuiChainVeinLayout.HeaderLayout layout) {
        List<Tab> tabs = List.of(Tab.BASIC, Tab.SETTINGS, Tab.HOTKEYS, Tab.PRESETS);
        OverlayDropdown<Tab> tabDropdown = new OverlayDropdown<Tab>(
            layout.tabX, layout.tabY, layout.tabWidth, CONTROL_HEIGHT, 120, tabs.size(), tabs,
            tab -> StringUtils.translate("options.chainveinfabric.tab." + tab.name().toLowerCase(Locale.ROOT))
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                Tab selected = this.getSelectedEntry();
                if (selected != null && selected != currentTab) {
                    selectTab(selected);
                }
            }
        };
        this.activeDropdowns.add(tabDropdown);
        tabDropdown.setSelectedEntry(this.currentTab);
        this.addWidget(tabDropdown);
    }

    private void addTabButtons(GuiChainVeinLayout.HeaderLayout layout) {
        List<Tab> tabs = List.of(Tab.BASIC, Tab.SETTINGS, Tab.HOTKEYS, Tab.PRESETS);
        List<ButtonGeneric> tabButtons = new ArrayList<>();
        for (Tab tab : tabs) {
            tabButtons.add(this.createTabButton(tab));
        }

        int gap = layout.tabGap;
        int totalWidth = -gap;
        for (ButtonGeneric button : tabButtons) {
            totalWidth += button.getWidth() + gap;
        }

        int x = layout.tabX + Math.max(0, (layout.tabWidth - totalWidth) / 2);
        for (int i = 0; i < tabButtons.size(); i++) {
            ButtonGeneric button = tabButtons.get(i);
            Tab tab = tabs.get(i);
            button.setPosition(x, layout.tabY);
            this.addButton(button, (btn, mouseButton) -> this.selectTab(tab));
            x += button.getWidth() + gap;
        }
    }

    void selectTab(Tab tab) {
        if (tab == this.currentTab) return;
        this.currentTab = tab;
        this.reCreateListWidget();
        this.initGui();
    }

    private ButtonGeneric createTabButton(Tab tab) {
        String key = "options.chainveinfabric.tab." + tab.name().toLowerCase(Locale.ROOT);
        ButtonGeneric button = new ButtonGeneric(0, 0, -1, 20, StringUtils.translate(key));
        button.setEnabled(this.currentTab != tab);
        return button;
    }

    private void initBasicTab(int topY) {
        ChainVeinConfig.ChainMode mode = ChainveinfabricClient.CONFIG.mode;
        this.basicLayout = this.createBasicLayout(topY, mode);

        // Mode dropdown
        List<ChainVeinConfig.ChainMode> modes = getAvailableModes();
        OverlayDropdown<ChainVeinConfig.ChainMode> modeDropdown = new OverlayDropdown<ChainVeinConfig.ChainMode>(
            this.basicLayout.mode.x,
            this.basicLayout.mode.y,
            this.basicLayout.mode.width,
            this.basicLayout.mode.height,
            200,
            5,
            modes,
            this::getBasicModeString
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                ChainVeinConfig.ChainMode selected = this.getSelectedEntry();
                if (selected != null && ChainveinfabricClient.CONFIG.mode != selected) {
                    ChainveinfabricClient.disarmAutoMining();
                    ChainveinfabricClient.CONFIG.mode = selected;
                    ChainveinfabricClient.CONFIG.save();
                    initGui();
                }
            }
        };
        this.activeDropdowns.add(modeDropdown);
        modeDropdown.setSelectedEntry(ChainveinfabricClient.CONFIG.mode);
        this.addWidget(modeDropdown);

        // Toggle enabled
        GuiChainVeinLayout.Rect toggle = this.basicLayout.toggle;
        ButtonGeneric toggleBtn = new ButtonGeneric(toggle.x, toggle.y, toggle.width, toggle.height, getToggleString());
        this.updateAutoToggleTooltip(toggleBtn);
        this.addButton(toggleBtn, (button, mb) -> {
            if (mb == 0 && ChainveinfabricClient.isAutoMiningArmed()) {
                ChainveinfabricClient.disarmAutoMining();
                ChainveinfabricClient.CONFIG.isChainVeinEnabled = false;
                ChainveinfabricClient.CONFIG.save();
                button.setDisplayString(getToggleString());
                this.updateAutoToggleTooltip(button);
                return;
            }

            if (mb == 0 && this.controlClick
                    && ChainveinfabricClient.CONFIG.mode.isMiningMode()) {
                ChainveinfabricClient.toggleAutoMining();
                ChainveinfabricClient.CONFIG.save();
                button.setDisplayString(getToggleString());
                this.updateAutoToggleTooltip(button);
                return;
            }

            ChainveinfabricClient.disarmAutoMining();
            ChainveinfabricClient.CONFIG.isChainVeinEnabled = !ChainveinfabricClient.CONFIG.isChainVeinEnabled;
            ChainveinfabricClient.CONFIG.save();
            button.setDisplayString(getToggleString());
            this.updateAutoToggleTooltip(button);
        });

        // Toggle outlines
        GuiChainVeinLayout.Rect outline = this.basicLayout.outline;
        ButtonGeneric outlineBtn = new ButtonGeneric(outline.x, outline.y, outline.width, outline.height,
            getBasicOutlineToggleString());
        this.updateCompactButtonTooltip(outlineBtn, this.getOutlineToggleString());
        this.addButton(outlineBtn, (button, mb) -> {
            ChainveinfabricClient.CONFIG.showBlockOutlines = !ChainveinfabricClient.CONFIG.showBlockOutlines;
            ChainveinfabricClient.CONFIG.save();
            ConfigProxies.load();
            button.setDisplayString(getBasicOutlineToggleString());
            this.updateCompactButtonTooltip(button, this.getOutlineToggleString());
        });

        if (this.basicLayout.importButton.isVisible()) {
            GuiChainVeinLayout.Rect importRect = this.basicLayout.importButton;
            ButtonGeneric importButton = new ButtonGeneric(
                    importRect.x,
                    importRect.y,
                    importRect.width,
                    importRect.height,
                    getBasicImportString()
            );
            this.updateCompactButtonTooltip(importButton,
                StringUtils.translate("options.chainveinfabric.whitelist.import"));
            this.addButton(importButton, (button, mb) -> {
                WhitelistImportService.start(
                        net.minecraft.client.Minecraft.getInstance(),
                        ChainveinfabricClient.CONFIG.mode,
                        this::refreshLists
                );
            });

            if (this.basicLayout.renderLayer.isVisible()) {
                GuiChainVeinLayout.Rect renderLayer = this.basicLayout.renderLayer;
                ButtonGeneric renderLayerButton = new ButtonGeneric(
                        renderLayer.x,
                        renderLayer.y,
                        renderLayer.width,
                        renderLayer.height,
                        getBasicRenderLayerToggleString()
                );
                this.updateCompactButtonTooltip(renderLayerButton, this.getRenderLayerToggleString());
                this.addButton(renderLayerButton, (button, mb) -> {
                    ChainveinfabricClient.CONFIG.respectSchematicRenderLayer =
                            !ChainveinfabricClient.CONFIG.respectSchematicRenderLayer;
                    ChainveinfabricClient.CONFIG.save();
                    button.setDisplayString(getBasicRenderLayerToggleString());
                    this.updateCompactButtonTooltip(button, this.getRenderLayerToggleString());
                });
            }
        }

        if (this.basicLayout.singlePane) {
            this.addCompactPaneButtons();
        }

        GuiChainVeinLayout.Rect search = this.basicLayout.search;
        this.searchBar = new WidgetSearchBar(search.x, search.y, search.width, search.height, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);

        if (!this.basicLayout.singlePane || this.compactPane == BasicPane.AVAILABLE) {
            GuiChainVeinLayout.Rect list = this.basicLayout.leftList;
            this.leftList = new WidgetChainList(list.x, list.y, list.width, list.height, null, false, this::getLeftListData, this);
            this.leftList.bindSearchBar(this.searchBar);
        }

        if (!this.basicLayout.singlePane || this.compactPane == BasicPane.WHITELIST) {
            GuiChainVeinLayout.Rect list = this.basicLayout.rightList;
            this.rightList = new WidgetChainList(list.x, list.y, list.width, list.height, null, true, this::getRightListData, this);
            this.rightList.bindSearchBar(this.searchBar);
        }

        this.refreshLists();
    }

    private GuiChainVeinLayout.BasicLayout createBasicLayout(
            int topY, ChainVeinConfig.ChainMode mode) {
        boolean showImport = mode.isSchematicMode();
        boolean showRenderLayer = mode == ChainVeinConfig.ChainMode.SCHEMATIC_EXTRA
                || mode == ChainVeinConfig.ChainMode.SCHEMATIC_WRONG;
        List<Integer> compactWidths = this.createCompactControlWidths(showImport, showRenderLayer);
        return GuiChainVeinLayout.basic(
                this.width, this.height, topY,
                showImport, showRenderLayer, compactWidths);
    }

    private List<Integer> createCompactControlWidths(boolean showImport, boolean showRenderLayer) {
        List<Integer> widths = new ArrayList<>();
        int modeWidth = getAvailableModes().stream()
            .map(this::getCompactModeString)
            .mapToInt(this::getStringWidth)
            .max()
            .orElse(70) + 24;
        widths.add(Math.max(80, modeWidth));
        widths.add(this.getToggleLabelWidth("options.chainveinfabric.showBlockOutlines"));
        if (showImport) {
            widths.add(this.getStringWidth(StringUtils.translate("options.chainveinfabric.whitelist.import")) + 12);
        }
        if (showRenderLayer) {
            widths.add(this.getToggleLabelWidth("options.chainveinfabric.schematic.respectRenderLayer"));
        }
        widths.add(Math.max(
                Math.max(this.getStringWidth("ON"), this.getStringWidth("OFF")),
                this.getStringWidth("AUTO")) + 12);
        return widths;
    }

    private int getToggleLabelWidth(String translationKey) {
        String label = StringUtils.translate(translationKey);
        int enabledWidth = this.getStringWidth("✓ " + label);
        int disabledWidth = this.getStringWidth("✗ " + label);
        return Math.max(enabledWidth, disabledWidth) + 12;
    }

    private void addCompactPaneButtons() {
        GuiChainVeinLayout.Rect available = this.basicLayout.availablePane;
        ButtonGeneric availableButton = new ButtonGeneric(
                available.x,
                available.y,
                available.width,
                available.height,
                StringUtils.translate("options.chainveinfabric.allBlocks")
        );
        availableButton.setEnabled(this.compactPane != BasicPane.AVAILABLE);
        this.addButton(availableButton, (button, mb) -> this.switchCompactPane(BasicPane.AVAILABLE));

        GuiChainVeinLayout.Rect whitelist = this.basicLayout.whitelistPane;
        ButtonGeneric whitelistButton = new ButtonGeneric(
                whitelist.x,
                whitelist.y,
                whitelist.width,
                whitelist.height,
                StringUtils.translate(this.getWhitelistTitleKey())
        );
        whitelistButton.setEnabled(this.compactPane != BasicPane.WHITELIST);
        this.addButton(whitelistButton, (button, mb) -> this.switchCompactPane(BasicPane.WHITELIST));
    }

    private void switchCompactPane(BasicPane pane) {
        if (this.compactPane != pane) {
            this.compactPane = pane;
            this.initGui();
        }
    }

    private void initPresetTab(int topY) {
        int listWidth = Math.max(1, Math.min(BASIC_BODY_MAX_WIDTH, this.width - PAGE_MARGIN * 2));
        int listX = (this.width - listWidth) / 2;
        int listY = topY;
        this.presetList = new PresetListWidget(
                listX, listY, listWidth, this.height - listY - 20, this);
        this.presetList.refreshEntries();
    }

    void refreshPresetList() {
        if (this.presetList != null) {
            this.presetList.refreshEntriesPreserveScroll();
        }
    }

    ChainVeinConfig.ChainMode presetWhitelistMode() {
        return this.presetWhitelistMode;
    }

    void selectPresetWhitelistMode(ChainVeinConfig.ChainMode mode) {
        this.presetWhitelistMode = mode;
        this.refreshPresetList();
    }

    List<ChainVeinConfig.ChainMode> availableModes() {
        return this.getAvailableModes();
    }

    String modeString(ChainVeinConfig.ChainMode mode) {
        return this.getModeString(mode);
    }

    void registerDropdown(OverlayDropdown<?> dropdown) {
        this.activeDropdowns.add(dropdown);
    }

    void clearDropdowns() {
        this.activeDropdowns.clear();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null) this.leftList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.rightList != null) this.rightList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.searchBar != null) this.searchBar.render(ctx, mouseX, mouseY, false);

            if (this.basicLayout != null && !this.basicLayout.singlePane) {
                this.drawString(ctx, StringUtils.translate("options.chainveinfabric.allBlocks"), this.basicLayout.leftList.x, this.basicLayout.titleY, 0xFFFFFF);
                this.drawString(ctx, StringUtils.translate(this.getWhitelistTitleKey()), this.basicLayout.rightList.x, this.basicLayout.titleY, 0xFFFFFF);
            }
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null) this.presetList.drawContents(ctx, mouseX, mouseY, partialTicks);
        }

    }

    @Override
    protected void drawButtonHoverTexts(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawButtonHoverTexts(ctx, mouseX, mouseY, partialTicks);
        this.renderOpenDropdowns(ctx, mouseX, mouseY);
    }

    private void renderOpenDropdowns(GuiContext ctx, int mouseX, int mouseY) {
        for (OverlayDropdown<?> dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                boolean selected = dd.isMouseOver(mouseX, mouseY);
                dd.renderOverlay(ctx, mouseX, mouseY, selected);
            }
        }
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        this.controlClick = click.hasControlDown();
        try {
            for (OverlayDropdown<?> dd : this.activeDropdowns) {
                if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                    if (dd.isMouseOver((int)click.x(), (int)click.y())) {
                        return dd.onMouseClicked(click, doubleClick);
                    }
                }
            }
            if (super.onMouseClicked(click, doubleClick)) return true;
            if (currentTab == Tab.BASIC) {
                if (this.searchBar != null && this.searchBar.onMouseClicked(click, doubleClick)) return true;
                if (this.leftList != null && this.leftList.onMouseClicked(click, doubleClick)) return true;
                if (this.rightList != null && this.rightList.onMouseClicked(click, doubleClick)) return true;
            } else if (currentTab == Tab.PRESETS) {
                if (this.presetList != null && this.presetList.onMouseClicked(click, doubleClick)) return true;
            }
            return false;
        } finally {
            this.controlClick = false;
        }
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        if (super.onMouseReleased(click)) return true;
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null && this.leftList.onMouseReleased(click)) return true;
            if (this.rightList != null && this.rightList.onMouseReleased(click)) return true;
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null && this.presetList.onMouseReleased(click)) return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (OverlayDropdown<?> dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                if (dd.isMouseOver((int)mouseX, (int)mouseY)) {
                    return ((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                }
            }
        }
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null && this.leftList.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
            if (this.rightList != null && this.rightList.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null && this.presetList.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(KeyEvent key) {
        if ((currentTab == Tab.SETTINGS || currentTab == Tab.HOTKEYS) && super.onKeyTyped(key)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onKeyTyped(key)) {
                this.refreshLists();
                return true;
            }
        } else if (currentTab == Tab.PRESETS && this.presetList != null) {
            if (this.presetList.onKeyTyped(key)) return true;
        }
        return false;
    }

    @Override
    public boolean onCharTyped(CharacterEvent character) {
        if ((currentTab == Tab.SETTINGS || currentTab == Tab.HOTKEYS) && super.onCharTyped(character)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onCharTyped(character)) {
                this.refreshLists();
                return true;
            }
        } else if (currentTab == Tab.PRESETS && this.presetList != null) {
            if (this.presetList.onCharTyped(character)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.getListWidget() != null) {
            super.removed();
        }
        ConfigProxies.setAlgorithmChangeListener(null);
        ConfigProxies.save();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    public void refreshLists() {
        if (this.leftList != null) this.leftList.refreshEntries();
        if (this.rightList != null) this.rightList.refreshEntries();
    }

    private String getModeString() {
        return getModeString(ChainveinfabricClient.CONFIG.mode);
    }

    private String getModeString(ChainVeinConfig.ChainMode mode) {
        return StringUtils.translate("options.chainveinfabric.mode." + mode.name().toLowerCase().replace("chain_", ""));
    }

    private String getCompactModeString(ChainVeinConfig.ChainMode mode) {
        return StringUtils.translate(
            "options.chainveinfabric.mode.compact." + mode.name().toLowerCase().replace("chain_", ""));
    }

    private String getBasicModeString(ChainVeinConfig.ChainMode mode) {
        String text = this.basicLayout.controlDensity == GuiChainVeinLayout.ControlDensity.FULL
            ? this.getModeString(mode)
            : this.getCompactModeString(mode);
        return this.fitText(text, Math.max(1, this.basicLayout.mode.width - 18));
    }

    private String fitText(String text, int maxWidth) {
        if (this.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        int contentWidth = maxWidth - this.getStringWidth(ellipsis);
        return contentWidth > 0 ? this.font.plainSubstrByWidth(text, contentWidth) + ellipsis : "";
    }

    private String getWhitelistTitleKey() {
        return switch (ChainveinfabricClient.CONFIG.mode) {
            case CHAIN_MINE, SCHEMATIC_SELECTION, SCHEMATIC_EXTRA, SCHEMATIC_WRONG -> "options.chainveinfabric.whitelist";
            case CHAIN_PLANT -> "options.chainveinfabric.cropWhitelist";
            case CHAIN_UTILITY -> "options.chainveinfabric.utilityWhitelist";
        };
    }

    private String getToggleString() {
        if (ChainveinfabricClient.isAutoMiningArmed()) return "AUTO";
        return ChainveinfabricClient.CONFIG.isChainVeinEnabled ? "ON" : "OFF";
    }

    private void updateAutoToggleTooltip(ButtonBase button) {
        if (ChainveinfabricClient.isAutoMiningArmed()) {
            button.setHoverStrings(StringUtils.translate("options.chainveinfabric.autoMine.clickToDisable"));
        } else if (ChainveinfabricClient.CONFIG.mode.isMiningMode()) {
            button.setHoverStrings(StringUtils.translate("options.chainveinfabric.autoMine.ctrlHint"));
        } else {
            button.clearHoverStrings();
        }
    }

    private String getOutlineToggleString() {
        String state = ChainveinfabricClient.CONFIG.showBlockOutlines ? "ON" : "OFF";
        return StringUtils.translate("options.chainveinfabric.showBlockOutlines") + ": " + state;
    }

    private String getBasicOutlineToggleString() {
        return this.getBasicToggleLabel(
            "options.chainveinfabric.showBlockOutlines",
            ChainveinfabricClient.CONFIG.showBlockOutlines,
            "O"
        );
    }

    private String getBasicImportString() {
        return this.basicLayout.controlDensity == GuiChainVeinLayout.ControlDensity.ICON
            ? "I"
            : StringUtils.translate("options.chainveinfabric.whitelist.import");
    }

    private String getBasicRenderLayerToggleString() {
        return this.getBasicToggleLabel(
            "options.chainveinfabric.schematic.respectRenderLayer",
            ChainveinfabricClient.CONFIG.respectSchematicRenderLayer,
            "L"
        );
    }

    private String getBasicToggleLabel(String translationKey, boolean enabled, String iconLabel) {
        if (this.basicLayout.controlDensity == GuiChainVeinLayout.ControlDensity.FULL) {
            return StringUtils.translate(translationKey) + ": " + (enabled ? "ON" : "OFF");
        }
        if (this.basicLayout.controlDensity == GuiChainVeinLayout.ControlDensity.ICON) {
            return iconLabel + (enabled ? "+" : "-");
        }
        return (enabled ? "✓ " : "✗ ") + StringUtils.translate(translationKey);
    }

    private void updateCompactButtonTooltip(ButtonBase button, String fullLabel) {
        if (this.basicLayout.controlDensity == GuiChainVeinLayout.ControlDensity.FULL) {
            button.clearHoverStrings();
        } else {
            button.setHoverStrings(fullLabel);
        }
    }

    private List<ItemStack> getLeftListData() {
        ChainVeinConfig.ChainMode mode = ChainveinfabricClient.CONFIG.mode;
        Set<String> whitelist = getWhitelistForMode(mode);

        List<ItemStack> list = new ArrayList<>();
        if (mode != ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            list.addAll(getBlockItemList(whitelist));
        } else {
            for (Item item : BuiltInRegistries.ITEM) {
                if (PlantingItems.isPlantable(item) && !whitelist.contains(BuiltInRegistries.ITEM.getKey(item).toString())) {
                    list.add(new ItemStack(item));
                }
            }
        }
        return list;
    }

    private List<ItemStack> getBlockItemList(Set<String> whitelist) {
        List<ItemStack> list = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            if (whitelist.contains(itemId) || !seenItems.add(itemId)) continue;
            list.add(new ItemStack(item));
        }

        return list;
    }

    private List<ItemStack> getRightListData() {
        Set<String> whitelist = getWhitelistForMode(ChainveinfabricClient.CONFIG.mode);

        List<ItemStack> list = new ArrayList<>();
        for (String id : whitelist) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;

            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item != null && item != Items.AIR) list.add(new ItemStack(item));
        }
        return list;
    }

    private Set<String> getWhitelistForMode(ChainVeinConfig.ChainMode mode) {
        return ChainveinfabricClient.CONFIG.getWhitelist(mode);
    }

    private String getRenderLayerToggleString() {
        String state = ChainveinfabricClient.CONFIG.respectSchematicRenderLayer ? "ON" : "OFF";
        return StringUtils.translate("options.chainveinfabric.schematic.respectRenderLayer") + ": " + state;
    }

    private List<ChainVeinConfig.ChainMode> getAvailableModes() {
        return Arrays.stream(ChainVeinConfig.ChainMode.values())
                .filter(mode -> !mode.isSchematicMode() || LitematicaIntegration.isAvailable())
                .toList();
    }

    void ensureAvailableMode() {
        if (ChainveinfabricClient.CONFIG.mode.isSchematicMode() && !LitematicaIntegration.isAvailable()) {
            ChainveinfabricClient.CONFIG.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
        }
    }

}
