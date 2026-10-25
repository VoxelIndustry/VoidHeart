package net.voxelindustry.voidheart.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class PortalCoreItem extends Item
{
    public PortalCoreItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.COMMON)
                .stacksTo(1));
    }

    public static boolean checkPearlUseHereAndWarn(ItemStack stack, boolean isInPocket, Player player)
    {
        var linkStart = stack.get(VoidHeartDataComponents.PORTAL_LINK);

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            boolean canUse = linkStart == null || linkStart.firstPos().dimension().equals(player.level().dimension());

            if (!canUse)
                player.sendOverlayMessage(Component.translatable(MODID + ".must_be_same_world"));
            return canUse;
        }

        // If first point is already set then it must not be already inside a pocket
        if (isInPocket && (linkStart == null || !linkStart.firstPos().dimension().equals(VoidHeart.VOID_WORLD_KEY)))
            return true;
        // If first point is already set then it must be inside a pocket
        if (!isInPocket && (linkStart == null || linkStart.firstPos().dimension().equals(VoidHeart.VOID_WORLD_KEY)))
            return true;

        player.sendOverlayMessage(Component.translatable(MODID + ".must_be_inside_outside"));
        return false;
    }

    public static boolean doesPearlHasFirstPosition(ItemStack stack)
    {
        return stack.has(VoidHeartDataComponents.PORTAL_LINK);
    }

    public static void sendSuccessMessage(Player player, ItemStack stack, boolean alreadyHasFirstPoint)
    {
        if (alreadyHasFirstPoint)
            return;

        if (stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".link_started_local"));
            return;
        }

        var linkStart = stack.get(VoidHeartDataComponents.PORTAL_LINK);
        if (linkStart != null && linkStart.firstPos().dimension().equals(VoidHeart.VOID_WORLD_KEY))
            player.sendOverlayMessage(Component.translatable(MODID + ".link_started_pocket"));
        else
            player.sendOverlayMessage(Component.translatable(MODID + ".link_started_outside"));
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return doesPearlHasFirstPosition(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var linkStart = stack.get(VoidHeartDataComponents.PORTAL_LINK);

        if (linkStart == null)
        {
            tooltip.accept(Component.translatable(MODID + ".portal_core.manual.lore"));
            return;
        }

        tooltip.accept(Component.translatable(MODID + ".portal_core.owner.lore", Component.nullToEmpty("§6" + linkStart.ownerName())));

        BlockPos blockPos = linkStart.firstPos().pos();

        if (linkStart.firstPos().dimension().equals(VoidHeart.VOID_WORLD_KEY))
        {
            tooltip.accept(Component.translatable(MODID + ".portal_core.pocket.lore", Component.nullToEmpty("§b" + linkStart.ownerName())));
            tooltip.accept(Component.translatable(MODID + ".portal_core.pocket.lore2",
                    Component.nullToEmpty("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }
        else
        {
            tooltip.accept(Component.translatable(MODID + ".portal_core.external.lore",
                    Component.nullToEmpty("§6" + capitalize(linkStart.firstPos().dimension().identifier().getPath()))));
            tooltip.accept(Component.translatable(MODID + ".portal_core.pocket.lore2",
                    Component.nullToEmpty("§2" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ())));
        }

        tooltip.accept(Component.translatable(MODID + ".portal_core.erase.lore"));
    }
}
