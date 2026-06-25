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
import net.minecraft.client.gui.GuiGraphics;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;

public class GuiChainVein extends GuiConfigsBase {

    private enum Tab { BASIC, SETTINGS, PRESETS }

    private Tab currentTab = Tab.BASIC;
    private final List<IDropdown> activeDropdowns = new ArrayList<>();
    private ChainVeinConfig.ChainMode presetWhitelistMode = ChainVeinConfig.ChainMode.CHAIN_MINE;

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

        @Override public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (this.isOpen && mouseY > this.y + this.height) {
                int visible = Math.min(this.maxVisibleEntries, this.filteredEntries.size());
                int dropHeight = this.height + (visible * this.height) + 4;
                
                if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + dropHeight) {
                    if (mouseX < this.x + this.width - this.scrollbarWidth) {
                        int relIndex = (mouseY - this.y - this.height - 1) / this.height;
                        relIndex = fi.dy.masa.malilib.util.MathUtils.clamp(relIndex, 0, visible - 1);
                        this.setSelectedEntry(this.scrollBar.getValue() + relIndex);
                        this.isOpen = false;
                        if (this.searchBar != null && this.searchBar.getTextField() != null) {
                            this.searchBar.getTextField().setValue("");
                        }
                        this.updateFilteredEntries();
                        return true;
                    }
                }
            }
            return this.onMouseClicked(mouseX, mouseY, mouseButton); 
        }

        @Override public void render(int mouseX, int mouseY, boolean selected, GuiGraphics ctx) {
            boolean wasOpen = this.isOpen;
            this.isOpen = false;
            super.render(mouseX, mouseY, selected, ctx);
            this.isOpen = wasOpen;
            this.setLastDrawn(System.currentTimeMillis());
        }
        @Override public void handleRender(GuiGraphics ctx, int mouseX, int mouseY, boolean selected) {
            boolean wasOpen = this.isOpen;
            this.isOpen = true;
            super.render(mouseX, mouseY, selected, ctx);
            this.isOpen = wasOpen;
        }
    }

    public interface IDropdown {
        boolean isMenuOpen();
        void setLastDrawn(long time);
        long getLastDrawn();
        boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton);
        void handleRender(GuiGraphics ctx, int mouseX, int mouseY, boolean selected);
        boolean isMouseOver(int mouseX, int mouseY);
    }

    private WidgetChainList leftList;
    private WidgetChainList rightList;
    private WidgetSearchBar searchBar;
    private WidgetPresetList presetList;

    public GuiChainVein() {
        super(20, 40, "chainveinfabric", null, "options.chainveinfabric.chainVein");
        this.setConfigWidth(108);
        ConfigProxies.load();
        ConfigProxies.ALGO.setValueChangeCallback((config) -> {
            ConfigProxies.save();
            if (this.currentTab == Tab.SETTINGS) {
                this.reCreateListWidget();
                this.initGui();
            }
        });
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 40;
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY) {
        if (this.currentTab != Tab.SETTINGS) {
            return null;
        }
        return new WidgetListConfigOptions(listX, listY,
            this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this) {

            @Override
            protected fi.dy.masa.malilib.gui.widgets.WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper wrapper) {
                return new DropdownConfigOption(x, y, this.browserEntryWidth, this.browserEntryHeight,
                    this.maxLabelWidth, this.configWidth, wrapper, listIndex, GuiChainVein.this, this);
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
        protected void addConfigOption(int x, int y, float zLevel, int labelWidth, int configWidth, fi.dy.masa.malilib.config.IConfigBase config) {
            if (config.getType() == fi.dy.masa.malilib.config.ConfigType.OPTION_LIST) {
                fi.dy.masa.malilib.config.IConfigOptionList optionList = (fi.dy.masa.malilib.config.IConfigOptionList) config;
                fi.dy.masa.malilib.config.IConfigResettable resettable = (fi.dy.masa.malilib.config.IConfigResettable) config;

                y += 1;
                int configHeight = 20;

                String configName = config.getConfigGuiDisplayName();
                this.addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, configName);

                String comment;
                fi.dy.masa.malilib.gui.interfaces.IConfigInfoProvider infoProvider = this.host.getHoverInfoProvider();

                if (infoProvider != null) {
                    comment = infoProvider.getHoverInfo(config);
                } else {
                    comment = config.getComment();
                }

                if (comment != null) {
                    this.addWidget(new fi.dy.masa.malilib.gui.widgets.WidgetHoverInfo(x, y + 5, labelWidth, 12, comment));
                }

                x += labelWidth + 10;

                List<fi.dy.masa.malilib.config.IConfigOptionListEntry> entries = new ArrayList<>();
                fi.dy.masa.malilib.config.IConfigOptionListEntry current = optionList.getOptionListValue();
                fi.dy.masa.malilib.config.IConfigOptionListEntry iter = current;
                if (iter != null) {
                    do {
                        entries.add(iter);
                        iter = iter.cycle(true);
                    } while (iter != current && iter != null && entries.size() < 100);
                }

                ButtonGeneric resetButton = this.createResetButton(x + configWidth + 2, y, resettable);

                MyDropdown<fi.dy.masa.malilib.config.IConfigOptionListEntry> dropdown =
                    new MyDropdown<fi.dy.masa.malilib.config.IConfigOptionListEntry>(
                        x, y, configWidth, configHeight, 200, 5, entries,
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
                super.addConfigOption(x, y, zLevel, labelWidth, configWidth, config);
            }
        }
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<fi.dy.masa.malilib.config.IConfigBase> configs = new ArrayList<>();
        configs.add(ConfigProxies.ALGO);

        switch ((ConfigProxies.MAlgo) ConfigProxies.ALGO.getOptionListValue()) {
            case SPHERE:
                configs.add(ConfigProxies.SPHERE_RADIUS);
                break;
            case SQUARE:
                configs.add(ConfigProxies.SQUARE_LENGTH);
                configs.add(ConfigProxies.SQUARE_POINT);
                break;
            case CUBOID:
                configs.add(ConfigProxies.CUBOID_L);
                configs.add(ConfigProxies.CUBOID_W);
                configs.add(ConfigProxies.CUBOID_H);
                configs.add(ConfigProxies.CUBOID_POINT);
                break;
            default:
                break;
        }

        configs.add(ConfigProxies.MAX_BLOCKS);
        configs.add(ConfigProxies.MAX_RADIUS);
        configs.add(ConfigProxies.DIRECT_INV);
        configs.add(ConfigProxies.TOOL_PROT);
        configs.add(ConfigProxies.DIAG_EDGE);
        configs.add(ConfigProxies.DIAG_CORNER);
        configs.add(ConfigProxies.PACKET_INV);
        configs.add(ConfigProxies.OPEN_CONFIG);
        configs.add(ConfigProxies.TOGGLE_CHAIN_VEIN);

        return ConfigOptionWrapper.createFor(configs);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.activeDropdowns.clear();

        int centerX = this.width / 2;
        int y = 10;

        ButtonGeneric btnBasic = new ButtonGeneric(centerX - 160, y, 100, 20, StringUtils.translate("options.chainveinfabric.tab.basic"));
        btnBasic.setEnabled(this.currentTab != Tab.BASIC);
        this.addButton(btnBasic, (button, mouseButton) -> {
            this.currentTab = Tab.BASIC;
            this.reCreateListWidget();
            this.initGui();
        });

        ButtonGeneric btnSettings = new ButtonGeneric(centerX - 50, y, 100, 20, StringUtils.translate("options.chainveinfabric.tab.settings"));
        btnSettings.setEnabled(this.currentTab != Tab.SETTINGS);
        this.addButton(btnSettings, (button, mouseButton) -> {
            this.currentTab = Tab.SETTINGS;
            this.reCreateListWidget();
            this.initGui();
        });

        ButtonGeneric btnPresets = new ButtonGeneric(centerX + 60, y, 100, 20, StringUtils.translate("options.chainveinfabric.tab.presets"));
        btnPresets.setEnabled(this.currentTab != Tab.PRESETS);
        this.addButton(btnPresets, (button, mouseButton) -> {
            this.currentTab = Tab.PRESETS;
            this.reCreateListWidget();
            this.initGui();
        });

        int topY = 40;
        if (this.currentTab == Tab.BASIC) {
            initBasicTab(centerX, topY);
        } else if (this.currentTab == Tab.PRESETS) {
            initPresetTab(centerX, topY);
        }
    }

    private void initBasicTab(int centerX, int topY) {
        final int pad = 10;
        int leftX = centerX - 200;
        int rightX = centerX + 5;
        int rightToggleX = centerX + 140;
        int outlineX = centerX - 25;

        // Mode dropdown
        List<ChainVeinConfig.ChainMode> modes = Arrays.asList(ChainVeinConfig.ChainMode.values());
        MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
            leftX, topY, 170, 20, 200, 5, modes, this::getModeString
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                ChainVeinConfig.ChainMode selected = this.getSelectedEntry();
                if (selected != null && ChainveinfabricClient.CONFIG.mode != selected) {
                    ChainveinfabricClient.CONFIG.mode = selected;
                    ChainveinfabricClient.CONFIG.save();
                    refreshLists();
                }
            }
        };
        this.activeDropdowns.add(modeDropdown);
        modeDropdown.setSelectedEntry(ChainveinfabricClient.CONFIG.mode);
        this.addWidget(modeDropdown);

        // Toggle enabled
        ButtonGeneric toggleBtn = new ButtonGeneric(rightToggleX, topY, 60, 20, getToggleString());
        this.addButton(toggleBtn, (button, mb) -> {
            ChainveinfabricClient.CONFIG.isChainVeinEnabled = !ChainveinfabricClient.CONFIG.isChainVeinEnabled;
            ChainveinfabricClient.CONFIG.save();
            button.setDisplayString(getToggleString());
        });

        // Toggle outlines
        ButtonGeneric outlineBtn = new ButtonGeneric(outlineX, topY, 80, 20, getOutlineToggleString());
        this.addButton(outlineBtn, (button, mb) -> {
            ChainveinfabricClient.CONFIG.showBlockOutlines = !ChainveinfabricClient.CONFIG.showBlockOutlines;
            ChainveinfabricClient.CONFIG.save();
            ConfigProxies.load();
            button.setDisplayString(getOutlineToggleString());
        });

        // Search Bar
        this.searchBar = new WidgetSearchBar(leftX, topY + 30, 400, 20, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);

        int listWidth = 200;
        int listTopY = topY + 65;
        int listHeight = this.height - listTopY - 20;

        this.leftList = new WidgetChainList(leftX, listTopY, listWidth, listHeight, null, false, this::getLeftListData, this);
        this.leftList.bindSearchBar(searchBar);

        this.rightList = new WidgetChainList(rightX, listTopY, listWidth, listHeight, null, true, this::getRightListData, this);

        this.refreshLists();
    }

    private void initPresetTab(int centerX, int topY) {
        int listX = centerX - 200;
        int listWidth = 400;
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
        public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
            for (WidgetPresetEntry widget : this.listWidgets) {
                if (widget.onKeyTyped(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return super.onKeyTyped(keyCode, scanCode, modifiers);
        }
    }

    private static class WidgetPresetEntry extends WidgetListEntryBase<PresetRow> {
        private final boolean isOdd;
        private final GuiChainVein parentScreen;
        private GuiTextFieldGeneric nameField;
        private String lastSavedName;

        private WidgetPresetEntry(int x, int y, int width, int height, boolean isOdd, PresetRow row, int listIndex, GuiChainVein parentScreen, WidgetPresetList parentList) {
            super(x, y, width, height, row, listIndex);
            this.isOdd = isOdd;
            this.parentScreen = parentScreen;

            int buttonWidth = 58;
            int nameWidth = Math.max(120, width - buttonWidth * 2 - 30);
            int buttonY = y + (height - 20) / 2;

            switch (row.type) {
                case WHITELIST_HEADER -> {
                    List<ChainVeinConfig.ChainMode> modes = Arrays.asList(ChainVeinConfig.ChainMode.values());
                    MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
                        x + 120, buttonY, 200, 20, 200, 5, modes, parentScreen::getModeString
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
        public void render(GuiGraphics ctx, int mouseX, int mouseY, boolean selected) {
            if (this.entry != null && this.entry.type == PresetRow.Type.WHITELIST_HEADER) {
                this.drawString(ctx, this.x + 2, this.y + 8, 0xFFFFFFFF, StringUtils.translate("options.chainveinfabric.preset.whitelist"));
                super.render(ctx, mouseX, mouseY, selected);
                return;
            }

            if (this.entry != null && this.entry.type == PresetRow.Type.CONFIG_HEADER) {
                this.drawString(ctx, this.x + 2, this.y + 8, 0xFFFFFFFF, StringUtils.translate("options.chainveinfabric.preset.config"));
                return;
            }

            if (selected || this.isMouseOver(mouseX, mouseY)) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x50FFFFFF);
            } else if (this.isOdd) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20FFFFFF);
            }

            if (this.nameField != null) {
                this.nameField.renderWidget(ctx, mouseX, mouseY, 0f);
            }

            super.render(ctx, mouseX, mouseY, selected);
        }

        @Override
        public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (this.nameField != null && this.nameField.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
            if (this.nameField != null && !this.nameField.isMouseOver(mouseX, mouseY)) {
                this.nameField.setFocused(false);
            }
            return super.onMouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.keyPressed(keyCode, scanCode, modifiers);
                if (!before.equals(this.nameField.getValue())) {
                    this.saveName();
                }
                return handled;
            }
            return super.onKeyTyped(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean onCharTyped(char charIn, int modifiers) {
            if (this.nameField != null && this.nameField.isFocused()) {
                String before = this.nameField.getValue();
                boolean handled = this.nameField.charTyped(charIn, modifiers);
                if (!before.equals(this.nameField.getValue())) {
                    this.saveName();
                }
                return handled;
            }
            return super.onCharTyped(charIn, modifiers);
        }

        @Override
        public boolean canSelectAt(int mouseX, int mouseY, int mouseButton) {
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
    public void drawContents(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null) this.leftList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.rightList != null) this.rightList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.searchBar != null) this.searchBar.render(mouseX, mouseY, false, ctx);
            
            this.drawString(ctx, StringUtils.translate("options.chainveinfabric.allBlocks"), this.width / 2 - 200, 105 - 12, 0xFFFFFF);
            String rightTitle = switch (ChainveinfabricClient.CONFIG.mode) {
                case CHAIN_MINE -> "options.chainveinfabric.whitelist";
                case CHAIN_PLANT -> "options.chainveinfabric.cropWhitelist";
                case CHAIN_UTILITY -> "options.chainveinfabric.utilityWhitelist";
            };
            this.drawString(ctx, StringUtils.translate(rightTitle), this.width / 2 + 5, 105 - 12, 0xFFFFFF);
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null) this.presetList.drawContents(ctx, mouseX, mouseY, partialTicks);
        }

    }

    @Override
    protected void drawButtonHoverTexts(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawButtonHoverTexts(ctx, mouseX, mouseY, partialTicks);
        this.renderOpenDropdowns(ctx, mouseX, mouseY);
    }

    private void renderOpenDropdowns(GuiGraphics ctx, int mouseX, int mouseY) {
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                boolean selected = dd.isMouseOver(mouseX, mouseY);
                dd.handleRender(ctx, mouseX, mouseY, selected);
            }
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                if (((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).isMouseOver(mouseX, mouseY)) {
                    return dd.handleMouseClicked(mouseX, mouseY, mouseButton);
                }
            }
        }
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (currentTab == Tab.BASIC) {
            if (this.searchBar != null && this.searchBar.onMouseClicked(mouseX, mouseY, mouseButton)) return true;
            if (this.leftList != null && this.leftList.onMouseClicked(mouseX, mouseY, mouseButton)) return true;
            if (this.rightList != null && this.rightList.onMouseClicked(mouseX, mouseY, mouseButton)) return true;
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null && this.presetList.onMouseClicked(mouseX, mouseY, mouseButton)) return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseReleased(mouseX, mouseY, mouseButton)) return true;
        if (currentTab == Tab.BASIC) {
             if (this.leftList != null && this.leftList.onMouseReleased(mouseX, mouseY, mouseButton)) return true;
             if (this.rightList != null && this.rightList.onMouseReleased(mouseX, mouseY, mouseButton)) return true;
        } else if (currentTab == Tab.PRESETS) {
            if (this.presetList != null && this.presetList.onMouseReleased(mouseX, mouseY, mouseButton)) return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                if (((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).isMouseOver(mouseX, mouseY)) {
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
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (currentTab != Tab.BASIC && super.onKeyTyped(keyCode, scanCode, modifiers)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onKeyTyped(keyCode, scanCode, modifiers)) {
                this.leftList.refreshEntries();
                return true;
            }
        } else if (currentTab == Tab.PRESETS && this.presetList != null) {
            if (this.presetList.onKeyTyped(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean onCharTyped(char charIn, int modifiers) {
        if (currentTab != Tab.BASIC && super.onCharTyped(charIn, modifiers)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onCharTyped(charIn, modifiers)) {
                this.leftList.refreshEntries();
                return true;
            }
        } else if (currentTab == Tab.PRESETS && this.presetList != null) {
            if (this.presetList.onCharTyped(charIn, modifiers)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        ConfigProxies.save();
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

    private String getToggleString() {
        return ChainveinfabricClient.CONFIG.isChainVeinEnabled ? "ON" : "OFF";
    }

    private String getOutlineToggleString() {
        String state = ChainveinfabricClient.CONFIG.showBlockOutlines ? "ON" : "OFF";
        return StringUtils.translate("options.chainveinfabric.showBlockOutlines") + ": " + state;
    }

    private List<ItemStack> getLeftListData() {
        ChainVeinConfig.ChainMode mode = ChainveinfabricClient.CONFIG.mode;
        Set<String> whitelist = getWhitelistForMode(mode);

        List<ItemStack> list = new ArrayList<>();
        if (mode == ChainVeinConfig.ChainMode.CHAIN_MINE) {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.asItem() != Items.AIR && !whitelist.contains(BuiltInRegistries.BLOCK.getKey(block).toString())) {
                    list.add(new ItemStack(block.asItem()));
                }
            }
        } else if (mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (isPlantable(item) && !whitelist.contains(BuiltInRegistries.ITEM.getKey(item).toString())) {
                    list.add(new ItemStack(item));
                }
            }
        } else {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.asItem() != Items.AIR && !whitelist.contains(BuiltInRegistries.BLOCK.getKey(block).toString())) {
                    list.add(new ItemStack(block.asItem()));
                }
            }
        }
        return list;
    }

    private List<ItemStack> getRightListData() {
        ChainVeinConfig.ChainMode mode = ChainveinfabricClient.CONFIG.mode;
        Set<String> whitelist = getWhitelistForMode(mode);

        List<ItemStack> list = new ArrayList<>();
        for (String id : whitelist) {
            ResourceLocation identifier = ResourceLocation.tryParse(id);
            if (identifier == null) continue;

            if (mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
                Item item = BuiltInRegistries.ITEM.getValue(identifier);
                if (item != null && item != Items.AIR) list.add(new ItemStack(item));
            } else {
                Block block = BuiltInRegistries.BLOCK.getValue(identifier);
                if (block != null && block.asItem() != Items.AIR) list.add(new ItemStack(block.asItem()));
            }
        }
        return list;
    }

    private Set<String> getWhitelistForMode(ChainVeinConfig.ChainMode mode) {
        if (mode == ChainVeinConfig.ChainMode.CHAIN_MINE) return ChainveinfabricClient.CONFIG.whitelistedBlocks;
        if (mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) return ChainveinfabricClient.CONFIG.whitelistedCrops;
        return ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks;
    }

    private boolean isPlantable(Item item) {
        if (item == Items.NETHER_WART || item == Items.COCOA_BEANS || item == Items.SUGAR_CANE || item == Items.BAMBOO || item == Items.SWEET_BERRIES || item == Items.CHORUS_FRUIT) return true;
        if (item instanceof BlockItem blockItem) {
            Block b = blockItem.getBlock();
            return b instanceof BushBlock || b instanceof CropBlock || b instanceof SaplingBlock || b instanceof StemBlock || b instanceof AttachedStemBlock || b instanceof AzaleaBlock || b instanceof SeaPickleBlock;
        }
        return false;
    }
}
