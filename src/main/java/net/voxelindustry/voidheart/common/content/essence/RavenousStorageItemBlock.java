package net.voxelindustry.voidheart.common.content.essence;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageItemBlock extends BlockItem
{
    public RavenousStorageItemBlock(Block block)
    {
        super(block, new Item.Properties()
                .stacksTo(1)
                .durability(RavenousCollectorTile.CAPACITY)
        );
    }

    @Override
    public boolean canBeDepleted()
    {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient)
    {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context)
    {
        super.appendHoverText(stack, world, tooltip, context);

        if (stack.hasTag() && stack.getTag().contains("storage"))
        {
            var storage = EssenceStorage.fromNbt(stack.getTag().getCompound("storage"));

            tooltip.add(Component.translatable(storage.type().translatableName()));
            tooltip.add(Component.literal(storage.amount() + " / " + storage.maximumAmount()));
        }
        else
            tooltip.add(Component.translatable(MODID + ".ravenous_collector.untyped"));
    }
}
