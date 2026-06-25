package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
    private String selectedWhitelistPresetId;
    private String selectedConfigPresetId;
    private GuiTextFieldGeneric whitelistPresetNameField;
    private GuiTextFieldGeneric configPresetNameField;
    private ButtonGeneric deleteWhitelistPresetButton;
    private ButtonGeneric useWhitelistPresetButton;
    private ButtonGeneric deleteConfigPresetButton;
    private ButtonGeneric useConfigPresetButton;

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
        protected void addConfigOption(int x, int y, int labelWidth, int configWidth, fi.dy.masa.malilib.config.IConfigBase config) {
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
                super.addConfigOption(x, y, labelWidth, configWidth, config);
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
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        int leftX = centerX - 200;
        int rightX = centerX + 20;
        int panelWidth = 180;
        int rowY = topY + 22;

        this.selectedWhitelistPresetId = getValidSelectedWhitelistPresetId(config, this.presetWhitelistMode);
        this.selectedConfigPresetId = getValidSelectedConfigPresetId(config);

        this.drawPresetLabels(leftX, rightX, topY, panelWidth);

        List<ChainVeinConfig.ChainMode> modes = Arrays.asList(ChainVeinConfig.ChainMode.values());
        MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
            leftX, rowY, panelWidth, 20, 200, 5, modes, this::getModeString
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                ChainVeinConfig.ChainMode selected = this.getSelectedEntry();
                if (selected != null && GuiChainVein.this.presetWhitelistMode != selected) {
                    GuiChainVein.this.presetWhitelistMode = selected;
                    GuiChainVein.this.selectedWhitelistPresetId = ChainveinfabricClient.CONFIG.getActiveWhitelistPresetId(selected);
                    GuiChainVein.this.initGui();
                }
            }
        };
        this.activeDropdowns.add(modeDropdown);
        modeDropdown.setSelectedEntry(this.presetWhitelistMode);
        this.addWidget(modeDropdown);

        List<WhitelistPreset> whitelistPresets = config.getWhitelistPresets(this.presetWhitelistMode);
        MyDropdown<WhitelistPreset> whitelistDropdown = new MyDropdown<WhitelistPreset>(
            leftX, rowY + 34, panelWidth, 20, 200, 6, whitelistPresets,
            preset -> getPresetDisplayName(preset.name, preset.id.equals(config.getActiveWhitelistPresetId(this.presetWhitelistMode)))
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                WhitelistPreset selected = this.getSelectedEntry();
                if (selected != null) {
                    GuiChainVein.this.selectedWhitelistPresetId = selected.id;
                    GuiChainVein.this.updateWhitelistPresetControls();
                }
            }
        };
        this.activeDropdowns.add(whitelistDropdown);
        whitelistDropdown.setSelectedEntry(config.getWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId));
        this.addWidget(whitelistDropdown);

        WhitelistPreset selectedWhitelist = config.getWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId);
        this.whitelistPresetNameField = new GuiTextFieldGeneric(leftX, rowY + 68, panelWidth, 20, this.font);
        this.whitelistPresetNameField.setValue(selectedWhitelist != null ? selectedWhitelist.name : "");
        this.addTextField(this.whitelistPresetNameField, field -> {
            ChainveinfabricClient.CONFIG.renameWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId, field.getValue());
            ChainveinfabricClient.CONFIG.save();
            return true;
        });

        ButtonGeneric newWhitelistButton = new ButtonGeneric(leftX, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.new"));
        this.addButton(newWhitelistButton, (button, mb) -> {
            WhitelistPreset preset = ChainveinfabricClient.CONFIG.createWhitelistPreset(this.presetWhitelistMode, this.whitelistPresetNameField.getValue());
            this.selectedWhitelistPresetId = preset.id;
            ChainveinfabricClient.CONFIG.save();
            this.initGui();
        });

        this.useWhitelistPresetButton = new ButtonGeneric(leftX + 61, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.use"));
        this.addButton(this.useWhitelistPresetButton, (button, mb) -> {
            if (ChainveinfabricClient.CONFIG.useWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId)) {
                ChainveinfabricClient.CONFIG.save();
                this.refreshLists();
                this.initGui();
            }
        });

        this.deleteWhitelistPresetButton = new ButtonGeneric(leftX + 122, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.delete"));
        this.addButton(this.deleteWhitelistPresetButton, (button, mb) -> {
            if (ChainveinfabricClient.CONFIG.deleteWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId)) {
                this.selectedWhitelistPresetId = ChainveinfabricClient.CONFIG.getActiveWhitelistPresetId(this.presetWhitelistMode);
                ChainveinfabricClient.CONFIG.save();
                this.initGui();
            }
        });

        List<ConfigPreset> configPresets = config.getConfigPresets();
        MyDropdown<ConfigPreset> configDropdown = new MyDropdown<ConfigPreset>(
            rightX, rowY + 34, panelWidth, 20, 200, 6, configPresets,
            preset -> getPresetDisplayName(preset.name, preset.id.equals(config.activeConfigPresetId))
        ) {
            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                ConfigPreset selected = this.getSelectedEntry();
                if (selected != null) {
                    GuiChainVein.this.selectedConfigPresetId = selected.id;
                    GuiChainVein.this.updateConfigPresetControls();
                }
            }
        };
        this.activeDropdowns.add(configDropdown);
        configDropdown.setSelectedEntry(config.getConfigPreset(this.selectedConfigPresetId));
        this.addWidget(configDropdown);

        ConfigPreset selectedConfig = config.getConfigPreset(this.selectedConfigPresetId);
        this.configPresetNameField = new GuiTextFieldGeneric(rightX, rowY + 68, panelWidth, 20, this.font);
        this.configPresetNameField.setValue(selectedConfig != null ? selectedConfig.name : "");
        this.addTextField(this.configPresetNameField, field -> {
            ChainveinfabricClient.CONFIG.renameConfigPreset(this.selectedConfigPresetId, field.getValue());
            ChainveinfabricClient.CONFIG.save();
            return true;
        });

        ButtonGeneric newConfigButton = new ButtonGeneric(rightX, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.new"));
        this.addButton(newConfigButton, (button, mb) -> {
            ConfigPreset preset = ChainveinfabricClient.CONFIG.createConfigPreset(this.configPresetNameField.getValue());
            this.selectedConfigPresetId = preset.id;
            ChainveinfabricClient.CONFIG.save();
            this.initGui();
        });

        this.useConfigPresetButton = new ButtonGeneric(rightX + 61, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.use"));
        this.addButton(this.useConfigPresetButton, (button, mb) -> {
            if (ChainveinfabricClient.CONFIG.useConfigPreset(this.selectedConfigPresetId)) {
                ChainveinfabricClient.CONFIG.save();
                ConfigProxies.load();
                this.refreshLists();
                this.initGui();
            }
        });

        this.deleteConfigPresetButton = new ButtonGeneric(rightX + 122, rowY + 102, 58, 20, StringUtils.translate("options.chainveinfabric.preset.delete"));
        this.addButton(this.deleteConfigPresetButton, (button, mb) -> {
            if (ChainveinfabricClient.CONFIG.deleteConfigPreset(this.selectedConfigPresetId)) {
                this.selectedConfigPresetId = ChainveinfabricClient.CONFIG.activeConfigPresetId;
                ChainveinfabricClient.CONFIG.save();
                this.initGui();
            }
        });

        this.updateWhitelistPresetControls();
        this.updateConfigPresetControls();
    }

    private void drawPresetLabels(int leftX, int rightX, int topY, int panelWidth) {
        this.addLabel(leftX, topY, panelWidth, 12, 0xFFFFFFFF, StringUtils.translate("options.chainveinfabric.preset.whitelist"));
        this.addLabel(rightX, topY, panelWidth, 12, 0xFFFFFFFF, StringUtils.translate("options.chainveinfabric.preset.config"));
        this.addLabel(leftX, topY + 48, panelWidth, 12, 0xFFAAAAAA, StringUtils.translate("options.chainveinfabric.preset.name"));
        this.addLabel(rightX, topY + 48, panelWidth, 12, 0xFFAAAAAA, StringUtils.translate("options.chainveinfabric.preset.name"));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null) this.leftList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.rightList != null) this.rightList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.searchBar != null) this.searchBar.render(ctx, mouseX, mouseY, false);

            this.drawString(ctx, StringUtils.translate("options.chainveinfabric.allBlocks"), this.width / 2 - 200, 105 - 12, 0xFFFFFF);
            String rightTitle = switch (ChainveinfabricClient.CONFIG.mode) {
                case CHAIN_MINE -> "options.chainveinfabric.whitelist";
                case CHAIN_PLANT -> "options.chainveinfabric.cropWhitelist";
                case CHAIN_UTILITY -> "options.chainveinfabric.utilityWhitelist";
            };
            this.drawString(ctx, StringUtils.translate(rightTitle), this.width / 2 + 5, 105 - 12, 0xFFFFFF);
        }

        // Post-render open dropdowns
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                boolean selected = dd.isMouseOver(mouseX, mouseY);
                dd.handleRender(ctx, mouseX, mouseY, selected);
            }
        }
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
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
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        if (super.onMouseReleased(click)) return true;
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null && this.leftList.onMouseReleased(click)) return true;
            if (this.rightList != null && this.rightList.onMouseReleased(click)) return true;
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
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(KeyEvent key) {
        if (super.onKeyTyped(key)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onKeyTyped(key)) {
                this.leftList.refreshEntries();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCharTyped(CharacterEvent character) {
        if (super.onCharTyped(character)) return true;
        if (currentTab == Tab.BASIC && this.searchBar != null) {
            if (this.searchBar.onCharTyped(character)) {
                this.leftList.refreshEntries();
                return true;
            }
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

    private void updateWhitelistPresetControls() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        WhitelistPreset preset = config.getWhitelistPreset(this.presetWhitelistMode, this.selectedWhitelistPresetId);
        boolean exists = preset != null;
        boolean active = exists && preset.id.equals(config.getActiveWhitelistPresetId(this.presetWhitelistMode));

        if (this.whitelistPresetNameField != null && exists && !this.whitelistPresetNameField.getValue().equals(preset.name)) {
            this.whitelistPresetNameField.setValue(preset.name);
        }
        if (this.useWhitelistPresetButton != null) this.useWhitelistPresetButton.setEnabled(exists && !active);
        if (this.deleteWhitelistPresetButton != null) this.deleteWhitelistPresetButton.setEnabled(exists && !active);
    }

    private void updateConfigPresetControls() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        ConfigPreset preset = config.getConfigPreset(this.selectedConfigPresetId);
        boolean exists = preset != null;
        boolean active = exists && preset.id.equals(config.activeConfigPresetId);

        if (this.configPresetNameField != null && exists && !this.configPresetNameField.getValue().equals(preset.name)) {
            this.configPresetNameField.setValue(preset.name);
        }
        if (this.useConfigPresetButton != null) this.useConfigPresetButton.setEnabled(exists && !active);
        if (this.deleteConfigPresetButton != null) this.deleteConfigPresetButton.setEnabled(exists && !active);
    }

    private String getValidSelectedWhitelistPresetId(ChainVeinConfig config, ChainVeinConfig.ChainMode mode) {
        if (config.getWhitelistPreset(mode, this.selectedWhitelistPresetId) != null) return this.selectedWhitelistPresetId;
        return config.getActiveWhitelistPresetId(mode);
    }

    private String getValidSelectedConfigPresetId(ChainVeinConfig config) {
        if (config.getConfigPreset(this.selectedConfigPresetId) != null) return this.selectedConfigPresetId;
        return config.activeConfigPresetId;
    }

    private String getPresetDisplayName(String name, boolean active) {
        if (active) {
            return name + " (" + StringUtils.translate("options.chainveinfabric.preset.active") + ")";
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
            Identifier identifier = Identifier.tryParse(id);
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
            return b instanceof VegetationBlock || b instanceof CropBlock || b instanceof SaplingBlock || b instanceof StemBlock || b instanceof AttachedStemBlock || b instanceof AzaleaBlock || b instanceof SeaPickleBlock;
        }
        return false;
    }
}
