package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * The columns themselves are in the block state. This remembers which repository they serve, a repository claiming the ones free around it,
 * and finds the center of their cluster for the renderer, since their floating spreads from it as a wave.
 * Until the owner has learned to use columns they are only promised to that repository: they stay dark, count for nothing, and go to any repository that can use them.
 */
public class KnowledgeColumnTile extends TileBase
{
    public record Link(BlockPos repository, UUID owner)
    {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("repository").forGetter(Link::repository),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Link::owner)
        ).apply(instance, Link::new));
    }

    private static final int MAX_CLUSTER_SPACES = 4096;
    private static final int LINK_CHECK_INTERVAL = 40;

    // Any column appearing or going can change every cluster it touches, so they are all recomputed when next drawn
    private static int clusterRevision;

    private int clusterStamp = -1;
    private Vec3 clusterCenter = Vec3.ZERO;

    @Getter
    private Optional<Link> link = Optional.empty();
    @Getter
    private boolean active;
    /** Client only, when the columns last started to rise or settle. */
    @Getter
    private long linkChangedAt = Long.MIN_VALUE / 2;

    public KnowledgeColumnTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.KNOWLEDGE_COLUMN, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, KnowledgeColumnTile column)
    {
        if (world instanceof ServerLevel level && column.link.isPresent() && (level.getGameTime() + pos.hashCode()) % LINK_CHECK_INTERVAL == 0 && !column.linkHolds(level))
            column.setLink(Optional.empty(), false);
    }

    /**
     * An unloaded repository is given the benefit of the doubt, like the columns it counted when it was last loaded.
     */
    private boolean linkHolds(ServerLevel level)
    {
        var current = link.orElseThrow();
        var repository = current.repository();
        if (!level.isLoaded(repository))
            return true;
        if (!(level.getBlockEntity(repository) instanceof KnowledgeRepositoryTile tile) || tile.getOwner().filter(current.owner()::equals).isEmpty())
            return false;

        int radius = Knowledge.columnRadius();
        var offset = getBlockPos().subtract(repository);
        return Math.abs(offset.getX()) <= radius && Math.abs(offset.getY()) <= radius && Math.abs(offset.getZ()) <= radius
               && KnowledgeState.get(level.getServer()).of(current.owner()).getRepository().filter(GlobalPos.of(level.dimension(), repository)::equals).isPresent();
    }

    /**
     * @param usable whether the owner of that repository knows how to use columns
     * @return whether these columns now count for that repository, which they do not while only promised or if another still holds them
     */
    public boolean claim(BlockPos repository, UUID owner, boolean usable)
    {
        var wanted = Optional.of(new Link(repository, owner));
        if (link.isPresent() && !link.equals(wanted) && (active || !usable))
            return false;

        if (!link.equals(wanted) || active != usable)
            setLink(wanted, usable);
        return usable;
    }

    private void setLink(Optional<Link> link, boolean active)
    {
        this.link = link;
        this.active = active;
        setChanged();
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        boolean wasActive = active;
        link = tag.read("link", Link.CODEC);
        active = tag.getBooleanOr("active", false);
        if (level != null && level.isClientSide() && wasActive != active)
            linkChangedAt = level.getGameTime();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        link.ifPresent(value -> tag.store("link", Link.CODEC, value));
        tag.putBoolean("active", active);

        super.saveAdditional(tag);
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
