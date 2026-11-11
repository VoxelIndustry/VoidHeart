package net.voxelindustry.voidheart.common.content.apparatus;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid.Placement;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

/**
 * Applies changes made from an apparatus screen to the apparatus in hand, taking components from the inventory and giving them back.
 */
public final class ApparatusEdits
{
    private ApparatusEdits()
    {
    }

    /**
     * The hand an apparatus screen edits: the main hand when it holds one, so the client and the server always pick the same.
     */
    public static @Nullable InteractionHand handOf(net.minecraft.world.entity.player.Player player)
    {
        if (player.getMainHandItem().getItem() instanceof ShieldingApparatusItem)
            return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof ShieldingApparatusItem)
            return InteractionHand.OFF_HAND;
        return null;
    }

    public static void handle(ShieldPayloads.Edit edit, ServerPlayNetworking.Context context)
    {
        apply(context.player(), edit);
    }

    public static boolean apply(ServerPlayer player, ShieldPayloads.Edit edit)
    {
        var hand = handOf(player);
        if (hand == null || !player.isAlive())
            return false;
        var apparatus = player.getItemInHand(hand);
        var tier = ((ShieldingApparatusItem) apparatus.getItem()).tier();
        var grid = ShieldingApparatus.grid(apparatus);

        if (edit.remove())
        {
            var placement = grid.at(edit.x(), edit.y());
            if (placement == null)
                return false;
            apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, grid.without(placement));
            var returned = new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(placement.component()));
            player.getInventory().placeItemBackInInventory(returned, Prediction.SERVER_ONLY);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 0.8F);
            return true;
        }

        if (!grid.canPlace(tier.size(), edit.component(), edit.x(), edit.y(), edit.rotation()))
            return false;
        var source = findComponent(player, edit.component());
        if (source == null)
            return false;

        source.shrink(1);
        apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, grid.with(new Placement(edit.component(), edit.x(), edit.y(), Math.floorMod(edit.rotation(), 4))));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.2F);
        return true;
    }

    private static @Nullable ItemStack findComponent(ServerPlayer player, ApparatusComponent component)
    {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            var stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ApparatusComponentItem item && item.component() == component)
                return stack;
        }
        return null;
    }
}
