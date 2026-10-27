package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Log4j2
public class PortalFrameCoreTile extends PortalFrameTile
{
    @Getter
    private final List<BlockPos> linkedFrames = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedInteriors = new ArrayList<>();

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private PortalFormerState portalState = new PortalFormerState();

    @Setter(AccessLevel.PACKAGE)
    private boolean isBroken;

    @Getter
    @Nullable
    private PortalLink link;
    @Getter
    @Nullable
    private PortalLink previousLink;

    @Getter
    @Setter
    private UUID ownerPlayerUUID;

    public PortalFrameCoreTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.PORTAL_FRAME_CORE, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        link = PortalLink.load(tag, "linked");
        previousLink = PortalLink.load(tag, "previousLinked");

        isBroken = tag.getBooleanOr("isBroken", false);

        portalState = new PortalFormerState();
        tag.child("portalState").ifPresent(portalState::load);

        linkedFrames.clear();
        int count = tag.getIntOr("linkedFramesCount", 0);
        for (int index = 0; index < count; index++)
            tag.getLong("linkedFrame" + index).map(BlockPos::of).ifPresent(this::addFrame);

        linkedInteriors.clear();
        count = tag.getIntOr("linkedInteriorsCount", 0);
        for (int index = 0; index < count; index++)
        {
            var interior = tag.getLong("linkedInterior" + index).map(BlockPos::of);
            if (interior.isPresent() && !linkedInteriors.contains(interior.get()))
                linkedInteriors.add(interior.get());
        }

        ownerPlayerUUID = tag.read("ownerPlayerUUID", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (link != null)
            link.save(tag, "linked");

        if (previousLink != null)
            previousLink.save(tag, "previousLinked");

        tag.putBoolean("isBroken", isBroken());

        if (portalState != null)
            portalState.save(tag.child("portalState"));

        tag.putInt("linkedFramesCount", linkedFrames.size());

        int index = 0;
        for (BlockPos frame : linkedFrames)
        {
            tag.putLong("linkedFrame" + index, frame.asLong());
            index++;
        }

        tag.putInt("linkedInteriorsCount", linkedInteriors.size());

        index = 0;
        for (BlockPos interior : linkedInteriors)
        {
            tag.putLong("linkedInterior" + index, interior.asLong());
            index++;
        }

        if (ownerPlayerUUID != null)
            tag.store("ownerPlayerUUID", UUIDUtil.CODEC, ownerPlayerUUID);

        super.saveAdditional(tag);
    }

    @Override
    public boolean isCore()
    {
        return true;
    }

    void addFrame(BlockPos framePos)
    {
        if (!linkedFrames.contains(framePos))
            linkedFrames.add(framePos.immutable());
    }

    void removeFrame(BlockPos framePos)
    {
        // A frame can still reference a core that was replaced by a new one at the same position
        if (!linkedFrames.remove(framePos))
            return;
        breakTile(framePos);
    }

    /**
     * Release every frame of this portal so it can be formed again without keeping stale references
     */
    void detachFrames()
    {
        for (BlockPos framePos : List.copyOf(linkedFrames))
        {
            if (framePos.equals(getBlockPos()))
                continue;

            if (level.getBlockEntity(framePos) instanceof PortalFrameTile frame && !frame.isCore())
                frame.removeCore(this);
        }
        linkedFrames.clear();
        setChanged();
    }

    /**
     * Cut link and notify potential linked portal
     *
     * @param eventSource the frame that was removed, or null when this core itself is removed
     */
    @Override
    public void breakTile(@Nullable BlockPos eventSource)
    {
        if (level == null || level.isClientSide())
            return;

        setChanged();
        setBroken(true);

        for (BlockPos framePos : List.copyOf(linkedFrames))
        {
            if (framePos.equals(eventSource) || framePos.equals(getBlockPos()))
                continue;

            if (level.getBlockEntity(framePos) instanceof PortalFrameTile frame && !frame.isCore())
            {
                frame.removeCore(this);
                frame.refreshLitStatus();
            }
        }
        linkedFrames.clear();

        destroyInteriors();

        if (eventSource != null && isStillInWorld())
            level.setBlockAndUpdate(getBlockPos(), level.getBlockState(getBlockPos()).setValue(StateProperties.BROKEN, true));

        if (link != null)
        {
            // Linked core is broken if the core of this portal is broken
            // Breaking a frame does not break the linked core since the portal can be restored later
            boolean shouldBreakLinkedCore = eventSource == null;
            findLinkedPortal().ifPresent(linked -> linked.cutLinkFromPortal(shouldBreakLinkedCore));
        }

        cutLinkFromPortal(false);
    }

    void setLink(PortalLink link)
    {
        if (this.link != null)
            throw new IllegalStateException("Portal " + this + " is already linked to " + this.link + ", cut the link before linking to " + link);
        this.link = link;
        setChanged();
    }

    /**
     * Must only be called once both cores of the link have been validated with {@link #canBeLinked()}
     */
    void linkPortal()
    {
        if (link == null)
            throw new IllegalStateException("Cannot activate portal " + this + " without a link");

        isBroken = false;
        level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition)
                .setValue(BlockStateProperties.LIT, true)
                .setValue(StateProperties.BROKEN, false)
        );

        var heartData = getOwnerHeartData();
        if (heartData != null)
            heartData.addPortal(level, worldPosition);

        Direction facing = getFacing();

        Pair<BlockPos, BlockPos> interiorPoints = PortalFormer.excludeBorders(portalState);

        linkedInteriors.clear();
        BlockPos.betweenClosedStream(interiorPoints.getLeft(), interiorPoints.getRight())
                .map(BlockPos::immutable)
                .forEach(pos ->
                {
                    if (!canHoldInterior(pos))
                    {
                        log.error("Portal interior is obstructed, skipping. pos={}, block={}, portal={}", pos, level.getBlockState(pos), this);
                        return;
                    }

                    level.setBlockAndUpdate(pos, VoidHeartBlocks.PORTAL_INTERIOR.defaultBlockState().setValue(BlockStateProperties.FACING, facing));

                    if (!(level.getBlockEntity(pos) instanceof PortalInteriorTile interior))
                    {
                        log.error("Tile inside portal is not of correct instance. tilePos={}, portal={}, portalPoints={}", pos, this, interiorPoints);
                        return;
                    }
                    interior.setCore(getBlockPos());

                    linkedInteriors.add(pos);
                });

        for (BlockPos linkedFrame : List.copyOf(linkedFrames))
        {
            if (level.getBlockEntity(linkedFrame) instanceof PortalFrameTile frame && !frame.isCore())
                frame.refreshLitStatus();
        }

        setChanged();
    }

    public void cutLinkFromPortal(boolean breakCore)
    {
        if (level == null || level.isClientSide())
            return;

        if (link != null)
        {
            previousLink = link;
            link = null;

            var heartData = getOwnerHeartData();
            if (heartData != null)
                heartData.removePortal(level, worldPosition);
        }

        if (isStillInWorld())
        {
            level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(BlockStateProperties.LIT, false));

            for (BlockPos linkedFrame : List.copyOf(linkedFrames))
            {
                if (level.getBlockEntity(linkedFrame) instanceof PortalFrameTile frame && !frame.isCore())
                    frame.refreshLitStatus();
            }
        }
        destroyInteriors();
        setChanged();

        if (breakCore && isStillInWorld())
            level.setBlockAndUpdate(getBlockPos(), VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState());
    }

    /**
     * Only removes interiors still owned by this core, a stale position may now hold any block placed by a player
     */
    private void destroyInteriors()
    {
        for (BlockPos pos : List.copyOf(linkedInteriors))
        {
            if (level.getBlockEntity(pos) instanceof PortalInteriorTile interior && getBlockPos().equals(interior.getCorePos()))
                level.destroyBlock(pos, false);
        }
        linkedInteriors.clear();
    }

    private boolean canHoldInterior(BlockPos pos)
    {
        var state = level.getBlockState(pos);
        if (state.isAir())
            return true;
        return state.is(VoidHeartBlocks.PORTAL_INTERIOR)
                && level.getBlockEntity(pos) instanceof PortalInteriorTile interior
                && getBlockPos().equals(interior.getCorePos());
    }

    /**
     * During preRemoveSideEffects the world already contains the replacing block
     */
    private boolean isStillInWorld()
    {
        return !isRemoved() && level.getBlockState(worldPosition).is(VoidHeartBlocks.PORTAL_FRAME_CORE);
    }

    public boolean isLinked()
    {
        return link != null;
    }

    /**
     * Formed, unbroken and with a clear interior: the portal can receive a link
     */
    public boolean canBeLinked()
    {
        if (!isFormed() || isBroken())
            return false;

        var interiorPoints = PortalFormer.excludeBorders(portalState);
        return BlockPos.betweenClosedStream(interiorPoints.getLeft(), interiorPoints.getRight()).allMatch(this::canHoldInterior);
    }

    public boolean isFormed()
    {
        if (portalState == null || !isStillInWorld())
            return false;

        var from = portalState.getFrom();
        var to = portalState.getTo();
        if (from.equals(to))
            return false;

        var pos = getBlockPos();
        return pos.getX() >= from.getX() && pos.getX() <= to.getX()
                && pos.getY() >= from.getY() && pos.getY() <= to.getY()
                && pos.getZ() >= from.getZ() && pos.getZ() <= to.getZ();
    }

    /**
     * @return the linked core, only if it still exists and is linked back to this core
     */
    public Optional<PortalFrameCoreTile> findLinkedPortal()
    {
        if (link == null)
            return Optional.empty();

        var linked = findCore(link);
        if (linked.isEmpty())
        {
            log.warn("Portal {} is linked to {} but no core exists there", this, link);
            return Optional.empty();
        }

        var linkedBack = linked.get().getLink();
        if (linkedBack == null || !linkedBack.pointsTo(level, getBlockPos()))
        {
            log.warn("Portal {} is linked to {} but that core is linked to {}", this, link, linkedBack);
            return Optional.empty();
        }
        return linked;
    }

    /**
     * @return the previously linked core, only if it is not linked anymore and its own previous link is this core
     */
    public Optional<PortalFrameCoreTile> findPreviouslyLinkedPortal()
    {
        if (previousLink == null)
            return Optional.empty();

        return findCore(previousLink)
                .filter(previous -> !previous.isLinked())
                .filter(previous -> previous.getPreviousLink() != null && previous.getPreviousLink().pointsTo(level, getBlockPos()));
    }

    private Optional<PortalFrameCoreTile> findCore(PortalLink target)
    {
        if (level == null || level.getServer() == null)
            return Optional.empty();

        ServerLevel targetLevel = level.getServer().getLevel(target.world());
        if (targetLevel == null)
            return Optional.empty();

        return targetLevel.getBlockEntity(target.pos(), VoidHeartTiles.PORTAL_FRAME_CORE)
                .filter(core -> !core.isRemoved() && core != this);
    }

    @Nullable
    private VoidHeartData getOwnerHeartData()
    {
        if (ownerPlayerUUID == null)
        {
            log.warn("Portal {} has no owner, cannot update its flexion count", this);
            return null;
        }

        var heartData = VoidPocketState.getVoidPocketState(level).getHeartData(ownerPlayerUUID);
        if (heartData == null)
            log.warn("Portal {} owner {} has no pocket, cannot update its flexion count", this, ownerPlayerUUID);
        return heartData;
    }

    public Vec3 getPortalMiddlePos(boolean yCentered)
    {
        float y;

        var facing = getFacing();

        if (facing.getAxis() != Direction.Axis.Y)
            y = 1;
        else if (facing == Direction.UP)
            y = 0;
        else // DOWN
            y = -1;

        Vec3 center = new Vec3(
                (portalState.getTo().getX() - portalState.getFrom().getX()) / 2F,
                yCentered ? (portalState.getTo().getY() - portalState.getFrom().getY()) / 2F : y,
                (portalState.getTo().getZ() - portalState.getFrom().getZ()) / 2F
        );

        center = center.add(
                facing.getStepX() + 0.5F + portalState.getFrom().getX(),
                facing.getStepY() + (yCentered ? 0.5F : 0) + portalState.getFrom().getY(),
                facing.getStepZ() + 0.5F + portalState.getFrom().getZ());
        return center;
    }

    public boolean isBroken()
    {
        return getBlockState().getBlock() == VoidHeartBlocks.PORTAL_FRAME_CORE && isBroken;
    }

    public Direction getFacing()
    {
        // Cached state, the world may already hold another block while this tile is being removed
        return getBlockState().getOptionalValue(BlockStateProperties.FACING).orElse(portalState.getFacing());
    }

    @Override
    public String toString()
    {
        return "PortalCore[" + (level == null ? "?" : level.dimension().identifier()) + " " + getBlockPos().toShortString() + "]";
    }
}
