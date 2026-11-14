package net.voxelindustry.voidheart.common.content.apparatus;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusHolder.Carried;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Worn in its own slot, it holds a shield in front of health and armor. Used in hand, or right clicked in the inventory or in its slot, it opens its grid,
 * where components are set like pieces of a puzzle.
 */
public class ShieldingApparatusItem extends Item
{
    /** Set by the client, which asks the server to open the grid of an apparatus right clicked in a screen. */
    public static Consumer<ApparatusHolder> openFromSlot = holder -> {};

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
        if (player instanceof ServerPlayer serverPlayer)
            openGrid(serverPlayer, new Carried(hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND));
        return InteractionResult.SUCCESS;
    }

    /**
     * A right click with nothing held opens the grid, on the client so it also works from the creative inventory, which never tells the server of a click.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried)
    {
        if (action != ClickAction.SECONDARY || !other.isEmpty())
            return false;
        var holder = ApparatusHolder.of(player, slot, stack);
        if (holder == null)
            return false;
        if (player.level().isClientSide() && !(player.containerMenu instanceof ApparatusMenu menu && menu.getHolder().equals(holder)))
            openFromSlot.accept(holder);
        return true;
    }

    public static void openGrid(ServerPlayer player, ApparatusHolder holder)
    {
        var stack = holder.get(player);
        if (!(stack.getItem() instanceof ShieldingApparatusItem) || !player.isAlive() || player.isSpectator())
            return;
        player.openMenu(new ExtendedMenuProvider<ApparatusHolder>()
        {
            @Override
            public ApparatusHolder getScreenOpeningData(ServerPlayer player)
            {
                return holder;
            }

            @Override
            public Component getDisplayName()
            {
                return stack.getHoverName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
            {
                return new ApparatusMenu(containerId, inventory, holder);
            }
        });
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        var grid = ShieldingApparatus.grid(stack);
        return grid.isEmpty() ? Optional.empty() : Optional.of(new ApparatusTooltip(grid, tier.size()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var grid = ShieldingApparatus.grid(stack);
        var stats = grid.stats(tier);
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.grid", tier.size(), tier.size()).withStyle(ChatFormatting.DARK_GRAY));
        appendStats(stats, tooltip);
        appendComponents(grid, tier.size(), tooltip);
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.use").withStyle(ChatFormatting.DARK_PURPLE));
    }

    /**
     * The components set, each kind once with how many, in the colour of what they give.
     */
    public static void appendComponents(ApparatusGrid grid, int size, Consumer<Component> tooltip)
    {
        var counts = new EnumMap<ApparatusComponent, Integer>(ApparatusComponent.class);
        for (var placement : grid.valid(size))
            counts.merge(placement.component(), 1, Integer::sum);
        if (counts.isEmpty())
        {
            tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.components").withStyle(ChatFormatting.GRAY));
        counts.forEach((component, count) -> tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.component", count,
                        Component.translatable(VoidHeartItems.APPARATUS_COMPONENTS.get(component).getDescriptionId()))
                .withColor(component.rgb())));
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
