package org.edtp.chainveinfabric.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ChainVeinMixin {
    @Shadow
    protected ServerWorld world;
    @Shadow
    @Final
    protected ServerPlayerEntity player;

    private boolean isChainMining = false; // Prevent recursion if tryBreakBlock is called within chain mining

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isChainMining || !Chainveinfabric.CONFIG.isChainVeinEnabled) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        if (!Chainveinfabric.CONFIG.whitelistedBlocks.contains(blockId)) {
            return;
        }

        // Logic for chain mining
        isChainMining = true;
        try {
            performChainMining(pos, state);
            // We've handled everything, including the original block.
            // Cancel the original call to prevent double breaking of the first block.
            cir.setReturnValue(true); 
        } finally {
            isChainMining = false;
        }
    }

    private void performChainMining(BlockPos startPos, BlockState targetState) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toBreak = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = Chainveinfabric.CONFIG.maxChainBlocks;
        Block targetBlock = targetState.getBlock();

        // 1. Find all connected blocks of the same type up to the limit using BFS
        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            BlockPos current = queue.poll();
            toBreak.add(current);

            for (BlockPos neighbor : BlockPos.iterate(current.add(-1, -1, -1), current.add(1, 1, 1))) {
                BlockPos immutableNeighbor = neighbor.toImmutable();
                if (!visited.contains(immutableNeighbor)) {
                    visited.add(immutableNeighbor);
                    if (world.getBlockState(immutableNeighbor).isOf(targetBlock)) {
                        queue.add(immutableNeighbor);
                    }
                }
            }
        }

        // 2. Break blocks and handle drops/inventory
        ItemStack tool = player.getMainHandStack();
        
        for (BlockPos pos : toBreak) {
            if (tool.isEmpty() && !player.isCreative()) {
                break; // Stop if tool breaks
            }

            BlockState state = world.getBlockState(pos);
            if (!state.isOf(targetBlock)) continue;

            // Handle block breaking effects and logic
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            // Check if player can harvest this specific block
            boolean canHarvest = player.canHarvest(state);

            // Call onBreak (spawns particles, triggers events)
            state.getBlock().onBreak(world, pos, state, player);
            
            // Remove block
            boolean removed = world.removeBlock(pos, false);
            if (removed) {
                state.getBlock().onBroken(world, pos, state);
                
                if (!player.isCreative()) {
                    // Handle drops ONLY if canHarvest is true
                    if (canHarvest) {
                        if (Chainveinfabric.CONFIG.directToInventory) {
                            List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, blockEntity, player, tool);
                            for (ItemStack drop : drops) {
                                if (!player.getInventory().insertStack(drop)) {
                                    // Inventory full, drop at original position
                                    Block.dropStack(world, startPos, drop);
                                }
                            }
                        } else {
                            // Normal drop at current position
                            Block.dropStacks(state, world, pos, blockEntity, player, tool);
                        }
                    }

                    // Handle tool durability
                    tool.postMine(world, state, pos, player);
                    if (tool.isEmpty()) {
                        player.sendEquipmentBreakStatus(tool.getItem(), EquipmentSlot.MAINHAND);
                    }
                }
            }
        }
    }
}
