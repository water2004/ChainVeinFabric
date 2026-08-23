package org.edtp.chainveinfabric.client.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainSearcherTest {
    private static final BlockPos START = BlockPos.ZERO;

    @Test
    void adjacentSearchHonorsDiagonalSettings() {
        assertEquals(7, adjacent(false, false).size());
        assertEquals(19, adjacent(true, false).size());
        assertEquals(27, adjacent(true, true).size());
    }

    @Test
    void adjacentSearchHonorsBlockAndRadiusLimits() {
        Set<BlockPos> capped = ChainSearcher.findBlocks(
                null, START, 5, 20, pos -> true, true, true);
        Set<BlockPos> zeroRadius = ChainSearcher.findBlocks(
                null, START, 100, 0, pos -> true, true, true);

        assertEquals(5, capped.size());
        assertEquals(Set.of(START), zeroRadius);
    }

    @Test
    void sphereUsesAnInclusiveEuclideanRadius() {
        Set<BlockPos> result = ChainSearcher.findSphere(
                null, START, 2, pos -> true);

        assertEquals(33, result.size());
        assertTrue(result.contains(new BlockPos(2, 0, 0)));
        assertTrue(result.contains(new BlockPos(1, 1, 1)));
    }

    @Test
    void squareAndCuboidHaveExactConfiguredDimensions() {
        Set<BlockPos> square = ChainSearcher.findSquare(
                null, START, 4, ChainVeinConfig.MiningPoint.CENTER,
                Direction.NORTH, pos -> true);
        Set<BlockPos> cuboid = ChainSearcher.findCuboid(
                null, START, 3, 4, 5, ChainVeinConfig.MiningPoint.FRONT_TOP_LEFT,
                Direction.NORTH, pos -> true);

        assertEquals(16, square.size());
        assertEquals(60, cuboid.size());
        assertTrue(square.contains(START));
        assertTrue(cuboid.contains(START));
    }

    private static Set<BlockPos> adjacent(boolean edge, boolean corner) {
        return ChainSearcher.findBlocks(
                null, START, 100, 1, pos -> true, edge, corner);
    }
}
