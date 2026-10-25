package net.voxelindustry.voidheart.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class PortalCoreItem extends Item
{
    public PortalCoreItem()
    {
        super(new Properties()
                .rarity(Rarity.COMMON)
                .stacksTo(1));
    }

    public static boolean checkPearlUseHereAndWarn(ItemStack stack, boolean isInPocket, Player player)
    {
        var tag = stack.getOrCreateTag();

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            boolean canUse = !tag.contains("firstDimension") || ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension"))).equals(player.level().dimension());

            if (!canUse)
                player.displayClientMessage(Component.translatable(MODID + ".must_be_same_world"), true);
            return canUse;
        }

        // If first point is already set then it must not be already inside a pocket
        if (isInPocket && (!tag.contains("firstPos") || !ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;
        // If first point is already set then it must be inside a pocket
        if (!isInPocket && (!tag.contains("firstPos") || ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;

        player.displayClientMessage(Component.translatable(MODID + ".must_be_inside_outside"), true);
        return false;
    }

    public static boolean doesPearlHasFirstPosition(ItemStack stack)
    {
        return stack.getOrCreateTag().contains("firstPos");
    }

    public static void sendSuccessMessage(Player player, ItemStack stack, boolean alreadyHasFirstPoint)
    {
        if (alreadyHasFirstPoint)
            return;

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            player.displayClientMessage(Component.translatable(MODID + ".link_started_local"), true);
            return;
        }

        var tag = stack.getOrCreateTag();
        if (ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
            player.displayClientMessage(Component.translatable(MODID + ".link_started_pocket"), true);
        else
            player.displayClientMessage(Component.translatable(MODID + ".link_started_outside"), true);
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return stack.hasTag() && doesPearlHasFirstPosition(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context)
    {
        super.appendHoverText(stack, world, tooltip, context);

        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.contains("firstPos"))
        {
            tooltip.add(Component.translatable(MODID + ".portal_core.manual.lore"));
            return;
        }

        tooltip.add(Component.translatable(MODID + ".portal_core.owner.lore", Component.nullToEmpty("§6" + tag.getString("ownerName"))));

        var blockPos = BlockPos.of(tag.getLong("firstPos"));

        if (ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
        {
            tooltip.add(Component.translatable(MODID + ".portal_core.pocket.lore", Component.nullToEmpty("§b" + tag.getString("playerName"))));
            tooltip.add(Component.translatable(MODID + ".portal_core.pocket.lore2",
                    Component.nullToEmpty("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }
        else
        {
            tooltip.add(Component.translatable(MODID + ".portal_core.external.lore",
                    Component.nullToEmpty("§6" + capitalize(new ResourceLocation(tag.getString("firstDimension")).getPath()))));
            tooltip.add(Component.translatable(MODID + ".portal_core.pocket.lore2",
                    Component.nullToEmpty("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }

        tooltip.add(Component.translatable(MODID + ".portal_core.erase.lore"));
    }
}
