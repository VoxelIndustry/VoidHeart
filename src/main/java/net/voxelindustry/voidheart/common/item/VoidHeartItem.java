package net.voxelindustry.voidheart.common.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartItem extends Item
{
    public VoidHeartItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.RARE)
                .stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack)
    {
        var owner = stack.get(VoidHeartDataComponents.HEART_OWNER);
        if (owner == null)
            return Component.translatable(getDescriptionId() + ".empty");

        return Component.translatable(getDescriptionId(), owner.playerName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {

        tooltip.accept(Component.translatable(MODID + ".void_heart.lore"));
    }
}
