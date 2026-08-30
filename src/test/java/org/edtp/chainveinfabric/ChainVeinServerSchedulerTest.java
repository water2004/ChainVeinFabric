package org.edtp.chainveinfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class ChainVeinServerSchedulerTest {
    @Test
    void sharesBudgetEvenlyAndRotatesIndivisibleRemainder() {
        int[] demands = {100, 100, 100};

        assertArrayEquals(
                new int[]{2, 2, 1},
                ChainVeinServerScheduler.allocateFairShares(demands, 5, 0));
        assertArrayEquals(
                new int[]{2, 1, 2},
                ChainVeinServerScheduler.allocateFairShares(demands, 5, 2));
    }

    @Test
    void redistributesCapacityThatShortJobsCannotUse() {
        assertArrayEquals(
                new int[]{1, 2, 2},
                ChainVeinServerScheduler.allocateFairShares(
                        new int[]{1, 100, 100}, 5, 0));
    }

    @Test
    void neverAllocatesMoreThanDemandOrBudget() {
        assertArrayEquals(
                new int[]{1, 0, 2},
                ChainVeinServerScheduler.allocateFairShares(
                        new int[]{1, 0, 2}, 100, 1));
    }
}
