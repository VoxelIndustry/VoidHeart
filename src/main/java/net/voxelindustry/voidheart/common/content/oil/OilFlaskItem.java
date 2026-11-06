package net.voxelindustry.voidheart.common.content.oil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.item.LoreItem;

/**
 * A flask of oil that coats a weapon when either is used on the other in an inventory, as bundles are filled. The empty bottle comes back.
 */
public class OilFlaskItem extends LoreItem
{
    private final OilType type;

    public OilFlaskItem(Properties properties, OilType type, String... loreKeys)
    {
        super(properties, loreKeys);
        this.type = type;
    }

    public OilType type()
    {
        return type;
    }

    /**
     * The flask is carried and used on the weapon's slot.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player)
    {
        if (clickAction != ClickAction.SECONDARY || !slot.allowModification(player))
            return false;

        var weapon = slot.getItem();
        if (!OilCoating.canCoat(weapon))
            return false;

        coat(self, weapon, player);
        slot.setChanged();
        return true;
    }

    /**
     * The weapon is carried and used on the flask's slot.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem)
    {
        if (clickAction != ClickAction.SECONDARY || !slot.allowModification(player) || !OilCoating.canCoat(other))
            return false;

        coat(self, other, player);
        slot.setChanged();
        return true;
    }

    private void coat(ItemStack flask, ItemStack weapon, Player player)
    {
        OilCoating.coat(weapon, type);
        flask.shrink(1);
        player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.2F);

        // The client predicts the coating but leaves the bottle to the server
        if (player instanceof ServerPlayer)
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
    }
}
