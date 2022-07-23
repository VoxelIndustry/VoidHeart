package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class PortalInteriorTile extends BlockEntity
{
    private BlockPos        corePos;
    private PortalFrameCoreTile core;

    public PortalInteriorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.POCKET_PORTAL, pos, state);
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        corePos = BlockPos.fromLong(tag.getLong("corePos"));
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        tag.putLong("corePos", corePos.asLong());

        super.writeNbt(tag);
    }

    public void teleport(Entity collider)
    {
        if (!(collider instanceof ServerPlayerEntity) || corePos == null)
            return;

        var core = getCore();

        if (core.getLinkedWorld() != null)
        {
            ServerWorld destination = world.getServer().getWorld(core.getLinkedWorldKey());

            var linkedPortalOpt =  getWorld().getServer().getWorld(core.getLinkedWorldKey()).getBlockEntity(core.getLinkedPos(), VoidHeartTiles.PORTAL_FRAME_CORE);

            // If pos become invalid.
            // Almost impossible but we need to prevent the world to end corrupted by a player stuck inside the portal.
            if (linkedPortalOpt.isEmpty())
                return;

            Vec3d destinationPos = linkedPortalOpt.get().getPortalMiddlePos(false);
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

    public PortalFrameCoreTile getCore()
    {
        if (core == null)
        {
            if (corePos == null)
                return null;
            var tileOpt = getWorld().getBlockEntity(corePos, VoidHeartTiles.PORTAL_FRAME_CORE);

            tileOpt.ifPresent(tile -> this.core = tile);
        }
        return core;
    }
}
