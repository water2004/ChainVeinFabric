package org.edtp.chainveinfabric.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV2;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV3;
import org.edtp.chainveinfabric.client.config.schema.ConfigSchemaV4;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Handles JSON persistence, schema migration, and repair of persisted values. */
final class ChainVeinConfigCodec {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("chainveinfabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA_VERSION = 5;

    private ChainVeinConfigCodec() {
    }

    static ChainVeinConfig load() {
        if (!CONFIG_PATH.toFile().exists()) {
            ChainVeinConfig config = createFresh();
            save(config);
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            DecodedConfig decoded = decode(root);
            if (decoded.needsSave()) save(decoded.config());
            return decoded.config();
        } catch (Exception ignored) {
            return createFresh();
        }
    }

    static void save(ChainVeinConfig config) {
        repairScalars(config);
        config.presetManager().prepareForSave();

        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
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
            return new DecodedConfig(config, repair(config));
        }
        return new DecodedConfig(createFresh(), false);
    }

    private static ChainVeinConfig createFresh() {
        ChainVeinConfig config = new ChainVeinConfig();
        repairScalars(config);
        config.presetManager().initializeDefaults();
        config.presetManager().repair();
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
        repair(config);
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
        target.whitelistPresets = source.whitelistPresets != null
                ? source.whitelistPresets
                : new LinkedHashMap<>();
        target.activeWhitelistPresetIds = source.activeWhitelistPresetIds != null
                ? source.activeWhitelistPresetIds
                : new LinkedHashMap<>();
    }

    private static boolean repair(ChainVeinConfig config) {
        repairScalars(config);
        return config.presetManager().repair();
    }

    private static void repairScalars(ChainVeinConfig config) {
        config.version = CURRENT_SCHEMA_VERSION;
        if (config.mode == null) config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
        if (config.searchAlgorithm == null) {
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        }
        if (config.squareMiningPoint == null) {
            config.squareMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        }
        if (config.cuboidMiningPoint == null) {
            config.cuboidMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        }
        if (config.openConfigHotkey == null) config.openConfigHotkey = "V";
        if (config.toggleChainVeinHotkey == null) config.toggleChainVeinHotkey = "";
        if (config.toggleTargetWhitelistHotkey == null) config.toggleTargetWhitelistHotkey = "";
        if (config.cycleModeHotkey == null) config.cycleModeHotkey = "";
        if (config.switchToMineModeHotkey == null) config.switchToMineModeHotkey = "";
        if (config.switchToPlantModeHotkey == null) config.switchToPlantModeHotkey = "";
        if (config.switchToUtilityModeHotkey == null) config.switchToUtilityModeHotkey = "";
        if (config.switchToSchematicSelectionModeHotkey == null) {
            config.switchToSchematicSelectionModeHotkey = "";
        }
        if (config.switchToSchematicExtraModeHotkey == null) {
            config.switchToSchematicExtraModeHotkey = "";
        }
        if (config.switchToSchematicWrongModeHotkey == null) {
            config.switchToSchematicWrongModeHotkey = "";
        }
        if (config.autoMineCooldownTicks < 10 || config.autoMineCooldownTicks > 200) {
            config.autoMineCooldownTicks = 40;
        }
    }

    record DecodedConfig(ChainVeinConfig config, boolean needsSave) {
    }
}
