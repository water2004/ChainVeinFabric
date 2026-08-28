package org.edtp.chainveinfabric.client.config.schema;

/** Schema v5 adds the guarded automatic-mining trigger cooldown. */
public class ConfigSchemaV5 extends ConfigSchemaV4 {
    public int autoMineCooldownTicks = 40;

    public ConfigSchemaV5() {
        this.version = 5;
    }
}
