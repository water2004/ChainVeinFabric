package org.edtp.chainveinfabric.client.config;

import com.google.gson.JsonParser;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainVeinConfigTest {
    @Test
    void migratesV2ThroughV3AndV4ToV5() {
        ChainVeinConfigCodec.DecodedConfig decoded = decode("""
                {
                  "version": 2,
                  "isChainVeinEnabled": true,
                  "mode": "CHAIN_PLANT",
                  "searchAlgorithm": "SPHERE",
                  "maxChainBlocks": 123,
                  "directToInventory": true,
                  "configPresets": [],
                  "whitelistPresets": {},
                  "activeWhitelistPresetIds": {}
                }
                """);

        ChainVeinConfig config = decoded.config();
        assertTrue(decoded.needsSave());
        assertEquals(5, config.version);
        assertTrue(config.isChainVeinEnabled);
        assertEquals(ChainVeinConfig.ChainMode.CHAIN_PLANT, config.mode);
        assertEquals(ChainVeinConfig.SearchAlgorithm.SPHERE, config.searchAlgorithm);
        assertEquals(123, config.maxChainBlocks);
        assertTrue(config.directToInventory);
        assertTrue(config.respectSchematicRenderLayer);
        assertFalse(config.quickShulkerOverflow);
        assertNotNull(config.getActiveConfigPreset());
        for (ChainVeinConfig.ChainMode mode : ChainVeinConfig.ChainMode.values()) {
            assertNotNull(config.getActiveWhitelistPreset(mode));
        }
    }

    @Test
    void migratesV3FieldsAndIntroducesDisabledQuickShulkerOverflow() {
        ChainVeinConfigCodec.DecodedConfig decoded = decode("""
                {
                  "version": 3,
                  "respectSchematicRenderLayer": false,
                  "enableChainVeinOnModeHotkey": true,
                  "cycleModeHotkey": "R",
                  "configPresets": [],
                  "whitelistPresets": {},
                  "activeWhitelistPresetIds": {}
                }
                """);

        ChainVeinConfig config = decoded.config();
        assertTrue(decoded.needsSave());
        assertEquals(5, config.version);
        assertFalse(config.respectSchematicRenderLayer);
        assertTrue(config.enableChainVeinOnModeHotkey);
        assertEquals("R", config.cycleModeHotkey);
        assertFalse(config.quickShulkerOverflow);
    }

    @Test
    void migratesV4ToFinalV5AutomaticMiningSettings() {
        ChainVeinConfigCodec.DecodedConfig decoded = decode("""
                {
                  "version": 4,
                  "quickShulkerOverflow": true,
                  "mode": null,
                  "searchAlgorithm": null,
                  "configPresets": [],
                  "whitelistPresets": {},
                  "activeWhitelistPresetIds": {}
                }
                """);

        ChainVeinConfig config = decoded.config();
        assertTrue(decoded.needsSave());
        assertEquals(5, config.version);
        assertEquals(ChainVeinConfig.ChainMode.CHAIN_MINE, config.mode);
        assertEquals(ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME, config.searchAlgorithm);
        assertEquals(40, config.autoMineCooldownTicks);
        assertTrue(config.quickShulkerOverflow);
        assertNotNull(config.getActiveConfigPreset());
    }

    @Test
    void modeCapabilitiesStayDisjoint() {
        assertTrue(ChainVeinConfig.ChainMode.CHAIN_MINE.isMiningMode());
        assertTrue(ChainVeinConfig.ChainMode.CHAIN_MINE.isManualMiningMode());
        assertTrue(ChainVeinConfig.ChainMode.CHAIN_PLANT.isInteractionMode());
        assertTrue(ChainVeinConfig.ChainMode.CHAIN_UTILITY.isInteractionMode());

        for (ChainVeinConfig.ChainMode mode : new ChainVeinConfig.ChainMode[] {
                ChainVeinConfig.ChainMode.SCHEMATIC_SELECTION,
                ChainVeinConfig.ChainMode.SCHEMATIC_EXTRA,
                ChainVeinConfig.ChainMode.SCHEMATIC_WRONG
        }) {
            assertTrue(mode.isMiningMode());
            assertTrue(mode.isSchematicMode());
            assertFalse(mode.isInteractionMode());
        }
    }

    @Test
    void whitelistPresetsRemainModeScopedAndSwitchAtomically() {
        ChainVeinConfig config = decode("""
                {
                  "version": 5,
                  "configPresets": [],
                  "whitelistPresets": {},
                  "activeWhitelistPresetIds": {}
                }
                """).config();

        Set<String> mining = config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE);
        mining.add("minecraft:stone");
        WhitelistPreset alternate = config.createWhitelistPreset(
                ChainVeinConfig.ChainMode.CHAIN_MINE, " Alternate ");
        assertEquals("Alternate", alternate.name);
        assertEquals(Set.of("minecraft:stone"), alternate.entries);

        config.replaceWhitelist(
                ChainVeinConfig.ChainMode.CHAIN_MINE,
                alternate.id,
                Set.of("minecraft:dirt"));
        assertEquals(Set.of("minecraft:stone"), mining);
        assertTrue(config.useWhitelistPreset(
                ChainVeinConfig.ChainMode.CHAIN_MINE, alternate.id));
        assertEquals(Set.of("minecraft:dirt"),
                config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE));
        assertTrue(config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).isEmpty());
        assertFalse(config.deleteWhitelistPreset(
                ChainVeinConfig.ChainMode.CHAIN_MINE, alternate.id));
    }

    private static ChainVeinConfigCodec.DecodedConfig decode(String json) {
        return ChainVeinConfigCodec.decode(JsonParser.parseString(json).getAsJsonObject());
    }
}
