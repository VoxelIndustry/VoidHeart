package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidAmalgamItem extends Item
{
    public VoidAmalgamItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.RARE)
                .maxCount(1)
                .food(new FoodComponent.Builder().alwaysEdible().build()));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(new TranslatableText(MODID + ".void_amalgam.lore"));
        tooltip.add(new TranslatableText(MODID + ".void_amalgam.lore2"));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user)
    {
        if (world.isClient() || !(user instanceof PlayerEntity))
            return stack;

        ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
        VoidPocketState voidPocketState = VoidPocketState.getVoidPocketState(voidWorld);

        if (voidPocketState.hasPocket(user.getUuid()))
            return stack;

        ItemStack result = new ItemStack(VoidHeartItems.VOID_HEART);
        CompoundTag tag = result.getOrCreateTag();
        tag.putUuid("player", user.getUuid());
        tag.putString("playerName", user.getEntityName());

        return result;
    }
}
