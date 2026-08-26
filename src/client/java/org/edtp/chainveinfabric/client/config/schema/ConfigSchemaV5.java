package org.edtp.chainveinfabric.client.config.schema;

/** Schema v5 adds automatic mining and its dedicated mode hotkey. */
public class ConfigSchemaV5 extends ConfigSchemaV4 {
    public String switchToAutoMineModeHotkey = "";

    public ConfigSchemaV5() {
        this.version = 5;
    }
}
