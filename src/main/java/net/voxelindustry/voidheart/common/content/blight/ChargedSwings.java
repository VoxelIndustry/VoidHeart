package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

import java.util.WeakHashMap;

/**
 * Which players swung a void blight charged enough for its effect, this tick. A swing's charge is spent before the item or a death hears of the hit, so
 * it is read here, as the attack starts.
 */
public final class ChargedSwings
{
    /** A swing must be charged at least this much. */
    public static final float CHARGE = 0.9F;

    private static final WeakHashMap<Player, Long> CHARGED = new WeakHashMap<>();

    private ChargedSwings()
    {
    }

    public static InteractionResult beforeAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit)
    {
        if (level.isClientSide())
            return InteractionResult.PASS;
        if (player.getAttackStrengthScale(0.5F) >= CHARGE)
            CHARGED.put(player, level.getGameTime());
        else
            CHARGED.remove(player);
        return InteractionResult.PASS;
    }

    /** Whether the player's swing landing this tick was charged. A sweep landing with it counts too. */
    public static boolean isCharged(Player player)
    {
        var charged = CHARGED.get(player);
        return charged != null && charged == player.level().getGameTime();
    }
}
