package org.edtp.chainveinfabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fi.dy.masa.malilib.event.RenderEventHandler;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;
import org.edtp.chainveinfabric.client.input.ChainVeinInputHandler;
import org.edtp.chainveinfabric.client.renderer.BlockOutlineRenderer;
import org.edtp.chainveinfabric.client.renderer.ConfigSnapshot;
import org.edtp.chainveinfabric.client.renderer.SearchWorker;
import fi.dy.masa.malilib.event.InputEventHandler;

public class ChainveinfabricClient implements ClientModInitializer {

    public static ChainVeinConfig CONFIG;

    // Outline preview state
    private static BlockPos outlineLastTarget = null;
    private static long outlineLastConfigHash = 0;
    private static int outlineGeneration = 0;
    private static SearchWorker outlineWorker;

    @Override
    public void onInitializeClient() {
        CONFIG = ChainVeinConfig.load();
        ConfigProxies.load();
        InputEventHandler.getKeybindManager().registerKeybindProvider(ChainVeinInputHandler.getInstance());
        InputEventHandler.getKeybindManager().updateUsedKeys();

        outlineWorker = new SearchWorker();
        outlineWorker.start();
        RenderEventHandler.getInstance().registerWorldLastRenderer(new BlockOutlineRenderer(outlineWorker));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientChainHandler.onTick(client);
            onOutlineTick(client);
        });

        // Use modern HudElementRegistry instead of deprecated HudRenderCallback
        HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath("chainveinfabric", "indicator"), (context, deltaTracker) -> {
            if (CONFIG != null && CONFIG.isChainVeinEnabled) {
                Component activeText = Component.translatable("hud.chainveinfabric.active");
                int width = context.guiWidth();
                context.drawCenteredString(
                        Minecraft.getInstance().font,
                        activeText,
                        width / 2,
                        5, // Small offset from top
                        0xFFFF0000 // Red color
                );
            }
        });
    }

    private static void onOutlineTick(Minecraft client) {
        if (CONFIG == null || !CONFIG.isChainVeinEnabled || !CONFIG.showBlockOutlines) {
            outlineLastTarget = null;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        BlockPos target = null;
        Direction face = Direction.UP;
        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) client.hitResult;
            target = blockHit.getBlockPos();
            face = blockHit.getDirection();
        }

        if (target == null || client.level == null || client.player == null) {
            outlineLastTarget = null;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        if (client.level.getBlockState(target).isAir()) {
            outlineLastTarget = null;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        long configHash = computeOutlineConfigHash(CONFIG);
        boolean configChanged = (configHash != outlineLastConfigHash);
        boolean targetChanged = !target.equals(outlineLastTarget);

        if (!configChanged && !targetChanged) return;

        outlineLastTarget = target;
        outlineLastConfigHash = configHash;

        outlineWorker.signal(
            ++outlineGeneration,
            ConfigSnapshot.from(CONFIG),
            target,
            client.level.getBlockState(target),
            face,
            client.player.getDirection(),
            (ClientLevel) client.level
        );
    }

    private static long computeOutlineConfigHash(ChainVeinConfig config) {
        long hash = config.mode.ordinal();
        hash = 31 * hash + config.searchAlgorithm.ordinal();
        hash = 31 * hash + config.maxChainBlocks;
        hash = 31 * hash + config.maxRadius;
        hash = 31 * hash + config.sphereRadius;
        hash = 31 * hash + config.squareLength;
        hash = 31 * hash + config.squareMiningPoint.ordinal();
        hash = 31 * hash + config.cuboidL;
        hash = 31 * hash + config.cuboidW;
        hash = 31 * hash + config.cuboidH;
        hash = 31 * hash + config.cuboidMiningPoint.ordinal();
        hash = 31 * hash + (config.diagonalEdge ? 1 : 0);
        hash = 31 * hash + (config.diagonalCorner ? 1 : 0);
        hash = 31 * hash + config.whitelistedBlocks.hashCode();
        hash = 31 * hash + config.whitelistedCrops.hashCode();
        hash = 31 * hash + config.whitelistedUtilityBlocks.hashCode();
        return hash;
    }
}
