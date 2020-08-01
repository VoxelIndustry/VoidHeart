package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.TranslatableText;
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
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

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

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private boolean isCore;

    @Setter
    private UUID portalEntityID;

    private Identifier         linkedWorld;
    private BlockPos           linkedPos;
    private Direction          linkedFacing;
    private RegistryKey<World> linkedWorldKey;

    private boolean wasImmersive;

    public PortalFrameTile()
    {
        super(VoidHeartTiles.PORTAL_WALL);
    }

    public static boolean voidPieceInteract(PortalFrameTile portalFrameTile, World world, BlockPos pos, Direction direction, PlayerEntity player, ItemStack voidPiece, boolean isInPocket)
    {
        CompoundTag tag = voidPiece.getOrCreateTag();

        Optional<DeferredRollbackWork<PortalFormerState>> portalFormer = Optional.empty();

        if (portalFrameTile == null || !portalFrameTile.isCore())
        {
            portalFormer = Optional.of(PortalFormer.tryForm(world,
                    portalFrameTile == null ? VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction) : portalFrameTile.getCachedState(),
                    pos,
                    direction));

            if (!portalFormer.get().maySucceed())
            {
                player.sendMessage(new TranslatableText(MODID + ".portal_form_error"), true);
                return false;
            }
        }

        if (isInPocket)
        {
            if (tag.contains("externalPos"))
            {
                BlockPos externalPos = BlockPos.fromLong(tag.getLong("externalPos"));
                RegistryKey<World> externalDimension = RegistryKey.of(Registry.DIMENSION, new Identifier(tag.getString("externalDimension")));

                ServerWorld outsideWorld = world.getServer().getWorld(externalDimension);
                BlockEntity linkedPortal = outsideWorld.getBlockEntity(externalPos);

                if (linkedPortal instanceof PortalFrameTile)
                {
                    if (portalFormer.isPresent() && !portalFormer.get().getState().areShapeEquals(((PortalFrameTile) linkedPortal).portalState)
                            || portalFrameTile != null && !portalFrameTile.portalState.areShapeEquals(((PortalFrameTile) linkedPortal).portalState))
                    {
                        player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                        return false;
                    }

                    if (portalFormer.isPresent())
                    {
                        if (portalFrameTile == null)
                        {
                            world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
                            portalFrameTile = (PortalFrameTile) world.getBlockEntity(pos);
                        }

                        portalFrameTile.portalState = portalFormer.get().getState();
                        portalFormer.get().execute();
                    }

                    Direction externalFacing = Direction.byId(tag.getByte("externalFacing"));
                    portalFrameTile.setLinkedPos(externalPos);
                    portalFrameTile.setLinkedWorld(externalDimension.getValue());
                    portalFrameTile.setLinkedFacing(externalFacing);
                    portalFrameTile.linkPortal(VoidHeart.useImmersivePortal());
                    portalFrameTile.getWorld().setBlockState(portalFrameTile.pos, portalFrameTile.getCachedState().with(Properties.LIT, true));

                    voidPiece.decrement(1);

                    ((PortalFrameTile) linkedPortal).setLinkedPos(portalFrameTile.getPos());
                    ((PortalFrameTile) linkedPortal).setLinkedWorld(VoidHeart.VOID_WORLD_KEY.getValue());
                    ((PortalFrameTile) linkedPortal).setLinkedFacing(portalFrameTile.getFacing());
                    ((PortalFrameTile) linkedPortal).linkPortal(VoidHeart.useImmersivePortal());
                    outsideWorld.setBlockState(portalFrameTile.getLinkedPos(), linkedPortal.getCachedState().with(Properties.LIT, true));

                    player.sendMessage(new TranslatableText(MODID + ".link_successful"), true);
                }
                else
                {
                    player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos"), true);
                    return false;
                }
            }
            else if (!tag.contains("pocketPos"))
            {
                if (portalFormer.isPresent())
                {
                    if (portalFrameTile == null)
                    {
                        world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
                        portalFrameTile = (PortalFrameTile) world.getBlockEntity(pos);
                    }

                    portalFrameTile.portalState = portalFormer.get().getState();
                    portalFormer.get().execute();
                }

                tag.putLong("pocketPos", portalFrameTile.getPos().asLong());
                tag.putByte("pocketFacing", (byte) portalFrameTile.getFacing().ordinal());
                player.sendMessage(new TranslatableText(MODID + ".link_started_pocket"), true);
            }
            else
                return false;
            return true;
        }

        // Not in pocket

        if (tag.contains("pocketPos"))
        {
            BlockPos pocketPos = BlockPos.fromLong(tag.getLong("pocketPos"));

            ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);

            BlockEntity linkedPortal = voidWorld.getBlockEntity(pocketPos);

            if (linkedPortal instanceof PortalFrameTile)
            {
                if (portalFormer.isPresent() && !portalFormer.get().getState().areShapeEquals(((PortalFrameTile) linkedPortal).portalState)
                        || portalFrameTile != null && !portalFrameTile.portalState.areShapeEquals(((PortalFrameTile) linkedPortal).portalState))
                {
                    player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                    return false;
                }

                if (portalFormer.isPresent())
                {
                    if (portalFrameTile == null)
                    {
                        world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
                        portalFrameTile = (PortalFrameTile) world.getBlockEntity(pos);
                    }

                    portalFrameTile.portalState = portalFormer.get().getState();
                    portalFormer.get().execute();
                }

                Direction pocketFacing = Direction.byId(tag.getByte("pocketFacing"));
                portalFrameTile.setLinkedPos(pocketPos);
                portalFrameTile.setLinkedWorld(VoidHeart.VOID_WORLD_KEY.getValue());
                portalFrameTile.setLinkedFacing(pocketFacing);
                portalFrameTile.linkPortal(VoidHeart.useImmersivePortal());
                portalFrameTile.getWorld().setBlockState(portalFrameTile.pos, portalFrameTile.getCachedState().with(Properties.LIT, true));

                voidPiece.decrement(1);

                ((PortalFrameTile) linkedPortal).setLinkedPos(portalFrameTile.getPos());
                ((PortalFrameTile) linkedPortal).setLinkedWorld(portalFrameTile.getWorld().getRegistryKey().getValue());
                ((PortalFrameTile) linkedPortal).setLinkedFacing(portalFrameTile.getFacing());
                ((PortalFrameTile) linkedPortal).linkPortal(VoidHeart.useImmersivePortal());
                voidWorld.setBlockState(portalFrameTile.getLinkedPos(), linkedPortal.getCachedState().with(Properties.LIT, true));

                player.sendMessage(new TranslatableText(MODID + ".link_successful", "§3"), true);
            }
            else
            {
                player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos"), true);
                return false;
            }
            return true;
        }
        else if (!tag.contains("externalPos"))
        {
            if (portalFormer.isPresent())
            {
                if (portalFrameTile == null)
                {
                    world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
                    portalFrameTile = (PortalFrameTile) world.getBlockEntity(pos);
                }

                portalFrameTile.portalState = portalFormer.get().getState();
                portalFormer.get().execute();
            }

            tag.putLong("externalPos", portalFrameTile.getPos().asLong());
            tag.putString("externalDimension", portalFrameTile.getWorld().getRegistryKey().getValue().toString());
            tag.putByte("externalFacing", (byte) portalFrameTile.getFacing().ordinal());
            player.sendMessage(new TranslatableText(MODID + ".link_started_outside"), true);
            return true;
        }
        return false;
    }

    /**
     * Cut link and notify potential linked portal
     */
    public void breakTile(BlockPos eventSource)
    {
        markDirty();

        if (isCore())
        {
            isCore = false;
            linkedFrames.forEach(pos ->
            {
                if (pos.equals(eventSource))
                    return;

                PortalFrameTile wall = (PortalFrameTile) getWorld().getBlockEntity(pos);
                if (wall != null)
                    wall.removeCore(this);
            });

            linkedInteriors.forEach(pos -> getWorld().breakBlock(pos, true));
        }
        else
            linkedCores.forEach(pos ->
            {
                if (pos.equals(eventSource))
                    return;

                PortalFrameTile wall = (PortalFrameTile) getWorld().getBlockEntity(pos);
                if (wall != null)
                    wall.removeFrame(this);
            });

        if (world.isClient() || linkedWorld == null)
        {
            cutLinkFromPortal();
            return;
        }

        PortalFrameTile linked = getLinkedPortal();

        if (linked != null)
        {
            if (Objects.equals(linked.getLinkedPos(), getPos()) && Objects.equals(linked.getLinkedWorldKey(), getWorld().getRegistryKey()))
            {
                linked.cutLinkFromPortal();
            }
        }

        cutLinkFromPortal();
    }

    public void cutLinkFromPortal()
    {
        setLinkedWorld(null);
        setLinkedFacing(null);
        setLinkedPos(null);

        if (!getWorld().isAir(pos))
            getWorld().setBlockState(pos, getCachedState().with(Properties.LIT, false));
        linkedInteriors.forEach(pos -> getWorld().breakBlock(pos, true));

        if (getWorld() != null && isServer() && portalEntityID != null)
        {
            Entity portalEntity = ((ServerWorld) getWorld()).getEntity(portalEntityID);
            if (portalEntity != null)
                ((ServerWorld) getWorld()).removeEntity(portalEntity);
        }
    }

    PortalFrameTile getLinkedPortal()
    {
        return (PortalFrameTile) getWorld().getServer().getWorld(getLinkedWorldKey()).getBlockEntity(getLinkedPos());
    }

    private void linkPortal(boolean useImmersivePortal)
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

        wasImmersive = useImmersivePortal;
        markDirty();
    }

    public Vec3d getPortalMiddlePos()
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
                y,
                (portalState.getTo().getZ() - portalState.getFrom().getZ()) / 2F
        );

        Direction facing = getFacing();
        center = center.add(
                facing.getOffsetX() + 0.5F + portalState.getFrom().getX(),
                facing.getOffsetY() + portalState.getFrom().getY(),
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
    }

    private void removeFrame(PortalFrameTile wall)
    {
        linkedFrames.remove(wall);
        breakTile(wall.getPos());
    }

    public static Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }

    public boolean isCore()
    {
        return isCore;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        super.fromTag(state, tag);

        if (tag.contains("linkedWorld"))
        {
            linkedWorld = new Identifier(tag.getString("linkedWorld"));
            linkedPos = BlockPos.fromLong(tag.getLong("linkedPos"));
            linkedFacing = Direction.byId(tag.getByte("linkedFacing"));
        }

        isCore = tag.getBoolean("isCore");
        wasImmersive = tag.getBoolean("wasImmersive");

        if (tag.contains("portalPointFrom"))
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
    public CompoundTag toTag(CompoundTag tag)
    {
        if (linkedWorld != null)
        {
            tag.putString("linkedWorld", linkedWorld.toString());
            tag.putLong("linkedPos", linkedPos.asLong());
            tag.putByte("linkedFacing", (byte) linkedFacing.getId());
        }

        tag.putBoolean("isCore", isCore);
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

        return super.toTag(tag);
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
            linkedWorldKey = RegistryKey.of(Registry.DIMENSION, linkedWorld);

        return linkedWorldKey;
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
    public void load()
    {
        if (!isCore() || getLinkedWorld() == null)
            return;

        boolean useImmersivePortal = VoidHeart.useImmersivePortal();

        if (wasImmersive != useImmersivePortal)
            VoidHeartTicker.addTaskForLoadedPos(getPos(), () -> linkPortal(useImmersivePortal));
    }
}
