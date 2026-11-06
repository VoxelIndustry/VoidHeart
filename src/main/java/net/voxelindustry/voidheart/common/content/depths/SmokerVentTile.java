package net.voxelindustry.voidheart.common.content.depths;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.content.depths.SmokerCycle.Phase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Follows {@link SmokerCycle}: the server burns players standing in the jet, and the client draws it. A jet stops at the first solid block above the vent.
 */
public class SmokerVentTile extends TileBase
{
    public static final ResourceKey<DamageType> JET_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "scalding_jet"));

    private static final float JET_DAMAGE_AMOUNT = 4;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final double JET_RADIUS = 0.7;

    private static final double EFFECT_RANGE = 64;
    private static final double MOUTH_SPREAD = 0.14;
    private static final double MOUTH_DEPTH = 0.45;
    private static final int WISP_INTERVAL_TICKS = 12;
    private static final int SPARK_INTERVAL_TICKS = 2;
    private static final int RUMBLE_INTERVAL_TICKS = 24;
    private static final int PLUMES_PER_TICK = 2;
    private static final int CORES_PER_TICK = 3;
    private static final double SPARK_RISE = 0.06;
    private static final double WISP_RISE = 0.05;
    private static final double PLUME_RISE = 0.3;
    private static final double CORE_RISE = 0.2;
    private static final double EMBER_RISE = 0.16;
    private static final float WARNING_PITCH = 0.5F;
    private static final float ERUPTION_PITCH = 0.6F;

    private final long seed;
    private @Nullable Phase lastPhase;

    public SmokerVentTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.SMOKER_VENT, pos, state);
        seed = SmokerCycle.seed(pos);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SmokerVentTile vent)
    {
        var phase = SmokerCycle.phase(vent.seed, level.getGameTime());
        if (level instanceof ServerLevel server)
        {
            if (phase == Phase.ERUPTING && server.getGameTime() % DAMAGE_INTERVAL_TICKS == 0)
                vent.burnPlayers(server, pos);
            return;
        }
        vent.playEffects(level, pos, phase);
    }

    private void burnPlayers(ServerLevel level, BlockPos pos)
    {
        var jet = jetBox(level, pos);
        DamageSource source = null;
        for (var player : level.players())
        {
            if (player.isSpectator() || !player.getBoundingBox().intersects(jet))
                continue;
            if (source == null)
                source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(JET_DAMAGE));
            player.hurtServer(level, source, Elixirs.againstJet(player, JET_DAMAGE_AMOUNT));
        }
    }

    public AABB jetBox(Level level, BlockPos pos)
    {
        int height = 0;
        int wanted = SmokerCycle.jetHeight(seed);
        var cursor = pos.mutable();
        while (height < wanted && !isSolid(level, cursor.move(0, 1, 0)))
            height++;

        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        return new AABB(x - JET_RADIUS, pos.getY() + 1, z - JET_RADIUS, x + JET_RADIUS, pos.getY() + 1 + height, z + JET_RADIUS);
    }

    private static boolean isSolid(Level level, BlockPos pos)
    {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private void playEffects(Level level, BlockPos pos, Phase phase)
    {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5;
        if (phase != lastPhase)
        {
            if (lastPhase != null)
                announce(level, x, y, z, phase);
            lastPhase = phase;
        }
        if (!isWatched(level, x, y, z) || isSolid(level, pos.above()))
            return;

        long time = level.getGameTime();
        switch (phase)
        {
            case QUIET ->
            {
                if (time % WISP_INTERVAL_TICKS == 0)
                    spawn(level, VoidHeart.SMOKER_WISP_PARTICLE, x, y, z, WISP_RISE);
            }
            case WARNING ->
            {
                if (time % SPARK_INTERVAL_TICKS == 0)
                    spawn(level, VoidHeart.SMOKER_EMBER_PARTICLE, x, y, z, SPARK_RISE);
                if (time % RUMBLE_INTERVAL_TICKS == 0)
                    sound(level, x, y, z, SoundEvents.LAVA_POP, WARNING_PITCH);
            }
            case ERUPTING ->
            {
                for (int i = 0; i < PLUMES_PER_TICK; i++)
                    spawn(level, VoidHeart.SMOKER_PLUME_PARTICLE, x, y, z, PLUME_RISE);
                for (int i = 0; i < CORES_PER_TICK; i++)
                    spawn(level, VoidHeart.SMOKER_CORE_PARTICLE, x, y, z, CORE_RISE);
                spawn(level, VoidHeart.SMOKER_EMBER_PARTICLE, x, y, z, EMBER_RISE);
            }
        }
    }

    private static boolean isWatched(Level level, double x, double y, double z)
    {
        for (var player : level.players())
        {
            if (player.distanceToSqr(x, y, z) < EFFECT_RANGE * EFFECT_RANGE)
                return true;
        }
        return false;
    }

    private static void announce(Level level, double x, double y, double z, Phase phase)
    {
        switch (phase)
        {
            case WARNING -> sound(level, x, y, z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, WARNING_PITCH);
            case ERUPTING ->
            {
                sound(level, x, y, z, SoundEvents.LAVA_EXTINGUISH, ERUPTION_PITCH);
                sound(level, x, y, z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, ERUPTION_PITCH);
            }
            case QUIET -> sound(level, x, y, z, SoundEvents.FIRE_EXTINGUISH, WARNING_PITCH);
        }
    }

    private static void sound(Level level, double x, double y, double z, SoundEvent event, float pitch)
    {
        level.playLocalSound(x, y, z, event, SoundSource.BLOCKS, 1, pitch, false);
    }

    private static void spawn(Level level, SimpleParticleType type, double x, double y, double z, double rise)
    {
        var random = level.getRandom();
        level.addParticle(type,
                x + (random.nextDouble() - 0.5) * MOUTH_SPREAD,
                y + random.nextDouble() * MOUTH_DEPTH,
                z + (random.nextDouble() - 0.5) * MOUTH_SPREAD,
                0,
                rise * (0.85 + random.nextDouble() * 0.3),
                0);
    }
}
