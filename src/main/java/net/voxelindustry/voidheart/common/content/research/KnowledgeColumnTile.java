package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * The columns themselves are in the block state. Which repository they serve is kept by {@link RepositoryBoundTile}. This finds the center of their
 * cluster for the renderer, since their floating spreads from it as a wave.
 */
public class KnowledgeColumnTile extends RepositoryBoundTile
{
    private static final int MAX_CLUSTER_SPACES = 4096;

    // Any column appearing or going can change every cluster it touches, so they are all recomputed when next drawn
    private static int clusterRevision;

    private int clusterStamp = -1;
    private Vec3 clusterCenter = Vec3.ZERO;

    public KnowledgeColumnTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.KNOWLEDGE_COLUMN, pos, state);
    }

    public Vec3 getClusterCenter()
    {
        if (clusterStamp != clusterRevision)
            computeCluster();
        return clusterCenter;
    }

    private void computeCluster()
    {
        var cluster = new ArrayList<KnowledgeColumnTile>();
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(getBlockPos());
        queue.add(getBlockPos());

        double x = 0;
        double z = 0;
        int columns = 0;
        while (!queue.isEmpty() && cluster.size() < MAX_CLUSTER_SPACES)
        {
            var pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof KnowledgeColumnTile tile))
                continue;
            cluster.add(tile);

            int mask = KnowledgeColumnBlock.maskOf(tile.getBlockState());
            for (int quarter = 0; quarter < 4; quarter++)
            {
                if ((mask & 1 << quarter) == 0)
                    continue;
                x += pos.getX() + KnowledgeMath.columnCenterX(quarter);
                z += pos.getZ() + KnowledgeMath.columnCenterZ(quarter);
                columns++;
            }

            for (var direction : Direction.values())
            {
                var next = pos.relative(direction);
                if (seen.add(next))
                    queue.add(next);
            }
        }

        var center = columns == 0 ? Vec3.atCenterOf(getBlockPos()) : new Vec3(x / columns, getBlockPos().getY() + 0.5, z / columns);
        for (var tile : cluster)
        {
            tile.clusterCenter = center;
            tile.clusterStamp = clusterRevision;
        }
    }

    private void invalidateClusters()
    {
        if (level != null && level.isClientSide())
            clusterRevision++;
    }

    @Override
    public void setBlockState(BlockState state)
    {
        super.setBlockState(state);
        invalidateClusters();
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        invalidateClusters();
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        invalidateClusters();
    }
}
