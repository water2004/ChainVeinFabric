package org.edtp.chainveinfabric.logic;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionBatchPlannerTest {
    private static final BlockPos START = BlockPos.ZERO;

    @Test
    void usesAllItemsRemainingAfterTheVanillaPlant() {
        List<BlockPos> targets = line(64);

        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                targets, START, 64, 63,
                false, false, false, false, Integer.MAX_VALUE);

        assertEquals(63, plan.queuedPositions().size());
        assertEquals(64, plan.affectedCount());
        assertEquals(new BlockPos(63, 0, 0), plan.queuedPositions().getLast());
    }

    @Test
    void configuredLimitIncludesTheOriginalInteraction() {
        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                line(20), START, 8, 19,
                false, false, false, false, Integer.MAX_VALUE);

        assertEquals(7, plan.queuedPositions().size());
        assertEquals(8, plan.affectedCount());
    }

    @Test
    void stillReservesTheOriginalSlotWhenSearchDoesNotReturnIt() {
        List<BlockPos> targets = line(64).subList(1, 64);

        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                targets, START, 64, 63,
                false, false, false, false, Integer.MAX_VALUE);

        assertEquals(63, plan.queuedPositions().size());
        assertEquals(64, plan.affectedCount());
    }

    @Test
    void durabilityProtectionKeepsTenUsesInReserve() {
        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                line(40), START, 40, 1,
                false, false, true, true, 15);

        assertEquals(5, plan.queuedPositions().size());
        assertEquals(6, plan.affectedCount());
        assertTrue(plan.limitedByDurability());
    }

    @Test
    void creativeModeIgnoresItemAndDurabilityCapacity() {
        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                line(12), START, 12, 0,
                true, false, true, true, 0);

        assertEquals(11, plan.queuedPositions().size());
        assertEquals(12, plan.affectedCount());
        assertFalse(plan.limitedByDurability());
    }

    private static List<BlockPos> line(int size) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            positions.add(new BlockPos(x, 0, 0));
        }
        return positions;
    }
}
