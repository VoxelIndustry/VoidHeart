package net.voxelindustry.voidheart.common.content.levitation;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.ANCHOR_SLACK;
import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.FIELD_CHECK_TICKS;
import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.FLIGHT_COST;

/**
 * Gives a belt wearer flight inside the field of an azurium pylon, paid from that field every tick they fly, and lets the lift fade when the field is lost
 * in flight. The jumps and the softer falls come from the belt's own attribute modifiers, worn or not near a pylon.
 * <p>
 * A Void Armor frame with wings fitted flies the same way anywhere, paying from its own charge, and its lift fades the same way when the frame cuts them.
 * <p>
 * The server decides when flight is given and taken; both sides bend the wearer's fall while the lift fades, since the client moves its own player and the
 * server counts its falls.
 */
public final class LevitationBelts
{
    public static final String SLOT = "legs/belt";

    public static final AttachmentType<BeltState> STATE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "levitation_belt"),
            builder -> builder.persistent(BeltState.CODEC).syncWith(BeltState.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /**
     * Where a flying wearer last drew from. The grid knows its consumers by position and delivers a tick ahead to each, so drawing from a fresh block
     * every tick of flight would leave a tick's worth waiting at each block left behind. Not saved.
     */
    private static final AttachmentType<BlockPos> ANCHOR = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "levitation_belt_anchor"));

    /** Shed by a belt holding its wearer up, drawn only on the client. */
    public static SimpleParticleType MOTE;

    private LevitationBelts()
    {
    }

    public static void register()
    {
        MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MODID, "levitation_mote"), FabricParticleTypes.simple(false));
    }

    public static BeltState state(Player player)
    {
        return player.getAttachedOrElse(STATE, BeltState.NONE);
    }

    /**
     * Whether the player wears a belt that works. Inside a Void Armor frame the frame carries the body, and the belt goes dormant.
     */
    public static boolean wearing(Player player)
    {
        if (ArmorWear.wearing(player))
            return false;
        var attachment = TrinketsApi.getAttachment(player);
        return attachment != null && attachment.isEquipped(stack -> stack.getItem() instanceof LevitationBeltItem, true);
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
            tickPlayer(player);
    }

    private static void tickPlayer(ServerPlayer player)
    {
        var state = state(player);
        var abilities = player.getAbilities();

        // These game modes fly on their own: the belt lets go without taking that away
        if (player.isCreative() || player.isSpectator())
        {
            if (!state.equals(BeltState.NONE))
                player.setAttached(STATE, BeltState.NONE);
            return;
        }

        var level = player.level();
        long now = level.getGameTime();
        long fadeSince = state.fadeSince();
        if (state.fading() && (held(player) || !BeltLift.fading(now - fadeSince)))
            fadeSince = -1;

        boolean wings = player.isAlive() && ArmorWear.wings(player);
        boolean belt = player.isAlive() && wearing(player);
        boolean flying = state.granted() && abilities.flying;
        boolean powered;
        // A frame's wings pay from its own charge, anywhere; the frame cuts them when it runs low
        if (wings)
            powered = !flying || ArmorWear.draw(player, ArmorBalance.LEVITATION_DRAIN) >= ArmorBalance.LEVITATION_DRAIN;
        else if (!belt)
            powered = false;
        else if (flying)
            powered = drawFlight(player) >= FLIGHT_COST;
        else if (player.tickCount % FIELD_CHECK_TICKS != 0)
            powered = state.granted();
        else
            powered = VoidEnergy.available(level, player.blockPosition()) >= FLIGHT_COST;

        boolean granted = state.granted();
        // Flight given by anything else stays theirs to take back
        if (powered && !abilities.mayfly)
        {
            abilities.mayfly = true;
            // A field reached again while the lift fades takes hold of the wearer at once
            if (fadeSince >= 0 && !player.onGround())
                abilities.flying = true;
            player.onUpdateAbilities();
            granted = true;
            fadeSince = -1;
        }
        else if (!powered && granted)
        {
            if (abilities.flying && player.isAlive() && !held(player))
                fadeSince = now;
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
            granted = false;
        }

        var next = new BeltState(granted, granted && abilities.flying, fadeSince);
        if (!next.equals(state))
            player.setAttached(STATE, next);
        if (next.fading() && BeltLift.cushioned(now - fadeSince))
            player.resetFallDistance();
    }

    /**
     * Pays a tick of flight from the anchor while the wearer stays near it, then from where they are, which becomes the new anchor.
     */
    private static long drawFlight(ServerPlayer player)
    {
        var level = player.level();
        var here = player.blockPosition();
        var anchor = player.getAttached(ANCHOR);
        long got = 0;
        if (anchor != null && anchor.closerThan(here, ANCHOR_SLACK))
        {
            got = VoidEnergy.draw(level, anchor, FLIGHT_COST);
            if (got >= FLIGHT_COST)
                return got;
        }
        got += VoidEnergy.draw(level, here, FLIGHT_COST - got);
        player.setAttached(ANCHOR, here.immutable());
        return got;
    }

    /**
     * Something other than the air holds the player, so a fading lift has nothing left to do.
     */
    private static boolean held(Player player)
    {
        return player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger() || player.isFallFlying() || player.onClimbable();
    }

    private static boolean liftBends(Player player)
    {
        var state = state(player);
        return state.fading() && !player.getAbilities().flying && !player.isFallFlying() && !player.isInWater() && !player.isInLava();
    }

    /**
     * Gravity as the wearer feels it, lowered while a fading lift still holds them up.
     */
    public static double gravity(Player player, double gravity)
    {
        if (!liftBends(player))
            return gravity;
        long elapsed = state(player).elapsed(player.level().getGameTime());
        return gravity * BeltLift.gravityScale(elapsed, player.getDeltaMovement().y <= 0);
    }

    /**
     * Once the tick's movement is worked out, slows a sinking wearer to what the fading lift allows.
     */
    public static void afterTravel(Player player)
    {
        if (!liftBends(player))
            return;
        double top = BeltLift.maxDescent(state(player).elapsed(player.level().getGameTime()));
        var motion = player.getDeltaMovement();
        if (motion.y < -top)
            player.setDeltaMovement(motion.x, -top, motion.z);
    }
}
