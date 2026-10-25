package net.voxelindustry.voidheart.common.content.door;

import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import org.jetbrains.annotations.Nullable;

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
            if (isBlank(stack) && !world.isClientSide() && world.dimension().equals(VoidHeart.VOID_WORLD_KEY))
            {
                if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
                    pos = pos.below();
                setDoor(stack, world, pos);
            }
        }
        else if (state.getBlock() instanceof DoorBlock)
        {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
                pos = pos.below();
            if (!isBlank(stack) && world instanceof ServerLevel serverLevel)
            {
                linkDoor(stack, serverLevel, pos, context.getPlayer());
                return InteractionResult.SUCCESS;
            }
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

    private void linkDoor(ItemStack stack, ServerLevel world, BlockPos pos, @Nullable Player player)
    {
        ServerLevel voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        var link = stack.get(VoidHeartDataComponents.DOOR_LINK);

        // The bound void door was broken or replaced since the key was made
        if (!(voidWorld.getBlockEntity(link.doorPos()) instanceof VoidDoorTile door) || !door.getId().equals(link.doorId()))
        {
            if (player != null)
                player.sendOverlayMessage(Component.translatable(VoidHeart.MODID + ".void_door_missing"));
            return;
        }

        VoidDoorLinks.get(world.getServer()).link(GlobalPos.of(world.dimension(), pos), link.doorPos());

        if (player != null)
            player.sendOverlayMessage(Component.translatable(VoidHeart.MODID + ".void_door_linked"));
    }
}
