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
        return super.createListWidget(listX, listY);
    }

    @Override
    public List<fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper> getConfigs() {
        java.util.List<fi.dy.masa.malilib.config.IConfigBase> configs = new java.util.ArrayList<>();
        configs.add(ConfigProxies.MODE);
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
        // Mode dropdown (using malilib cycle button from proxy config, or dropdown)
        // Let's use ButtonGeneric to cycle mode, just like tweakeroo OptionList
        ButtonGeneric modeBtn = new ButtonGeneric(centerX - 210, topY, 150, 20, getModeString());
        this.addButton(modeBtn, (button, mb) -> {
            ChainveinfabricClient.CONFIG.mode = ChainVeinConfig.ChainMode.values()[
                (ChainveinfabricClient.CONFIG.mode.ordinal() + 1) % ChainVeinConfig.ChainMode.values().length];
            ChainveinfabricClient.CONFIG.save();
            button.setDisplayString(getModeString());
            refreshLists();
        });

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
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (currentTab == Tab.BASIC) {
            if (this.leftList != null) this.leftList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.rightList != null) this.rightList.drawContents(ctx, mouseX, mouseY, partialTicks);
            if (this.searchBar != null) this.searchBar.render(ctx, mouseX, mouseY, false);
            
            // Draw titles for the lists - using the manual topY position which was 'topY + 65' (40 + 65 = 105)
            this.drawString(ctx, "Available", this.width / 2 - 210, 105 - 12, 0xFFFFFF);
            this.drawString(ctx, "Whitelisted", this.width / 2 + 10, 105 - 12, 0xFFFFFF);
        }
    }

    @Override
    public boolean onMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick) {
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
        return StringUtils.translate("options.chainveinfabric.mode." + ChainveinfabricClient.CONFIG.mode.name().toLowerCase().replace("chain_", ""));
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
