package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class PortalInteriorTile extends BlockEntity
{
    private BlockPos        corePos;
    private PortalFrameTile core;

    public PortalInteriorTile()
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
        if (!(collider instanceof ServerPlayerEntity) || corePos == null)
            return;

        PortalFrameTile core = getCore();

        if (core.getLinkedWorld() != null)
        {
            ServerWorld destination = world.getServer().getWorld(core.getLinkedWorldKey());

            PortalFrameTile linkedPortal = (PortalFrameTile) getWorld().getServer().getWorld(core.getLinkedWorldKey()).getBlockEntity(core.getLinkedPos());

            // If pos become invalid.
            // Almost impossible but we need to prevent the world to end corrupted by a player stuck inside the portal.
            if (linkedPortal == null)
                return;

            Vec3d destinationPos = linkedPortal.getPortalMiddlePos();
            ((ServerPlayerEntity) collider).teleport(destination,
                    destinationPos.getX(),
                    destinationPos.getY(),
                    destinationPos.getZ(),
                    collider.getHeadYaw(),
                    collider.getPitch(0));
        }
    }

    public void setCore(BlockPos pos)
    {
        corePos = pos;
        markDirty();
    }

    public PortalFrameTile getCore()
    {
        if (core == null)
        {
            if (corePos == null)
                return null;
            BlockEntity tile = getWorld().getBlockEntity(corePos);

            if (!(tile instanceof PortalFrameTile))
                return null;
            core = (PortalFrameTile) tile;
        }
        return core;
    }
}
