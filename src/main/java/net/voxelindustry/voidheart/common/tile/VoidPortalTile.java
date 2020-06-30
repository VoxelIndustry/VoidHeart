package net.voxelindustry.voidheart.common.tile;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidPortalTile extends BlockEntity
{
    private BlockPos       corePos;
    private PortalWallTile core;

    public VoidPortalTile()
    {
        super(VoidHeartTiles.POCKET_PORTAL);
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        super.fromTag(state, tag);

        corePos = BlockPos.fromLong(tag.getLong("corePos"));
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.putLong("corePos", corePos.asLong());

        return super.toTag(tag);
    }

    public void teleport(Entity collider)
    {

    }

    public void setCore(BlockPos pos)
    {
        corePos = pos;
    }

    public PortalWallTile getCore()
    {
        if (core == null)
        {
            if (corePos == null)
                return null;
            core = (PortalWallTile) getWorld().getBlockEntity(corePos);
        }
        return core;
    }
}
