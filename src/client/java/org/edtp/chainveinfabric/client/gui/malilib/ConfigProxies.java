package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigProxies {
    public enum MAlgo implements IConfigOptionListEntry {
        ADJACENT_SAME, ADJACENT_WHITELIST, SPHERE, SQUARE, CUBOID;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() { return StringUtils.translate("options.chainveinfabric.searchAlgorithm." + this.name().toLowerCase()); }
        @Override public IConfigOptionListEntry cycle(boolean forward) { return values()[(this.ordinal() + (forward ? 1 : -1) + values().length) % values().length]; }
        @Override public IConfigOptionListEntry fromString(String value) { try { return valueOf(value); } catch(Exception e) { return ADJACENT_SAME; } }
    }
    public enum MSquareMiningPoint implements IConfigOptionListEntry {
        CENTER, FRONT_TOP_LEFT, FRONT_TOP_RIGHT, BACK_BOTTOM_LEFT, BACK_BOTTOM_RIGHT;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() { return StringUtils.translate("options.chainveinfabric.miningPoint." + this.name().toLowerCase()); }
        @Override public IConfigOptionListEntry cycle(boolean forward) { return values()[(this.ordinal() + (forward ? 1 : -1) + values().length) % values().length]; }
        @Override public IConfigOptionListEntry fromString(String value) { try { return valueOf(value); } catch(Exception e) { return CENTER; } }
    }
    public enum MCuboidMiningPoint implements IConfigOptionListEntry {
        CENTER, FRONT_TOP_LEFT, FRONT_TOP_RIGHT, FRONT_BOTTOM_LEFT, FRONT_BOTTOM_RIGHT,
        BACK_TOP_LEFT, BACK_TOP_RIGHT, BACK_BOTTOM_LEFT, BACK_BOTTOM_RIGHT;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() { return StringUtils.translate("options.chainveinfabric.miningPoint." + this.name().toLowerCase()); }
        @Override public IConfigOptionListEntry cycle(boolean forward) { return values()[(this.ordinal() + (forward ? 1 : -1) + values().length) % values().length]; }
        @Override public IConfigOptionListEntry fromString(String value) { try { return valueOf(value); } catch(Exception e) { return CENTER; } }
    }

    public static final ConfigOptionList ALGO = new ConfigOptionList("options.chainveinfabric.searchAlgorithm", MAlgo.ADJACENT_SAME, "");

    public static final ConfigInteger MAX_BLOCKS = new ConfigInteger("options.chainveinfabric.maxBlocks", 64, 1, 2048, "");
    public static final ConfigInteger MAX_RADIUS = new ConfigInteger("options.chainveinfabric.maxRadius", 6, 1, 100, "");
    public static final ConfigInteger SPHERE_RADIUS = new ConfigInteger("options.chainveinfabric.sphereRadius", 3, 1, 100, "");
    public static final ConfigInteger SQUARE_LENGTH = new ConfigInteger("options.chainveinfabric.squareLength", 3, 1, 100, "");
    public static final ConfigOptionList SQUARE_POINT = new ConfigOptionList("options.chainveinfabric.miningPoint", MSquareMiningPoint.CENTER, "");
    public static final ConfigInteger CUBOID_L = new ConfigInteger("options.chainveinfabric.cuboidL", 3, 1, 100, "");
    public static final ConfigInteger CUBOID_W = new ConfigInteger("options.chainveinfabric.cuboidW", 3, 1, 100, "");
    public static final ConfigInteger CUBOID_H = new ConfigInteger("options.chainveinfabric.cuboidH", 3, 1, 100, "");
    public static final ConfigOptionList CUBOID_POINT = new ConfigOptionList("options.chainveinfabric.miningPoint", MCuboidMiningPoint.CENTER, "");

    public static final ConfigBoolean DIRECT_INV = new ConfigBoolean("options.chainveinfabric.directToInventory", false, "");
    public static final ConfigBoolean TOOL_PROT = new ConfigBoolean("options.chainveinfabric.toolProtection", false, "");
    public static final ConfigBoolean DIAG_EDGE = new ConfigBoolean("options.chainveinfabric.diagonalEdge", false, "");
    public static final ConfigBoolean DIAG_CORNER = new ConfigBoolean("options.chainveinfabric.diagonalCorner", false, "");
    public static final ConfigInteger PACKET_INV = new ConfigInteger("options.chainveinfabric.packetInterval", 0, 0, 100, "");

    private static boolean loading = false;

    static {
        MAX_BLOCKS.setValueChangeCallback(c -> { if (!loading) save(); });
        MAX_RADIUS.setValueChangeCallback(c -> { if (!loading) save(); });
        SPHERE_RADIUS.setValueChangeCallback(c -> { if (!loading) save(); });
        SQUARE_LENGTH.setValueChangeCallback(c -> { if (!loading) save(); });
        SQUARE_POINT.setValueChangeCallback(c -> { if (!loading) save(); });
        CUBOID_L.setValueChangeCallback(c -> { if (!loading) save(); });
        CUBOID_W.setValueChangeCallback(c -> { if (!loading) save(); });
        CUBOID_H.setValueChangeCallback(c -> { if (!loading) save(); });
        CUBOID_POINT.setValueChangeCallback(c -> { if (!loading) save(); });
        DIRECT_INV.setValueChangeCallback(c -> { if (!loading) save(); });
        TOOL_PROT.setValueChangeCallback(c -> { if (!loading) save(); });
        DIAG_EDGE.setValueChangeCallback(c -> { if (!loading) save(); });
        DIAG_CORNER.setValueChangeCallback(c -> { if (!loading) save(); });
        PACKET_INV.setValueChangeCallback(c -> { if (!loading) save(); });
    }

    public static void load() {
        loading = true;
        try {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            ALGO.setOptionListValue(MAlgo.valueOf(config.searchAlgorithm.name()));
            MAX_BLOCKS.setIntegerValue(config.maxChainBlocks);
            MAX_RADIUS.setIntegerValue(config.maxRadius);
            SPHERE_RADIUS.setIntegerValue(config.sphereRadius);
            SQUARE_LENGTH.setIntegerValue(config.squareLength);
            try {
                SQUARE_POINT.setOptionListValue(MSquareMiningPoint.valueOf(config.squareMiningPoint.name()));
            } catch (Exception e) {
                SQUARE_POINT.setOptionListValue(MSquareMiningPoint.CENTER);
            }
            CUBOID_L.setIntegerValue(config.cuboidL);
            CUBOID_W.setIntegerValue(config.cuboidW);
            CUBOID_H.setIntegerValue(config.cuboidH);
            try {
                CUBOID_POINT.setOptionListValue(MCuboidMiningPoint.valueOf(config.cuboidMiningPoint.name()));
            } catch (Exception e) {
                CUBOID_POINT.setOptionListValue(MCuboidMiningPoint.CENTER);
            }
            DIRECT_INV.setBooleanValue(config.directToInventory);
            TOOL_PROT.setBooleanValue(config.toolProtection);
            DIAG_EDGE.setBooleanValue(config.diagonalEdge);
            DIAG_CORNER.setBooleanValue(config.diagonalCorner);
            PACKET_INV.setIntegerValue(config.packetInterval);
        } finally {
            loading = false;
        }
    }

    public static void save() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.valueOf(((MAlgo)ALGO.getOptionListValue()).name());
        config.maxChainBlocks = MAX_BLOCKS.getIntegerValue();
        config.maxRadius = MAX_RADIUS.getIntegerValue();
        config.sphereRadius = SPHERE_RADIUS.getIntegerValue();
        config.squareLength = SQUARE_LENGTH.getIntegerValue();
        config.squareMiningPoint = ChainVeinConfig.MiningPoint.valueOf(((MSquareMiningPoint)SQUARE_POINT.getOptionListValue()).name());
        config.cuboidL = CUBOID_L.getIntegerValue();
        config.cuboidW = CUBOID_W.getIntegerValue();
        config.cuboidH = CUBOID_H.getIntegerValue();
        config.cuboidMiningPoint = ChainVeinConfig.MiningPoint.valueOf(((MCuboidMiningPoint)CUBOID_POINT.getOptionListValue()).name());
        config.directToInventory = DIRECT_INV.getBooleanValue();
        config.toolProtection = TOOL_PROT.getBooleanValue();
        config.diagonalEdge = DIAG_EDGE.getBooleanValue();
        config.diagonalCorner = DIAG_CORNER.getBooleanValue();
        config.packetInterval = PACKET_INV.getIntegerValue();
        config.save();
    }
}
