package org.edtp.chainveinfabric.logic;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the follow-up interactions after vanilla has already processed
 * the block the player clicked.
 */
public final class InteractionBatchPlanner {
    private InteractionBatchPlanner() {
    }

    public static Plan create(List<BlockPos> targets, BlockPos startPos,
                              int maxAffectedBlocks, int remainingItems,
                              boolean creative, boolean emptyHand, boolean damageable,
                              boolean toolProtection, int remainingDurability) {
        List<BlockPos> candidates = new ArrayList<>(targets);
        candidates.remove(startPos);

        // The original vanilla interaction already affected startPos and must
        // consume one slot from the configured total, but not from the current
        // (post-interaction) item count.
        int configuredAdditional = Math.max(0, maxAffectedBlocks - 1);
        int availableAdditional = configuredAdditional;
        boolean limitedByDurability = false;

        if (!creative) {
            if (!emptyHand && !damageable) {
                availableAdditional = Math.min(availableAdditional, Math.max(0, remainingItems));
            } else if (damageable && toolProtection) {
                int safeAdditional = Math.max(0, remainingDurability - 10);
                if (safeAdditional < availableAdditional) {
                    availableAdditional = safeAdditional;
                    limitedByDurability = candidates.size() > safeAdditional;
                }
            }
        }

        int queuedCount = Math.min(candidates.size(), availableAdditional);
        List<BlockPos> queuedPositions = List.copyOf(candidates.subList(0, queuedCount));
        return new Plan(queuedPositions, 1 + queuedCount, limitedByDurability);
    }

    public record Plan(List<BlockPos> queuedPositions, int affectedCount,
                       boolean limitedByDurability) {
    }
}
