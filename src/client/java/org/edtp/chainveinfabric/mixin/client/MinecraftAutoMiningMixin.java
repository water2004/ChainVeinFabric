package org.edtp.chainveinfabric.mixin.client;

import net.minecraft.client.Minecraft;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Turns an attack press into one guarded automatic-mining pulse while armed. */
@Mixin(Minecraft.class)
public abstract class MinecraftAutoMiningMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void chainveinfabric$triggerAutoMining(CallbackInfoReturnable<Boolean> cir) {
        if (ChainveinfabricClient.handleAutoMiningAttack()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void chainveinfabric$suppressHeldAttack(boolean attackDown, CallbackInfo ci) {
        if (attackDown && ChainveinfabricClient.isAutoMiningArmed()) {
            ci.cancel();
        }
    }
}
