package net.voxelindustry.voidheart.common.energy.pearl;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * The kindled seed, hung under an upright chain to grow. A seed taken down unfinished carries its growth, told in words rather than numbers.
 */
public class KindledSeedItem extends BlockItem
{
    public KindledSeedItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("voidheart.kindled_fractal_seed.lore"));
        var growth = stack.get(PearlContent.PEARL_GROWTH);
        if (growth != null && growth > 0)
        {
            var stage = PearlGrowth.stageOf((float) (growth / PearlBalance.GROWTH_TICKS));
            tooltip.accept(Component.translatable("voidheart.kindled_fractal_seed.grown." + stage.name().toLowerCase()));
        }
        tooltip.accept(Component.translatable("voidheart.kindled_fractal_seed.hint"));
    }
}
