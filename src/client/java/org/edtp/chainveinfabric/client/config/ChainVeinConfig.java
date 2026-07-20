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

public class ChainVeinConfig extends ConfigSchemaV3 {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chainveinfabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA_VERSION = 3;

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
            int storedVersion = root.has("version") ? root.get("version").getAsInt() : 1;
            if (storedVersion == 2) {
                ConfigSchemaV2 v2 = GSON.fromJson(root, ConfigSchemaV2.class);
                ChainVeinConfig config = migrateV2ToV3(v2);
                config.save();
                return config;
            }

            if (storedVersion == CURRENT_SCHEMA_VERSION) {
                ChainVeinConfig config = GSON.fromJson(root, ChainVeinConfig.class);
                if (config == null) config = createFresh();
                boolean whitelistChanged = config.fixV3();
                if (whitelistChanged) config.save();
                return config;
            }

            return createFresh();
        } catch (Exception e) {
            // Config file is invalid, create a new one with default values.
            ChainVeinConfig config = createFresh();
            return config;
        }
    }

    private static ChainVeinConfig createFresh() {
        ChainVeinConfig config = new ChainVeinConfig();
        config.fixV3Scalars();
        config.createDefaultPresets();
        config.applyActivePresets();
        return config;
    }

    private static ChainVeinConfig migrateV2ToV3(ConfigSchemaV2 v2) {
        if (v2 == null) return createFresh();

        ChainVeinConfig config = new ChainVeinConfig();
        config.version = CURRENT_SCHEMA_VERSION;
        config.isChainVeinEnabled = v2.isChainVeinEnabled;
        config.mode = v2.mode;
        config.searchAlgorithm = v2.searchAlgorithm;
        config.maxChainBlocks = v2.maxChainBlocks;
        config.maxRadius = v2.maxRadius;
        config.sphereRadius = v2.sphereRadius;
        config.squareLength = v2.squareLength;
        config.squareMiningPoint = v2.squareMiningPoint;
        config.cuboidL = v2.cuboidL;
        config.cuboidW = v2.cuboidW;
        config.cuboidH = v2.cuboidH;
        config.cuboidMiningPoint = v2.cuboidMiningPoint;
        config.directToInventory = v2.directToInventory;
        config.toolProtection = v2.toolProtection;
        config.diagonalEdge = v2.diagonalEdge;
        config.diagonalCorner = v2.diagonalCorner;
        config.packetInterval = v2.packetInterval;
        config.showBlockOutlines = v2.showBlockOutlines;
        config.openConfigHotkey = v2.openConfigHotkey;
        config.toggleChainVeinHotkey = v2.toggleChainVeinHotkey;
        config.toggleTargetWhitelistHotkey = v2.toggleTargetWhitelistHotkey;
        config.configPresets = v2.configPresets != null ? v2.configPresets : new ArrayList<>();
        config.activeConfigPresetId = v2.activeConfigPresetId;
        config.whitelistPresets = v2.whitelistPresets != null ? v2.whitelistPresets : new LinkedHashMap<>();
        config.activeWhitelistPresetIds = v2.activeWhitelistPresetIds != null ? v2.activeWhitelistPresetIds : new LinkedHashMap<>();
        config.fixV3();
        return config;
    }

    private boolean fixV3() {
        this.fixV3Scalars();
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

    private void fixV3Scalars() {
        this.version = CURRENT_SCHEMA_VERSION;
        if (this.mode == null) this.mode = ChainMode.CHAIN_MINE;
        if (this.searchAlgorithm == null) this.searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
        if (this.squareMiningPoint == null) this.squareMiningPoint = MiningPoint.CENTER;
        if (this.cuboidMiningPoint == null) this.cuboidMiningPoint = MiningPoint.CENTER;
        if (this.openConfigHotkey == null) this.openConfigHotkey = "V";
        if (this.toggleChainVeinHotkey == null) this.toggleChainVeinHotkey = "";
        if (this.toggleTargetWhitelistHotkey == null) this.toggleTargetWhitelistHotkey = "";
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

    private static String sanitizePresetName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        return name.trim();
    }

    public void save() {
        this.fixV3Scalars();
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
