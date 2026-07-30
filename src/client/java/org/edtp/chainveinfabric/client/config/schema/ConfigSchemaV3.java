package org.edtp.chainveinfabric.client.config.schema;

/**
 * Schema v3 adds the Litematica-backed chain modes and their independent
 * whitelist presets. The serialized field layout remains intentionally small;
 * mode-specific whitelist data continues to live in the preset maps.
 */
public class ConfigSchemaV3 extends ConfigSchemaV2 {
    public boolean respectSchematicRenderLayer = true;
    public boolean enableChainVeinOnModeHotkey = false;
    public String cycleModeHotkey = "";
    public String switchToMineModeHotkey = "";
    public String switchToPlantModeHotkey = "";
    public String switchToUtilityModeHotkey = "";
    public String switchToSchematicSelectionModeHotkey = "";
    public String switchToSchematicExtraModeHotkey = "";
    public String switchToSchematicWrongModeHotkey = "";

    public ConfigSchemaV3() {
        this.version = 3;
    }
}
