package net.voxelindustry.voidheart.common.tile;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidPortalTile extends BlockEntity
{
    private BlockPos        corePos;
    private PortalFrameTile core;

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
        if (corePos == null)
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

            FabricDimensions.teleport(collider, destination,
                    (entity, newWorld, direction, offsetX, offsetY) ->
                    {
                        int yaw = (core.getFacing().getHorizontal() - core.getLinkedFacing().getOpposite().getHorizontal()) * 90;
                        return new BlockPattern.TeleportTarget(linkedPortal.getPortalMiddlePos(), collider.getVelocity(), yaw);
                    });
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
