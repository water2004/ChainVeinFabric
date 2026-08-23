package org.edtp.chainveinfabric.client.logic;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantingItemsTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recognizesSeedsSaplingsAndSpecialPlantingItems() {
        assertTrue(PlantingItems.isPlantable(Items.WHEAT_SEEDS));
        assertTrue(PlantingItems.isPlantable(Items.OAK_SAPLING));
        assertTrue(PlantingItems.isPlantable(Items.NETHER_WART));
        assertTrue(PlantingItems.isPlantable(Items.COCOA_BEANS));
    }

    @Test
    void rejectsOrdinaryBlocksAndTools() {
        assertFalse(PlantingItems.isPlantable(Items.DIRT));
        assertFalse(PlantingItems.isPlantable(Items.STICK));
        assertFalse(PlantingItems.isPlantable(Items.DIAMOND_PICKAXE));
    }
}
