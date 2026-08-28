package org.edtp.chainveinfabric.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV2;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV3;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV4;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV5;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChainVeinConfig extends ConfigSchemaV5 {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chainveinfabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA_VERSION = 5;

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

    private transient Map<ChainMode, Set<String>> activeWhitelists = new EnumMap<>(ChainMode.class);

    public static ChainVeinConfig load() {
        if (!CONFIG_PATH.toFile().exists()) {
            ChainVeinConfig config = createFresh();
            config.save();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            DecodedConfig decoded = decode(root);
            if (decoded.needsSave()) decoded.config().save();
            return decoded.config();
        } catch (Exception e) {
            // Config file is invalid, create a new one with default values.
            ChainVeinConfig config = createFresh();
            return config;
        }
    }

    private static ChainVeinConfig createFresh() {
        ChainVeinConfig config = new ChainVeinConfig();
        config.fixV5Scalars();
        config.createDefaultPresets();
        config.applyActivePresets();
        return config;
    }

    private static ConfigSchemaV3 migrateV2ToV3(ConfigSchemaV2 v2) {
        if (v2 == null) return null;

        ConfigSchemaV3 v3 = new ConfigSchemaV3();
        copyV2Fields(v2, v3);
        return v3;
    }

    private static ConfigSchemaV4 migrateV3ToV4(ConfigSchemaV3 v3) {
        if (v3 == null) return null;

        ConfigSchemaV4 v4 = new ConfigSchemaV4();
        copyV2Fields(v3, v4);
        copyV3Fields(v3, v4);
        v4.quickShulkerOverflow = false;
        return v4;
    }

    private static ChainVeinConfig migrateV4ToV5(ConfigSchemaV4 v4) {
        if (v4 == null) return createFresh();

        ChainVeinConfig config = new ChainVeinConfig();
        copyV2Fields(v4, config);
        copyV3Fields(v4, config);
        config.quickShulkerOverflow = v4.quickShulkerOverflow;
        config.autoMineCooldownTicks = 40;
        config.fixV5();
        return config;
    }

    private static void copyV3Fields(ConfigSchemaV3 source, ConfigSchemaV3 target) {
        target.respectSchematicRenderLayer = source.respectSchematicRenderLayer;
        target.enableChainVeinOnModeHotkey = source.enableChainVeinOnModeHotkey;
        target.cycleModeHotkey = source.cycleModeHotkey;
        target.switchToMineModeHotkey = source.switchToMineModeHotkey;
        target.switchToPlantModeHotkey = source.switchToPlantModeHotkey;
        target.switchToUtilityModeHotkey = source.switchToUtilityModeHotkey;
        target.switchToSchematicSelectionModeHotkey = source.switchToSchematicSelectionModeHotkey;
        target.switchToSchematicExtraModeHotkey = source.switchToSchematicExtraModeHotkey;
        target.switchToSchematicWrongModeHotkey = source.switchToSchematicWrongModeHotkey;
    }

    private static void copyV2Fields(ConfigSchemaV2 source, ConfigSchemaV2 target) {
        target.isChainVeinEnabled = source.isChainVeinEnabled;
        target.mode = source.mode;
        target.searchAlgorithm = source.searchAlgorithm;
        target.maxChainBlocks = source.maxChainBlocks;
        target.maxRadius = source.maxRadius;
        target.sphereRadius = source.sphereRadius;
        target.squareLength = source.squareLength;
        target.squareMiningPoint = source.squareMiningPoint;
        target.cuboidL = source.cuboidL;
        target.cuboidW = source.cuboidW;
        target.cuboidH = source.cuboidH;
        target.cuboidMiningPoint = source.cuboidMiningPoint;
        target.directToInventory = source.directToInventory;
        target.toolProtection = source.toolProtection;
        target.diagonalEdge = source.diagonalEdge;
        target.diagonalCorner = source.diagonalCorner;
        target.packetInterval = source.packetInterval;
        target.showBlockOutlines = source.showBlockOutlines;
        target.openConfigHotkey = source.openConfigHotkey;
        target.toggleChainVeinHotkey = source.toggleChainVeinHotkey;
        target.toggleTargetWhitelistHotkey = source.toggleTargetWhitelistHotkey;
        target.configPresets = source.configPresets != null ? source.configPresets : new ArrayList<>();
        target.activeConfigPresetId = source.activeConfigPresetId;
        target.whitelistPresets = source.whitelistPresets != null ? source.whitelistPresets : new LinkedHashMap<>();
        target.activeWhitelistPresetIds = source.activeWhitelistPresetIds != null
                ? source.activeWhitelistPresetIds
                : new LinkedHashMap<>();
    }

    private boolean fixV5() {
        this.fixV5Scalars();
        if (this.configPresets == null) this.configPresets = new ArrayList<>();
        if (this.whitelistPresets == null) this.whitelistPresets = new LinkedHashMap<>();
        if (this.activeWhitelistPresetIds == null) this.activeWhitelistPresetIds = new LinkedHashMap<>();
        Set<String> presetModeKeys = Set.copyOf(this.whitelistPresets.keySet());
        Set<String> activeModeKeys = Set.copyOf(this.activeWhitelistPresetIds.keySet());

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

        boolean whitelistChanged = this.normalizeWhitelistEntries();
        boolean structureChanged = !presetModeKeys.equals(this.whitelistPresets.keySet())
                || !activeModeKeys.equals(this.activeWhitelistPresetIds.keySet());
        this.applyActivePresets();
        return whitelistChanged || structureChanged;
    }

    private void fixV5Scalars() {
        this.version = CURRENT_SCHEMA_VERSION;
        if (this.mode == null) this.mode = ChainMode.CHAIN_MINE;
        if (this.searchAlgorithm == null) this.searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
        if (this.squareMiningPoint == null) this.squareMiningPoint = MiningPoint.CENTER;
        if (this.cuboidMiningPoint == null) this.cuboidMiningPoint = MiningPoint.CENTER;
        if (this.openConfigHotkey == null) this.openConfigHotkey = "V";
        if (this.toggleChainVeinHotkey == null) this.toggleChainVeinHotkey = "";
        if (this.toggleTargetWhitelistHotkey == null) this.toggleTargetWhitelistHotkey = "";
        if (this.cycleModeHotkey == null) this.cycleModeHotkey = "";
        if (this.switchToMineModeHotkey == null) this.switchToMineModeHotkey = "";
        if (this.switchToPlantModeHotkey == null) this.switchToPlantModeHotkey = "";
        if (this.switchToUtilityModeHotkey == null) this.switchToUtilityModeHotkey = "";
        if (this.switchToSchematicSelectionModeHotkey == null) this.switchToSchematicSelectionModeHotkey = "";
        if (this.switchToSchematicExtraModeHotkey == null) this.switchToSchematicExtraModeHotkey = "";
        if (this.switchToSchematicWrongModeHotkey == null) this.switchToSchematicWrongModeHotkey = "";
        if (this.autoMineCooldownTicks < 10 || this.autoMineCooldownTicks > 200) {
            this.autoMineCooldownTicks = 40;
        }
        if (this.activeWhitelists == null) this.activeWhitelists = new EnumMap<>(ChainMode.class);
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
        if (preset.autoMineCooldownTicks < 10 || preset.autoMineCooldownTicks > 200) {
            preset.autoMineCooldownTicks = 40;
        }
    }

    private static void fixWhitelistPreset(WhitelistPreset preset) {
        if (preset.id == null || preset.id.isBlank()) preset.id = UUID.randomUUID().toString();
        if (preset.name == null || preset.name.isBlank()) preset.name = WhitelistPreset.DEFAULT_NAME;
        if (preset.entries == null) preset.entries = new HashSet<>();
    }

    /**
     * Whitelist entries are stored as item IDs. Older versions stored block IDs
     * for mining and utility targets, so convert those entries on load.
     */
    private boolean normalizeWhitelistEntries() {
        boolean changed = false;

        if (this.whitelistPresets == null) return false;

        for (ChainMode mode : ChainMode.values()) {
            List<WhitelistPreset> presets = this.whitelistPresets.get(mode.name());
            if (presets == null) continue;

            for (WhitelistPreset preset : presets) {
                Set<String> normalized = new LinkedHashSet<>();
                for (String entry : preset.entries) {
                    String normalizedEntry = normalizeWhitelistEntry(entry);
                    if (normalizedEntry != null) {
                        normalized.add(normalizedEntry);
                    }
                }

                if (!normalized.equals(preset.entries)) {
                    preset.entries = normalized;
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static String normalizeWhitelistEntry(String entry) {
        if (entry == null || entry.isBlank()) return null;

        Identifier identifier = Identifier.tryParse(entry);
        if (identifier == null) return null;

        // Keep already-valid item IDs unchanged.
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).equals(identifier)) {
            return identifier.toString();
        }

        // Convert legacy block IDs to the item used to represent the block.
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        if (block == null) return null;

        String itemId = getWhitelistItemId(block);
        return itemId;
    }

    public static String getWhitelistItemId(Block block) {
        if (block == null) return null;

        Item item = block.asItem();
        if (item == null || item == Items.AIR) return null;

        return BuiltInRegistries.ITEM.getKey(item).toString();
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
        this.activeWhitelists.put(mode, entries);
    }

    public Set<String> getWhitelist(ChainMode mode) {
        Set<String> whitelist = this.activeWhitelists.get(mode);
        if (whitelist == null) {
            this.applyActiveWhitelistPreset(mode);
            whitelist = this.activeWhitelists.get(mode);
        }
        return whitelist;
    }

    public void replaceWhitelist(ChainMode mode, Set<String> entries) {
        this.replaceWhitelist(mode, this.getActiveWhitelistPresetId(mode), entries);
    }

    public void replaceWhitelist(ChainMode mode, String presetId, Set<String> entries) {
        WhitelistPreset preset = this.getWhitelistPreset(mode, presetId);
        if (preset == null) return;

        preset.entries.clear();
        preset.entries.addAll(entries);
        if (preset.id.equals(this.getActiveWhitelistPresetId(mode))) {
            this.activeWhitelists.put(mode, preset.entries);
        }
    }

    static DecodedConfig decode(JsonObject root) {
        int storedVersion = root != null && root.has("version")
                ? root.get("version").getAsInt()
                : 1;

        if (storedVersion == 2) {
            ConfigSchemaV2 v2 = GSON.fromJson(root, ConfigSchemaV2.class);
            return new DecodedConfig(migrateV4ToV5(migrateV3ToV4(migrateV2ToV3(v2))), true);
        }

        if (storedVersion == 3) {
            ConfigSchemaV3 v3 = GSON.fromJson(root, ConfigSchemaV3.class);
            return new DecodedConfig(migrateV4ToV5(migrateV3ToV4(v3)), true);
        }

        if (storedVersion == 4) {
            ConfigSchemaV4 v4 = GSON.fromJson(root, ConfigSchemaV4.class);
            return new DecodedConfig(migrateV4ToV5(v4), true);
        }

        if (storedVersion == CURRENT_SCHEMA_VERSION) {
            ChainVeinConfig config = GSON.fromJson(root, ChainVeinConfig.class);
            if (config == null) config = createFresh();
            return new DecodedConfig(config, config.fixV5());
        }

        return new DecodedConfig(createFresh(), false);
    }

    record DecodedConfig(ChainVeinConfig config, boolean needsSave) {
    }

    private static String sanitizePresetName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        return name.trim();
    }

    public void save() {
        this.fixV5Scalars();
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
