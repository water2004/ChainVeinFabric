package org.edtp.chainveinfabric.gametest.mixin;

import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simulates a server that does not advertise ChainVein's custom payload. */
@Mixin(value = ChainVeinClientApi.class, remap = false)
public abstract class ChainVeinServerCapabilityMixin {
    @Inject(method = "canUseServerProtocol", at = @At("HEAD"), cancellable = true)
    private static void chainVeinGameTest$forceServerAbsent(
            CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.parseBoolean(System.getProperty(
                "chainveinfabric.gametest.serverInstalled", "true"))) {
            cir.setReturnValue(false);
        }
    }
}
