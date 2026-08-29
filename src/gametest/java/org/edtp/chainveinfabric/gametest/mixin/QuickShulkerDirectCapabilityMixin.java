package org.edtp.chainveinfabric.gametest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces the legacy adapter while the current Quick Shulker jar is installed. */
@Pseudo
@Mixin(targets = "org.edtp.chainveinfabric.compat.quickshulker.QuickStorageDirectBridge",
        remap = false)
public abstract class QuickShulkerDirectCapabilityMixin {
    @Inject(method = "isUsable", at = @At("HEAD"), cancellable = true, require = 0)
    private static void chainVeinGameTest$forceLegacy(
            CallbackInfoReturnable<Boolean> cir) {
        if ("new-legacy".equals(System.getProperty(
                "chainveinfabric.gametest.quickshulker", "legacy"))) {
            cir.setReturnValue(false);
        }
    }
}
