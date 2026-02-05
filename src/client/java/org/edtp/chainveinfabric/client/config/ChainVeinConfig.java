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

    public boolean isChainVeinEnabled = false;
    public int maxChainBlocks = 64;
    public boolean directToInventory = false;
    public boolean toolProtection = false;
    public Set<String> whitelistedBlocks = new HashSet<>();

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
