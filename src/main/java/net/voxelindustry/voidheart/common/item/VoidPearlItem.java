package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class VoidPearlItem extends Item
{
    public VoidPearlItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.COMMON)
                .maxCount(1)
                .food(new FoodComponent.Builder().alwaysEdible().build()));
    }

    public static boolean checkPearlUseHereAndWarn(ItemStack stack, boolean isInPocket, PlayerEntity player)
    {
        var tag = stack.getOrCreateNbt();

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            boolean canUse = !tag.contains("firstDimension") || RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension"))).equals(player.getWorld().getRegistryKey());

            if (!canUse)
                player.sendMessage(new TranslatableText(MODID + ".must_be_same_world"), true);
            return canUse;
        }

        // If first point is already set then it must not be already inside a pocket
        if (isInPocket && (!tag.contains("firstPos") || !RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;
        // If first point is already set then it must be inside a pocket
        if (!isInPocket && (!tag.contains("firstPos") || RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;

        player.sendMessage(new TranslatableText(MODID + ".must_be_inside_outside"), true);
        return false;
    }

    public static boolean doesPearlHasFirstPosition(ItemStack stack)
    {
        return stack.getOrCreateNbt().contains("firstPos");
    }

    public static void sendSuccessMessage(PlayerEntity player, ItemStack stack, boolean alreadyHasFirstPoint)
    {
        if (alreadyHasFirstPoint)
            return;

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            player.sendMessage(new TranslatableText(MODID + ".link_started_local"), true);
            return;
        }

        var tag = stack.getOrCreateNbt();
        if (RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
            player.sendMessage(new TranslatableText(MODID + ".link_started_pocket"), true);
        else
            player.sendMessage(new TranslatableText(MODID + ".link_started_outside"), true);
    }

    @Override
    public boolean hasGlint(ItemStack stack)
    {
        return stack.hasNbt() && doesPearlHasFirstPosition(stack) || super.hasGlint(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        NbtCompound tag = stack.getOrCreateNbt();

        if (tag.contains("firstPos"))
        {
            var blockPos = BlockPos.fromLong(tag.getLong("firstPos"));

            if (RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
            {
                tooltip.add(new TranslatableText(MODID + ".void_pearl.pocket.lore", Text.of("§b" + tag.getString("playerName"))));
                tooltip.add(new TranslatableText(MODID + ".void_pearl.pocket.lore2",
                        Text.of("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
            }
            else
            {
                tooltip.add(new TranslatableText(MODID + ".void_pearl.external.lore",
                        Text.of("§6" + capitalize(new Identifier(tag.getString("firstDimension")).getPath()))));
                tooltip.add(new TranslatableText(MODID + ".void_pearl.pocket.lore2",
                        Text.of("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
            }

            tooltip.add(new TranslatableText(MODID + ".void_pearl.erase.lore"));
        }
        else
            tooltip.add(new TranslatableText(MODID + ".void_pearl.lore", Formatting.AQUA, Formatting.RESET));
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
                ((PlayerEntity) user).sendMessage(new TranslatableText(MODID + ".teleport_in_progress", 5), true);
            }
            else
                ((PlayerEntity) user).sendMessage(new TranslatableText(MODID + ".no_pocket_for_player"), true);
        }
        return stack;
    }
}
