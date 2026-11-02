package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/** Placed on a void pillar it turns the pillar into a Knowledge Repository, see the pillar block. */
public class KnowledgeTabletItem extends Item
{
    public KnowledgeTabletItem(Properties properties)
    {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".knowledge_tablet.lore").withStyle(ChatFormatting.GRAY));
    }
}
