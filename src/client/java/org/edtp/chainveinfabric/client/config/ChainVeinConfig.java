package org.edtp.chainveinfabric.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV1;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV2;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChainVeinConfig extends ConfigSchemaV2 {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chainveinfabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA_VERSION = 2;

    public enum ChainMode {
        CHAIN_MINE,
        CHAIN_PLANT,
        CHAIN_UTILITY
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

    public transient Set<String> whitelistedBlocks = new HashSet<>();
    public transient Set<String> whitelistedCrops = new HashSet<>();
    public transient Set<String> whitelistedUtilityBlocks = new HashSet<>();

    public static ChainVeinConfig load() {
        if (!CONFIG_PATH.toFile().exists()) {
            ChainVeinConfig config = createFresh();
            config.save();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            ChainVeinConfig config;
            if (root.has("version") && root.get("version").getAsInt() == CURRENT_SCHEMA_VERSION) {
                config = GSON.fromJson(root, ChainVeinConfig.class);
                if (config == null) config = createFresh();
                config.fixV2();
            } else {
                ConfigSchemaV1 v1 = GSON.fromJson(root, ConfigSchemaV1.class);
                config = migrateV1ToV2(v1);
                config.save();
            }
            return config;
        } catch (Exception e) {
            // Config file is invalid, create a new one with default values.
            ChainVeinConfig config = createFresh();
            return config;
        }
    }

    private static ChainVeinConfig createFresh() {
        ChainVeinConfig config = new ChainVeinConfig();
        config.fixV2Scalars();
        config.createDefaultPresets();
        config.applyActivePresets();
        return config;
    }

    private static ChainVeinConfig migrateV1ToV2(ConfigSchemaV1 v1) {
        if (v1 == null) return createFresh();

        v1.fixNulls();

        ChainVeinConfig config = new ChainVeinConfig();
        config.version = CURRENT_SCHEMA_VERSION;
        config.isChainVeinEnabled = v1.isChainVeinEnabled;
        config.mode = v1.mode;
        config.searchAlgorithm = v1.searchAlgorithm;
        config.maxChainBlocks = v1.maxChainBlocks;
        config.maxRadius = v1.maxRadius;
        config.sphereRadius = v1.sphereRadius;
        config.squareLength = v1.squareLength;
        config.squareMiningPoint = v1.squareMiningPoint;
        config.cuboidL = v1.cuboidL;
        config.cuboidW = v1.cuboidW;
        config.cuboidH = v1.cuboidH;
        config.cuboidMiningPoint = v1.cuboidMiningPoint;
        config.directToInventory = v1.directToInventory;
        config.toolProtection = v1.toolProtection;
        config.diagonalEdge = v1.diagonalEdge;
        config.diagonalCorner = v1.diagonalCorner;
        config.packetInterval = v1.packetInterval;
        config.showBlockOutlines = v1.showBlockOutlines;
        config.openConfigHotkey = v1.openConfigHotkey;
        config.toggleChainVeinHotkey = v1.toggleChainVeinHotkey;

        config.whitelistPresets = new LinkedHashMap<>();
        config.activeWhitelistPresetIds = new LinkedHashMap<>();
        config.putDefaultWhitelistPreset(ChainMode.CHAIN_MINE, v1.whitelistedBlocks);
        config.putDefaultWhitelistPreset(ChainMode.CHAIN_PLANT, v1.whitelistedCrops);
        config.putDefaultWhitelistPreset(ChainMode.CHAIN_UTILITY, v1.whitelistedUtilityBlocks);
        config.configPresets = new ArrayList<>();
        config.configPresets.add(ConfigPreset.create(ConfigPreset.DEFAULT_ID, ConfigPreset.DEFAULT_NAME, config));
        config.activeConfigPresetId = ConfigPreset.DEFAULT_ID;
        config.applyActivePresets();
        return config;
    }

    private void fixV2() {
        this.fixV2Scalars();
        if (this.configPresets == null) this.configPresets = new ArrayList<>();
        if (this.whitelistPresets == null) this.whitelistPresets = new LinkedHashMap<>();
        if (this.activeWhitelistPresetIds == null) this.activeWhitelistPresetIds = new LinkedHashMap<>();

        if (this.configPresets.isEmpty()) {
            this.configPresets.add(ConfigPreset.create(ConfigPreset.DEFAULT_ID, ConfigPreset.DEFAULT_NAME, this));
            this.activeConfigPresetId = ConfigPreset.DEFAULT_ID;
        }

        for (ConfigPreset preset : this.configPresets) {
            fixConfigPreset(preset);
        }

        if (this.getConfigPreset(this.activeConfigPresetId) == null) {
            this.activeConfigPresetId = this.configPresets.get(0).id;
        }

        for (ChainMode mode : ChainMode.values()) {
            this.ensureWhitelistPreset(mode);
        }

        this.applyActivePresets();
    }

    private void fixV2Scalars() {
        this.version = CURRENT_SCHEMA_VERSION;
        if (this.mode == null) this.mode = ChainMode.CHAIN_MINE;
        if (this.searchAlgorithm == null) this.searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
        if (this.squareMiningPoint == null) this.squareMiningPoint = MiningPoint.CENTER;
        if (this.cuboidMiningPoint == null) this.cuboidMiningPoint = MiningPoint.CENTER;
        if (this.openConfigHotkey == null) this.openConfigHotkey = "V";
        if (this.toggleChainVeinHotkey == null) this.toggleChainVeinHotkey = "";
        if (this.whitelistedBlocks == null) this.whitelistedBlocks = new HashSet<>();
        if (this.whitelistedCrops == null) this.whitelistedCrops = new HashSet<>();
        if (this.whitelistedUtilityBlocks == null) this.whitelistedUtilityBlocks = new HashSet<>();
    }

    private void createDefaultPresets() {
        this.configPresets = new ArrayList<>();
        this.configPresets.add(ConfigPreset.create(ConfigPreset.DEFAULT_ID, ConfigPreset.DEFAULT_NAME, this));
        this.activeConfigPresetId = ConfigPreset.DEFAULT_ID;
        this.whitelistPresets = new LinkedHashMap<>();
        this.activeWhitelistPresetIds = new LinkedHashMap<>();
        for (ChainMode mode : ChainMode.values()) {
            this.putDefaultWhitelistPreset(mode, Set.of());
        }
    }

    private void putDefaultWhitelistPreset(ChainMode mode, Set<String> entries) {
        List<WhitelistPreset> presets = new ArrayList<>();
        presets.add(WhitelistPreset.create(WhitelistPreset.DEFAULT_ID, WhitelistPreset.DEFAULT_NAME, entries));
        this.whitelistPresets.put(mode.name(), presets);
        this.activeWhitelistPresetIds.put(mode.name(), WhitelistPreset.DEFAULT_ID);
    }

    private void ensureWhitelistPreset(ChainMode mode) {
        List<WhitelistPreset> presets = this.whitelistPresets.computeIfAbsent(mode.name(), key -> new ArrayList<>());

        if (presets.isEmpty()) {
            presets.add(WhitelistPreset.create(WhitelistPreset.DEFAULT_ID, WhitelistPreset.DEFAULT_NAME, Set.of()));
            this.activeWhitelistPresetIds.put(mode.name(), WhitelistPreset.DEFAULT_ID);
        }

        for (WhitelistPreset preset : presets) {
            fixWhitelistPreset(preset);
        }

        String activeId = this.activeWhitelistPresetIds.get(mode.name());
        if (this.getWhitelistPreset(mode, activeId) == null) {
            this.activeWhitelistPresetIds.put(mode.name(), presets.get(0).id);
        }
    }

    private static void fixConfigPreset(ConfigPreset preset) {
        if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
        if (preset.name == null || preset.name.isBlank()) preset.name = ConfigPreset.DEFAULT_NAME;
        if (preset.mode == null) preset.mode = ChainMode.CHAIN_MINE;
        if (preset.searchAlgorithm == null) preset.searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
        if (preset.squareMiningPoint == null) preset.squareMiningPoint = MiningPoint.CENTER;
        if (preset.cuboidMiningPoint == null) preset.cuboidMiningPoint = MiningPoint.CENTER;
    }

    private static void fixWhitelistPreset(WhitelistPreset preset) {
        if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
        if (preset.name == null || preset.name.isBlank()) preset.name = WhitelistPreset.DEFAULT_NAME;
        if (preset.entries == null) preset.entries = new HashSet<>();
    }

    private void applyActivePresets() {
        ConfigPreset configPreset = this.getActiveConfigPreset();
        if (configPreset != null) {
            configPreset.applyTo(this);
        }

        for (ChainMode mode : ChainMode.values()) {
            this.applyActiveWhitelistPreset(mode);
        }
    }

    public List<ConfigPreset> getConfigPresets() {
        if (this.configPresets == null) this.configPresets = new ArrayList<>();
        return this.configPresets;
    }

    public ConfigPreset getActiveConfigPreset() {
        return this.getConfigPreset(this.activeConfigPresetId);
    }

    public ConfigPreset getConfigPreset(String id) {
        if (id == null) return null;
        for (ConfigPreset preset : this.getConfigPresets()) {
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    public ConfigPreset createConfigPreset(String name) {
        ConfigPreset preset = ConfigPreset.create(UUID.randomUUID().toString(), sanitizePresetName(name, "Config Preset"), this);
        this.getConfigPresets().add(preset);
        return preset;
    }

    public boolean useConfigPreset(String id) {
        ConfigPreset preset = this.getConfigPreset(id);
        if (preset == null) return false;
        this.activeConfigPresetId = preset.id;
        preset.applyTo(this);
        return true;
    }

    public boolean renameConfigPreset(String id, String name) {
        ConfigPreset preset = this.getConfigPreset(id);
        if (preset == null || name == null || name.isBlank()) return false;
        preset.name = name.trim();
        return true;
    }

    public boolean deleteConfigPreset(String id) {
        if (id == null || id.equals(this.activeConfigPresetId)) return false;
        return this.getConfigPresets().removeIf(preset -> id.equals(preset.id));
    }

    public List<WhitelistPreset> getWhitelistPresets(ChainMode mode) {
        if (this.whitelistPresets == null) this.whitelistPresets = new LinkedHashMap<>();
        this.ensureWhitelistPreset(mode);
        return this.whitelistPresets.get(mode.name());
    }

    public String getActiveWhitelistPresetId(ChainMode mode) {
        if (this.activeWhitelistPresetIds == null) this.activeWhitelistPresetIds = new LinkedHashMap<>();
        this.ensureWhitelistPreset(mode);
        return this.activeWhitelistPresetIds.get(mode.name());
    }

    public WhitelistPreset getActiveWhitelistPreset(ChainMode mode) {
        return this.getWhitelistPreset(mode, this.getActiveWhitelistPresetId(mode));
    }

    public WhitelistPreset getWhitelistPreset(ChainMode mode, String id) {
        if (id == null) return null;
        List<WhitelistPreset> presets = this.whitelistPresets != null ? this.whitelistPresets.get(mode.name()) : null;
        if (presets == null) return null;
        for (WhitelistPreset preset : presets) {
            if (id.equals(preset.id)) return preset;
        }
        return null;
    }

    public WhitelistPreset createWhitelistPreset(ChainMode mode, String name) {
        WhitelistPreset active = this.getActiveWhitelistPreset(mode);
        Set<String> entries = active != null ? active.entries : Set.of();
        WhitelistPreset preset = WhitelistPreset.create(UUID.randomUUID().toString(), sanitizePresetName(name, "Whitelist Preset"), entries);
        this.getWhitelistPresets(mode).add(preset);
        return preset;
    }

    public boolean useWhitelistPreset(ChainMode mode, String id) {
        WhitelistPreset preset = this.getWhitelistPreset(mode, id);
        if (preset == null) return false;
        this.activeWhitelistPresetIds.put(mode.name(), preset.id);
        this.applyActiveWhitelistPreset(mode);
        return true;
    }

    public boolean renameWhitelistPreset(ChainMode mode, String id, String name) {
        WhitelistPreset preset = this.getWhitelistPreset(mode, id);
        if (preset == null || name == null || name.isBlank()) return false;
        preset.name = name.trim();
        return true;
    }

    public boolean deleteWhitelistPreset(ChainMode mode, String id) {
        if (id == null || id.equals(this.getActiveWhitelistPresetId(mode))) return false;
        return this.getWhitelistPresets(mode).removeIf(preset -> id.equals(preset.id));
    }

    public void syncActiveConfigPresetFromCurrent() {
        ConfigPreset preset = this.getActiveConfigPreset();
        if (preset != null) {
            preset.captureFrom(this);
        }
    }

    public void applyActiveWhitelistPreset(ChainMode mode) {
        WhitelistPreset preset = this.getActiveWhitelistPreset(mode);
        Set<String> entries = preset != null ? preset.entries : new HashSet<>();

        if (mode == ChainMode.CHAIN_MINE) {
            this.whitelistedBlocks = entries;
        } else if (mode == ChainMode.CHAIN_PLANT) {
            this.whitelistedCrops = entries;
        } else {
            this.whitelistedUtilityBlocks = entries;
        }
    }

    private static String sanitizePresetName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        return name.trim();
    }

    public void save() {
        this.fixV2Scalars();
        this.syncActiveConfigPresetFromCurrent();
        for (ChainMode mode : ChainMode.values()) {
            this.ensureWhitelistPreset(mode);
        }

        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
