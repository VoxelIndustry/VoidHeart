package net.voxelindustry.voidheart.client.levitation;

import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.levitation.BeltLift;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * Client side of the Levitation Belt: the belt drawn at the waist, and the motes it sheds while it holds its wearer up.
 */
public final class LevitationClient
{
    /** Chance a tick that a belt in flight sheds a mote, scaled down by the lift left while it fades. */
    private static final float  MOTE_CHANCE   = 0.45F;
    private static final double VISIBLE_RANGE = 32;
    private static final double RING_RADIUS   = 0.32;
    /** Where the belt sits, as a share of the wearer's height from the feet. */
    private static final double WAIST         = 0.47;

    private LevitationClient()
    {
    }

    public static void register()
    {
        ModelLayerRegistry.registerModelLayer(BeltWornRender.LAYER, BeltWornRender::createLayer);
        TrinketRendererRegistry.registerRenderer(VoidHeartItems.LEVITATION_BELT, BeltWornRender::new);
        ParticleProviderRegistry.getInstance().register(LevitationBelts.MOTE,
                spriteSet -> (options, level, x, y, z, xAux, yAux, zAux, random) -> new BeltMoteParticle(level, x, y, z, xAux, yAux, zAux, spriteSet.get(random)));
        ClientTickEvents.END_CLIENT_TICK.register(LevitationClient::tick);
    }

    private static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null || minecraft.isPaused())
            return;

        var camera = minecraft.gameRenderer.mainCamera().position();
        var random = level.getRandom();
        long now = level.getGameTime();
        for (var player : level.players())
        {
            // A frame flies on its wings, which draw themselves
            if (player.isInvisible() || player.position().distanceTo(camera) > VISIBLE_RANGE || ArmorWear.wearing(player))
                continue;
            float chance = MOTE_CHANCE * lift(player, now);
            if (chance <= 0 || random.nextFloat() >= chance)
                continue;

            double angle = random.nextDouble() * Mth.TWO_PI;
            var motion = player.getDeltaMovement();
            level.addParticle(LevitationBelts.MOTE,
                    player.getX() + Math.cos(angle) * RING_RADIUS, player.getY() + player.getBbHeight() * WAIST, player.getZ() + Math.sin(angle) * RING_RADIUS,
                    motion.x, motion.y, motion.z);
        }
    }

    /**
     * How strongly the belt holds the player up: fully in flight, then less and less as the lift fades.
     */
    private static float lift(Player player, long now)
    {
        var state = LevitationBelts.state(player);
        if (state.flying())
            return 1;
        if (!state.fading())
            return 0;
        return (float) (1 - BeltLift.progress(state.elapsed(now)));
    }
}
