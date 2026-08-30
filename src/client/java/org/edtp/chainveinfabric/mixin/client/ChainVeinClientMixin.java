package org.edtp.chainveinfabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.InteractLogic;
import org.edtp.chainveinfabric.client.logic.MineLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ChainVeinClientMixin {

    @Shadow @Final private Minecraft minecraft;

    private BlockState capturedState;
    private BlockPos chainveinfabric$claimedDestroyPos;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void beforeManualMining(BlockPos pos, Direction direction,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!ChainVeinClientApi.isDispatching()
                && ChainVeinClientApi.hasPendingMineJobs()) {
            ChainVeinClientApi.cancelClientMining(this.minecraft);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ChainVeinClientApi.isDispatching() ||
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            !ChainveinfabricClient.CONFIG.mode.isManualMiningMode() ||
            ChainveinfabricClient.isAutoMiningArmed() ||
            minecraft.level == null || minecraft.player == null) {
            return;
        }

        BlockState state = minecraft.level.getBlockState(pos);
        if (MineLogic.performAndClaimOrigin(minecraft, pos, state)) {
            this.chainveinfabric$claimedDestroyPos = pos.immutable();
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "startPrediction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void replaceClaimedDestroyPacket(
            ClientPacketListener connection, Packet<?> packet) {
        if (packet instanceof ServerboundPlayerActionPacket action
                && this.chainveinfabric$claimedDestroyPos != null
                && action.getPos().equals(this.chainveinfabric$claimedDestroyPos)
                && (action.getAction()
                        == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                    || action.getAction()
                        == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)) {
            this.chainveinfabric$claimedDestroyPos = null;
            connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    action.getPos(), action.getDirection(), action.getSequence()));
            return;
        }
        connection.send(packet);
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
        
        // 仅当交互未明确失败时触发连锁（骨粉等物品客户端返回 PASS，仍需放行）
        if (result == InteractionResult.FAIL || ChainVeinClientApi.isDispatching() ||
            !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            !ChainveinfabricClient.CONFIG.mode.isInteractionMode() ||
            minecraft.level == null || minecraft.player == null || hand != InteractionHand.MAIN_HAND || capturedState == null) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        ItemStack stack = player.getItemInHand(hand);
        
        if (stack.isEmpty()) return;

        // 执行连锁处理（种植、打蜡等），使用捕获到的原始状态
        InteractLogic.perform(minecraft, pos, capturedState, stack);
        this.capturedState = null;
    }
}
