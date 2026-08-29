package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
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
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;
import org.edtp.chainveinfabric.client.logic.PlantingItems;
import org.edtp.chainveinfabric.client.logic.WhitelistImportService;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;

public class GuiChainVein extends GuiConfigsBase {

    private static final int PAGE_MARGIN = 12;
    private static final int BASIC_MAX_WIDTH = 445;
    private static final int BASIC_BODY_MAX_WIDTH = 400;
    private static final int DUAL_LIST_MIN_WIDTH = 360;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 5;
    private static final int HEADER_TAB_Y = 10;
    private static final int HEADER_CONTENT_Y = 40;
    private static final int HEADER_TAB_GAP = 10;
    private static final int COMPACT_HEADER_TAB_GAP = 4;
    private static final int CONFIG_SWITCHER_WIDTH = 155;
    private static final int COLLAPSED_TAB_WIDTH = 130;
    private static final int CONFIG_LABEL_GAP = 10;
    private static final int CONFIG_RESET_GAP = 2;
    private static final int CONFIG_SCROLLBAR_CLEARANCE = 6;
    private static final int CONFIG_MIN_LABEL_WIDTH = 40;
    private static final int CONFIG_MIN_CONTROL_WIDTH = 40;

    private enum Tab { BASIC, SETTINGS, HOTKEYS, PRESETS }
    private enum BasicPane { AVAILABLE, WHITELIST }
    private enum ControlDensity { FULL, COMPACT, ICON }

    private record LayoutRect(int x, int y, int width, int height) {
        private static LayoutRect hidden() {
            return new LayoutRect(0, 0, 0, 0);
        }

        private boolean isVisible() {
            return this.width > 0 && this.height > 0;
        }
    }

    private record BasicLayout(
            LayoutRect mode,
            LayoutRect toggle,
            LayoutRect outline,
            LayoutRect importButton,
            LayoutRect renderLayer,
            LayoutRect availablePane,
            LayoutRect whitelistPane,
            LayoutRect search,
            LayoutRect leftList,
            LayoutRect rightList,
            int titleY,
            boolean singlePane,
            ControlDensity controlDensity
    ) {
    }

    private record HeaderLayout(
            int tabX,
            int tabY,
            int tabWidth,
            int tabGap,
            int contentY,
            boolean hideTitle,
            boolean collapsed
    ) {
    }

    private Tab currentTab = Tab.BASIC;
    private BasicPane compactPane = BasicPane.AVAILABLE;
    private final List<IDropdown> activeDropdowns = new ArrayList<>();
    private ChainVeinConfig.ChainMode presetWhitelistMode = ChainVeinConfig.ChainMode.CHAIN_MINE;
    private boolean controlClick;

    public static abstract class MyDropdown<T> extends WidgetDropDownList<T> implements IDropdown {
        private long lastDrawn;
        public MyDropdown(int x, int y, int width, int height, int maxHeight, int maxVisibleEntries, List<T> entries, fi.dy.masa.malilib.interfaces.IStringRetriever<T> stringRetriever) {
            super(x, y, width, height, maxHeight, maxVisibleEntries, entries, stringRetriever);
        }
        @Override public boolean isMenuOpen() { return this.isOpen; }
        @Override public void setLastDrawn(long t) { this.lastDrawn = t; }
        @Override public long getLastDrawn() { return this.lastDrawn; }

        @Override public boolean isMouseOver(int mouseX, int mouseY) {
            if (this.isOpen) {
                int visible = Math.min(this.maxVisibleEntries, this.filteredEntries.size());
                int dropHeight = this.height + (visible * this.height) + 4;
                return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + dropHeight;
            }
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
            double clickY = click.y();
            if (this.isOpen && clickY > this.y + this.height) {
                int visible = Math.min(this.maxVisibleEntries, this.filteredEntries.size());
                int dropHeight = this.height + (visible * this.height) + 4;

                if (click.x() >= this.x && click.x() < this.x + this.width && clickY >= this.y && clickY < this.y + dropHeight) {
                    if (click.x() < this.x + this.width - this.scrollbarWidth) {
                        int relIndex = (int)((clickY - this.y - this.height - 1) / this.height);
                        relIndex = fi.dy.masa.malilib.util.MathUtils.clamp(relIndex, 0, visible - 1);
                        this.setSelectedEntry(this.scrollBar.getValue() + relIndex);
                        this.isOpen = false;
                        if (this.searchBar != null && this.searchBar.textField() != null) {
                            this.searchBar.textField().setValue("");
                        }
                        this.updateFilteredEntries();
                        return true;
                    }
                }
            }
            return super.onMouseClicked(click, doubleClick);
        }

        @Override public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
            boolean wasOpen = this.isOpen;
            this.isOpen = false;
            super.render(ctx, mouseX, mouseY, selected);
            this.isOpen = wasOpen;
            this.setLastDrawn(System.currentTimeMillis());
        }
        @Override public void handleRender(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
            boolean wasOpen = this.isOpen;
            this.isOpen = true;
            super.render(ctx, mouseX, mouseY, selected);
            this.isOpen = wasOpen;
        }
    }

    public interface IDropdown {
        boolean isMenuOpen();
        void setLastDrawn(long time);
        long getLastDrawn();
        boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick);
        void handleRender(GuiContext ctx, int mouseX, int mouseY, boolean selected);
        boolean isMouseOver(int mouseX, int mouseY);
    }

    private WidgetChainList leftList;
    private WidgetChainList rightList;
    private WidgetSearchBar searchBar;
    private WidgetPresetList presetList;
    private BasicLayout basicLayout;
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
        return new WidgetListConfigOptions(listX, listY,
            this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this) {

            @Override
            protected fi.dy.masa.malilib.gui.widgets.WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper wrapper) {
                int resetWidth = this.getStringWidth(
                    StringUtils.translate("malilib.gui.button.reset.caps")) + 10;
                int fixedWidth = resetWidth + CONFIG_LABEL_GAP + CONFIG_RESET_GAP
                    + CONFIG_SCROLLBAR_CLEARANCE;
                int availableWidth = Math.max(1, this.browserEntryWidth - fixedWidth);
                int configWidth = Math.min(this.configWidth,
                    Math.max(CONFIG_MIN_CONTROL_WIDTH, availableWidth - CONFIG_MIN_LABEL_WIDTH));
                configWidth = Math.min(configWidth, Math.max(1, availableWidth - 1));
                int labelWidth = Math.min(this.maxLabelWidth,
                    Math.max(1, availableWidth - configWidth));
                return new DropdownConfigOption(x, y, this.browserEntryWidth, this.browserEntryHeight,
                    labelWidth, configWidth, wrapper, listIndex, GuiChainVein.this, this);
            }
        };
    }

    private static class DropdownConfigOption extends fi.dy.masa.malilib.gui.widgets.WidgetConfigOption {
        public DropdownConfigOption(int x, int y, int width, int height, int labelWidth, int configWidth,
            ConfigOptionWrapper wrapper, int listIndex,
            fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui host, fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase<?, ?> parent) {
            super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
        }

        @Override
        protected void addConfigOption(int x, int y, int labelWidth, int configWidth, fi.dy.masa.malilib.config.IConfigBase config) {
            if (config.getType() == fi.dy.masa.malilib.config.ConfigType.HOTKEY) {
                y += 1;
                int controlsX = this.getControlsX(configWidth);
                this.addResponsiveConfigLabel(x, y, controlsX - x - 10, config);
                this.addHotkeyConfigElements(controlsX, y, configWidth, config.getName(),
                    (fi.dy.masa.malilib.hotkeys.IHotkey) config);
            } else if (config.getType() == fi.dy.masa.malilib.config.ConfigType.BOOLEAN) {
                y += 1;
                int controlsX = this.getControlsX(configWidth);
                this.addResponsiveConfigLabel(x, y, controlsX - x - 10, config);

                fi.dy.masa.malilib.config.IConfigBoolean booleanConfig =
                    (fi.dy.masa.malilib.config.IConfigBoolean) config;
                fi.dy.masa.malilib.gui.button.ConfigButtonBoolean button =
                    new fi.dy.masa.malilib.gui.button.ConfigButtonBoolean(
                        controlsX, y, configWidth, 20, booleanConfig);
                this.addConfigButtonEntry(controlsX + configWidth + 2, y,
                    (fi.dy.masa.malilib.config.IConfigResettable) config, button);
            } else if (config.getType() == fi.dy.masa.malilib.config.ConfigType.OPTION_LIST) {
                fi.dy.masa.malilib.config.IConfigOptionList optionList = (fi.dy.masa.malilib.config.IConfigOptionList) config;
                fi.dy.masa.malilib.config.IConfigResettable resettable = (fi.dy.masa.malilib.config.IConfigResettable) config;

                y += 1;
                int configHeight = 20;
                int controlsX = this.getControlsX(configWidth);
                this.addResponsiveConfigLabel(x, y, controlsX - x - 10, config);

                List<fi.dy.masa.malilib.config.IConfigOptionListEntry> entries = new ArrayList<>();
                fi.dy.masa.malilib.config.IConfigOptionListEntry current = optionList.getOptionListValue();
                fi.dy.masa.malilib.config.IConfigOptionListEntry iter = current;
                if (iter != null) {
                    do {
                        entries.add(iter);
                        iter = iter.cycle(true);
                    } while (iter != current && iter != null && entries.size() < 100);
                }

                ButtonGeneric resetButton = this.createResetButton(controlsX + configWidth + 2, y, resettable);

                MyDropdown<fi.dy.masa.malilib.config.IConfigOptionListEntry> dropdown =
                    new MyDropdown<fi.dy.masa.malilib.config.IConfigOptionListEntry>(
                        controlsX, y, configWidth, configHeight, 200, 5, entries,
                        entry -> entry.getDisplayName()
                    ) {
                        @Override
                        protected void setSelectedEntry(int index) {
                            super.setSelectedEntry(index);
                            if (this.getSelectedEntry() != null) {
                                optionList.setOptionListValue(this.getSelectedEntry());
                                resetButton.setEnabled(resettable.isModified());
                            }
                        }
                    };
                ((GuiChainVein)this.host).activeDropdowns.add(dropdown);
                dropdown.setSelectedEntry(current);

                IButtonActionListener resetListener = (button, mouseButton) -> {
                    resettable.resetToDefault();
                    dropdown.setSelectedEntry(optionList.getOptionListValue());
                    resetButton.setEnabled(resettable.isModified());
                };

                this.addWidget(dropdown);
                this.addButton(resetButton, resetListener);

            } else {
                int controlsX = this.getControlsX(configWidth);
                super.addConfigOption(x, y, Math.max(1, controlsX - x - CONFIG_LABEL_GAP),
                    configWidth, config);
            }
        }

        private int getControlsX(int configWidth) {
            int resetWidth = this.textRenderer.width(
                StringUtils.translate("malilib.gui.button.reset.caps")) + 10;
            return this.x + this.width - configWidth - resetWidth
                - CONFIG_RESET_GAP - CONFIG_SCROLLBAR_CLEARANCE;
        }

        private void addResponsiveConfigLabel(int x, int y, int availableWidth,
            fi.dy.masa.malilib.config.IConfigBase config) {
            int labelWidth = Math.max(0, availableWidth);
            String translatedName = StringUtils.translate(config.getName());
            String configName = this.fitText(
                translatedName.equals(config.getName()) ? config.getConfigGuiDisplayName() : translatedName,
                labelWidth
            );
            this.addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, configName);

            fi.dy.masa.malilib.gui.interfaces.IConfigInfoProvider infoProvider = this.host.getHoverInfoProvider();
            String comment = infoProvider != null ? infoProvider.getHoverInfo(config) : config.getComment();
            if (comment != null) {
                this.addWidget(new fi.dy.masa.malilib.gui.widgets.WidgetHoverInfo(
                    x, y + 5, labelWidth, 12, comment));
            }
        }

        private String fitText(String text, int maxWidth) {
            if (this.textRenderer.width(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "…";
            int contentWidth = maxWidth - this.textRenderer.width(ellipsis);
            return contentWidth > 0
                ? this.textRenderer.plainSubstrByWidth(text, contentWidth) + ellipsis
                : "";
        }
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
        HeaderLayout headerLayout = this.createHeaderLayout();
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

    private HeaderLayout createHeaderLayout() {
        List<Tab> tabs = List.of(Tab.BASIC, Tab.SETTINGS, Tab.HOTKEYS, Tab.PRESETS);
        int buttonWidth = 0;
        for (Tab tab : tabs) {
            String key = "options.chainveinfabric.tab." + tab.name().toLowerCase(Locale.ROOT);
            buttonWidth += this.getStringWidth(StringUtils.translate(key)) + 10;
        }

        int titleRight = 20 + this.getStringWidth(this.getTitleString()) + 12;
        int switcherLeft = this.width - CONFIG_SWITCHER_WIDTH - 8;
        int firstRowWidth = Math.max(0, switcherLeft - titleRight);
        int normalWidth = buttonWidth + HEADER_TAB_GAP * (tabs.size() - 1);

        if (normalWidth <= firstRowWidth) {
            return new HeaderLayout(titleRight, HEADER_TAB_Y, firstRowWidth,
                HEADER_TAB_GAP, HEADER_CONTENT_Y, false, false);
        }

        int compactRowWidth = Math.max(0, switcherLeft - PAGE_MARGIN);
        int compactWidth = buttonWidth + COMPACT_HEADER_TAB_GAP * (tabs.size() - 1);
        if (compactWidth <= compactRowWidth) {
            return new HeaderLayout(PAGE_MARGIN, HEADER_TAB_Y, compactRowWidth,
                COMPACT_HEADER_TAB_GAP, HEADER_CONTENT_Y, true, false);
        }

        int dropdownWidth = Math.min(COLLAPSED_TAB_WIDTH, compactRowWidth);
        return new HeaderLayout(PAGE_MARGIN + Math.max(0, (compactRowWidth - dropdownWidth) / 2),
            HEADER_TAB_Y, Math.max(1, dropdownWidth), 0, HEADER_CONTENT_Y, true, true);
    }

    private void addTabNavigation(HeaderLayout layout) {
        if (layout.collapsed) {
            this.addCollapsedTabDropdown(layout);
        } else {
            this.addTabButtons(layout);
        }
    }

    private void addCollapsedTabDropdown(HeaderLayout layout) {
        List<Tab> tabs = List.of(Tab.BASIC, Tab.SETTINGS, Tab.HOTKEYS, Tab.PRESETS);
        MyDropdown<Tab> tabDropdown = new MyDropdown<Tab>(
            layout.tabX, layout.tabY, layout.tabWidth, CONTROL_HEIGHT, 120, tabs.size(), tabs,
            tab -> StringUtils.translate("options.chainveinfabric.tab." + tab.name().toLowerCase(Locale.ROOT))
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                Tab selected = this.getSelectedEntry();
                if (selected != null && selected != currentTab) {
                    currentTab = selected;
                    reCreateListWidget();
                    initGui();
                }
            }
        };
        this.activeDropdowns.add(tabDropdown);
        tabDropdown.setSelectedEntry(this.currentTab);
        this.addWidget(tabDropdown);
    }

    private void addTabButtons(HeaderLayout layout) {
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
            this.addButton(button, (btn, mouseButton) -> {
                this.currentTab = tab;
                this.reCreateListWidget();
                this.initGui();
            });
            x += button.getWidth() + gap;
        }
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
        MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
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
        LayoutRect toggle = this.basicLayout.toggle;
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
        LayoutRect outline = this.basicLayout.outline;
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
            LayoutRect importRect = this.basicLayout.importButton;
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
                LayoutRect renderLayer = this.basicLayout.renderLayer;
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

        LayoutRect search = this.basicLayout.search;
        this.searchBar = new WidgetSearchBar(search.x, search.y, search.width, search.height, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);

        if (!this.basicLayout.singlePane || this.compactPane == BasicPane.AVAILABLE) {
            LayoutRect list = this.basicLayout.leftList;
            this.leftList = new WidgetChainList(list.x, list.y, list.width, list.height, null, false, this::getLeftListData, this);
            this.leftList.bindSearchBar(this.searchBar);
        }

        if (!this.basicLayout.singlePane || this.compactPane == BasicPane.WHITELIST) {
            LayoutRect list = this.basicLayout.rightList;
            this.rightList = new WidgetChainList(list.x, list.y, list.width, list.height, null, true, this::getRightListData, this);
            this.rightList.bindSearchBar(this.searchBar);
        }

        this.refreshLists();
    }

    private BasicLayout createBasicLayout(int topY, ChainVeinConfig.ChainMode mode) {
        int contentWidth = Math.max(1, Math.min(BASIC_MAX_WIDTH, this.width - PAGE_MARGIN * 2));
        int contentX = (this.width - contentWidth) / 2;
        boolean showImport = mode.isSchematicMode();
        boolean showRenderLayer = mode == ChainVeinConfig.ChainMode.SCHEMATIC_EXTRA
                || mode == ChainVeinConfig.ChainMode.SCHEMATIC_WRONG;

        List<Integer> fullWidths = this.createFullControlWidths(showImport, showRenderLayer);
        List<Integer> compactWidths = this.createCompactControlWidths(showImport, showRenderLayer);
        ControlDensity controlDensity;
        List<Integer> controlWidths;
        if (rowWidth(fullWidths) <= contentWidth) {
            controlDensity = ControlDensity.FULL;
            controlWidths = fullWidths;
        } else if (rowWidth(compactWidths) <= contentWidth) {
            controlDensity = ControlDensity.COMPACT;
            controlWidths = compactWidths;
        } else {
            controlDensity = ControlDensity.ICON;
            controlWidths = this.createIconControlWidths(contentWidth, showImport, showRenderLayer);
        }

        LayoutRect modeRect;
        LayoutRect outlineRect;
        LayoutRect importRect = LayoutRect.hidden();
        LayoutRect renderLayerRect = LayoutRect.hidden();
        LayoutRect toggleRect;
        LayoutRect[] row = centeredRow(contentX, topY, contentWidth, controlWidths);
        int index = 0;
        modeRect = row[index++];
        outlineRect = row[index++];
        if (showImport) importRect = row[index++];
        if (showRenderLayer) renderLayerRect = row[index++];
        toggleRect = row[index];
        int controlsBottom = topY + CONTROL_HEIGHT;

        int bodyWidth = Math.min(BASIC_BODY_MAX_WIDTH, contentWidth);
        int bodyX = (this.width - bodyWidth) / 2;
        // Two narrow columns leave no useful room for item names, so compact
        // screens switch between the available-items and whitelist panes.
        boolean singlePane = bodyWidth < DUAL_LIST_MIN_WIDTH;
        LayoutRect availablePane = LayoutRect.hidden();
        LayoutRect whitelistPane = LayoutRect.hidden();
        int searchY = controlsBottom + 10;
        int titleY = -1;
        int listY;

        if (singlePane) {
            int leftPaneWidth = (bodyWidth - CONTROL_GAP) / 2;
            int rightPaneWidth = bodyWidth - CONTROL_GAP - leftPaneWidth;
            availablePane = new LayoutRect(bodyX, searchY, leftPaneWidth, CONTROL_HEIGHT);
            whitelistPane = new LayoutRect(bodyX + leftPaneWidth + CONTROL_GAP, searchY, rightPaneWidth, CONTROL_HEIGHT);
            searchY += CONTROL_HEIGHT + CONTROL_GAP;
            listY = searchY + CONTROL_HEIGHT + CONTROL_GAP;
        } else {
            titleY = searchY + CONTROL_HEIGHT + 5;
            listY = titleY + 12;
        }

        LayoutRect searchRect = new LayoutRect(bodyX, searchY, bodyWidth, CONTROL_HEIGHT);
        int listHeight = Math.max(CONTROL_HEIGHT, this.height - listY - 20);
        LayoutRect leftListRect;
        LayoutRect rightListRect;
        if (singlePane) {
            leftListRect = new LayoutRect(bodyX, listY, bodyWidth, listHeight);
            rightListRect = leftListRect;
        } else {
            int leftWidth = (bodyWidth - CONTROL_GAP) / 2;
            int rightWidth = bodyWidth - CONTROL_GAP - leftWidth;
            leftListRect = new LayoutRect(bodyX, listY, leftWidth, listHeight);
            rightListRect = new LayoutRect(bodyX + leftWidth + CONTROL_GAP, listY, rightWidth, listHeight);
        }

        return new BasicLayout(
                modeRect,
                toggleRect,
                outlineRect,
                importRect,
                renderLayerRect,
                availablePane,
                whitelistPane,
                searchRect,
                leftListRect,
                rightListRect,
                titleY,
                singlePane,
                controlDensity
        );
    }

    private List<Integer> createFullControlWidths(boolean showImport, boolean showRenderLayer) {
        List<Integer> widths = new ArrayList<>();
        widths.add(170);
        widths.add(80);
        if (showImport) widths.add(50);
        if (showRenderLayer) widths.add(85);
        widths.add(40);
        return widths;
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

    private List<Integer> createIconControlWidths(int contentWidth, boolean showImport, boolean showRenderLayer) {
        List<Integer> widths = new ArrayList<>();
        int fixedWidth = 28 + 40;
        int fixedCount = 2;
        if (showImport) {
            fixedWidth += 28;
            fixedCount++;
        }
        if (showRenderLayer) {
            fixedWidth += 28;
            fixedCount++;
        }
        int gapWidth = fixedCount * CONTROL_GAP;
        widths.add(Math.max(40, contentWidth - fixedWidth - gapWidth));
        widths.add(28);
        if (showImport) widths.add(28);
        if (showRenderLayer) widths.add(28);
        widths.add(40);
        return widths;
    }

    private int getToggleLabelWidth(String translationKey) {
        String label = StringUtils.translate(translationKey);
        int enabledWidth = this.getStringWidth("✓ " + label);
        int disabledWidth = this.getStringWidth("✗ " + label);
        return Math.max(enabledWidth, disabledWidth) + 12;
    }

    private static int rowWidth(List<Integer> widths) {
        return widths.stream().mapToInt(Integer::intValue).sum()
                + Math.max(0, widths.size() - 1) * CONTROL_GAP;
    }

    private static LayoutRect[] centeredRow(int contentX, int y, int contentWidth, List<Integer> preferredWidths) {
        int count = preferredWidths.size();
        int gapWidth = Math.max(0, count - 1) * CONTROL_GAP;
        int availableForControls = Math.max(count, contentWidth - gapWidth);
        int preferredTotal = preferredWidths.stream().mapToInt(Integer::intValue).sum();
        int[] widths = new int[count];
        int assigned = 0;

        for (int i = 0; i < count; i++) {
            int width = preferredWidths.get(i);
            if (preferredTotal > availableForControls) {
                width = Math.max(1, width * availableForControls / preferredTotal);
            }
            widths[i] = width;
            assigned += width;
        }

        if (assigned < availableForControls && preferredTotal > availableForControls) {
            widths[0] += availableForControls - assigned;
        }

        int actualWidth = Arrays.stream(widths).sum() + gapWidth;
        int x = contentX + Math.max(0, (contentWidth - actualWidth) / 2);
        LayoutRect[] row = new LayoutRect[count];
        for (int i = 0; i < count; i++) {
            row[i] = new LayoutRect(x, y, widths[i], CONTROL_HEIGHT);
            x += widths[i] + CONTROL_GAP;
        }
        return row;
    }

    private void addCompactPaneButtons() {
        LayoutRect available = this.basicLayout.availablePane;
        ButtonGeneric availableButton = new ButtonGeneric(
                available.x,
                available.y,
                available.width,
                available.height,
                StringUtils.translate("options.chainveinfabric.allBlocks")
        );
        availableButton.setEnabled(this.compactPane != BasicPane.AVAILABLE);
        this.addButton(availableButton, (button, mb) -> this.switchCompactPane(BasicPane.AVAILABLE));

        LayoutRect whitelist = this.basicLayout.whitelistPane;
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
        this.presetList = new WidgetPresetList(listX, listY, listWidth, this.height - listY - 20, this);
        this.presetList.refreshEntries();
    }

    private void refreshPresetList() {
        if (this.presetList != null) {
            this.presetList.refreshEntriesPreserveScroll();
        }
    }

    private static class PresetRow {
        private enum Type { WHITELIST_HEADER, NEW_WHITELIST, WHITELIST, CONFIG_HEADER, NEW_CONFIG, CONFIG }

        private final Type type;
        private final WhitelistPreset whitelistPreset;
        private final ConfigPreset configPreset;

        private PresetRow(Type type, WhitelistPreset whitelistPreset, ConfigPreset configPreset) {
            this.type = type;
            this.whitelistPreset = whitelistPreset;
            this.configPreset = configPreset;
        }

        private static PresetRow whitelistHeader() {
            return new PresetRow(Type.WHITELIST_HEADER, null, null);
        }

        private static PresetRow newWhitelist() {
            return new PresetRow(Type.NEW_WHITELIST, null, null);
        }

        private static PresetRow whitelist(WhitelistPreset preset) {
            return new PresetRow(Type.WHITELIST, preset, null);
        }

        private static PresetRow configHeader() {
            return new PresetRow(Type.CONFIG_HEADER, null, null);
        }

        private static PresetRow newConfig() {
            return new PresetRow(Type.NEW_CONFIG, null, null);
        }

        private static PresetRow config(ConfigPreset preset) {
            return new PresetRow(Type.CONFIG, null, preset);
        }
    }

    private static class WidgetPresetList extends WidgetListBase<PresetRow, WidgetPresetEntry> {
        private final GuiChainVein parent;

        private WidgetPresetList(int x, int y, int width, int height, GuiChainVein parent) {
            super(x, y, width, height, null);
            this.parent = parent;
            this.browserEntryHeight = 24;
        }

        @Override
        protected Collection<PresetRow> getAllEntries() {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            List<PresetRow> rows = new ArrayList<>();
            rows.add(PresetRow.whitelistHeader());
            rows.add(PresetRow.newWhitelist());
            for (WhitelistPreset preset : config.getWhitelistPresets(this.parent.presetWhitelistMode)) {
                rows.add(PresetRow.whitelist(preset));
            }
            rows.add(PresetRow.configHeader());
            rows.add(PresetRow.newConfig());
            for (ConfigPreset preset : config.getConfigPresets()) {
                rows.add(PresetRow.config(preset));
            }
            return rows;
        }

        @Override
        protected int getBrowserEntryHeightFor(PresetRow row) {
            return row.type == PresetRow.Type.WHITELIST_HEADER || row.type == PresetRow.Type.CONFIG_HEADER ? 28 : 24;
        }

        @Override
        protected WidgetPresetEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, PresetRow row) {
            return new WidgetPresetEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(row), isOdd, row, listIndex, this.parent, this);
        }

        @Override
        protected void reCreateListEntryWidgets() {
            this.parent.activeDropdowns.clear();
            super.reCreateListEntryWidgets();
        }

        private void refreshEntriesPreserveScroll() {
            int scroll = this.scrollBar.getValue();
            this.refreshEntries();
            this.scrollBar.setValue(scroll);
            this.reCreateListEntryWidgets();
        }

        @Override
        public boolean onKeyTyped(KeyEvent input) {
            for (WidgetPresetEntry widget : this.listWidgets) {
                if (widget.onKeyTyped(input)) {
                    return true;
                }
            }
            return super.onKeyTyped(input);
        }
    }

    private static class WidgetPresetEntry extends WidgetListEntryBase<PresetRow> {
        private final boolean isOdd;
        private final GuiChainVein parentScreen;
        private GuiTextFieldGeneric nameField;
        private String lastSavedName;
        private int headerLabelWidth;

        private WidgetPresetEntry(int x, int y, int width, int height, boolean isOdd, PresetRow row, int listIndex, GuiChainVein parentScreen, WidgetPresetList parentList) {
            super(x, y, width, height, row, listIndex);
            this.isOdd = isOdd;
            this.parentScreen = parentScreen;

            int buttonWidth = Math.min(58, Math.max(36, (width - 50) / 2));
            int nameWidth = Math.max(1, width - buttonWidth * 2 - 30);
            int buttonY = y + (height - 20) / 2;

            switch (row.type) {
                case WHITELIST_HEADER -> {
                    List<ChainVeinConfig.ChainMode> modes = parentScreen.getAvailableModes();
                    String header = StringUtils.translate("options.chainveinfabric.preset.whitelist");
                    int preferredLabelWidth = this.textRenderer.width(header) + 10;
                    int minimumDropdownWidth = Math.min(120, Math.max(70, width / 2));
                    int labelWidth = Math.min(preferredLabelWidth, Math.max(50, width - minimumDropdownWidth - 4));
                    int dropdownWidth = Math.max(1, width - labelWidth - 4);
                    this.headerLabelWidth = Math.max(1, labelWidth - 4);
                    MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
                        x + labelWidth, buttonY, dropdownWidth, 20, 200, 5, modes, parentScreen::getModeString
                    ) {
                        @Override
                        protected void setSelectedEntry(int index) {
                            super.setSelectedEntry(index);
                            ChainVeinConfig.ChainMode selected = this.getSelectedEntry();
                            if (selected != null && parentScreen.presetWhitelistMode != selected) {
                                parentScreen.presetWhitelistMode = selected;
                                parentScreen.refreshPresetList();
                            }
                        }
                    };
                    modeDropdown.setSelectedEntry(parentScreen.presetWhitelistMode);
                    parentScreen.activeDropdowns.add(modeDropdown);
                    this.addWidget(modeDropdown);
                }
                case NEW_WHITELIST -> {
                    ButtonGeneric button = new ButtonGeneric(x + 2, buttonY, buttonWidth, 20, StringUtils.translate("options.chainveinfabric.preset.new"));
                    this.addButton(button, (btn, mb) -> {
                        ChainveinfabricClient.CONFIG.createWhitelistPreset(parentScreen.presetWhitelistMode,
                            parentScreen.nextWhitelistPresetName(ChainveinfabricClient.CONFIG, parentScreen.presetWhitelistMode));
                        ChainveinfabricClient.CONFIG.save();
                        parentScreen.refreshPresetList();
                    });
                }
                case WHITELIST -> {
                    boolean active = row.whitelistPreset.id.equals(ChainveinfabricClient.CONFIG.getActiveWhitelistPresetId(parentScreen.presetWhitelistMode));
                    this.nameField = new GuiTextFieldGeneric(x + 2, buttonY, nameWidth, 20, this.textRenderer);
                    this.nameField.setValue(row.whitelistPreset.name);
                    this.nameField.setEditable(!active);
                    this.lastSavedName = row.whitelistPreset.name;

                    ButtonGeneric useButton = new ButtonGeneric(x + nameWidth + 10, buttonY, buttonWidth, 20,
                        active ? StringUtils.translate("options.chainveinfabric.preset.active") : StringUtils.translate("options.chainveinfabric.preset.use"));
                    useButton.setEnabled(!active);
                    this.addButton(useButton, (btn, mb) -> {
                        if (ChainveinfabricClient.CONFIG.useWhitelistPreset(parentScreen.presetWhitelistMode, row.whitelistPreset.id)) {
                            ChainveinfabricClient.CONFIG.mode = parentScreen.presetWhitelistMode;
                            ChainveinfabricClient.CONFIG.save();
                            ConfigProxies.load();
                            parentScreen.refreshLists();
                            parentScreen.refreshPresetList();
                        }
                    });

                    ButtonGeneric deleteButton = new ButtonGeneric(x + nameWidth + buttonWidth + 15, buttonY, buttonWidth, 20, StringUtils.translate("options.chainveinfabric.preset.delete"));
                    deleteButton.setEnabled(!active);
                    this.addButton(deleteButton, (btn, mb) -> {
                        if (ChainveinfabricClient.CONFIG.deleteWhitelistPreset(parentScreen.presetWhitelistMode, row.whitelistPreset.id)) {
                            ChainveinfabricClient.CONFIG.save();
                            parentScreen.refreshPresetList();
                        }
                    });
                }
                case NEW_CONFIG -> {
                    ButtonGeneric button = new ButtonGeneric(x + 2, buttonY, buttonWidth, 20, StringUtils.translate("options.chainveinfabric.preset.new"));
                    this.addButton(button, (btn, mb) -> {
                        ChainveinfabricClient.CONFIG.createConfigPreset(parentScreen.nextConfigPresetName(ChainveinfabricClient.CONFIG));
                        ChainveinfabricClient.CONFIG.save();
                        parentScreen.refreshPresetList();
                    });
                }
                case CONFIG -> {
                    boolean active = row.configPreset.id.equals(ChainveinfabricClient.CONFIG.activeConfigPresetId);
                    this.nameField = new GuiTextFieldGeneric(x + 2, buttonY, nameWidth, 20, this.textRenderer);
                    this.nameField.setValue(row.configPreset.name);
                    this.nameField.setEditable(!active);
                    this.lastSavedName = row.configPreset.name;

                    ButtonGeneric useButton = new ButtonGeneric(x + nameWidth + 10, buttonY, buttonWidth, 20,
                        active ? StringUtils.translate("options.chainveinfabric.preset.active") : StringUtils.translate("options.chainveinfabric.preset.use"));
                    useButton.setEnabled(!active);
                    this.addButton(useButton, (btn, mb) -> {
                        if (ChainveinfabricClient.CONFIG.useConfigPreset(row.configPreset.id)) {
                            parentScreen.ensureAvailableMode();
                            ChainveinfabricClient.CONFIG.save();
                            ConfigProxies.load();
                            parentScreen.refreshLists();
                            parentScreen.refreshPresetList();
                        }
                    });

                    ButtonGeneric deleteButton = new ButtonGeneric(x + nameWidth + buttonWidth + 15, buttonY, buttonWidth, 20, StringUtils.translate("options.chainveinfabric.preset.delete"));
                    deleteButton.setEnabled(!active);
                    this.addButton(deleteButton, (btn, mb) -> {
                        if (ChainveinfabricClient.CONFIG.deleteConfigPreset(row.configPreset.id)) {
                            ChainveinfabricClient.CONFIG.save();
                            parentScreen.refreshPresetList();
                        }
                    });
                }
                default -> {
                }
            }
        }

        @Override
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
            if (this.entry != null && this.entry.type == PresetRow.Type.WHITELIST_HEADER) {
                String label = this.fitText(
                        StringUtils.translate("options.chainveinfabric.preset.whitelist"),
                        this.headerLabelWidth
                );
                this.drawString(ctx, this.x + 2, this.y + 8, 0xFFFFFFFF, label);
                super.render(ctx, mouseX, mouseY, selected);
                return;
            }

            if (this.entry != null && this.entry.type == PresetRow.Type.CONFIG_HEADER) {
                String label = this.fitText(
                        StringUtils.translate("options.chainveinfabric.preset.config"),
                        Math.max(1, this.width - 4)
                );
                this.drawString(ctx, this.x + 2, this.y + 8, 0xFFFFFFFF, label);
                return;
            }

            if (selected || this.isMouseOver(mouseX, mouseY)) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x50FFFFFF);
            } else if (this.isOdd) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
            }

            if (this.nameField != null) {
                this.nameField.extractRenderState(ctx.getGuiGraphics(), mouseX, mouseY, 0f);
            }

            super.render(ctx, mouseX, mouseY, selected);
        }

        private String fitText(String text, int maxWidth) {
            if (this.textRenderer.width(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "…";
            int contentWidth = maxWidth - this.textRenderer.width(ellipsis);
            return contentWidth > 0
                    ? this.textRenderer.plainSubstrByWidth(text, contentWidth) + ellipsis
                    : "";
        }

        @Override
        public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
            if (this.nameField != null && this.nameField.mouseClicked(click, doubleClick)) {
                return true;
            }
            if (this.nameField != null && !this.nameField.isMouseOver(click.x(), click.y())) {
                this.nameField.setFocused(false);
            }
            return super.onMouseClicked(click, doubleClick);
        }

        @Override
        public boolean onKeyTyped(KeyEvent input) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.keyPressed(input);
                if (!before.equals(this.nameField.getValue())) {
                    this.saveName();
                }
                return handled;
            }
            return super.onKeyTyped(input);
        }

        @Override
        public boolean onCharTyped(CharacterEvent input) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.charTyped(input);
                if (!before.equals(this.nameField.getValue())) {
                    this.saveName();
                }
                return handled;
            }
            return super.onCharTyped(input);
        }

        @Override
        public boolean canSelectAt(MouseButtonEvent click) {
            return false;
        }

        private void saveName() {
            if (this.entry == null || this.nameField == null || this.nameField.getValue().equals(this.lastSavedName)) {
                return;
            }

            boolean saved = false;
            if (this.entry.type == PresetRow.Type.WHITELIST && this.entry.whitelistPreset != null) {
                saved = ChainveinfabricClient.CONFIG.renameWhitelistPreset(this.parentScreen.presetWhitelistMode, this.entry.whitelistPreset.id, this.nameField.getValue());
            } else if (this.entry.type == PresetRow.Type.CONFIG && this.entry.configPreset != null) {
                saved = ChainveinfabricClient.CONFIG.renameConfigPreset(this.entry.configPreset.id, this.nameField.getValue());
            }

            if (saved) {
                this.lastSavedName = this.nameField.getValue();
                ChainveinfabricClient.CONFIG.save();
            }
        }
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
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                boolean selected = dd.isMouseOver(mouseX, mouseY);
                dd.handleRender(ctx, mouseX, mouseY, selected);
            }
        }
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        this.controlClick = click.hasControlDown();
        try {
            for (IDropdown dd : this.activeDropdowns) {
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
        for (IDropdown dd : this.activeDropdowns) {
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
        ConfigProxies.save();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    public void refreshLists() {
        if (this.leftList != null) this.leftList.refreshEntries();
        if (this.rightList != null) this.rightList.refreshEntries();
    }

    private String nextWhitelistPresetName(ChainVeinConfig config, ChainVeinConfig.ChainMode mode) {
        Set<String> names = new HashSet<>();
        for (WhitelistPreset preset : config.getWhitelistPresets(mode)) {
            names.add(preset.name);
        }
        return nextPresetName(names);
    }

    private String nextConfigPresetName(ChainVeinConfig config) {
        Set<String> names = new HashSet<>();
        for (ConfigPreset preset : config.getConfigPresets()) {
            names.add(preset.name);
        }
        return nextPresetName(names);
    }

    private String nextPresetName(Set<String> usedNames) {
        int index = 1;
        String baseName = StringUtils.translate("options.chainveinfabric.preset.newName");
        String name = baseName + " " + index;
        while (usedNames.contains(name)) {
            index++;
            name = baseName + " " + index;
        }
        return name;
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
        String text = this.basicLayout.controlDensity == ControlDensity.FULL
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
        return this.basicLayout.controlDensity == ControlDensity.ICON
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
        if (this.basicLayout.controlDensity == ControlDensity.FULL) {
            return StringUtils.translate(translationKey) + ": " + (enabled ? "ON" : "OFF");
        }
        if (this.basicLayout.controlDensity == ControlDensity.ICON) {
            return iconLabel + (enabled ? "+" : "-");
        }
        return (enabled ? "✓ " : "✗ ") + StringUtils.translate(translationKey);
    }

    private void updateCompactButtonTooltip(ButtonBase button, String fullLabel) {
        if (this.basicLayout.controlDensity == ControlDensity.FULL) {
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

    private void ensureAvailableMode() {
        if (ChainveinfabricClient.CONFIG.mode.isSchematicMode() && !LitematicaIntegration.isAvailable()) {
            ChainveinfabricClient.CONFIG.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
        }
    }

}
