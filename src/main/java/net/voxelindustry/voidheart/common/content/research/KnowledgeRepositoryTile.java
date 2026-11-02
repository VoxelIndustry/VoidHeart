package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

/**
 * Only remembers who it answers to: the knowledge itself belongs to that player and outlives the block.
 */
public class KnowledgeRepositoryTile extends TileBase
{
    @Getter
    private Optional<UUID> owner = Optional.empty();

    public KnowledgeRepositoryTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.KNOWLEDGE_REPOSITORY, pos, state);
    }

    public void setOwner(UUID owner)
    {
        this.owner = Optional.of(owner);
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        owner = tag.read("owner", UUIDUtil.CODEC);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        owner.ifPresent(uuid -> tag.store("owner", UUIDUtil.CODEC, uuid));

        super.saveAdditional(tag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level instanceof ServerLevel serverLevel)
            owner.ifPresent(uuid -> Knowledge.unbind(serverLevel.getServer(), uuid, GlobalPos.of(serverLevel.dimension(), pos)));

        super.preRemoveSideEffects(pos, state);
    }
}
