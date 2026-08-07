package org.edtp.chainveinfabric.client.config.schema;

/**
 * Schema v4 adds optional Quick Shulker overflow storage for drops that do not
 * fit in the player's inventory.
 */
public class ConfigSchemaV4 extends ConfigSchemaV3 {
    public boolean quickShulkerOverflow = false;

    public ConfigSchemaV4() {
        this.version = 4;
    }
}
