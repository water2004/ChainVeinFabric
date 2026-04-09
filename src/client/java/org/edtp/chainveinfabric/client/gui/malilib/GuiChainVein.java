package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class GuiChainVein extends fi.dy.masa.malilib.gui.GuiConfigsBase {

    private enum Tab { BASIC, SETTINGS }

    private Tab currentTab = Tab.BASIC;
    private final List<IDropdown> activeDropdowns = new ArrayList<>();
    public static abstract class MyDropdown<T> extends fi.dy.masa.malilib.gui.widgets.WidgetDropDownList<T> implements IDropdown {
        private long lastDrawn;
        public MyDropdown(int x, int y, int width, int height, int maxHeight, int maxVisibleEntries, java.util.List<T> entries, fi.dy.masa.malilib.interfaces.IStringRetriever<T> stringRetriever) {
            super(x, y, width, height, maxHeight, maxVisibleEntries, entries, stringRetriever);
        }
        @Override public boolean isMenuOpen() { return this.isOpen; }
        @Override public void setLastDrawn(long t) { this.lastDrawn = t; }
        @Override public long getLastDrawn() { return this.lastDrawn; }
        @Override public boolean handleMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) { return this.onMouseClicked(click, doubleClick); }
        @Override public void render(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, boolean selected) {
            boolean wasOpen = this.isOpen;
            this.isOpen = false;
            super.render(ctx, mouseX, mouseY, selected);
            this.isOpen = wasOpen;
            this.setLastDrawn(System.currentTimeMillis());
        }
        @Override public void handleRender(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, boolean selected) {
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
        boolean handleMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick);
        void handleRender(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, boolean selected);
    }
    private WidgetChainList leftList;
    private WidgetChainList rightList;
    private WidgetSearchBar searchBar;

    public GuiChainVein() {
        super(10, 40, "chainveinfabric", null, "options.chainveinfabric.chainVein");
        ConfigProxies.load(); // Load proxy values on launch
        ConfigProxies.ALGO.setValueChangeCallback((config) -> {
            boolean isAdvance = this.currentTab == Tab.SETTINGS;
            if (isAdvance) {
                this.reCreateListWidget();
                this.initGui();
            }
        });
    }

    @Override
    protected fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions createListWidget(int listX, int listY) {
        if (this.currentTab == Tab.BASIC) {
            return null;
        }
        return new fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions(listX, listY, 
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
            fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper wrapper, int listIndex, 
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
                
                java.util.List<fi.dy.masa.malilib.config.IConfigOptionListEntry> entries = new java.util.ArrayList<>();
                fi.dy.masa.malilib.config.IConfigOptionListEntry current = optionList.getOptionListValue();
                fi.dy.masa.malilib.config.IConfigOptionListEntry iter = current;
                if (iter != null) {
                    do {
                        entries.add(iter);
                        iter = iter.cycle(true);
                    } while (iter != current && iter != null && entries.size() < 100);
                }
                
                fi.dy.masa.malilib.gui.button.ButtonGeneric resetButton = this.createResetButton(x + configWidth + 2, y, resettable);
                
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
                
                fi.dy.masa.malilib.gui.button.IButtonActionListener resetListener = (button, mouseButton) -> {
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
    public List<fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper> getConfigs() {
        java.util.List<fi.dy.masa.malilib.config.IConfigBase> configs = new java.util.ArrayList<>();
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

        return fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper.createFor(configs);
    }

    @Override
    public void initGui() {
        super.initGui();

        int centerX = this.width / 2;
        int y = 10;

        ButtonGeneric btnBasic = new ButtonGeneric(centerX - 105, y, 100, 20, StringUtils.translate("options.chainveinfabric.tab.basic"));
        btnBasic.setEnabled(this.currentTab != Tab.BASIC);
        this.addButton(btnBasic, (button, mouseButton) -> {
            this.currentTab = Tab.BASIC;
            this.reCreateListWidget();
            this.initGui();
        });

        ButtonGeneric btnSettings = new ButtonGeneric(centerX + 5, y, 100, 20, StringUtils.translate("options.chainveinfabric.tab.settings"));
        btnSettings.setEnabled(this.currentTab != Tab.SETTINGS);
        this.addButton(btnSettings, (button, mouseButton) -> {
            this.currentTab = Tab.SETTINGS;
            this.reCreateListWidget();
            this.initGui();
        });

        int topY = 40;
        if (this.currentTab == Tab.BASIC) {
            initBasicTab(centerX, topY);
        }
    }

    private void initBasicTab(int centerX, int topY) {
        // Mode dropdown (using malilib WidgetDropDownList)
        List<ChainVeinConfig.ChainMode> modes = Arrays.asList(ChainVeinConfig.ChainMode.values());
        MyDropdown<ChainVeinConfig.ChainMode> modeDropdown = new MyDropdown<ChainVeinConfig.ChainMode>(
            centerX - 210, topY, 150, 20, 200, 5, modes, this::getModeString
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
        ButtonGeneric toggleBtn = new ButtonGeneric(centerX + 150, topY, 60, 20, getToggleString());
        this.addButton(toggleBtn, (button, mb) -> {
            ChainveinfabricClient.CONFIG.isChainVeinEnabled = !ChainveinfabricClient.CONFIG.isChainVeinEnabled;
            ChainveinfabricClient.CONFIG.save();
            button.setDisplayString(getToggleString());
        });

        // Search Bar
        this.searchBar = new WidgetSearchBar(centerX - 210, topY + 30, 420, 20, 0, fi.dy.masa.malilib.gui.MaLiLibIcons.SEARCH, LeftRight.LEFT);
        
        int listWidth = 200;
        int listTopY = topY + 65;
        int listHeight = this.height - listTopY - 20;

        this.leftList = new WidgetChainList(centerX - 210, listTopY, listWidth, listHeight, null, false, this::getLeftListData, this);
        this.leftList.bindSearchBar(searchBar);
        
        this.rightList = new WidgetChainList(centerX + 10, listTopY, listWidth, listHeight, null, true, this::getRightListData, this);

        this.refreshLists();
    }

    private void initSettingsTab(int centerX, int topY) {
    }

    @Override
    public void drawContents(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null) this.leftList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.rightList != null) this.rightList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.searchBar != null) this.searchBar.render(ctx, mouseX, mouseY, false);
            
            // Draw titles for the lists - using the manual topY position which was 'topY + 65' (40 + 65 = 105)
            this.drawString(ctx, "Available", this.width / 2 - 210, 105 - 12, 0xFFFFFF);
            this.drawString(ctx, "Whitelisted", this.width / 2 + 10, 105 - 12, 0xFFFFFF);
        }

        // Post-render open dropdowns so they appear on top of all lists/content
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                // Draw normally, our override injected inside handleRender automatically enforces isOpen=true.
                boolean selected = ((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).isMouseOver(mouseX, mouseY);
                dd.handleRender(ctx, mouseX, mouseY, selected);
            }
        }
    }

    @Override
    public boolean onMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
        for (IDropdown dd : this.activeDropdowns) {
            if (dd.isMenuOpen() && Math.abs(System.currentTimeMillis() - dd.getLastDrawn()) < 50) {
                if (((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).isMouseOver((int)click.x(), (int)click.y())) {
                    return dd.handleMouseClicked(click, doubleClick);
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
    public boolean onMouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
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
                if (((fi.dy.masa.malilib.gui.widgets.WidgetBase)dd).isMouseOver((int)mouseX, (int)mouseY)) {
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
    public boolean onKeyTyped(net.minecraft.client.input.KeyEvent key) {
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
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent character) {
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
    public void removed() {
        super.removed();
        ConfigProxies.save();
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
    
    private String getToggleString() {
        return ChainveinfabricClient.CONFIG.isChainVeinEnabled ? "ON" : "OFF";
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
