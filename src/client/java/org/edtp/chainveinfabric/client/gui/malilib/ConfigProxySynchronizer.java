package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.config.options.ConfigBase;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.List;
import java.util.function.Consumer;

import static org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies.*;

/** Defines the one-to-one mapping between persisted values and malilib controls. */
final class ConfigProxySynchronizer {
    private static final List<Binding> BINDINGS = List.of(
            binding(ALGO,
                    c -> ALGO.setOptionListValue(MAlgo.valueOf(c.searchAlgorithm.name())),
                    c -> c.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.valueOf(
                            ((MAlgo) ALGO.getOptionListValue()).name())),
            binding(MAX_BLOCKS, c -> MAX_BLOCKS.setIntegerValue(c.maxChainBlocks),
                    c -> c.maxChainBlocks = MAX_BLOCKS.getIntegerValue()),
            binding(MAX_RADIUS, c -> MAX_RADIUS.setIntegerValue(c.maxRadius),
                    c -> c.maxRadius = MAX_RADIUS.getIntegerValue()),
            binding(SPHERE_RADIUS, c -> SPHERE_RADIUS.setIntegerValue(c.sphereRadius),
                    c -> c.sphereRadius = SPHERE_RADIUS.getIntegerValue()),
            binding(SQUARE_LENGTH, c -> SQUARE_LENGTH.setIntegerValue(c.squareLength),
                    c -> c.squareLength = SQUARE_LENGTH.getIntegerValue()),
            binding(SQUARE_POINT,
                    c -> SQUARE_POINT.setOptionListValue(squarePoint(c.squareMiningPoint)),
                    c -> c.squareMiningPoint = ChainVeinConfig.MiningPoint.valueOf(
                            ((MSquareMiningPoint) SQUARE_POINT.getOptionListValue()).name())),
            binding(CUBOID_L, c -> CUBOID_L.setIntegerValue(c.cuboidL),
                    c -> c.cuboidL = CUBOID_L.getIntegerValue()),
            binding(CUBOID_W, c -> CUBOID_W.setIntegerValue(c.cuboidW),
                    c -> c.cuboidW = CUBOID_W.getIntegerValue()),
            binding(CUBOID_H, c -> CUBOID_H.setIntegerValue(c.cuboidH),
                    c -> c.cuboidH = CUBOID_H.getIntegerValue()),
            binding(CUBOID_POINT,
                    c -> CUBOID_POINT.setOptionListValue(cuboidPoint(c.cuboidMiningPoint)),
                    c -> c.cuboidMiningPoint = ChainVeinConfig.MiningPoint.valueOf(
                            ((MCuboidMiningPoint) CUBOID_POINT.getOptionListValue()).name())),
            binding(DIRECT_INV, c -> DIRECT_INV.setBooleanValue(c.directToInventory),
                    c -> c.directToInventory = DIRECT_INV.getBooleanValue()),
            binding(QUICK_SHULKER_OVERFLOW,
                    c -> QUICK_SHULKER_OVERFLOW.setBooleanValue(c.quickShulkerOverflow),
                    c -> c.quickShulkerOverflow = QUICK_SHULKER_OVERFLOW.getBooleanValue()),
            binding(TOOL_PROT, c -> TOOL_PROT.setBooleanValue(c.toolProtection),
                    c -> c.toolProtection = TOOL_PROT.getBooleanValue()),
            binding(DIAG_EDGE, c -> DIAG_EDGE.setBooleanValue(c.diagonalEdge),
                    c -> c.diagonalEdge = DIAG_EDGE.getBooleanValue()),
            binding(DIAG_CORNER, c -> DIAG_CORNER.setBooleanValue(c.diagonalCorner),
                    c -> c.diagonalCorner = DIAG_CORNER.getBooleanValue()),
            binding(SHOW_OUTLINES, c -> SHOW_OUTLINES.setBooleanValue(c.showBlockOutlines),
                    c -> c.showBlockOutlines = SHOW_OUTLINES.getBooleanValue()),
            binding(PACKET_INV, c -> PACKET_INV.setIntegerValue(c.packetInterval),
                    c -> c.packetInterval = PACKET_INV.getIntegerValue()),
            binding(AUTO_MINE_COOLDOWN,
                    c -> AUTO_MINE_COOLDOWN.setIntegerValue(c.autoMineCooldownTicks),
                    c -> c.autoMineCooldownTicks = AUTO_MINE_COOLDOWN.getIntegerValue()),
            binding(OPEN_CONFIG, c -> OPEN_CONFIG.setValueFromString(c.openConfigHotkey),
                    c -> c.openConfigHotkey = OPEN_CONFIG.getStringValue()),
            binding(TOGGLE_CHAIN_VEIN,
                    c -> TOGGLE_CHAIN_VEIN.setValueFromString(c.toggleChainVeinHotkey),
                    c -> c.toggleChainVeinHotkey = TOGGLE_CHAIN_VEIN.getStringValue()),
            binding(CYCLE_MODE, c -> CYCLE_MODE.setValueFromString(c.cycleModeHotkey),
                    c -> c.cycleModeHotkey = CYCLE_MODE.getStringValue()),
            binding(SWITCH_TO_MINE_MODE,
                    c -> SWITCH_TO_MINE_MODE.setValueFromString(c.switchToMineModeHotkey),
                    c -> c.switchToMineModeHotkey = SWITCH_TO_MINE_MODE.getStringValue()),
            binding(SWITCH_TO_PLANT_MODE,
                    c -> SWITCH_TO_PLANT_MODE.setValueFromString(c.switchToPlantModeHotkey),
                    c -> c.switchToPlantModeHotkey = SWITCH_TO_PLANT_MODE.getStringValue()),
            binding(SWITCH_TO_UTILITY_MODE,
                    c -> SWITCH_TO_UTILITY_MODE.setValueFromString(c.switchToUtilityModeHotkey),
                    c -> c.switchToUtilityModeHotkey = SWITCH_TO_UTILITY_MODE.getStringValue()),
            binding(SWITCH_TO_SCHEMATIC_SELECTION_MODE,
                    c -> SWITCH_TO_SCHEMATIC_SELECTION_MODE.setValueFromString(
                            c.switchToSchematicSelectionModeHotkey),
                    c -> c.switchToSchematicSelectionModeHotkey =
                            SWITCH_TO_SCHEMATIC_SELECTION_MODE.getStringValue()),
            binding(SWITCH_TO_SCHEMATIC_EXTRA_MODE,
                    c -> SWITCH_TO_SCHEMATIC_EXTRA_MODE.setValueFromString(
                            c.switchToSchematicExtraModeHotkey),
                    c -> c.switchToSchematicExtraModeHotkey =
                            SWITCH_TO_SCHEMATIC_EXTRA_MODE.getStringValue()),
            binding(SWITCH_TO_SCHEMATIC_WRONG_MODE,
                    c -> SWITCH_TO_SCHEMATIC_WRONG_MODE.setValueFromString(
                            c.switchToSchematicWrongModeHotkey),
                    c -> c.switchToSchematicWrongModeHotkey =
                            SWITCH_TO_SCHEMATIC_WRONG_MODE.getStringValue()),
            binding(TOGGLE_TARGET_WHITELIST,
                    c -> TOGGLE_TARGET_WHITELIST.setValueFromString(c.toggleTargetWhitelistHotkey),
                    c -> c.toggleTargetWhitelistHotkey = TOGGLE_TARGET_WHITELIST.getStringValue()),
            binding(ENABLE_CHAIN_VEIN_ON_MODE_HOTKEY,
                    c -> ENABLE_CHAIN_VEIN_ON_MODE_HOTKEY.setBooleanValue(
                            c.enableChainVeinOnModeHotkey),
                    c -> c.enableChainVeinOnModeHotkey =
                            ENABLE_CHAIN_VEIN_ON_MODE_HOTKEY.getBooleanValue())
    );

    private static boolean loading;
    private static Runnable algorithmChangeListener;

    private ConfigProxySynchronizer() {
    }

    static void initializeCallbacks() {
        for (Binding binding : BINDINGS) {
            registerCallback(binding);
        }
    }

    static void load() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null) return;

        loading = true;
        try {
            for (Binding binding : BINDINGS) {
                binding.load().accept(config);
            }
        } finally {
            loading = false;
        }
    }

    static void save() {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null) return;

        for (Binding binding : BINDINGS) {
            binding.save().accept(config);
        }
        config.save();
    }

    static void setAlgorithmChangeListener(Runnable listener) {
        algorithmChangeListener = listener;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerCallback(Binding binding) {
        ((ConfigBase) binding.option()).setValueChangeCallback(ignored -> {
            if (loading) return;
            save();
            if (binding.option() == ALGO && algorithmChangeListener != null) {
                algorithmChangeListener.run();
            }
        });
    }

    private static Binding binding(ConfigBase<?> option,
                                   Consumer<ChainVeinConfig> load,
                                   Consumer<ChainVeinConfig> save) {
        return new Binding(option, load, save);
    }

    private static MSquareMiningPoint squarePoint(ChainVeinConfig.MiningPoint point) {
        try {
            return MSquareMiningPoint.valueOf(point.name());
        } catch (Exception ignored) {
            return MSquareMiningPoint.CENTER;
        }
    }

    private static MCuboidMiningPoint cuboidPoint(ChainVeinConfig.MiningPoint point) {
        try {
            return MCuboidMiningPoint.valueOf(point.name());
        } catch (Exception ignored) {
            return MCuboidMiningPoint.CENTER;
        }
    }

    private record Binding(ConfigBase<?> option,
                           Consumer<ChainVeinConfig> load,
                           Consumer<ChainVeinConfig> save) {
    }
}
