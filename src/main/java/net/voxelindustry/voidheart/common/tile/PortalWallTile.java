package net.voxelindustry.voidheart.common.tile;

import lombok.Getter;
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
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.lang.Math.abs;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalWallTile extends BlockEntity
{
    @Getter
    private final List<BlockPos> linkedFrames    = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedInteriors = new ArrayList<>();
    @Getter
    private final List<BlockPos> linkedCores     = new ArrayList<>();

    @Getter
    private Pair<BlockPos, BlockPos> portalPoints;

    @Getter
    private boolean isCore;

    private Identifier         linkedWorld;
    private BlockPos           linkedPos;
    private Direction          linkedFacing;
    private RegistryKey<World> linkedWorldKey;

    public PortalWallTile()
    {
        super(VoidHeartTiles.PORTAL_WALL);
    }

    public boolean voidPieceInteract(Direction direction, PlayerEntity player, ItemStack voidPiece)
    {
        CompoundTag tag = voidPiece.getOrCreateTag();
        if (!tag.containsUuid("player"))
            return false;

        if (!isCore())
        {
            if (!tryForm(direction))
            {
                player.sendMessage(new TranslatableText(MODID + ".portal_form_error"), true);
                return false;
            }
        }

        UUID playerUUID = tag.getUuid("player");
        boolean isInPocket = isInPocket(playerUUID);

        if (isInPocket)
        {
            if (tag.contains("externalPos"))
            {
                BlockPos externalPos = BlockPos.fromLong(tag.getLong("externalPos"));
                RegistryKey<World> externalDimension = RegistryKey.of(Registry.DIMENSION, new Identifier(tag.getString("externalDimension")));

                BlockEntity linkedPortal = getWorld().getServer().getWorld(externalDimension).getBlockEntity(externalPos);

                if (linkedPortal instanceof PortalWallTile)
                {
                    if (!areShapeEquals(((PortalWallTile) linkedPortal)))
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

                    ((PortalWallTile) linkedPortal).setLinkedPos(getPos());
                    ((PortalWallTile) linkedPortal).setLinkedWorld(VoidHeart.VOID_WORLD_KEY.getValue());
                    ((PortalWallTile) linkedPortal).setLinkedFacing(getFacing());
                    ((PortalWallTile) linkedPortal).linkPortal();

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

            if (linkedPortal instanceof PortalWallTile)
            {
                if (!areShapeEquals(((PortalWallTile) linkedPortal)))
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

                ((PortalWallTile) linkedPortal).setLinkedPos(getPos());
                ((PortalWallTile) linkedPortal).setLinkedWorld(getWorld().getRegistryKey().getValue());
                ((PortalWallTile) linkedPortal).setLinkedFacing(getFacing());
                ((PortalWallTile) linkedPortal).linkPortal();

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

                PortalWallTile wall = (PortalWallTile) getWorld().getBlockEntity(pos);
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

                PortalWallTile wall = (PortalWallTile) getWorld().getBlockEntity(pos);
                if (wall != null)
                    wall.removeFrame(this);
            });

        if (world.isClient() || linkedWorld == null)
        {
            cutLinkFromPortal();
            return;
        }

        PortalWallTile linked = getLinkedPortal();

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

        linkedInteriors.forEach(pos -> getWorld().breakBlock(pos, true));
    }

    private PortalWallTile getLinkedPortal()
    {
        return (PortalWallTile) getWorld().getServer().getWorld(getLinkedWorldKey()).getBlockEntity(getLinkedPos());
    }

    private void linkPortal()
    {
        Direction facing = getFacing();

        Pair<BlockPos, BlockPos> interiorPoints = PortalFormer.excludeBorders(portalPoints);
        BlockPos.stream(interiorPoints.getLeft(), interiorPoints.getRight()).forEach(pos ->
        {
            world.setBlockState(pos, VoidHeartBlocks.POCKET_PORTAL.getDefaultState().with(Properties.FACING, facing));

            VoidPortalTile portal = (VoidPortalTile) world.getBlockEntity(pos);
            portal.setCore(getPos());

            linkedInteriors.add(pos.toImmutable());
        });
    }

    private boolean areShapeEquals(PortalWallTile otherPortal)
    {
        if (portalPoints == null || otherPortal.portalPoints == null)
            return false;

        return getWidth() == otherPortal.getWidth() && getHeight() == otherPortal.getHeight();
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
        BlockPos.Mutable center = portalPoints.getRight().subtract(portalPoints.getLeft()).mutableCopy();

        center.setX(center.getX() / 2);
        center.setY(center.getY() / 2);
        center.setZ(center.getZ() / 2);
        if (getFacing().getAxis() != Axis.Y)
            center.setY(1);

        center.move(getFacing());
        return Vec3d.ofCenter(center.add(portalPoints.getLeft()));
    }

    private boolean tryForm(Direction direction)
    {
        Pair<BlockPos, BlockPos> portalPoints = PortalFormer.tryFloodFill(
                getPos(),
                16,
                pos -> canUseBlock(pos, getWorld().getBlockState(pos)),
                pos -> getWorld().isAir(pos),
                direction,
                getAdjacentDirection(direction));

        if (portalPoints.getLeft().equals(getPos()) && portalPoints.getRight().equals(getPos()))
            return false;

        world.setBlockState(getPos(), getCachedState().with(Properties.FACING, direction));
        BlockPos.stream(portalPoints.getLeft(), portalPoints.getRight()).forEach(pos ->
        {
            PortalWallTile wall = (PortalWallTile) world.getBlockEntity(pos);

            // Core check for corners (it's valid to have a core as a corner but not a wall of the portal)
            if (wall == null || wall.isCore())
                return;

            wall.addCore(this);
            linkedFrames.add(pos.toImmutable());
        });

        isCore = true;
        this.portalPoints = portalPoints;

        markDirty();

        return true;
    }

    public boolean isInPocket(UUID playerUUID)
    {
        ServerWorld voidWorld = getWorld().getServer().getWorld(VoidHeart.VOID_WORLD_KEY);

        if (getWorld() != voidWorld)
            return false;

        BlockPos pocketPos = VoidPocketState.getVoidPocketState((ServerWorld) getWorld()).getPosForPlayer(playerUUID);

        return abs(pocketPos.getX() - getPos().getX()) < 9 &&
                abs(pocketPos.getY() - getPos().getY()) < 9 &&
                abs(pocketPos.getZ() - getPos().getZ()) < 9;
    }

    private void addCore(PortalWallTile wall)
    {
        linkedCores.add(wall.getPos());
        markDirty();
    }

    private void removeCore(PortalWallTile wall)
    {
        linkedCores.remove(wall.getPos());
    }

    private void removeFrame(PortalWallTile wall)
    {
        linkedFrames.remove(wall);
        breakTile(wall.getPos());
    }

    private Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }

    private boolean canUseBlock(BlockPos pos, BlockState state)
    {
        if (pos.equals(getPos()))
            return true;

        if (state.getBlock() != VoidHeartBlocks.PORTAL_WALL)
            return false;

        PortalWallTile tile = (PortalWallTile) getWorld().getBlockEntity(pos);

        return tile != null && !tile.isCore();
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
