package org.edtp.chainveinfabric.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.selection.SelectionMode;
import fi.dy.masa.litematica.world.ChunkSchematicState;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;

import static org.edtp.chainveinfabric.gametest.AutoMiningTestSupport.check;

/** An in-memory selection and placement using Litematica's real block-state containers. */
final class LitematicaTestFixture implements AutoCloseable {
    static final BlockPos TARGET = new BlockPos(2, 65, 0);
    static final BlockPos MATCHING = TARGET.east();
    static final BlockPos NON_WHITELISTED = TARGET.east(2);
    static final BlockPos OUTSIDE = TARGET.east(3);

    private final ClientGameTestContext context;
    private final SelectionMode previousMode;
    private final Box simpleBox;
    private final BlockPos previousFirst;
    private final BlockPos previousSecond;
    private final SchematicPlacement placement;

    private LitematicaTestFixture(ClientGameTestContext context, ChainMode mode) {
        this.context = context;
        var manager = DataManager.getSelectionManager();
        this.previousMode = manager.getSelectionMode();
        this.simpleBox = manager.getSimpleSelection().getSelectedSubRegionBox();
        check(this.simpleBox != null, "The simple Litematica selection has no box");
        this.previousFirst = this.simpleBox.getPos1();
        this.previousSecond = this.simpleBox.getPos2();
        AreaSelection region = new AreaSelection();
        region.setName("ChainVein AUTO fixture");
        region.setExplicitOrigin(TARGET);
        region.addSubRegionBox(new Box(TARGET, NON_WHITELISTED, "test"), true);
        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(region, "ChainVein GameTest");
        check(schematic != null, "Could not create the test schematic");
        var blocks = schematic.getSubRegionContainer("test");
        blocks.set(0, 0, 0, mode == ChainMode.SCHEMATIC_EXTRA
                ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
        blocks.set(1, 0, 0, Blocks.DIRT.defaultBlockState());
        // Keep rendering enabled: Litematica's maintenance unloads hidden placement chunks.
        this.placement = SchematicPlacement.createFor(schematic, TARGET, "ChainVein AUTO fixture", true, true);
        this.placement.setShouldBeSaved(false);
    }

    static LitematicaTestFixture create(ClientGameTestContext context, ChainMode mode) {
        check(LitematicaIntegration.isAvailable(), "Real schematic game tests require Litematica");
        LitematicaTestFixture fixture = context.computeOnClient(client -> new LitematicaTestFixture(context, mode));
        try {
            context.runOnClient(client -> fixture.install());
            context.waitFor(client -> {
                var world = SchematicWorldHandler.getSchematicWorld();
                return world != null && world.getChunkSource().getChunkState(0, 0).atLeast(ChunkSchematicState.LOADED)
                        && world.getBlockState(MATCHING).is(Blocks.DIRT)
                        && LitematicaIntegration.createContext(mode, false).matches(client.level, TARGET);
            });
            return fixture;
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private void install() {
        var manager = DataManager.getSelectionManager();
        if (manager.getSelectionMode() != SelectionMode.SIMPLE) manager.switchSelectionMode();
        this.simpleBox.setPos1(TARGET);
        this.simpleBox.setPos2(TARGET);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(this.placement, false);
    }

    static String describeTarget() {
        var world = SchematicWorldHandler.getSchematicWorld();
        return "schematicTarget=" + (world == null ? "no world" : world.getBlockState(TARGET))
                + ", chunk=" + (world == null ? "no world" : world.getChunkSource().getChunkState(0, 0))
                + ", placements=" + DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
    }

    @Override
    public void close() {
        this.context.runOnClient(client -> {
            DataManager.getSchematicPlacementManager().removeSchematicPlacement(this.placement);
            this.simpleBox.setPos1(this.previousFirst);
            this.simpleBox.setPos2(this.previousSecond);
            var manager = DataManager.getSelectionManager();
            if (manager.getSelectionMode() != this.previousMode) manager.switchSelectionMode();
        });
    }
}
