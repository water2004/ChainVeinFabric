package org.edtp.chainveinfabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ChainVeinClientMixin {

    @Shadow @Final private Minecraft minecraft;

    private BlockState capturedState;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ClientChainHandler.isChainOperating() || 
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            ChainveinfabricClient.CONFIG.mode != ChainVeinConfig.ChainMode.CHAIN_MINE ||
            minecraft.level == null || minecraft.player == null) {
            return;
        }

        BlockState state = minecraft.level.getBlockState(pos);
        ClientChainHandler.performChainMine(minecraft, pos, state);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void beforeInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (minecraft.level != null) {
            this.capturedState = minecraft.level.getBlockState(hitResult.getBlockPos());
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        
        // 只有当点击操作成功（即成功放置了作物/发生了交互）时才触发连锁
        if (!result.consumesAction() || ClientChainHandler.isChainOperating() || 
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            minecraft.level == null || minecraft.player == null || hand != InteractionHand.MAIN_HAND || capturedState == null) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        ItemStack stack = player.getItemInHand(hand);
        
        if (stack.isEmpty()) return;

        // 执行连锁处理（种植、打蜡等），使用捕获到的原始状态
        ClientChainHandler.performChainInteract(minecraft, pos, capturedState, stack);
        this.capturedState = null;
    }
}
