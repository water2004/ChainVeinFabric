package org.edtp.chainveinfabric.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persistent, server-wide limits for ChainVein work and drop collection. */
public final class ChainVeinServerConfig {
    /** Hard protocol limit for one client request; this is not configurable. */
    public static final int MAX_REQUEST_POSITIONS = 2048;

    /** Maximum number of submitted positions processed across all players per tick. */
    public static final int DEFAULT_MAX_BLOCKS = 256;
    public static final int MIN_MAX_BLOCKS = 1;
    public static final int MAX_MAX_BLOCKS = 2048;

    public static final int DEFAULT_PICKUP_RADIUS = 10;
    public static final int MIN_PICKUP_RADIUS = 0;
    public static final int MAX_PICKUP_RADIUS = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger("ChainVeinFabric/ServerConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("chainveinfabric-server.json");

    private static volatile Values values = Values.defaults();

    private ChainVeinServerConfig() {
    }

    public static synchronized void load() {
        if (!Files.exists(CONFIG_PATH)) {
            values = Values.defaults();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            StoredValues stored = GSON.fromJson(reader, StoredValues.class);
            values = stored == null ? Values.defaults() : stored.sanitized();
        } catch (Exception exception) {
            LOGGER.error("Failed to load {}; using defaults", CONFIG_PATH, exception);
            values = Values.defaults();
        }
    }

    public static Values values() {
        return values;
    }

    public static synchronized int setMaxBlocks(int maxBlocks) {
        Values current = values;
        values = new Values(
                clamp(maxBlocks, MIN_MAX_BLOCKS, MAX_MAX_BLOCKS),
                current.pickupRadius());
        save();
        return values.maxBlocks();
    }

    public static synchronized int setPickupRadius(int pickupRadius) {
        Values current = values;
        values = new Values(
                current.maxBlocks(),
                clamp(pickupRadius, MIN_PICKUP_RADIUS, MAX_PICKUP_RADIUS));
        save();
        return values.pickupRadius();
    }

    static synchronized void setForTests(Values testValues) {
        values = testValues.sanitized();
    }

    private static void save() {
        Path parent = CONFIG_PATH.getParent();
        Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(StoredValues.from(values), writer);
            }
            try {
                Files.move(temporary, CONFIG_PATH,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save {}", CONFIG_PATH, exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Values(int maxBlocks, int pickupRadius) {
        public static Values defaults() {
            return new Values(
                    DEFAULT_MAX_BLOCKS,
                    DEFAULT_PICKUP_RADIUS);
        }

        private Values sanitized() {
            return new Values(
                    clamp(maxBlocks, MIN_MAX_BLOCKS, MAX_MAX_BLOCKS),
                    clamp(pickupRadius, MIN_PICKUP_RADIUS, MAX_PICKUP_RADIUS));
        }
    }

    private static final class StoredValues {
        private Integer maxBlocks;
        private Integer pickupRadius;

        private Values sanitized() {
            Values defaults = Values.defaults();
            return new Values(
                    maxBlocks == null ? defaults.maxBlocks() : maxBlocks,
                    pickupRadius == null ? defaults.pickupRadius() : pickupRadius)
                    .sanitized();
        }

        private static StoredValues from(Values values) {
            StoredValues stored = new StoredValues();
            stored.maxBlocks = values.maxBlocks();
            stored.pickupRadius = values.pickupRadius();
            return stored;
        }
    }
}
