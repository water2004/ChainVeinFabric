package org.edtp.chainveinfabric.client.renderer;

import java.util.List;

import fi.dy.masa.malilib.util.data.Color4f;

public record OutlineData(List<LineSegment> lines, int generation) {}

record LineSegment(
    float x1, float y1, float z1,
    float x2, float y2, float z2,
    Color4f color
) {}
