package net.voxelindustry.voidheart.common.content.door;

import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.math.Vec3f;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.portalframe.ImmersivePortalFrameCreator;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidKeyItem extends Item
{
    public VoidKeyItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.UNCOMMON)
                .maxCount(1));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = context.getStack();

        if (state.getBlock() == VoidHeartBlocks.VOID_DOOR)
        {
            if (isBlank(stack))
                setDoor(stack, world, pos);
        }
        else if (BlockTags.DOORS.contains(state.getBlock()))
        {
            if (!isBlank(stack) && !world.isClient())
                createPortal(stack, world, pos, state);
        }
        return super.useOnBlock(context);
    }

    private boolean isBlank(ItemStack stack)
    {
        return !stack.hasTag() || !stack.getTag().contains("doorPos");
    }

    private void setDoor(ItemStack stack, World world, BlockPos pos)
    {
        VoidDoorTile door = (VoidDoorTile) world.getBlockEntity(pos);

        if (door == null)
            return;

        stack.getOrCreateTag().putLong("doorPos", pos.asLong());
        stack.getOrCreateTag().putUuid("doorID", door.getId());
    }

    private void createPortal(ItemStack stack, World world, BlockPos pos, BlockState state)
    {
        if (state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            pos = pos.up();

        CompoundTag tag = stack.getTag();
        ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
        BlockPos doorPos = BlockPos.fromLong(tag.getLong("doorPos"));
        VoidDoorTile door = (VoidDoorTile) voidWorld.getBlockEntity(doorPos);

        if (door == null)
            return;

        Direction sourceFacing = state.get(DoorBlock.FACING).getOpposite();
        Direction doorFacing = door.getCachedState().get(DoorBlock.FACING);

        Portal portal = Portal.entityType.create(world);
        portal.dimensionTo = voidWorld.getRegistryKey();
        portal.width = 1;
        portal.height = 2;
        portal.destination = Vec3d.ofBottomCenter(doorPos.up());

        portal.axisH = new Vec3d(ImmersivePortalFrameCreator.getUnitVector(Direction.UP));
        portal.axisW = new Vec3d(ImmersivePortalFrameCreator.getUnitVector(sourceFacing.rotateYCounterclockwise()));

        if (doorFacing == sourceFacing)
            portal.rotation = Vec3f.UP.getDegreesQuaternion(180);
        else if (doorFacing == sourceFacing.getOpposite())
        {
            // DO NOTHING
        }
        else if (doorFacing == sourceFacing.rotateYClockwise())
        {
            portal.rotation = Vec3f.UP.getDegreesQuaternion(90);
        }
        else if (doorFacing == sourceFacing.rotateYCounterclockwise())
        {
            portal.rotation = Vec3f.UP.getDegreesQuaternion(-90);
        }

        Vec3d sourcePos = Vec3d.ofBottomCenter(pos).add(5 / 16D * sourceFacing.getVector().getX(), 0, 5 / 16D * sourceFacing.getVector().getZ());
        portal.updatePosition(sourcePos.x, sourcePos.y, sourcePos.z);
        world.spawnEntity(portal);

        door.setPortalDestinationEntityID(portal.getUuid());
    }
}
