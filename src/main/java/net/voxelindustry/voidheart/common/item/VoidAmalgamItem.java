package net.voxelindustry.voidheart.common.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
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


import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidAmalgamItem extends Item
{
    public VoidAmalgamItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.RARE)
                .stacksTo(1)
                .food(new FoodProperties.Builder().alwaysEdible().build()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {

        tooltip.accept(Component.translatable(MODID + ".void_amalgam.lore"));
        tooltip.accept(Component.translatable(MODID + ".void_amalgam.lore2"));
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
        world.playSound(null, user.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1 + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.4F);

        ItemStack result = new ItemStack(VoidHeartItems.VOID_HEART);
        result.set(VoidHeartDataComponents.HEART_OWNER, new VoidHeartDataComponents.HeartOwner(user.getUUID(), user.getScoreboardName()));

        return result;
    }
}
