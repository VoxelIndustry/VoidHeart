package net.voxelindustry.voidheart.common.ley;

import java.util.List;

@FunctionalInterface
public interface LeyAnchorSource
{
    LeyAnchorSource NONE = (minX, minZ, maxX, maxZ) -> List.of();

    /**
     * Anchors (predicted structure positions) inside the inclusive block bounds. Must be deterministic.
     */
    List<Anchor> anchorsIn(int minX, int minZ, int maxX, int maxZ);

    record Anchor(int x, int z, LeyNodeKind kind)
    {
    }
}
