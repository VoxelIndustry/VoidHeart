package net.voxelindustry.voidheart.common.content.apparatus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Worn in its own slot, it holds a shield in front of health and armor. Used in hand it opens its grid, where components are set like pieces of a puzzle.
 */
public class ShieldingApparatusItem extends Item
{
    /** Set by the client, which alone can open a screen. */
    public static Consumer<InteractionHand> openGrid = hand -> {};

    private final ApparatusTier tier;

    public ShieldingApparatusItem(Properties properties, ApparatusTier tier)
    {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public ApparatusTier tier()
    {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (level.isClientSide())
            openGrid.accept(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var grid = ShieldingApparatus.grid(stack);
        var stats = grid.stats(tier);
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.grid", tier.size(), tier.size()).withStyle(ChatFormatting.DARK_GRAY));
        appendStats(stats, tooltip);
        if (grid.isEmpty())
            tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.empty").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.use").withStyle(ChatFormatting.DARK_PURPLE));
    }

    public static void appendStats(ShieldStats stats, Consumer<Component> tooltip)
    {
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.capacity", number(stats.capacity() / 2)).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.regen", number(stats.regenPerTick() * 20 / 2), number(stats.delayTicks() / 20F))
                .withStyle(ChatFormatting.GRAY));
        for (var ward : stats.wards())
            tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.ward." + ward.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
        stats.breakEffect().ifPresent(effect ->
                tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.break." + effect.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    public static String number(float value)
    {
        return Math.abs(value - Math.round(value)) < 0.05F ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
