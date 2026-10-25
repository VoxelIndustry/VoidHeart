package net.voxelindustry.voidheart.common.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidAmalgamItem extends Item
{
    public VoidAmalgamItem()
    {
        super(new Properties()
                .rarity(Rarity.RARE)
                .stacksTo(1)
                .food(new FoodProperties.Builder().alwaysEat().build()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context)
    {
        super.appendHoverText(stack, world, tooltip, context);

        tooltip.add(Component.translatable(MODID + ".void_amalgam.lore"));
        tooltip.add(Component.translatable(MODID + ".void_amalgam.lore2"));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user)
    {
        if (world.isClientSide() || !(user instanceof Player))
            return stack;

        ServerLevel voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        VoidPocketState voidPocketState = VoidPocketState.getVoidPocketState(voidWorld);

        if (voidPocketState.hasPocket(user.getUUID()))
            return stack;

        MobEffectInstance blindness = new MobEffectInstance(MobEffects.BLINDNESS, 40);
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 80);
        MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, 40);
        user.addEffect(blindness);
        user.addEffect(poison);
        user.addEffect(wither);
        ((Player) user).playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1 + (world.random.nextFloat() - world.random.nextFloat()) * 0.4F);

        ItemStack result = new ItemStack(VoidHeartItems.VOID_HEART);
        CompoundTag tag = result.getOrCreateTag();
        tag.putUUID("player", user.getUUID());
        tag.putString("playerName", user.getScoreboardName());

        return result;
    }
}
