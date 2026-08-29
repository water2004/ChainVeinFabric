package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.List;

public class ConfigProxies {
    public enum MAlgo implements IConfigOptionListEntry {
        ADJACENT_SAME, ADJACENT_WHITELIST, SPHERE, SQUARE, CUBOID;
        @Override public String getStringValue() { return this.name(); }
        @Override public String getDisplayName() {
            return StringUtils.translate("options.chainveinfabric.searchAlgorithm." + this.name().toLowerCase());
        }
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
    public static final ConfigBoolean QUICK_SHULKER_OVERFLOW = new ConfigBoolean(
            "options.chainveinfabric.quickShulkerOverflow", false, "");
    public static final ConfigBoolean TOOL_PROT = new ConfigBoolean("options.chainveinfabric.toolProtection", false, "");
    public static final ConfigBoolean DIAG_EDGE = new ConfigBoolean("options.chainveinfabric.diagonalEdge", false, "");
    public static final ConfigBoolean DIAG_CORNER = new ConfigBoolean("options.chainveinfabric.diagonalCorner", false, "");
    public static final ConfigBoolean SHOW_OUTLINES = new ConfigBoolean("options.chainveinfabric.showBlockOutlines", false, "");
    public static final ConfigInteger PACKET_INV = new ConfigInteger("options.chainveinfabric.packetInterval", 0, 0, 100, "");
    public static final ConfigInteger AUTO_MINE_COOLDOWN = new ConfigInteger(
            "options.chainveinfabric.autoMineCooldownTicks", 40, 10, 200, "");
    public static final ConfigHotkey OPEN_CONFIG = new ConfigHotkey("key.chainveinfabric.config", "V", "");
    public static final ConfigHotkey TOGGLE_CHAIN_VEIN = new ConfigHotkey("options.chainveinfabric.toggleChainVeinHotkey", "", "");
    public static final ConfigHotkey CYCLE_MODE = new ConfigHotkey("options.chainveinfabric.cycleModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_MINE_MODE = new ConfigHotkey("options.chainveinfabric.switchToMineModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_PLANT_MODE = new ConfigHotkey("options.chainveinfabric.switchToPlantModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_UTILITY_MODE = new ConfigHotkey("options.chainveinfabric.switchToUtilityModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_SCHEMATIC_SELECTION_MODE = new ConfigHotkey("options.chainveinfabric.switchToSchematicSelectionModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_SCHEMATIC_EXTRA_MODE = new ConfigHotkey("options.chainveinfabric.switchToSchematicExtraModeHotkey", "", "");
    public static final ConfigHotkey SWITCH_TO_SCHEMATIC_WRONG_MODE = new ConfigHotkey("options.chainveinfabric.switchToSchematicWrongModeHotkey", "", "");
    public static final ConfigHotkey TOGGLE_TARGET_WHITELIST = new ConfigHotkey("options.chainveinfabric.toggleTargetWhitelistHotkey", "", "");
    public static final ConfigBoolean ENABLE_CHAIN_VEIN_ON_MODE_HOTKEY = new ConfigBoolean(
            "options.chainveinfabric.enableChainVeinOnModeHotkey", false, "");
    private static final List<IHotkey> SCHEMATIC_MODE_HOTKEYS = List.of(
            SWITCH_TO_SCHEMATIC_SELECTION_MODE,
            SWITCH_TO_SCHEMATIC_EXTRA_MODE,
            SWITCH_TO_SCHEMATIC_WRONG_MODE
    );
    public static final List<IHotkey> HOTKEY_LIST = List.of(
            OPEN_CONFIG,
            TOGGLE_CHAIN_VEIN,
            CYCLE_MODE,
            SWITCH_TO_MINE_MODE,
            SWITCH_TO_PLANT_MODE,
            SWITCH_TO_UTILITY_MODE,
            SWITCH_TO_SCHEMATIC_SELECTION_MODE,
            SWITCH_TO_SCHEMATIC_EXTRA_MODE,
            SWITCH_TO_SCHEMATIC_WRONG_MODE,
            TOGGLE_TARGET_WHITELIST
    );

    static {
        ConfigProxySynchronizer.initializeCallbacks();
    }

    public static void load() {
        ConfigProxySynchronizer.load();
    }

    public static void save() {
        ConfigProxySynchronizer.save();
    }

    static void setAlgorithmChangeListener(Runnable listener) {
        ConfigProxySynchronizer.setAlgorithmChangeListener(listener);
    }

    public static List<IHotkey> getAvailableHotkeys() {
        if (LitematicaIntegration.isAvailable()) {
            return HOTKEY_LIST;
        }
        return HOTKEY_LIST.stream()
                .filter(hotkey -> !SCHEMATIC_MODE_HOTKEYS.contains(hotkey))
                .toList();
    }

    public static ConfigHotkey getModeHotkey(ChainVeinConfig.ChainMode mode) {
        return switch (mode) {
            case CHAIN_MINE -> SWITCH_TO_MINE_MODE;
            case CHAIN_PLANT -> SWITCH_TO_PLANT_MODE;
            case CHAIN_UTILITY -> SWITCH_TO_UTILITY_MODE;
            case SCHEMATIC_SELECTION -> SWITCH_TO_SCHEMATIC_SELECTION_MODE;
            case SCHEMATIC_EXTRA -> SWITCH_TO_SCHEMATIC_EXTRA_MODE;
            case SCHEMATIC_WRONG -> SWITCH_TO_SCHEMATIC_WRONG_MODE;
        };
    }
}
