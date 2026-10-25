package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.util.ExperienceUtil;


import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ExperienceSkullItemBlock extends BlockItem
{
    public ExperienceSkullItemBlock(Block block, Item.Properties properties)
    {
        super(block, properties
                .stacksTo(1)
                .durability(ExperienceSkullTile.MAX_EXPERIENCE)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {

        tooltip.accept(Component.translatable(MODID + ".skull_item.lore"));

        var experience = stack.get(VoidHeartDataComponents.EXPERIENCE);
        if (experience != null)
        {
            tooltip.accept(Component.translatable(MODID + ".skull_item.lore2", ExperienceUtil.getExperienceLevel(experience), experience));
        }
    }
}
