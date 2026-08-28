package org.edtp.chainveinfabric.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChainVeinServerConfigTest {
    private ChainVeinServerConfig.Values original;

    @BeforeEach
    void rememberConfig() {
        original = ChainVeinServerConfig.values();
    }

    @AfterEach
    void restoreConfig() {
        ChainVeinServerConfig.setForTests(original);
    }

    @Test
    void defaultsPreservePreviousProtocolBehavior() {
        ChainVeinServerConfig.Values defaults = ChainVeinServerConfig.Values.defaults();

        assertEquals(256, defaults.maxBlocks());
        assertEquals(10, defaults.pickupRadius());
    }

    @Test
    void testValuesAreClampedToServerLimits() {
        ChainVeinServerConfig.setForTests(new ChainVeinServerConfig.Values(
                Integer.MAX_VALUE,
                Integer.MIN_VALUE));

        ChainVeinServerConfig.Values values = ChainVeinServerConfig.values();
        assertEquals(ChainVeinServerConfig.MAX_MAX_BLOCKS, values.maxBlocks());
        assertEquals(ChainVeinServerConfig.MIN_PICKUP_RADIUS, values.pickupRadius());
    }
}
