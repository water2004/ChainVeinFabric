package org.edtp.chainveinfabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.edtp.chainveinfabric.server.DirectDropCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void chainveinfabric$captureDirectDrop(
            Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (DirectDropCollector.capture((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
        }
    }
}
