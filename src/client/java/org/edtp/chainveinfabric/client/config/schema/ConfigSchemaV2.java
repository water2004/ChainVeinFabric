package org.edtp.chainveinfabric.client.config.schema;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.config.preset.WhitelistPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigSchemaV2 {
    public int version = 2;
    public boolean isChainVeinEnabled = false;
    public ChainVeinConfig.ChainMode mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
    public ChainVeinConfig.SearchAlgorithm searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
    public int maxChainBlocks = 64;
    public int maxRadius = 6;
    public int sphereRadius = 3;
    public int squareLength = 3;
    public ChainVeinConfig.MiningPoint squareMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
    public int cuboidL = 3;
    public int cuboidW = 3;
    public int cuboidH = 3;
    public ChainVeinConfig.MiningPoint cuboidMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
    public boolean directToInventory = false;
    public boolean toolProtection = false;
    public boolean diagonalEdge = false;
    public boolean diagonalCorner = false;
    public int packetInterval = 0;
    public boolean showBlockOutlines = false;
    public String openConfigHotkey = "V";
    public String toggleChainVeinHotkey = "";
    public List<ConfigPreset> configPresets = new ArrayList<>();
    public String activeConfigPresetId = ConfigPreset.DEFAULT_ID;
    public Map<String, List<WhitelistPreset>> whitelistPresets = new LinkedHashMap<>();
    public Map<String, String> activeWhitelistPresetIds = new LinkedHashMap<>();
}
