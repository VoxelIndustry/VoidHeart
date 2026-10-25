package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ExperienceSkullItemBlock extends BlockItem
{
    public ExperienceSkullItemBlock(Block block)
    {
        super(block, new Item.Properties()
                .stacksTo(1)
                .durability(ExperienceSkullTile.MAX_EXPERIENCE)
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

        tooltip.add(Component.translatable(MODID + ".skull_item.lore"));

        if (stack.hasTag() && stack.getTag().contains("experience"))
        {
            var experience = stack.getTag().getInt("experience");
            tooltip.add(Component.translatable(MODID + ".skull_item.lore2", ExperienceUtil.getExperienceLevel(experience), experience));
        }
    }
}
