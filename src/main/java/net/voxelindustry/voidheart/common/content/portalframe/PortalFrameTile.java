package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalFrameTile extends TileBase
{
    @Getter
    private final List<BlockPos> linkedFrames    = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedInteriors = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedCores     = new ArrayList<>();

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Pair<BlockPos, BlockPos> portalPoints;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private boolean isCore;

    @Setter
    private UUID portalEntityID;

    private Identifier         linkedWorld;
    private BlockPos           linkedPos;
    private Direction          linkedFacing;
    private RegistryKey<World> linkedWorldKey;

    public PortalFrameTile()
    {
        super(VoidHeartTiles.PORTAL_WALL);
    }

    public boolean voidPieceInteract(Direction direction, PlayerEntity player, ItemStack voidPiece, boolean isInPocket)
    {
        CompoundTag tag = voidPiece.getOrCreateTag();

        if (!isCore())
        {
            if (!PortalFormer.tryForm(getWorld(), getCachedState(), getPos(), direction))
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

                BlockEntity linkedPortal = getWorld().getServer().getWorld(externalDimension).getBlockEntity(externalPos);

                if (linkedPortal instanceof PortalFrameTile)
                {
                    if (!areShapeEquals(((PortalFrameTile) linkedPortal)))
                    {
                        player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                        return false;
                    }

                    Direction externalFacing = Direction.byId(tag.getByte("externalFacing"));
                    setLinkedPos(externalPos);
                    setLinkedWorld(externalDimension.getValue());
                    setLinkedFacing(externalFacing);
                    linkPortal();

                    voidPiece.decrement(1);

                    ((PortalFrameTile) linkedPortal).setLinkedPos(getPos());
                    ((PortalFrameTile) linkedPortal).setLinkedWorld(VoidHeart.VOID_WORLD_KEY.getValue());
                    ((PortalFrameTile) linkedPortal).setLinkedFacing(getFacing());
                    ((PortalFrameTile) linkedPortal).linkPortal();

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
                tag.putLong("pocketPos", getPos().asLong());
                tag.putByte("pocketFacing", (byte) getFacing().ordinal());
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

            ServerWorld voidWorld = getWorld().getServer().getWorld(VoidHeart.VOID_WORLD_KEY);

            BlockEntity linkedPortal = voidWorld.getBlockEntity(pocketPos);

            if (linkedPortal instanceof PortalFrameTile)
            {
                if (!areShapeEquals(((PortalFrameTile) linkedPortal)))
                {
                    player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                    return false;
                }

                Direction pocketFacing = Direction.byId(tag.getByte("pocketFacing"));
                setLinkedPos(pocketPos);
                setLinkedWorld(VoidHeart.VOID_WORLD_KEY.getValue());
                setLinkedFacing(pocketFacing);
                linkPortal();

                voidPiece.decrement(1);

                ((PortalFrameTile) linkedPortal).setLinkedPos(getPos());
                ((PortalFrameTile) linkedPortal).setLinkedWorld(getWorld().getRegistryKey().getValue());
                ((PortalFrameTile) linkedPortal).setLinkedFacing(getFacing());
                ((PortalFrameTile) linkedPortal).linkPortal();

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
            tag.putLong("externalPos", getPos().asLong());
            tag.putString("externalDimension", getWorld().getRegistryKey().getValue().toString());
            tag.putByte("externalFacing", (byte) getFacing().ordinal());
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
            ((ServerWorld) getWorld()).removeEntity(((ServerWorld) getWorld()).getEntity(portalEntityID));
    }

    PortalFrameTile getLinkedPortal()
    {
        return (PortalFrameTile) getWorld().getServer().getWorld(getLinkedWorldKey()).getBlockEntity(getLinkedPos());
    }

    private void linkPortal()
    {
        Direction facing = getFacing();

        if (FabricLoader.getInstance().isModLoaded(VoidHeart.IMMERSIVE_PORTALS))
            ImmersivePortalFrameCreator.linkImmersivePortal(this, facing);
        else
        {
            Pair<BlockPos, BlockPos> interiorPoints = PortalFormer.excludeBorders(portalPoints);

            BlockPos.stream(interiorPoints.getLeft(), interiorPoints.getRight()).forEach(pos ->
            {
                world.setBlockState(pos, VoidHeartBlocks.PORTAL_INTERIOR.getDefaultState().with(Properties.FACING, facing));

                PortalInteriorTile portal = (PortalInteriorTile) world.getBlockEntity(pos);
                portal.setCore(getPos());

                linkedInteriors.add(pos.toImmutable());
            });
        }

        getWorld().setBlockState(pos, getCachedState().with(Properties.LIT, true));
    }

    private boolean areShapeEquals(PortalFrameTile otherPortal)
    {
        if (portalPoints == null || otherPortal.portalPoints == null)
            return false;

        return getWidth() == otherPortal.getWidth()
                && getHeight() == otherPortal.getHeight()
                && getFacing().getAxis().isHorizontal() == otherPortal.getFacing().getAxis().isHorizontal();
    }

    public int getWidth()
    {
        switch (getFacing().getAxis())
        {
            case Z:
            case Y:
                return portalPoints.getRight().getX() - portalPoints.getLeft().getX();
            case X:
            default:
                return portalPoints.getRight().getZ() - portalPoints.getLeft().getZ();
        }
    }

    public int getHeight()
    {
        switch (getFacing().getAxis())
        {
            case X:
            case Z:
                return portalPoints.getRight().getY() - portalPoints.getLeft().getY();
            case Y:
            default:
                return portalPoints.getRight().getZ() - portalPoints.getLeft().getZ();
        }
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
                (portalPoints.getRight().getX() - portalPoints.getLeft().getX()) / 2F,
                y,
                (portalPoints.getRight().getZ() - portalPoints.getLeft().getZ()) / 2F
        );

        Direction facing = getFacing();
        center = center.add(
                facing.getOffsetX() + 0.5F + portalPoints.getLeft().getX(),
                facing.getOffsetY() + portalPoints.getLeft().getY(),
                facing.getOffsetZ() + 0.5F + portalPoints.getLeft().getZ());
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

        if (tag.contains("portalPointFrom"))
            portalPoints = Pair.of(BlockPos.fromLong(tag.getLong("portalPointFrom")),
                    BlockPos.fromLong(tag.getLong("portalPointTo")));

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

        if (portalPoints != null)
        {
            tag.putLong("portalPointFrom", portalPoints.getLeft().asLong());
            tag.putLong("portalPointTo", portalPoints.getRight().asLong());
        }

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
}
