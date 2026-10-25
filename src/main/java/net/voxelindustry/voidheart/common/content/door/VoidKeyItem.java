package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidKeyItem extends Item
{
    public VoidKeyItem()
    {
        super(new Settings()
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
            {
                if (state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
                    pos = pos.down();
                setDoor(stack, world, pos);
            }
        }
        else if (state.isIn(BlockTags.DOORS) && state.contains(DoorBlock.HALF))
        {
            if (state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                pos = pos.up();
            if (!isBlank(stack) && !world.isClient())
                createPortal(stack, world, pos, state);
        }
        return super.useOnBlock(context);
    }

    private boolean isBlank(ItemStack stack)
    {
        return !stack.hasNbt() || !stack.getNbt().contains("doorPos");
    }

    private void setDoor(ItemStack stack, World world, BlockPos pos)
    {
        VoidDoorTile door = (VoidDoorTile) world.getBlockEntity(pos);

        if (door == null)
            return;

        stack.getOrCreateNbt().putLong("doorPos", pos.asLong());
        stack.getOrCreateNbt().putUuid("doorID", door.getId());
    }

    private void createPortal(ItemStack stack, World world, BlockPos pos, BlockState state)
    {
        NbtCompound tag = stack.getNbt();
        ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
        BlockPos doorPos = BlockPos.fromLong(tag.getLong("doorPos"));
        VoidDoorTile door = (VoidDoorTile) voidWorld.getBlockEntity(doorPos);

        if (door == null)
            return;

        // TODO: Link source door to the void door (replaces the Immersive Portals entity)
    }
}
