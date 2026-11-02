package net.voxelindustry.voidheart.common.curse;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

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

    private Corruption()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static int get(ServerPlayer player)
    {
        return player.getAttachedOrElse(LEVEL, 0);
    }

    public static void set(ServerPlayer player, int level)
    {
        player.setAttached(LEVEL, Mth.clamp(level, 0, MAX));
    }
}
