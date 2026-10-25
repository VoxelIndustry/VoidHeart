package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
    public void load(CompoundTag tag)
    {
        if (tag.contains("doorID"))
            id = tag.getUUID("doorID");
        else
            id = UUID.randomUUID();

        super.load(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        if (id == null)
            id = UUID.randomUUID();

        tag.putUUID("doorID", id);

        super.saveAdditional(tag);
    }

    public UUID getId()
    {
        if (id == null)
            return id = UUID.randomUUID();
        return id;
    }
}
