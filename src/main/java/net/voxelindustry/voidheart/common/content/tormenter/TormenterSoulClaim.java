package net.voxelindustry.voidheart.common.content.tormenter;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Picks the one tormenter of a hotbar that takes a soul.
 */
public final class TormenterSoulClaim
{
    public static final int NONE = -1;

    /**
     * What a hotbar slot holds, {@code type} is null for an empty tormenter.
     */
    public record Held<T>(@Nullable T type, int souls)
    {
        public static <T> Held<T> empty()
        {
            return new Held<>(null, 0);
        }
    }

    private TormenterSoulClaim()
    {
    }

    /**
     * A tormenter already feeding on this mob goes first, so that souls gather instead of spreading over every empty one.
     *
     * @param hotbar one entry per slot, null where the slot holds no tormenter
     * @return the slot taking the soul, or {@link #NONE}
     */
    public static <T> int claimingSlot(List<Held<T>> hotbar, T killed)
    {
        var firstEmpty = NONE;
        for (var slot = 0; slot < hotbar.size(); slot++)
        {
            var held = hotbar.get(slot);
            if (held == null)
                continue;

            if (held.type() == null)
            {
                if (firstEmpty == NONE)
                    firstEmpty = slot;
            }
            else if (held.type().equals(killed) && held.souls() < TormenterSouls.REQUIRED)
                return slot;
        }
        return firstEmpty;
    }
}
