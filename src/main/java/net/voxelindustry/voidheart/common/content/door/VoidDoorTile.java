package net.voxelindustry.voidheart.common.content.door;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
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
        if (tag.read("doorID", Codec.PASSTHROUGH).isPresent())
            id = tag.read("doorID", UUIDUtil.CODEC).orElseThrow();
        else
            id = UUID.randomUUID();

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

    public UUID getId()
    {
        if (id == null)
            return id = UUID.randomUUID();
        return id;
    }
}
