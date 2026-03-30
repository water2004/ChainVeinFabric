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
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
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
            return GSON.fromJson(reader, ChainVeinConfig.class);
        } catch (IOException e) {
            // Config file doesn't exist or is invalid, create a new one with default values
            ChainVeinConfig config = new ChainVeinConfig();
            config.save();
            return config;
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
