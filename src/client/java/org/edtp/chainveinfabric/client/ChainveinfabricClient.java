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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.edtp.chainveinfabric.client.input.ChainVeinInputHandler;
import org.edtp.chainveinfabric.client.logic.WhitelistImportService;
import org.edtp.chainveinfabric.client.renderer.AutoMiningHazardHud;
import org.edtp.chainveinfabric.client.renderer.BlockOutlineRenderer;
import org.edtp.chainveinfabric.client.renderer.SearchWorker;
import org.edtp.chainveinfabric.client.logic.AutoMiningController;
import org.edtp.chainveinfabric.client.logic.search.SearchConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.edtp.chainveinfabric.client.logic.search.SearchService;
import fi.dy.masa.malilib.event.InputEventHandler;

public class ChainveinfabricClient implements ClientModInitializer {

    public static ChainVeinConfig CONFIG;

    // Outline preview state
    private static BlockPos outlineLastTarget = null;
    private static long outlineLastConfigHash = 0;
    private static Direction outlineLastFacing;
    private static int outlineAutoRefreshTicks;
    private static SearchService searchService;
    private static AutoMiningController autoMiningController;
    private static SearchWorker outlineWorker;

    public static SearchService getSearchService() {
        return searchService;
    }

    @Override
    public void onInitializeClient() {
        CONFIG = ChainVeinConfig.load();
        if (CONFIG.mode.isSchematicMode() && !LitematicaIntegration.isAvailable()) {
            CONFIG.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
        }
        ConfigProxies.load();
        InputEventHandler.getKeybindManager().registerKeybindProvider(ChainVeinInputHandler.getInstance());
        InputEventHandler.getKeybindManager().updateUsedKeys();

        searchService = new SearchService(Minecraft.getInstance());
        searchService.start();
        autoMiningController = new AutoMiningController(searchService);
        outlineWorker = new SearchWorker(searchService);
        RenderEventHandler.getInstance().registerWorldLastRenderer(new BlockOutlineRenderer(outlineWorker));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChainVeinClientApi.tick(client);
            WhitelistImportService.tick(client);
            autoMiningController.tick(client);
            onOutlineTick(client);
        });

        // Use modern HudElementRegistry instead of deprecated HudRenderCallback
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("chainveinfabric", "indicator"), (context, deltaTracker) -> {
            if (CONFIG != null && CONFIG.isChainVeinEnabled) {
                if (CONFIG.mode == ChainVeinConfig.ChainMode.AUTO_MINE) {
                    AutoMiningHazardHud.render(context, Minecraft.getInstance().font);
                    return;
                }
                Component activeText = Component.translatable("hud.chainveinfabric.active");
                int width = context.guiWidth();
                context.centeredText(
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
            outlineLastFacing = null;
            outlineAutoRefreshTicks = 0;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        if (client.level == null || client.player == null) {
            outlineLastTarget = null;
            outlineLastFacing = null;
            outlineAutoRefreshTicks = 0;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        boolean automatic = CONFIG.mode == ChainVeinConfig.ChainMode.AUTO_MINE;
        boolean periodicRefresh = automatic && ++outlineAutoRefreshTicks >= 5;
        if (!automatic) outlineAutoRefreshTicks = 0;
        BlockPos target = automatic ? client.player.blockPosition() : null;
        if (!automatic && client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
            target = ((BlockHitResult) client.hitResult).getBlockPos();
        }

        if (target == null || client.level == null || client.player == null) {
            outlineLastTarget = null;
            outlineLastFacing = null;
            outlineAutoRefreshTicks = 0;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        if (!automatic && client.level.getBlockState(target).isAir()) {
            outlineLastTarget = null;
            outlineLastFacing = null;
            outlineAutoRefreshTicks = 0;
            if (outlineWorker != null) outlineWorker.clear();
            return;
        }

        LitematicaContext litematicaContext = LitematicaIntegration.createContext(
                CONFIG.mode,
                CONFIG.respectSchematicRenderLayer
        );
        long configHash = computeOutlineConfigHash(CONFIG, litematicaContext);
        boolean configChanged = (configHash != outlineLastConfigHash);
        boolean targetChanged = !target.equals(outlineLastTarget);
        boolean facingChanged = client.player.getDirection() != outlineLastFacing;

        if (!configChanged && !targetChanged && !facingChanged && !periodicRefresh) return;

        outlineLastTarget = target;
        outlineLastFacing = client.player.getDirection();
        outlineAutoRefreshTicks = 0;
        outlineLastConfigHash = configHash;

        SearchConfig searchConfig = SearchConfig.from(CONFIG);
        SearchRequest request = automatic
                ? SearchRequest.automatic((ClientLevel) client.level, target,
                        client.player.getDirection(), searchConfig, client.player.isCreative())
                : SearchRequest.targeted((ClientLevel) client.level, target,
                        client.level.getBlockState(target), client.player.getDirection(),
                        searchConfig, litematicaContext, client.player.isCreative());
        outlineWorker.signal(request);
    }

    private static long computeOutlineConfigHash(ChainVeinConfig config, LitematicaContext litematicaContext) {
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
        hash = 31 * hash + config.getWhitelist(config.mode).hashCode();
        hash = 31 * hash + (config.respectSchematicRenderLayer ? 1 : 0);
        hash = 31 * hash + litematicaContext.fingerprint();
        return hash;
    }
}
