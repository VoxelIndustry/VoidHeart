package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartItem extends Item
{
    public VoidHeartItem()
    {
        super(new Settings()
                .rarity(Rarity.RARE)
                .maxCount(1));
    }

    @Override
    public Text getName(ItemStack stack)
    {
        NbtCompound tag = stack.getOrCreateNbt();
        if (!tag.containsUuid("player"))
            return Text.translatable(getTranslationKey(stack) + ".empty");

        return Text.translatable(getTranslationKey(stack), tag.getString("playerName"));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(Text.translatable(MODID + ".void_heart.lore"));
    }
}
