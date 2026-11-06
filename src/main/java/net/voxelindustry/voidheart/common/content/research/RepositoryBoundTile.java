package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;

import java.util.Optional;
import java.util.UUID;

/**
 * Something that works for a Knowledge Repository standing near it: columns, Cogitants, the Void Loom and Meat Proxies. This remembers which repository
 * it serves, a repository claiming the ones free around it. Until the owner has learned to use it, it is only promised to that repository: it stays
 * dark, counts for nothing, and goes to any repository that can use it.
 */
public abstract class RepositoryBoundTile extends TileBase
{
    public record Link(BlockPos repository, UUID owner)
    {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("repository").forGetter(Link::repository),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Link::owner)
        ).apply(instance, Link::new));
    }

    private static final int LINK_CHECK_INTERVAL = 40;

    @Getter
    private Optional<Link> link = Optional.empty();
    @Getter
    private boolean active;
    /** Client only, when it last started to work or stopped. */
    @Getter
    private long linkChangedAt = Long.MIN_VALUE / 2;

    protected RepositoryBoundTile(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, RepositoryBoundTile tile)
    {
        if (world instanceof ServerLevel level && tile.link.isPresent() && (level.getGameTime() + pos.hashCode()) % LINK_CHECK_INTERVAL == 0 && !tile.linkHolds(level))
            tile.setLink(Optional.empty(), false);
    }

    /**
     * An unloaded repository is given the benefit of the doubt, like what it counted when it was last loaded.
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
     * @param usable whether the owner of that repository knows how to use it
     * @return whether it now counts for that repository, which it does not while only promised or if another still holds it
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

    /** Lets go of a repository that holds more of these than it can use. */
    public void release(BlockPos repository)
    {
        if (link.filter(current -> current.repository().equals(repository)).isPresent())
            setLink(Optional.empty(), false);
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
}
