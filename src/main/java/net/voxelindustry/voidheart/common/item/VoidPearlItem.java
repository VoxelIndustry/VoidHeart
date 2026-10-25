package net.voxelindustry.voidheart.common.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.world.VoidPocketState;


import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;

public class VoidPearlItem extends Item
{
    public VoidPearlItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.COMMON)
                .stacksTo(1)
                .food(new FoodProperties.Builder().alwaysEdible().build()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {

        tooltip.accept(Component.translatable(MODID + ".void_pearl.lore", ChatFormatting.AQUA, ChatFormatting.RESET));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user)
    {
        if (!world.isClientSide() && user instanceof ServerPlayer)
        {
            ServerLevel voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
            VoidPocketState voidPocketState = getVoidPocketState(voidWorld);

            if (voidPocketState.hasPocket(user.getUUID()))
            {
                Vec3 destinationPos = Vec3.atCenterOf(voidPocketState.getPosForPlayer(user.getUUID()).above());

                VoidHeartTicker.addDelayedTask(world.getServer(), 100, () ->
                        TeleportUtil.teleport((ServerPlayer) user, voidWorld, destinationPos, user.getYHeadRot(), user.getViewXRot(0)));
                stack.shrink(1);
                ((Player) user).sendOverlayMessage(Component.translatable(MODID + ".teleport_in_progress", 5));
            }
            else
                ((Player) user).sendOverlayMessage(Component.translatable(MODID + ".no_pocket_for_player"));
        }
        return stack;
    }
}
