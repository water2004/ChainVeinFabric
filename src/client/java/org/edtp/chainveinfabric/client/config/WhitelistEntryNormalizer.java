package org.edtp.chainveinfabric.client.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Converts persisted whitelist values to the canonical item identifiers used at runtime. */
final class WhitelistEntryNormalizer {
    private WhitelistEntryNormalizer() {
    }

    static String normalize(String entry) {
        if (entry == null || entry.isBlank()) return null;

        ResourceLocation identifier = ResourceLocation.tryParse(entry);
        if (identifier == null) return null;

        Item item = BuiltInRegistries.ITEM.get(identifier);
        if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).equals(identifier)) {
            return identifier.toString();
        }

        Block block = BuiltInRegistries.BLOCK.get(identifier);
        return getWhitelistItemId(block);
    }

    static String getWhitelistItemId(Block block) {
        if (block == null) return null;

        Item item = block.asItem();
        if (item == null || item == Items.AIR) return null;
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
