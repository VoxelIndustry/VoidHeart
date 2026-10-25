package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    @Setter
    private Identifier linkedWorld;
    @Getter
    @Setter
    private BlockPos linkedPos;
    @Getter
    @Setter
    private Direction linkedFacing;
    private ResourceKey<Level> linkedWorldKey;

    @Getter
    @Setter
    private UUID ownerPlayerUUID;

    @Getter
    private Identifier previousLinkedWorld;
    @Getter
    private BlockPos previousLinkedPos;
    @Getter
    private Direction previousLinkedFacing;
    private ResourceKey<Level> previousLinkedWorldKey;

    public PortalFrameCoreTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.PORTAL_FRAME_CORE, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);


        if (tag.read("linkedWorld", Codec.PASSTHROUGH).isPresent())
        {
            linkedWorld = Identifier.parse(tag.getStringOr("linkedWorld", ""));
            linkedPos = BlockPos.of(tag.getLongOr("linkedPos", 0L));
            linkedFacing = Direction.from3DDataValue(tag.getByteOr("linkedFacing", (byte) 0));
        }

        if (tag.read("previousLinkedWorld", Codec.PASSTHROUGH).isPresent())
        {
            previousLinkedWorld = Identifier.parse(tag.getStringOr("previousLinkedWorld", ""));
            previousLinkedPos = BlockPos.of(tag.getLongOr("previousLinkedPos", 0L));
            previousLinkedFacing = Direction.from3DDataValue(tag.getByteOr("previousLinkedFacing", (byte) 0));
        }

        isBroken = tag.getBooleanOr("isBroken", false);

        if (tag.read("portalState", Codec.PASSTHROUGH).isPresent())
            portalState.load(tag.childOrEmpty("portalState"));

        int count = tag.getIntOr("linkedFramesCount", 0);
        for (int index = 0; index < count; index++)
        {
            linkedFrames.add(BlockPos.of(tag.getLongOr("linkedFrame" + index, 0L)));
        }

        count = tag.getIntOr("linkedInteriorsCount", 0);
        for (int index = 0; index < count; index++)
        {
            linkedInteriors.add(BlockPos.of(tag.getLongOr("linkedInterior" + index, 0L)));
        }

        if (tag.read("ownerPlayerUUID", UUIDUtil.CODEC).isPresent())
            ownerPlayerUUID = tag.read("ownerPlayerUUID", UUIDUtil.CODEC).orElseThrow();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (linkedWorld != null)
        {
            tag.putString("linkedWorld", linkedWorld.toString());
            tag.putLong("linkedPos", linkedPos.asLong());
            tag.putByte("linkedFacing", (byte) linkedFacing.get3DDataValue());
        }

        if (previousLinkedWorld != null)
        {
            tag.putString("previousLinkedWorld", previousLinkedWorld.toString());
            tag.putLong("previousLinkedPos", previousLinkedPos.asLong());
            tag.putByte("previousLinkedFacing", (byte) previousLinkedFacing.get3DDataValue());
        }

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

    void removeFrame(BlockPos framePos)
    {
        linkedFrames.remove(framePos);
        breakTile(framePos);
    }

    /**
     * Cut link and notify potential linked portal
     */
    @Override
    public void breakTile(BlockPos eventSource)
    {
        setChanged();

        setBroken(true);
        linkedFrames.forEach(pos ->
        {
            if (pos.equals(eventSource))
                return;

            BlockEntity tile = getLevel().getBlockEntity(pos);
            if (tile instanceof PortalFrameTile frame)
            {
                frame.removeCore(this);
                frame.refreshLitStatus();
            }
        });

        linkedInteriors.forEach(pos -> getLevel().destroyBlock(pos, true));

        if (!eventSource.equals(BlockPos.ZERO))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(StateProperties.BROKEN, true));

        if (level.isClientSide() || linkedWorld == null)
        {
            cutLinkFromPortal(false);
            return;
        }

        var linkedOpt = getLinkedPortal();

        if (linkedOpt.isPresent())
        {
            // Is linked portal actually linked to us
            if (Objects.equals(linkedOpt.get().getLinkedPos(), getBlockPos()) && Objects.equals(linkedOpt.get().getLinkedWorldKey(), getLevel().dimension()))
            {
                // Linked core is broken if the core of this portal is broken
                // Breaking a frame does not break the linked core since the portal can be restored later
                boolean shouldBreakLinkedCore = isCore() && eventSource.equals(BlockPos.ZERO);
                linkedOpt.get().cutLinkFromPortal(shouldBreakLinkedCore);
            }
        }

        cutLinkFromPortal(false);
    }

    void linkPortal()
    {
        level.setBlockAndUpdate(worldPosition, getBlockState()
                .setValue(BlockStateProperties.LIT, true)
                .setValue(StateProperties.BROKEN, false)
        );

        VoidPocketState.getVoidPocketState(level).getHeartData(ownerPlayerUUID).addPortal(level, worldPosition);

        Direction facing = getFacing();

        Pair<BlockPos, BlockPos> interiorPoints = PortalFormer.excludeBorders(portalState);

        linkedInteriors.clear();
        BlockPos.betweenClosedStream(interiorPoints.getLeft(), interiorPoints.getRight())
                .forEach(mutablePos ->
                {
                    BlockPos pos = mutablePos.immutable();

                    level.setBlockAndUpdate(pos, VoidHeartBlocks.PORTAL_INTERIOR.defaultBlockState().setValue(BlockStateProperties.FACING, facing));

                    BlockEntity tile = level.getBlockEntity(pos);
                    if (!(tile instanceof PortalInteriorTile))
                    {
                        log.error("Tile inside portal is not of correct instance. tilePos={}, portalCorePos={}, portalPoints={}", pos, getBlockPos(), interiorPoints);
                        return;
                    }
                    var portal = (PortalInteriorTile) tile;
                    portal.setCore(getBlockPos());

                    linkedInteriors.add(pos);
                });

        for (BlockPos linkedFrame : getLinkedFrames())
        {
            BlockEntity tile = level.getBlockEntity(linkedFrame);
            if (tile instanceof PortalFrameTile frame && !frame.isCore())
                frame.refreshLitStatus();
        }

        setChanged();
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
        if (getLevel().getBlockState(worldPosition).getBlock() == getBlockState().getBlock())
        {
            getLevel().setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, false));

            for (BlockPos linkedFrame : getLinkedFrames())
            {
                BlockEntity tile = level.getBlockEntity(linkedFrame);
                if (tile instanceof PortalFrameTile frame)
                    frame.refreshLitStatus();
            }
        }
        linkedInteriors.forEach(pos -> getLevel().destroyBlock(pos, true));

        if (getLevel() != null && isServer())
            VoidPocketState.getVoidPocketState(level).getHeartData(ownerPlayerUUID).removePortal(level, worldPosition);

        if (breakCore)
            getLevel().setBlockAndUpdate(getBlockPos(), VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState());
    }

    Optional<PortalFrameCoreTile> getLinkedPortal()
    {
        return getLevel().getServer().getLevel(getLinkedWorldKey()).getBlockEntity(getLinkedPos(), VoidHeartTiles.PORTAL_FRAME_CORE);
    }

    Optional<PortalFrameCoreTile> getPreviouslyLinkedPortal()
    {
        return getLevel().getServer().getLevel(getPreviousLinkedWorldKey()).getBlockEntity(getPreviousLinkedPos(), VoidHeartTiles.PORTAL_FRAME_CORE);
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

    public ResourceKey<Level> getLinkedWorldKey()
    {
        if (linkedWorldKey == null)
            linkedWorldKey = ResourceKey.create(Registries.DIMENSION, linkedWorld);

        return linkedWorldKey;
    }

    public ResourceKey<Level> getPreviousLinkedWorldKey()
    {
        if (previousLinkedWorldKey == null)
            previousLinkedWorldKey = ResourceKey.create(Registries.DIMENSION, previousLinkedWorld);

        return previousLinkedWorldKey;
    }

    public Direction getFacing()
    {
        return getLevel().getBlockState(getBlockPos()).getValue(BlockStateProperties.FACING);
    }
}
