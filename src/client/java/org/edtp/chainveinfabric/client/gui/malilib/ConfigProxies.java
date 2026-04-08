package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import fi.dy.masa.malilib.util.StringUtils;

public class ConfigProxies {
    public enum MMode implements IConfigOptionListEntry {
        CHAIN_MINE, CHAIN_PLANT, CHAIN_UTILITY;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() { return StringUtils.translate("options.chainveinfabric.mode." + this.name().toLowerCase().replace("chain_", "")); }
        @Override public IConfigOptionListEntry cycle(boolean forward) { return values()[(this.ordinal() + (forward ? 1 : -1) + values().length) % values().length]; }
        @Override public IConfigOptionListEntry fromString(String value) { try { return valueOf(value); } catch(Exception e) { return CHAIN_MINE; } }
    }
    public enum MAlgo implements IConfigOptionListEntry {
        ADJACENT_SAME, ADJACENT_WHITELIST, SPHERE, SQUARE, CUBOID;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() { return StringUtils.translate("options.chainveinfabric.searchAlgorithm." + this.name().toLowerCase()); }
        @Override public IConfigOptionListEntry cycle(boolean forward) { return values()[(this.ordinal() + (forward ? 1 : -1) + values().length) % values().length]; }
        @Override public IConfigOptionListEntry fromString(String value) { try { return valueOf(value); } catch(Exception e) { return ADJACENT_SAME; } }
    }

    public static final ConfigOptionList MODE = new ConfigOptionList("options.chainveinfabric.mode", MMode.CHAIN_MINE, "");
    public static final ConfigOptionList ALGO = new ConfigOptionList("options.chainveinfabric.searchAlgorithm", MAlgo.ADJACENT_SAME, "");
    public static final ConfigInteger MAX_BLOCKS = new ConfigInteger("options.chainveinfabric.maxBlocks", 64, 1, 2048, "");
    public static final ConfigInteger MAX_RADIUS = new ConfigInteger("options.chainveinfabric.maxRadius", 6, 1, 100, "");
    public static final ConfigBoolean DIRECT_INV = new ConfigBoolean("options.chainveinfabric.directToInventory", false, "");
    public static final ConfigBoolean TOOL_PROT = new ConfigBoolean("options.chainveinfabric.toolProtection", false, "");
    // (You can map all the rest here for the actual settings list!)

    public static void load() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        MODE.setOptionListValue(MMode.valueOf(config.mode.name()));
        ALGO.setOptionListValue(MAlgo.valueOf(config.searchAlgorithm.name()));
        MAX_BLOCKS.setIntegerValue(config.maxChainBlocks);
        MAX_RADIUS.setIntegerValue(config.maxRadius);
        DIRECT_INV.setBooleanValue(config.directToInventory);
        TOOL_PROT.setBooleanValue(config.toolProtection);
    }
    
    public static void save() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        config.mode = ChainVeinConfig.ChainMode.valueOf(((MMode)MODE.getOptionListValue()).name());
        config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.valueOf(((MAlgo)ALGO.getOptionListValue()).name());
        config.maxChainBlocks = MAX_BLOCKS.getIntegerValue();
        config.maxRadius = MAX_RADIUS.getIntegerValue();
        config.directToInventory = DIRECT_INV.getBooleanValue();
        config.toolProtection = TOOL_PROT.getBooleanValue();
        config.save();
    }
}
