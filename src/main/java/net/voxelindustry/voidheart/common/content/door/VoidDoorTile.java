package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.VoidHeart;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

public class VoidDoorTile extends BlockEntity
{
    private UUID id;

    public VoidDoorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_DOOR, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        id = tag.read("doorID", UUIDUtil.CODEC).orElse(null);

        super.loadAdditional(tag);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (id == null)
            id = UUID.randomUUID();

        tag.store("doorID", UUIDUtil.CODEC, id);

        super.saveAdditional(tag);
    }

    /**
     * Rift Keys open onto the lower half, whose removal frees the flexion it held.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && serverLevel.dimension().equals(VoidHeart.VOID_WORLD_KEY)
                && state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            RiftKeys.doorRemoved(serverLevel, pos);
    }

    public UUID getId()
    {
        // Keys are bound to this id, it must be saved as soon as it exists
        if (id == null)
        {
            id = UUID.randomUUID();
            setChanged();
        }
        return id;
    }
}
