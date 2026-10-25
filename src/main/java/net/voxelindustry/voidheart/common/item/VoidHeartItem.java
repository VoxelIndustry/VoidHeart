package net.voxelindustry.voidheart.common.item;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartItem extends Item
{
    public VoidHeartItem()
    {
        super(new Properties()
                .rarity(Rarity.RARE)
                .stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("player"))
            return Component.translatable(getDescriptionId(stack) + ".empty");

        return Component.translatable(getDescriptionId(stack), tag.getString("playerName"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context)
    {
        super.appendHoverText(stack, world, tooltip, context);

        tooltip.add(Component.translatable(MODID + ".void_heart.lore"));
    }
}
