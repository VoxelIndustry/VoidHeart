package net.voxelindustry.voidheart.common.content.door;

import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidKeyItem extends Item
{
    public VoidKeyItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.UNCOMMON)
                .stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = context.getItemInHand();

        if (state.getBlock() == VoidHeartBlocks.VOID_DOOR)
        {
            if (isBlank(stack))
            {
                if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
                    pos = pos.below();
                setDoor(stack, world, pos);
            }
        }
        else if (state.is(BlockTags.DOORS) && state.hasProperty(DoorBlock.HALF))
        {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                pos = pos.above();
            if (!isBlank(stack) && !world.isClientSide())
                createPortal(stack, world, pos, state);
        }
        return super.useOn(context);
    }

    private boolean isBlank(ItemStack stack)
    {
        return !stack.has(VoidHeartDataComponents.DOOR_LINK);
    }

    private void setDoor(ItemStack stack, Level world, BlockPos pos)
    {
        VoidDoorTile door = (VoidDoorTile) world.getBlockEntity(pos);

        if (door == null)
            return;

        stack.set(VoidHeartDataComponents.DOOR_LINK, new VoidHeartDataComponents.DoorLink(pos, door.getId()));
    }

    private void createPortal(ItemStack stack, Level world, BlockPos pos, BlockState state)
    {
        ServerLevel voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        BlockPos doorPos = stack.get(VoidHeartDataComponents.DOOR_LINK).doorPos();
        VoidDoorTile door = (VoidDoorTile) voidWorld.getBlockEntity(doorPos);

        if (door == null)
            return;

        // TODO: Link source door to the void door (replaces the Immersive Portals entity)
    }
}
