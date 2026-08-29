package org.edtp.chainveinfabric.client.config;

import net.minecraft.world.level.block.Block;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV5;

import java.util.List;
import java.util.Set;

/** Current serialized configuration model and its stable domain API. */
public class ChainVeinConfig extends ConfigSchemaV5 {
    public enum ChainMode {
        CHAIN_MINE,
        CHAIN_PLANT,
        CHAIN_UTILITY,
        SCHEMATIC_SELECTION,
        SCHEMATIC_EXTRA,
        SCHEMATIC_WRONG;

        public boolean isMiningMode() {
            return this == CHAIN_MINE || this.isSchematicMode();
        }

        /** Kept as a stable API alias now that all mining modes share one path. */
        public boolean isManualMiningMode() {
            return this.isMiningMode();
        }

        public boolean isInteractionMode() {
            return this == CHAIN_PLANT || this == CHAIN_UTILITY;
        }

        public boolean isSchematicMode() {
            return this == SCHEMATIC_SELECTION || this == SCHEMATIC_EXTRA || this == SCHEMATIC_WRONG;
        }
    }

    public enum SearchAlgorithm {
        ADJACENT_SAME,
        ADJACENT_WHITELIST,
        SPHERE,
        SQUARE,
        CUBOID
    }

    public enum MiningPoint {
        CENTER,
        FRONT_TOP_LEFT,
        FRONT_TOP_RIGHT,
        FRONT_BOTTOM_LEFT,
        FRONT_BOTTOM_RIGHT,
        BACK_TOP_LEFT,
        BACK_TOP_RIGHT,
        BACK_BOTTOM_LEFT,
        BACK_BOTTOM_RIGHT
    }

    private transient ConfigPresetManager presetManager;

    public static ChainVeinConfig load() {
        return ChainVeinConfigCodec.load();
    }

    public void save() {
        ChainVeinConfigCodec.save(this);
    }

    public static String getWhitelistItemId(Block block) {
        return WhitelistEntryNormalizer.getWhitelistItemId(block);
    }

    public List<ConfigPreset> getConfigPresets() {
        return presetManager().getConfigPresets();
    }

    public ConfigPreset getActiveConfigPreset() {
        return presetManager().getActiveConfigPreset();
    }

    public ConfigPreset getConfigPreset(String id) {
        return presetManager().getConfigPreset(id);
    }

    public ConfigPreset createConfigPreset(String name) {
        return presetManager().createConfigPreset(name);
    }

    public boolean useConfigPreset(String id) {
        return presetManager().useConfigPreset(id);
    }

    public boolean renameConfigPreset(String id, String name) {
        return presetManager().renameConfigPreset(id, name);
    }

    public boolean deleteConfigPreset(String id) {
        return presetManager().deleteConfigPreset(id);
    }

    public List<WhitelistPreset> getWhitelistPresets(ChainMode mode) {
        return presetManager().getWhitelistPresets(mode);
    }

    public String getActiveWhitelistPresetId(ChainMode mode) {
        return presetManager().getActiveWhitelistPresetId(mode);
    }

    public WhitelistPreset getActiveWhitelistPreset(ChainMode mode) {
        return presetManager().getActiveWhitelistPreset(mode);
    }

    public WhitelistPreset getWhitelistPreset(ChainMode mode, String id) {
        return presetManager().getWhitelistPreset(mode, id);
    }

    public WhitelistPreset createWhitelistPreset(ChainMode mode, String name) {
        return presetManager().createWhitelistPreset(mode, name);
    }

    public boolean useWhitelistPreset(ChainMode mode, String id) {
        return presetManager().useWhitelistPreset(mode, id);
    }

    public boolean renameWhitelistPreset(ChainMode mode, String id, String name) {
        return presetManager().renameWhitelistPreset(mode, id, name);
    }

    public boolean deleteWhitelistPreset(ChainMode mode, String id) {
        return presetManager().deleteWhitelistPreset(mode, id);
    }

    public void syncActiveConfigPresetFromCurrent() {
        presetManager().syncActiveConfigPresetFromCurrent();
    }

    public void applyActiveWhitelistPreset(ChainMode mode) {
        presetManager().applyActiveWhitelistPreset(mode);
    }

    public Set<String> getWhitelist(ChainMode mode) {
        return presetManager().getWhitelist(mode);
    }

    public void replaceWhitelist(ChainMode mode, Set<String> entries) {
        presetManager().replaceWhitelist(mode, entries);
    }

    public void replaceWhitelist(ChainMode mode, String presetId, Set<String> entries) {
        presetManager().replaceWhitelist(mode, presetId, entries);
    }

    ConfigPresetManager presetManager() {
        if (this.presetManager == null) {
            this.presetManager = new ConfigPresetManager(this);
        }
        return this.presetManager;
    }
}
