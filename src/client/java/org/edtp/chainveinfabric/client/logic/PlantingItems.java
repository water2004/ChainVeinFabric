package org.edtp.chainveinfabric.client.logic;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;

public final class PlantingItems {
    private PlantingItems() {
    }

    public static boolean isPlantable(Item item) {
        if (item == Items.NETHER_WART
                || item == Items.COCOA_BEANS
                || item == Items.SUGAR_CANE
                || item == Items.BAMBOO
                || item == Items.SWEET_BERRIES
                || item == Items.CHORUS_FRUIT) {
            return true;
        }

        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof VegetationBlock
                || block instanceof CropBlock
                || block instanceof SaplingBlock
                || block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof AzaleaBlock
                || block instanceof SeaPickleBlock;
    }
}
