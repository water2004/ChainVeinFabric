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

    private boolean isChainMining = false;

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

        isChainMining = true;
        try {
            performChainMining(pos, state);
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

        // 1. 广度优先搜索 (BFS) 查找相连的同类方块
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

        // 2. 预优化判断
        ItemStack tool = player.getMainHandStack();
        boolean isCreative = player.isCreative();
        boolean canHarvest = player.canHarvest(targetState); // 循环外判断一次
        boolean directToInv = Chainveinfabric.CONFIG.directToInventory;
        boolean startedWithItem = !tool.isEmpty(); // 记录初始是否持有物品
        boolean toolProtection = Chainveinfabric.CONFIG.toolProtection;
        
        for (BlockPos pos : toBreak) {
            // 如果是非创造模式，且初始有工具但现在工具损毁了，停止连锁
            if (!isCreative && startedWithItem && tool.isEmpty()) {
                break;
            }

            // 工具保护逻辑：耐久低于等于10停止
            if (!isCreative && toolProtection && startedWithItem && tool.isDamageable()) {
                if (tool.getMaxDamage() - tool.getDamage() <= 10) {
                    break;
                }
            }

            BlockState state = world.getBlockState(pos);
            if (!state.isOf(targetBlock)) continue;

            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            // 触发方块被破坏前的逻辑（粒子效果、猪灵愤怒等）
            targetBlock.onBreak(world, pos, state, player);
            
            if (world.removeBlock(pos, false)) {
                targetBlock.onBroken(world, pos, state);
                
                if (!isCreative) {
                    if (canHarvest) {
                        if (directToInv) {
                            // 获取掉落物并尝试存入背包
                            List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, blockEntity, player, tool);
                            for (ItemStack drop : drops) {
                                if (!player.getInventory().insertStack(drop)) {
                                    // 背包满了，掉落在起始位置
                                    Block.dropStack(world, startPos, drop);
                                }
                            }
                            // 显式触发经验值掉落
                            state.onStacksDropped(world, pos, tool, true);
                        } else {
                            // 正常在原位掉落物品和经验
                            Block.dropStacks(state, world, pos, blockEntity, player, tool);
                        }
                    }

                    // 消耗耐久并检查工具是否损毁
                    tool.postMine(world, state, pos, player);
                    if (tool.isEmpty()) {
                        player.sendEquipmentBreakStatus(tool.getItem(), EquipmentSlot.MAINHAND);
                    }
                }
            }
        }
    }
}