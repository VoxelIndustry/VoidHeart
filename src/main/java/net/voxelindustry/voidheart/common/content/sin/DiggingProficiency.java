package net.voxelindustry.voidheart.common.content.sin;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A player's proficiency in digging with a Ravenous tool, apart from the Gluttony they fight with. Only the bite grows it, and nothing fades it, since no
 * other sin goes on tools. Saved with the player, kept through death, and sent to that player alone for their tooltips.
 */
public final class DiggingProficiency
{
    /**
     * Held apart so the pure stage rules can be tested without Fabric's registries.
     */
    private static final class Holder
    {
        static final AttachmentType<Float> POINTS = AttachmentRegistry.create(
                Identifier.fromNamespaceAndPath(MODID, "ravenous_digging"),
                builder -> builder.persistent(Codec.FLOAT)
                        .copyOnDeath()
                        .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
    }

    private DiggingProficiency()
    {
    }

    public static float points(Player player)
    {
        return player.getAttachedOrElse(Holder.POINTS, 0F);
    }

    public static int stage(Player player)
    {
        return stageOf(points(player));
    }

    /**
     * @return the lore stage, from 1 to {@link SinBalance#STAGES}
     */
    public static int stageOf(float points)
    {
        return SinProficiency.stageOf(Math.clamp(points, 0, SinBalance.DIGGING_DEVOTED_POINTS) / SinBalance.DIGGING_DEVOTED_POINTS);
    }

    private static int index(int stage)
    {
        return Math.clamp(stage, 1, SinBalance.STAGES) - 1;
    }

    /**
     * How many ore blocks a vein bite eats beside the one broken, at a stage.
     */
    public static int veinSize(int stage)
    {
        return SinBalance.DIGGING_VEIN[index(stage)];
    }

    public static int squareRadius(int stage)
    {
        return SinBalance.DIGGING_SQUARE_RADIUS[index(stage)];
    }

    public static int squareDepth(int stage)
    {
        return SinBalance.DIGGING_SQUARE_DEPTH[index(stage)];
    }

    public static void gain(ServerPlayer player, float amount)
    {
        if (amount <= 0)
            return;
        var before = points(player);
        set(player, before + amount);
    }

    public static void set(ServerPlayer player, float points)
    {
        var before = points(player);
        var after = Math.clamp(points, 0, SinBalance.DIGGING_DEVOTED_POINTS);
        if (after == before)
            return;
        player.setAttached(Holder.POINTS, after);
        if (stageOf(after) > stageOf(before))
            ServerPlayNetworking.send(player, new SinStageCrossedPayload(Sin.GLUTTONY));
    }

    /**
     * Loads the attachment type before any player does.
     */
    static void register()
    {
        Holder.POINTS.identifier();
    }
}
