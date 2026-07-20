package org.edtp.chainveinfabric.client.compat.litematica;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static BlockBounds of(BlockPos first, BlockPos second) {
        return new BlockBounds(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.minX && pos.getX() <= this.maxX
                && pos.getY() >= this.minY && pos.getY() <= this.maxY
                && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
    }

    BlockBounds clamp(Direction.Axis axis, int min, int max) {
        int clampedMinX = axis == Direction.Axis.X ? Math.max(this.minX, min) : this.minX;
        int clampedMinY = axis == Direction.Axis.Y ? Math.max(this.minY, min) : this.minY;
        int clampedMinZ = axis == Direction.Axis.Z ? Math.max(this.minZ, min) : this.minZ;
        int clampedMaxX = axis == Direction.Axis.X ? Math.min(this.maxX, max) : this.maxX;
        int clampedMaxY = axis == Direction.Axis.Y ? Math.min(this.maxY, max) : this.maxY;
        int clampedMaxZ = axis == Direction.Axis.Z ? Math.min(this.maxZ, max) : this.maxZ;
        return clampedMinX <= clampedMaxX && clampedMinY <= clampedMaxY && clampedMinZ <= clampedMaxZ
                ? new BlockBounds(clampedMinX, clampedMinY, clampedMinZ, clampedMaxX, clampedMaxY, clampedMaxZ)
                : null;
    }
}
