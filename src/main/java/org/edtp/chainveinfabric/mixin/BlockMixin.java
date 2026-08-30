package org.edtp.chainveinfabric.mixin;

import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.edtp.chainveinfabric.server.DirectDropCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(
            method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"),
            cancellable = true)
    private static void chainveinfabric$deferBlockDropEntity(
            Level level,
            Supplier<ItemEntity> entityFactory,
            ItemStack stack,
            CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && DirectDropCollector.captureLazyBlockDrop(
                        serverLevel, stack, entityFactory)) {
            ci.cancel();
        }
    }
}
