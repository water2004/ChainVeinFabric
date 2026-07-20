package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.compat.litematica.BlockBounds;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaImportSnapshot;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WhitelistImportService {
    private static final int BLOCKS_PER_TICK = 8192;
    private static ImportJob activeJob;

    private WhitelistImportService() {
    }

    public static void start(Minecraft client, ChainVeinConfig.ChainMode mode, Runnable completionCallback) {
        if (client.level == null || !mode.isSchematicMode()) return;

        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        LitematicaImportSnapshot snapshot = LitematicaIntegration.createImportSnapshot(
                mode,
                config.respectSchematicRenderLayer
        );
        activeJob = new ImportJob(
                client.level,
                mode,
                config.getActiveWhitelistPresetId(mode),
                snapshot.bounds(),
                snapshot.context(),
                completionCallback
        );
        showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.import.started"));
    }

    public static void tick(Minecraft client) {
        ImportJob job = activeJob;
        if (job == null) return;

        if (client.level != job.world) {
            activeJob = null;
            showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.import.cancelled"));
            return;
        }

        if (job.process(BLOCKS_PER_TICK)) {
            ChainveinfabricClient.CONFIG.replaceWhitelist(job.mode, job.presetId, job.entries);
            ChainveinfabricClient.CONFIG.save();
            activeJob = null;
            job.completionCallback.run();
            showOverlay(client, Component.translatable(
                    "message.chainveinfabric.whitelist.import.completed",
                    job.entries.size(),
                    job.matchingBlocks,
                    job.loadedChunks,
                    job.skippedChunks
            ));
        }
    }

    private static void showOverlay(Minecraft client, Component message) {
        if (client.gui != null) {
            client.gui.hud.setOverlayMessage(message, false);
        }
    }

    private static final class ImportJob {
        private final ClientLevel world;
        private final ChainVeinConfig.ChainMode mode;
        private final String presetId;
        private final List<BlockBounds> bounds;
        private final LitematicaContext context;
        private final Runnable completionCallback;
        private final Set<String> entries = new LinkedHashSet<>();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private int boundsIndex;
        private int chunkX;
        private int chunkZ;
        private boolean chunkCursorInitialized;
        private ScanBox scanBox;
        private int scanX;
        private int scanY;
        private int scanZ;
        private long matchingBlocks;
        private int loadedChunks;
        private int skippedChunks;

        private ImportJob(ClientLevel world, ChainVeinConfig.ChainMode mode, String presetId,
                          List<BlockBounds> bounds, LitematicaContext context, Runnable completionCallback) {
            this.world = world;
            this.mode = mode;
            this.presetId = presetId;
            this.bounds = bounds;
            this.context = context;
            this.completionCallback = completionCallback;
        }

        private boolean process(int budget) {
            while (budget > 0) {
                if (this.scanBox == null && !this.moveToNextLoadedChunk()) {
                    return true;
                }

                this.mutablePos.set(this.scanX, this.scanY, this.scanZ);
                if (this.context.matches(this.world, this.mutablePos)) {
                    this.matchingBlocks++;
                    BlockState state = this.world.getBlockState(this.mutablePos);
                    String itemId = ChainVeinConfig.getWhitelistItemId(state.getBlock());
                    if (itemId != null) this.entries.add(itemId);
                }

                this.advancePosition();
                budget--;
            }
            return false;
        }

        private boolean moveToNextLoadedChunk() {
            while (this.boundsIndex < this.bounds.size()) {
                BlockBounds bounds = this.bounds.get(this.boundsIndex);
                if (!this.chunkCursorInitialized) {
                    this.chunkX = bounds.minX() >> 4;
                    this.chunkZ = bounds.minZ() >> 4;
                    this.chunkCursorInitialized = true;
                }

                int currentChunkX = this.chunkX;
                int currentChunkZ = this.chunkZ;
                this.advanceChunkCursor(bounds);

                if (!this.world.hasChunk(currentChunkX, currentChunkZ)) {
                    this.skippedChunks++;
                    continue;
                }

                this.loadedChunks++;
                this.scanBox = ScanBox.intersection(bounds, currentChunkX, currentChunkZ);
                this.scanX = this.scanBox.minX;
                this.scanY = this.scanBox.minY;
                this.scanZ = this.scanBox.minZ;
                return true;
            }
            return false;
        }

        private void advanceChunkCursor(BlockBounds bounds) {
            int maxChunkX = bounds.maxX() >> 4;
            int maxChunkZ = bounds.maxZ() >> 4;
            if (this.chunkX < maxChunkX) {
                this.chunkX++;
            } else if (this.chunkZ < maxChunkZ) {
                this.chunkX = bounds.minX() >> 4;
                this.chunkZ++;
            } else {
                this.boundsIndex++;
                this.chunkCursorInitialized = false;
            }
        }

        private void advancePosition() {
            if (this.scanX < this.scanBox.maxX) {
                this.scanX++;
            } else if (this.scanZ < this.scanBox.maxZ) {
                this.scanX = this.scanBox.minX;
                this.scanZ++;
            } else if (this.scanY < this.scanBox.maxY) {
                this.scanX = this.scanBox.minX;
                this.scanZ = this.scanBox.minZ;
                this.scanY++;
            } else {
                this.scanBox = null;
            }
        }
    }

    private record ScanBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static ScanBox intersection(BlockBounds bounds, int chunkX, int chunkZ) {
            return new ScanBox(
                    Math.max(bounds.minX(), chunkX << 4),
                    bounds.minY(),
                    Math.max(bounds.minZ(), chunkZ << 4),
                    Math.min(bounds.maxX(), (chunkX << 4) + 15),
                    bounds.maxY(),
                    Math.min(bounds.maxZ(), (chunkZ << 4) + 15)
            );
        }
    }
}
