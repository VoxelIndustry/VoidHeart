package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ExperienceSkullItemBlock extends BlockItem
{
    public ExperienceSkullItemBlock(Block block)
    {
        super(block, new Item.Settings()
                .maxCount(1)
                .maxDamage(ExperienceSkullTile.MAX_EXPERIENCE)
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

        tooltip.add(Text.translatable(MODID + ".skull_item.lore"));

        if (stack.hasNbt() && stack.getNbt().contains("experience"))
        {
            var experience = stack.getNbt().getInt("experience");
            tooltip.add(Text.translatable(MODID + ".skull_item.lore2", ExperienceUtil.getExperienceLevel(experience), experience));
        }
    }
}
