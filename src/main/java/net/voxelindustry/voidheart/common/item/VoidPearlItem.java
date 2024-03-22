package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;

public class VoidPearlItem extends Item
{
    public VoidPearlItem()
    {
        super(new Settings()
                .rarity(Rarity.COMMON)
                .maxCount(1)
                .food(new FoodComponent.Builder().alwaysEdible().build()));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(Text.translatable(MODID + ".void_pearl.lore", Formatting.AQUA, Formatting.RESET));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user)
    {
        if (!world.isClient() && user instanceof ServerPlayerEntity)
        {
            ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
            VoidPocketState voidPocketState = getVoidPocketState(voidWorld);

            if (voidPocketState.hasPocket(user.getUuid()))
            {
                Vec3d destinationPos = Vec3d.ofCenter(voidPocketState.getPosForPlayer(user.getUuid()).up());

                VoidHeartTicker.addDelayedTask(world.getServer(), 100, () ->
                        ((ServerPlayerEntity) user).teleport(voidWorld,
                                destinationPos.getX(),
                                destinationPos.getY(),
                                destinationPos.getZ(),
                                user.getHeadYaw(),
                                user.getPitch(0)));
                stack.decrement(1);
                ((PlayerEntity) user).sendMessage(Text.translatable(MODID + ".teleport_in_progress", 5), true);
            }
            else
                ((PlayerEntity) user).sendMessage(Text.translatable(MODID + ".no_pocket_for_player"), true);
        }
        return stack;
    }
}
