package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.ILoadable;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.compat.immportal.ImmersivePortalCompat;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Log4j2
public class PortalFrameTile extends TileBase implements ILoadable
{
    @Getter
    private final List<BlockPos> linkedFrames    = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedInteriors = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedCores     = new ArrayList<>();

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private PortalFormerState portalState = new PortalFormerState();

    @Setter(AccessLevel.PACKAGE)
    private boolean isCore;

    @Setter(AccessLevel.PACKAGE)
    private boolean isBroken;

    @Setter
    private UUID portalEntityID;

    private Identifier         linkedWorld;
    private BlockPos           linkedPos;
    private Direction          linkedFacing;
    private RegistryKey<World> linkedWorldKey;

    @Getter
    private Identifier         previousLinkedWorld;
    @Getter
    private BlockPos           previousLinkedPos;
    @Getter
    private Direction          previousLinkedFacing;
    private RegistryKey<World> previousLinkedWorldKey;

    private boolean wasImmersive;

    public PortalFrameTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.PORTAL_WALL, pos, state);
    }


    /**
     * Cut link and notify potential linked portal
     */
    public void breakTile(BlockPos eventSource)
    {
        markDirty();

        if (isCore())
        {
            setBroken(true);
            linkedFrames.forEach(pos ->
            {
                if (pos.equals(eventSource))
                    return;

                BlockEntity tile = getWorld().getBlockEntity(pos);
                if (tile instanceof PortalFrameTile frame)
                {
                    frame.removeCore(this);
                    frame.refreshLitStatus();
                }
            });

            linkedInteriors.forEach(pos -> getWorld().breakBlock(pos, true));

            if (!eventSource.equals(BlockPos.ORIGIN))
                getWorld().setBlockState(getPos(), getCachedState().with(StateProperties.BROKEN, true));
        }
        else
            linkedCores.forEach(pos ->
            {
                if (pos.equals(eventSource))
                    return;

                BlockEntity coreFrame = getWorld().getBlockEntity(pos);
                if (coreFrame instanceof PortalFrameTile)
                    ((PortalFrameTile) coreFrame).removeFrame(this);
            });

        if (world.isClient() || linkedWorld == null)
        {
            cutLinkFromPortal(false);
            return;
        }

        PortalFrameTile linked = getLinkedPortal();

        if (linked != null)
        {
            // Is linked portal actually linked to us
            if (Objects.equals(linked.getLinkedPos(), getPos()) && Objects.equals(linked.getLinkedWorldKey(), getWorld().getRegistryKey()))
            {
                // Linked core is broken if the core of this portal is broken
                // Breaking a frame does not break the linked core since the portal can be restored later
                boolean shouldBreakLinkedCore = isCore() && eventSource.equals(BlockPos.ORIGIN);
                linked.cutLinkFromPortal(shouldBreakLinkedCore);
            }
        }

        cutLinkFromPortal(false);
    }

    public void cutLinkFromPortal(boolean breakCore)
    {
        previousLinkedWorld = getLinkedWorld();
        previousLinkedPos = getLinkedPos();
        previousLinkedFacing = getLinkedFacing();

        setLinkedWorld(null);
        setLinkedFacing(null);
        setLinkedPos(null);

        // Check if block has been replaced
        if (getWorld().getBlockState(pos).getBlock() == getCachedState().getBlock())
        {
            getWorld().setBlockState(pos, getCachedState().with(Properties.LIT, false));

            for (BlockPos linkedFrame : getLinkedFrames())
            {
                BlockEntity tile = world.getBlockEntity(linkedFrame);
                if (tile instanceof PortalFrameTile frame)
                    frame.refreshLitStatus();
            }
        }
        linkedInteriors.forEach(pos -> getWorld().breakBlock(pos, true));

        if (getWorld() != null && isServer() && portalEntityID != null)
        {
            Entity portalEntity = ((ServerWorld) getWorld()).getEntity(portalEntityID);
            if (portalEntity != null)
                portalEntity.remove(Entity.RemovalReason.DISCARDED);
        }

        if (breakCore)
            getWorld().setBlockState(getPos(), VoidHeartBlocks.VOIDSTONE_BRICKS.getDefaultState());
    }

    PortalFrameTile getLinkedPortal()
    {
        return (PortalFrameTile) getWorld().getServer().getWorld(getLinkedWorldKey()).getBlockEntity(getLinkedPos());
    }

    PortalFrameTile getPreviouslyLinkedPortal()
    {
        return (PortalFrameTile) getWorld().getServer().getWorld(getPreviousLinkedWorldKey()).getBlockEntity(getPreviousLinkedPos());
    }

    void linkPortal(boolean useImmersivePortal)
    {
        Direction facing = getFacing();

        if (useImmersivePortal)
            ImmersivePortalFrameCreator.linkImmersivePortal(this, facing);

        Pair<BlockPos, BlockPos> interiorPoints = PortalFormer.excludeBorders(portalState);

        linkedInteriors.clear();
        BlockPos.stream(interiorPoints.getLeft(), interiorPoints.getRight())
                .forEach(mutablePos ->
                {
                    BlockPos pos = mutablePos.toImmutable();

                    if (useImmersivePortal)
                        world.setBlockState(pos, VoidHeartBlocks.PORTAL_IMMERSIVE_INTERIOR.getDefaultState().with(Properties.FACING, facing));
                    else
                        world.setBlockState(pos, VoidHeartBlocks.PORTAL_INTERIOR.getDefaultState().with(Properties.FACING, facing));

                    BlockEntity tile = world.getBlockEntity(pos);
                    if (!(tile instanceof PortalInteriorTile))
                        log.error("Tile inside portal is not of correct instance. tilePos={}, portalCorePos={}, portalPoints={}", pos, getPos(), interiorPoints);
                    PortalInteriorTile portal = (PortalInteriorTile) tile;
                    portal.setCore(getPos());

                    linkedInteriors.add(pos);
                });

        for (BlockPos linkedFrame : getLinkedFrames())
        {
            BlockEntity tile = world.getBlockEntity(linkedFrame);
            if (tile instanceof PortalFrameTile frame && !frame.isCore())
                frame.refreshLitStatus();
        }

        wasImmersive = useImmersivePortal;
        markDirty();
    }

    public Vec3d getPortalMiddlePos(boolean yCentered)
    {
        float y;

        if (getFacing().getAxis() != Axis.Y)
            y = 1;
        else if (getFacing() == Direction.UP)
            y = 0;
        else // DOWN
            y = -1;

        Vec3d center = new Vec3d(
                (portalState.getTo().getX() - portalState.getFrom().getX()) / 2F,
                yCentered ? (portalState.getTo().getY() - portalState.getFrom().getY()) / 2F : y,
                (portalState.getTo().getZ() - portalState.getFrom().getZ()) / 2F
        );

        Direction facing = getFacing();
        center = center.add(
                facing.getOffsetX() + 0.5F + portalState.getFrom().getX(),
                facing.getOffsetY() + (yCentered ? 0.5F : 0) + portalState.getFrom().getY(),
                facing.getOffsetZ() + 0.5F + portalState.getFrom().getZ());
        return center;
    }

    void addCore(PortalFrameTile wall)
    {
        linkedCores.add(wall.getPos());
        markDirty();
    }

    private void removeCore(PortalFrameTile wall)
    {
        linkedCores.remove(wall.getPos());
        markDirty();


    }

    private void removeFrame(PortalFrameTile frame)
    {
        linkedFrames.remove(frame);
        breakTile(frame.getPos());
    }

    public static Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }

    public void refreshLitStatus()
    {
        var existingState = world.getBlockState(getPos());

        if (existingState.getBlock() != getCachedState().getBlock())
            return;

        if (linkedCores.isEmpty())
            world.setBlockState(getPos(), existingState.with(Properties.LIT, false));

        var isAnyCoreLit = linkedCores.stream().anyMatch(this::isCoreLit);
        world.setBlockState(getPos(), existingState.with(Properties.LIT, isAnyCoreLit));
    }

    private boolean isCoreLit(BlockPos corePos)
    {
        var coreState = world.getBlockState(corePos);

        if (coreState.getBlock() != VoidHeartBlocks.PORTAL_FRAME_CORE)
            return false;
        return coreState.get(Properties.LIT);
    }

    public boolean isCore()
    {
        return isCore;
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        if (tag.contains("linkedWorld"))
        {
            linkedWorld = new Identifier(tag.getString("linkedWorld"));
            linkedPos = BlockPos.fromLong(tag.getLong("linkedPos"));
            linkedFacing = Direction.byId(tag.getByte("linkedFacing"));
        }

        if (tag.contains("previousLinkedWorld"))
        {
            previousLinkedWorld = new Identifier(tag.getString("previousLinkedWorld"));
            previousLinkedPos = BlockPos.fromLong(tag.getLong("previousLinkedPos"));
            previousLinkedFacing = Direction.byId(tag.getByte("previousLinkedFacing"));
        }

        isCore = tag.getBoolean("isCore");
        isBroken = tag.getBoolean("isBroken");
        wasImmersive = tag.getBoolean("wasImmersive");

        if (tag.contains("portalState"))
            portalState.fromTag(tag.getCompound("portalState"));

        int count = tag.getInt("linkedFramesCount");
        for (int index = 0; index < count; index++)
        {
            linkedFrames.add(BlockPos.fromLong(tag.getLong("linkedFrame" + index)));
        }

        count = tag.getInt("linkedInteriorsCount");
        for (int index = 0; index < count; index++)
        {
            linkedInteriors.add(BlockPos.fromLong(tag.getLong("linkedInterior" + index)));
        }

        count = tag.getInt("linkedCoreCount");
        for (int index = 0; index < count; index++)
        {
            linkedCores.add(BlockPos.fromLong(tag.getLong("linkedCore" + index)));
        }

        if (tag.containsUuid("portalEntityID"))
            portalEntityID = tag.getUuid("portalEntityID");
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (linkedWorld != null)
        {
            tag.putString("linkedWorld", linkedWorld.toString());
            tag.putLong("linkedPos", linkedPos.asLong());
            tag.putByte("linkedFacing", (byte) linkedFacing.getId());
        }

        if (previousLinkedWorld != null)
        {
            tag.putString("previousLinkedWorld", previousLinkedWorld.toString());
            tag.putLong("previousLinkedPos", previousLinkedPos.asLong());
            tag.putByte("previousLinkedFacing", (byte) previousLinkedFacing.getId());
        }

        tag.putBoolean("isCore", isCore);
        tag.putBoolean("isBroken", isBroken());
        tag.putBoolean("wasImmersive", wasImmersive);

        if (portalState != null)
            tag.put("portalState", portalState.toTag());

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

        tag.putInt("linkedCoreCount", linkedCores.size());

        index = 0;
        for (BlockPos core : linkedCores)
        {
            tag.putLong("linkedCore" + index, core.asLong());
            index++;
        }

        if (portalEntityID != null)
            tag.putUuid("portalEntityID", portalEntityID);

        super.writeNbt(tag);
    }

    public boolean isBroken()
    {
        return getCachedState().getBlock() == VoidHeartBlocks.PORTAL_FRAME_CORE && isBroken;
    }

    public Direction getFacing()
    {
        return getWorld().getBlockState(getPos()).get(Properties.FACING);
    }

    public Identifier getLinkedWorld()
    {
        return linkedWorld;
    }

    public BlockPos getLinkedPos()
    {
        return linkedPos;
    }

    public Direction getLinkedFacing()
    {
        return linkedFacing;
    }

    public RegistryKey<World> getLinkedWorldKey()
    {
        if (linkedWorldKey == null)
            linkedWorldKey = RegistryKey.of(Registry.WORLD_KEY, linkedWorld);

        return linkedWorldKey;
    }

    public RegistryKey<World> getPreviousLinkedWorldKey()
    {
        if (previousLinkedWorldKey == null)
            previousLinkedWorldKey = RegistryKey.of(Registry.WORLD_KEY, previousLinkedWorld);

        return previousLinkedWorldKey;
    }

    public void setLinkedWorld(Identifier linkedWorld)
    {
        this.linkedWorld = linkedWorld;
    }

    public void setLinkedPos(BlockPos linkedPos)
    {
        this.linkedPos = linkedPos;
    }

    public void setLinkedFacing(Direction linkedFacing)
    {
        this.linkedFacing = linkedFacing;
    }

    @Override
    public void serverLoad()
    {
        if (!isCore() || getLinkedWorld() == null)
            return;

        boolean useImmersivePortal = ImmersivePortalCompat.useImmersivePortal();

        if (wasImmersive != useImmersivePortal)
            VoidHeartTicker.addTaskForLoadedPos(getPos(), () -> linkPortal(useImmersivePortal));
    }
}
