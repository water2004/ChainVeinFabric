package org.edtp.chainveinfabric.client.logic.search;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;

/** All mutable client state needed by one worker-thread search. */
public record SearchRequest(
        ClientLevel level,
        BlockPos origin,
        BlockState targetState,
        Direction playerFacing,
        SearchConfig config,
        LitematicaContext litematicaContext,
        boolean creative,
        boolean automatic) {

    public SearchRequest {
        origin = origin.immutable();
        litematicaContext = litematicaContext != null ? litematicaContext : LitematicaContext.NONE;
    }

    public static SearchRequest targeted(ClientLevel level, BlockPos origin, BlockState targetState,
                                         Direction playerFacing, SearchConfig config,
                                         LitematicaContext litematicaContext, boolean creative) {
        return new SearchRequest(level, origin, targetState, playerFacing, config,
                litematicaContext, creative, false);
    }

    public static SearchRequest automatic(ClientLevel level, BlockPos playerPos,
                                          Direction playerFacing, SearchConfig config,
                                          boolean creative) {
        return new SearchRequest(level, playerPos, null, playerFacing, config,
                LitematicaContext.NONE, creative, true);
    }
}
