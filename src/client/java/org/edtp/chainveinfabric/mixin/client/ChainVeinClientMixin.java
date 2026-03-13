package org.edtp.chainveinfabric.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ChainVeinClientMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ClientChainHandler.isChainOperating() || 
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            ChainveinfabricClient.CONFIG.mode != ChainVeinConfig.ChainMode.CHAIN_MINE ||
            client.world == null || client.player == null) {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        ClientChainHandler.performChainMine(client, pos, state);
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        
        // 只有当点击操作成功（即成功放置了作物/发生了交互）时才触发连锁
        if (!result.isAccepted() || ClientChainHandler.isChainOperating() || 
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            client.world == null || client.player == null || hand != Hand.MAIN_HAND) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        ItemStack stack = player.getStackInHand(hand);
        
        if (stack.isEmpty()) return;

        // 执行连锁处理（种植、打蜡等）
        ClientChainHandler.performChainInteract(client, pos, state, stack);
    }
}
