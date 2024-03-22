package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class PortalCoreItem extends Item
{
    public PortalCoreItem()
    {
        super(new Settings()
                .rarity(Rarity.COMMON)
                .maxCount(1));
    }

    public static boolean checkPearlUseHereAndWarn(ItemStack stack, boolean isInPocket, PlayerEntity player)
    {
        var tag = stack.getOrCreateNbt();

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            boolean canUse = !tag.contains("firstDimension") || RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension"))).equals(player.getWorld().getRegistryKey());

            if (!canUse)
                player.sendMessage(Text.translatable(MODID + ".must_be_same_world"), true);
            return canUse;
        }

        // If first point is already set then it must not be already inside a pocket
        if (isInPocket && (!tag.contains("firstPos") || !RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;
        // If first point is already set then it must be inside a pocket
        if (!isInPocket && (!tag.contains("firstPos") || RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY)))
            return true;

        player.sendMessage(Text.translatable(MODID + ".must_be_inside_outside"), true);
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
            player.sendMessage(Text.translatable(MODID + ".link_started_local"), true);
            return;
        }

        var tag = stack.getOrCreateNbt();
        if (RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
            player.sendMessage(Text.translatable(MODID + ".link_started_pocket"), true);
        else
            player.sendMessage(Text.translatable(MODID + ".link_started_outside"), true);
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

        if (!tag.contains("firstPos"))
        {
            tooltip.add(Text.translatable(MODID + ".portal_core.manual.lore"));
            return;
        }

        tooltip.add(Text.translatable(MODID + ".portal_core.owner.lore", Text.of("§6" + tag.getString("ownerName"))));

        var blockPos = BlockPos.fromLong(tag.getLong("firstPos"));

        if (RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension"))).equals(VoidHeart.VOID_WORLD_KEY))
        {
            tooltip.add(Text.translatable(MODID + ".portal_core.pocket.lore", Text.of("§b" + tag.getString("playerName"))));
            tooltip.add(Text.translatable(MODID + ".portal_core.pocket.lore2",
                    Text.of("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }
        else
        {
            tooltip.add(Text.translatable(MODID + ".portal_core.external.lore",
                    Text.of("§6" + capitalize(new Identifier(tag.getString("firstDimension")).getPath()))));
            tooltip.add(Text.translatable(MODID + ".portal_core.pocket.lore2",
                    Text.of("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }

        tooltip.add(Text.translatable(MODID + ".portal_core.erase.lore"));
    }
}
