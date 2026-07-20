package org.edtp.chainveinfabric.client.compat.litematica;

import java.util.List;

public record LitematicaImportSnapshot(List<BlockBounds> bounds, LitematicaContext context) {
    public static final LitematicaImportSnapshot EMPTY = new LitematicaImportSnapshot(
            List.of(),
            LitematicaContext.NONE
    );
}
