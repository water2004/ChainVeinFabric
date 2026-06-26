package org.edtp.chainveinfabric.client.config.schema;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.HashSet;
import java.util.Set;

public class ConfigSchemaV1 {
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
    public String toggleTargetWhitelistHotkey = "";
    public Set<String> whitelistedBlocks = new HashSet<>();
    public Set<String> whitelistedCrops = new HashSet<>();
    public Set<String> whitelistedUtilityBlocks = new HashSet<>();

    public void fixNulls() {
        if (this.mode == null) this.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
        if (this.searchAlgorithm == null) this.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        if (this.squareMiningPoint == null) this.squareMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        if (this.cuboidMiningPoint == null) this.cuboidMiningPoint = ChainVeinConfig.MiningPoint.CENTER;
        if (this.openConfigHotkey == null) this.openConfigHotkey = "V";
        if (this.toggleChainVeinHotkey == null) this.toggleChainVeinHotkey = "";
        if (this.toggleTargetWhitelistHotkey == null) this.toggleTargetWhitelistHotkey = "";
        if (this.whitelistedBlocks == null) this.whitelistedBlocks = new HashSet<>();
        if (this.whitelistedCrops == null) this.whitelistedCrops = new HashSet<>();
        if (this.whitelistedUtilityBlocks == null) this.whitelistedUtilityBlocks = new HashSet<>();
    }
}
