package org.edtp.chainveinfabric.client.compat.litematica;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public interface LitematicaContext {
    LitematicaContext NONE = new LitematicaContext() {
        @Override
        public boolean matches(ClientLevel world, BlockPos pos) {
            return false;
        }

        @Override
        public long fingerprint() {
            return 0L;
        }
    };

    boolean matches(ClientLevel world, BlockPos pos);

    long fingerprint();
}
