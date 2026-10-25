package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
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
    public void readNbt(NbtCompound tag)
    {
        if (tag.contains("doorID"))
            id = tag.getUuid("doorID");
        else
            id = UUID.randomUUID();

        super.readNbt(tag);
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (id == null)
            id = UUID.randomUUID();

        tag.putUuid("doorID", id);

        super.writeNbt(tag);
    }

    public UUID getId()
    {
        if (id == null)
            return id = UUID.randomUUID();
        return id;
    }
}
