package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageItemBlock extends BlockItem
{
    public RavenousStorageItemBlock(Block block)
    {
        super(block, new Item.Settings()
                .maxCount(1)
                .maxDamage(RavenousCollectorTile.CAPACITY)
        );
    }

    @Override
    public boolean isDamageable()
    {
        return false;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient)
    {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        if (stack.hasNbt() && stack.getNbt().contains("storage"))
        {
            var storage = EssenceStorage.fromNbt(stack.getNbt().getCompound("storage"));

            tooltip.add(Text.translatable(storage.type().translatableName()));
            tooltip.add(Text.literal(storage.amount() + " / " + storage.maximumAmount()));
        }
        else
            tooltip.add(Text.translatable(MODID + ".ravenous_collector.untyped"));
    }
}
