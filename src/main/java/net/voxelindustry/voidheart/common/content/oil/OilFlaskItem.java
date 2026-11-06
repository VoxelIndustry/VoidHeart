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
 * A flask of oil that coats a weapon, or a few arrows, when either is used on the other in an inventory, as bundles are filled. The empty bottle comes back.
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

        var target = slot.getItem();
        if (OilCoating.canCoat(target))
            coat(self, target, player);
        else if (OilCoating.isArrow(target))
            coatArrows(self, target, player);
        else
            return false;

        slot.setChanged();
        return true;
    }

    /**
     * The weapon is carried and used on the flask's slot.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem)
    {
        if (clickAction != ClickAction.SECONDARY || !slot.allowModification(player))
            return false;

        if (OilCoating.canCoat(other))
            coat(self, other, player);
        else if (OilCoating.isArrow(other))
            coatArrows(self, other, player);
        else
            return false;

        slot.setChanged();
        return true;
    }

    private void coat(ItemStack flask, ItemStack weapon, Player player)
    {
        OilCoating.coat(weapon, type, Brews.brewOf(flask));
        useUp(flask, player);
    }

    /**
     * Coats the whole stack when it's small enough, or splits the flask's worth off it into the inventory.
     */
    private void coatArrows(ItemStack flask, ItemStack arrows, Player player)
    {
        if (arrows.getCount() <= OilCoating.ARROWS_PER_FLASK)
            OilCoating.coatArrows(arrows, type, Brews.brewOf(flask));
        else
        {
            var coated = arrows.split(OilCoating.ARROWS_PER_FLASK);
            OilCoating.coatArrows(coated, type, Brews.brewOf(flask));
            if (player instanceof ServerPlayer)
                player.getInventory().placeItemBackInInventory(coated, Prediction.SERVER_ONLY);
        }
        useUp(flask, player);
    }

    private void useUp(ItemStack flask, Player player)
    {
        flask.shrink(1);
        player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.2F);

        // The client predicts the coating but leaves the bottle to the server
        if (player instanceof ServerPlayer)
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
    }
}
