package net.voxelindustry.voidheart.common.content.portalinterior;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Log4j2
public class PortalInteriorTile extends BlockEntity
{
    @Getter
    @Nullable
    private BlockPos            corePos;
    private PortalFrameCoreTile core;

    public PortalInteriorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.POCKET_PORTAL, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        corePos = tag.getLong("corePos").map(BlockPos::of).orElse(null);
        core = null;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (corePos != null)
            tag.putLong("corePos", corePos.asLong());

        super.saveAdditional(tag);
    }

    public void teleport(Entity collider)
    {
        if (!(collider instanceof ServerPlayer) || corePos == null)
            return;

        var core = getCore();
        var linkedPortalOpt = core == null ? Optional.<PortalFrameCoreTile>empty() : core.findLinkedPortal();

        // If pos become invalid.
        // Almost impossible but we need to prevent the world to end corrupted by a player stuck inside the portal.
        // Cleanup is deferred to the block tick, the world must not be changed while entities are being moved.
        if (linkedPortalOpt.isEmpty())
        {
            getLevel().scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
            return;
        }

        ServerLevel destination = (ServerLevel) linkedPortalOpt.get().getLevel();
        Vec3 destinationPos = linkedPortalOpt.get().getPortalMiddlePos(false);
        TeleportUtil.teleport((ServerPlayer) collider, destination, destinationPos, collider.getYHeadRot(), collider.getViewXRot(0));
    }

    public void setCore(BlockPos pos)
    {
        corePos = pos.immutable();
        core = null;
        setChanged();
    }

    @Nullable
    public PortalFrameCoreTile getCore()
    {
        if (core != null && core.isRemoved())
            core = null;

        if (core == null)
        {
            if (corePos == null)
                return null;
            var tileOpt = getLevel().getBlockEntity(corePos, VoidHeartTiles.PORTAL_FRAME_CORE);

            tileOpt.ifPresent(tile -> this.core = tile);
        }
        return core;
    }

    /**
     * Remove this interior if orphaned, or cut the link of its core if the other side does not exist anymore
     */
    public void validate()
    {
        if (isOrphan())
        {
            log.warn("Removing orphan portal interior at {} of core {}", getBlockPos(), corePos);
            getLevel().removeBlock(getBlockPos(), false);
            return;
        }

        var core = getCore();
        if (core != null && core.isLinked() && core.findLinkedPortal().isEmpty())
        {
            log.warn("Portal {} has a dangling link to {}, cutting it", core, core.getLink());
            core.cutLinkFromPortal(false);
        }
    }

    /**
     * An interior is orphaned when its core is gone, unlinked or does not own it anymore.
     * Interiors cannot be broken by hand so they must clean themselves.
     */
    public boolean isOrphan()
    {
        if (corePos == null)
            return true;

        // Do not judge on an unloaded core, it would load the chunk just to check
        if (!getLevel().isLoaded(corePos))
            return false;

        var core = getCore();
        return core == null || !core.isLinked() || !core.getLinkedInteriors().contains(getBlockPos());
    }
}
