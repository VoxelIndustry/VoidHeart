package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    public void load(CompoundTag tag)
    {
        super.load(tag);

        corePos = BlockPos.of(tag.getLong("corePos"));
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        tag.putLong("corePos", corePos.asLong());

        super.saveAdditional(tag);
    }

    public void teleport(Entity collider)
    {
        if (!(collider instanceof ServerPlayer) || corePos == null)
            return;

        var core = getCore();

        if (core.getLinkedWorld() != null)
        {
            ServerLevel destination = level.getServer().getLevel(core.getLinkedWorldKey());

            var linkedPortalOpt =  getLevel().getServer().getLevel(core.getLinkedWorldKey()).getBlockEntity(core.getLinkedPos(), VoidHeartTiles.PORTAL_FRAME_CORE);

            // If pos become invalid.
            // Almost impossible but we need to prevent the world to end corrupted by a player stuck inside the portal.
            if (linkedPortalOpt.isEmpty())
                return;

            Vec3 destinationPos = linkedPortalOpt.get().getPortalMiddlePos(false);
            ((ServerPlayer) collider).teleportTo(destination,
                    destinationPos.x(),
                    destinationPos.y(),
                    destinationPos.z(),
                    collider.getYHeadRot(),
                    collider.getViewXRot(0));
        }
    }

    public void setCore(BlockPos pos)
    {
        corePos = pos;
        setChanged();
    }

    public PortalFrameCoreTile getCore()
    {
        if (core == null)
        {
            if (corePos == null)
                return null;
            var tileOpt = getLevel().getBlockEntity(corePos, VoidHeartTiles.PORTAL_FRAME_CORE);

            tileOpt.ifPresent(tile -> this.core = tile);
        }
        return core;
    }
}
