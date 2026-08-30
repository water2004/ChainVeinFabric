package org.edtp.chainveinfabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.edtp.chainveinfabric.server.DirectDropCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Containers.class)
public abstract class ContainersMixin {
    @Inject(method = "dropItemStack", at = @At("HEAD"), cancellable = true)
    private static void chainveinfabric$deferContainerDropEntities(
            Level level,
            double x,
            double y,
            double z,
            ItemStack stack,
            CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && DirectDropCollector.captureContainerDrop(
                        serverLevel,
                        x,
                        y,
                        z,
                        stack,
                        remainder -> Containers.dropItemStack(
                                level, x, y, z, remainder))) {
            ci.cancel();
        }
    }
}
