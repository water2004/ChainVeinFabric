package org.edtp.chainveinfabric.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashSet;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class ChainVeinConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chainveinfabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    public boolean isChainVeinEnabled = false;
    public ChainMode mode = ChainMode.CHAIN_MINE;
    public SearchAlgorithm searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
    public int maxChainBlocks = 64;
    public int maxRadius = 6;
    public int sphereRadius = 3;
    public int squareLength = 3;
    public MiningPoint squareMiningPoint = MiningPoint.CENTER;
    public int cuboidL = 3;
    public int cuboidW = 3;
    public int cuboidH = 3;
    public MiningPoint cuboidMiningPoint = MiningPoint.CENTER;
    public boolean directToInventory = false;
    public boolean toolProtection = false;
    public boolean diagonalEdge = false;
    public boolean diagonalCorner = false;
    public int packetInterval = 0;
    public Set<String> whitelistedBlocks = new HashSet<>();
    public Set<String> whitelistedCrops = new HashSet<>();
    public Set<String> whitelistedUtilityBlocks = new HashSet<>();

    public static ChainVeinConfig load() {
        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            ChainVeinConfig config = GSON.fromJson(reader, ChainVeinConfig.class);
            if (config == null) return new ChainVeinConfig();
            config.fixNulls();
            return config;
        } catch (Exception e) {
            // Config file doesn't exist or is invalid, create a new one with default values
            ChainVeinConfig config = new ChainVeinConfig();
            if (!CONFIG_PATH.toFile().exists()) {
                config.save();
            }
            return config;
        }
    }

    private void fixNulls() {
        if (mode == null) mode = ChainMode.CHAIN_MINE;
        if (searchAlgorithm == null) searchAlgorithm = SearchAlgorithm.ADJACENT_SAME;
        if (squareMiningPoint == null) squareMiningPoint = MiningPoint.CENTER;
        if (cuboidMiningPoint == null) cuboidMiningPoint = MiningPoint.CENTER;
        if (whitelistedBlocks == null) whitelistedBlocks = new HashSet<>();
        if (whitelistedCrops == null) whitelistedCrops = new HashSet<>();
        if (whitelistedUtilityBlocks == null) whitelistedUtilityBlocks = new HashSet<>();
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
