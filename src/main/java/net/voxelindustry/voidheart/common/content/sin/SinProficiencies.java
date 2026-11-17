package net.voxelindustry.voidheart.common.content.sin;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Proficiency is saved with the player, kept through death, and sent to that player alone so their tooltips can read it.
 */
public final class SinProficiencies
{
    public static final AttachmentType<SinProficiency> PROFICIENCY = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "sin_proficiency"),
            builder -> builder.persistent(SinProficiency.CODEC)
                    .copyOnDeath()
                    .syncWith(SinProficiency.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private SinProficiencies()
    {
    }

    public static void register()
    {
        SinStageCrossedPayload.register();
        GreedDebts.register();
        DiggingProficiency.register();
        RavenousBite.register();
    }

    public static SinProficiency of(Player player)
    {
        return player.getAttachedOrElse(PROFICIENCY, SinProficiency.NONE);
    }

    public static float devotion(Player player, Sin sin)
    {
        return of(player).devotion(sin);
    }

    /**
     * @return the sin of a weapon, or null when it carries none
     */
    public static Sin sinOf(ItemStack stack)
    {
        return stack == null || stack.isEmpty() ? null : stack.get(VoidHeartDataComponents.SIN);
    }

    /**
     * @return the sin a weapon fights with, or null for a diggable tool, whose sin only digs
     */
    public static Sin combatSinOf(ItemStack stack)
    {
        return stack == null || stack.isEmpty() || stack.is(VoidHeartTags.SIN_DIGGABLE) ? null : sinOf(stack);
    }

    public static void gain(ServerPlayer player, Sin sin, float amount)
    {
        var before = of(player);
        var after = before.gain(sin, amount);
        if (after.equals(before))
            return;

        player.setAttached(PROFICIENCY, after);
        if (after.stage(sin) > before.stage(sin))
            ServerPlayNetworking.send(player, new SinStageCrossedPayload(sin));
    }

    /**
     * Sets a sin outright, for commands. Gameplay goes through {@link #gain}.
     */
    public static void set(ServerPlayer player, Sin sin, float points)
    {
        var before = of(player);
        var after = before.with(sin, points);
        player.setAttached(PROFICIENCY, after);
        if (after.stage(sin) > before.stage(sin))
            ServerPlayNetworking.send(player, new SinStageCrossedPayload(sin));
    }
}
