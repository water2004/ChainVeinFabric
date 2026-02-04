package org.edtp.chainveinfabric;

import net.fabricmc.api.ModInitializer;
import org.edtp.chainveinfabric.config.ChainVeinConfig;

public class Chainveinfabric implements ModInitializer {

    public static ChainVeinConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = ChainVeinConfig.load();
    }
}
