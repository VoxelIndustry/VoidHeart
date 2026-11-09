package net.voxelindustry.voidheart.common.curse;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * How far the void has taken hold of a player. It stays on them through death, and decides which curses can strike.
 */
public final class Corruption
{
    public static final int MAX = 100;

    private static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "corruption"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    /**
     * The fraction of a point left over by scaled rises, so a ward halving many small rises still halves their total.
     */
    private static final AttachmentType<Float> CARRY = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "corruption_carry"),
            builder -> builder.persistent(Codec.FLOAT).copyOnDeath());

    private static final List<CorruptionModifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private Corruption()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachments
    }

    public static int get(ServerPlayer player)
    {
        return player.getAttachedOrElse(LEVEL, 0);
    }

    /**
     * Sets corruption outright, for commands. Gameplay goes through {@link #raise}.
     */
    public static void set(ServerPlayer player, int level)
    {
        player.setAttached(LEVEL, Mth.clamp(level, 0, MAX));
    }

    /**
     * The only way gameplay raises corruption. Every modifier applies, and fractions carry over to the next rise.
     */
    public static void raise(ServerPlayer player, int amount)
    {
        if (amount <= 0)
            return;

        var rise = CorruptionRise.of(amount, CorruptionRise.multiplier(player, MODIFIERS), player.getAttachedOrElse(CARRY, 0F));
        player.setAttached(CARRY, rise.carry());
        if (rise.points() > 0)
            set(player, get(player) + rise.points());
    }

    /**
     * Registered once at startup, and applied to every player on every rise, so a modifier checks for itself whether it covers the player.
     */
    public static void addModifier(CorruptionModifier modifier)
    {
        MODIFIERS.add(modifier);
    }

}
