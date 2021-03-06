package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartItem extends Item
{
    public VoidHeartItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.RARE)
                .maxCount(1));
    }

    @Override
    public Text getName(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.containsUuid("player"))
            return new TranslatableText(getTranslationKey(stack) + ".empty");

        return new TranslatableText(getTranslationKey(stack), tag.getString("playerName"));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(new TranslatableText(MODID + ".void_heart.lore"));
    }
}
