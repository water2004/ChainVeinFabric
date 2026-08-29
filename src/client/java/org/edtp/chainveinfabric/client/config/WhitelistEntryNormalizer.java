package org.edtp.chainveinfabric.client.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Converts persisted whitelist values to the canonical item identifiers used at runtime. */
final class WhitelistEntryNormalizer {
    private WhitelistEntryNormalizer() {
    }

    static String normalize(String entry) {
        if (entry == null || entry.isBlank()) return null;

        Identifier identifier = Identifier.tryParse(entry);
        if (identifier == null) return null;

        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).equals(identifier)) {
            return identifier.toString();
        }

        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        return getWhitelistItemId(block);
    }

    static String getWhitelistItemId(Block block) {
        if (block == null) return null;

        Item item = block.asItem();
        if (item == null || item == Items.AIR) return null;
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
