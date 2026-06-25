package org.edtp.chainveinfabric.client.config.preset;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.UUID;

public class ConfigPreset {
    public static final String DEFAULT_ID = "default";
    public static final String DEFAULT_NAME = "Default";

    public String id = UUID.randomUUID().toString();
    public String name = DEFAULT_NAME;
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

    public static ConfigPreset create(String id, String name, ChainVeinConfig config) {
        ConfigPreset preset = new ConfigPreset();
        preset.id = id;
        preset.name = name;
        preset.captureFrom(config);
        return preset;
    }

    public void captureFrom(ChainVeinConfig config) {
        this.isChainVeinEnabled = config.isChainVeinEnabled;
        this.mode = config.mode;
        this.searchAlgorithm = config.searchAlgorithm;
        this.maxChainBlocks = config.maxChainBlocks;
        this.maxRadius = config.maxRadius;
        this.sphereRadius = config.sphereRadius;
        this.squareLength = config.squareLength;
        this.squareMiningPoint = config.squareMiningPoint;
        this.cuboidL = config.cuboidL;
        this.cuboidW = config.cuboidW;
        this.cuboidH = config.cuboidH;
        this.cuboidMiningPoint = config.cuboidMiningPoint;
        this.directToInventory = config.directToInventory;
        this.toolProtection = config.toolProtection;
        this.diagonalEdge = config.diagonalEdge;
        this.diagonalCorner = config.diagonalCorner;
        this.packetInterval = config.packetInterval;
        this.showBlockOutlines = config.showBlockOutlines;
    }

    public void applyTo(ChainVeinConfig config) {
        config.isChainVeinEnabled = this.isChainVeinEnabled;
        config.mode = this.mode != null ? this.mode : ChainVeinConfig.ChainMode.CHAIN_MINE;
        config.searchAlgorithm = this.searchAlgorithm != null ? this.searchAlgorithm : ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        config.maxChainBlocks = this.maxChainBlocks;
        config.maxRadius = this.maxRadius;
        config.sphereRadius = this.sphereRadius;
        config.squareLength = this.squareLength;
        config.squareMiningPoint = this.squareMiningPoint != null ? this.squareMiningPoint : ChainVeinConfig.MiningPoint.CENTER;
        config.cuboidL = this.cuboidL;
        config.cuboidW = this.cuboidW;
        config.cuboidH = this.cuboidH;
        config.cuboidMiningPoint = this.cuboidMiningPoint != null ? this.cuboidMiningPoint : ChainVeinConfig.MiningPoint.CENTER;
        config.directToInventory = this.directToInventory;
        config.toolProtection = this.toolProtection;
        config.diagonalEdge = this.diagonalEdge;
        config.diagonalCorner = this.diagonalCorner;
        config.packetInterval = this.packetInterval;
        config.showBlockOutlines = this.showBlockOutlines;
    }
}
