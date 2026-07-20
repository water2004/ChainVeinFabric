package org.edtp.chainveinfabric.client.compat.litematica;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.IgnoreBlockRegistry;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.ChunkSchematicState;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation")
final class LitematicaBridge {
    private LitematicaBridge() {
    }

    static LitematicaContext createContext(ChainMode mode, boolean respectRenderLayer) {
        if (mode == ChainMode.SCHEMATIC_SELECTION) {
            AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();
            return selection != null
                    ? new SelectionContext(snapshotBounds(selection.getAllSubRegionBoxes()))
                    : LitematicaContext.NONE;
        }

        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) {
            return LitematicaContext.NONE;
        }

        List<BlockBounds> bounds = new ArrayList<>();
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            bounds.addAll(snapshotBounds(placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values()));
        }
        sortBounds(bounds);

        RenderRange range = respectRenderLayer
                ? RenderRange.snapshot(DataManager.getRenderLayerRange())
                : null;
        return new SchematicContext(
                mode,
                List.copyOf(bounds),
                schematicWorld,
                range,
                Configs.Visuals.IGNORE_EXISTING_FLUIDS.getBooleanValue(),
                new IgnoreBlockRegistry()
        );
    }

    static LitematicaImportSnapshot createImportSnapshot(ChainMode mode, boolean respectRenderLayer) {
        LitematicaContext context = createContext(mode, respectRenderLayer);
        if (context instanceof SelectionContext selection) {
            return new LitematicaImportSnapshot(selection.bounds, context);
        }
        if (context instanceof SchematicContext schematic) {
            List<BlockBounds> scanBounds = schematic.bounds;
            if (schematic.renderRange != null) {
                scanBounds = schematic.bounds.stream()
                        .map(schematic.renderRange::clamp)
                        .filter(java.util.Objects::nonNull)
                        .toList();
            }
            return new LitematicaImportSnapshot(scanBounds, context);
        }
        return LitematicaImportSnapshot.EMPTY;
    }

    private static List<BlockBounds> snapshotBounds(Iterable<Box> boxes) {
        List<BlockBounds> bounds = new ArrayList<>();
        for (Box box : boxes) {
            BlockPos first = box.getPos1();
            BlockPos second = box.getPos2();
            if (first != null && second != null) {
                bounds.add(BlockBounds.of(first, second));
            }
        }
        sortBounds(bounds);
        return List.copyOf(bounds);
    }

    private static void sortBounds(List<BlockBounds> bounds) {
        bounds.sort(Comparator.comparingInt(BlockBounds::minX)
                .thenComparingInt(BlockBounds::minY)
                .thenComparingInt(BlockBounds::minZ)
                .thenComparingInt(BlockBounds::maxX)
                .thenComparingInt(BlockBounds::maxY)
                .thenComparingInt(BlockBounds::maxZ));
    }

    private static boolean contains(List<BlockBounds> bounds, BlockPos pos) {
        for (BlockBounds box : bounds) {
            if (box.contains(pos)) return true;
        }
        return false;
    }

    private record SelectionContext(List<BlockBounds> bounds) implements LitematicaContext {
        @Override
        public boolean matches(ClientLevel world, BlockPos pos) {
            return contains(this.bounds, pos) && !world.getBlockState(pos).isAir();
        }

        @Override
        public long fingerprint() {
            return this.bounds.hashCode();
        }
    }

    private record SchematicContext(
            ChainMode mode,
            List<BlockBounds> bounds,
            WorldSchematic schematicWorld,
            RenderRange renderRange,
            boolean ignoreFluids,
            IgnoreBlockRegistry ignoredBlocks
    ) implements LitematicaContext {
        @Override
        public boolean matches(ClientLevel world, BlockPos pos) {
            if (!contains(this.bounds, pos)
                    || (this.renderRange != null && !this.renderRange.contains(pos))
                    || !this.schematicWorld.getChunkSource()
                            .getChunkState(pos.getX() >> 4, pos.getZ() >> 4)
                            .atLeast(ChunkSchematicState.LOADED)) {
                return false;
            }

            BlockState actual = world.getBlockState(pos);
            BlockState expected = this.schematicWorld.getBlockState(pos);
            if (this.mode == ChainMode.SCHEMATIC_EXTRA) {
                return expected.isAir()
                        && !actual.isAir()
                        && !(this.ignoreFluids && actual.liquid())
                        && !this.ignoredBlocks.hasBlock(actual.getBlock());
            }

            return !expected.isAir()
                    && !actual.isAir()
                    && !(this.ignoreFluids && actual.liquid())
                    && expected != actual;
        }

        @Override
        public long fingerprint() {
            long hash = 31L * this.mode.ordinal() + this.bounds.hashCode();
            if (this.renderRange != null) {
                hash = 31L * hash + this.renderRange.hashCode();
            }
            return hash;
        }
    }

    private record RenderRange(Direction.Axis axis, int min, int max) {
        static RenderRange snapshot(fi.dy.masa.malilib.util.position.LayerRange range) {
            return new RenderRange(range.getAxis(), range.getMinLayerBoundary(), range.getMaxLayerBoundary());
        }

        boolean contains(BlockPos pos) {
            int coordinate = switch (this.axis) {
                case X -> pos.getX();
                case Y -> pos.getY();
                case Z -> pos.getZ();
            };
            return coordinate >= this.min && coordinate <= this.max;
        }

        BlockBounds clamp(BlockBounds bounds) {
            return bounds.clamp(this.axis, this.min, this.max);
        }
    }
}
