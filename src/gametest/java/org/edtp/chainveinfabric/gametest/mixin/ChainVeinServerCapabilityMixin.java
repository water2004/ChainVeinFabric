package org.edtp.chainveinfabric.gametest.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simulates a server that does not advertise ChainVein's custom payload. */
@Mixin(value = ClientPlayNetworking.class, remap = false)
public abstract class ChainVeinServerCapabilityMixin {
    @Inject(method = "canSend(Lnet/minecraft/resources/Identifier;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void chainVeinGameTest$forceServerAbsent(
            Identifier channel,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.parseBoolean(System.getProperty(
                "chainveinfabric.gametest.serverInstalled", "true"))) {
            cir.setReturnValue(false);
        }
    }
}
