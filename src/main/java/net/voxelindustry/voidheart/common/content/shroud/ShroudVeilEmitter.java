package net.voxelindustry.voidheart.common.content.shroud;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;

/**
 * The smoke and motes of a shroud gate, given off on the client. Lifting, the seal's edge breaks into ink smoke that rolls up under the lintel and
 * spills out over the frame; settling, smoke sinks down the frame's face and back into the seal. While the gate stands open, motes drift slowly in
 * front of its void.
 */
public final class ShroudVeilEmitter
{
    /** Puffs a tick along the edge while the seal lifts, and while it settles. */
    private static final int EDGE_PUFFS = 4;
    private static final int SETTLE_PUFFS = 2;
    /** One tick in this many spills smoke out from under the lintel. */
    private static final int SPILL_EVERY = 1;
    private static final float SPILL_FROM = 0.3F;
    /** One tick in this many gives off a mote while open. */
    private static final int MOTE_EVERY = 4;

    private static final double EDGE_RISE = 0.05;
    private static final double EDGE_RISE_SPREAD = 0.04;
    private static final double EDGE_OUT = 0.012;
    private static final double SPILL_OUT = 0.035;
    private static final double SPILL_RISE = 0.03;
    private static final double SETTLE_SINK = 0.045;
    private static final double SETTLE_IN = 0.015;
    private static final double MOTE_SPEED = 0.006;
    private static final double NEAR_FACE = 0.12;
    private static final double FAR_FACE = 0.45;
    /** The frame's front, one block proud of the seal's face. */
    private static final double FRAME_FRONT = 1.05;
    private static final double OPENING_HALF = ShroudGate.SEAL_HALF_WIDTH + 0.5;

    private ShroudVeilEmitter()
    {
    }

    static void tick(Level level, ShroudAltarTile altar)
    {
        var gate = altar.gate();
        if (!level.getBlockState(new BlockPos(gate.x(), gate.y() + ShroudGate.SEAL_BOTTOM, gate.z())).is(VoidHeartBlocks.SHROUD_SEAL))
            return;

        var random = level.getRandom();
        float progress = altar.progress(level.getGameTime());
        float edge = ShroudGate.SEAL_BOTTOM + ShroudVeil.edgeHeight(ShroudVeil.risen(progress));
        float top = ShroudGate.SEAL_BOTTOM + ShroudGate.SEAL_HEIGHT;
        boolean moving = altar.isOpen() ? progress < 1 : progress > 0;
        if (moving && altar.isOpen())
        {
            for (int puff = 0; puff < EDGE_PUFFS; puff++)
                emit(level, gate, VoidHeart.SHROUD_SMOKE_PARTICLE, across(random), edge, depth(random), EDGE_OUT, EDGE_RISE + random.nextDouble() * EDGE_RISE_SPREAD);
            if (progress > SPILL_FROM && level.getGameTime() % SPILL_EVERY == 0)
                emit(level, gate, VoidHeart.SHROUD_SMOKE_PARTICLE, across(random), top - 0.2, FRAME_FRONT, SPILL_OUT, SPILL_RISE);
        }
        else if (moving)
        {
            for (int puff = 0; puff < SETTLE_PUFFS; puff++)
                emit(level, gate, VoidHeart.SHROUD_SMOKE_PARTICLE, across(random), top + random.nextDouble(), FRAME_FRONT + random.nextDouble() * 0.3, -SETTLE_IN, -SETTLE_SINK);
            emit(level, gate, VoidHeart.SHROUD_SMOKE_PARTICLE, across(random), edge + 0.3, depth(random), -SETTLE_IN, -SETTLE_SINK);
        }
        else if (altar.isOpen() && random.nextInt(MOTE_EVERY) == 0)
        {
            double height = ShroudGate.SEAL_BOTTOM + random.nextDouble() * ShroudGate.SEAL_HEIGHT;
            emit(level, gate, VoidHeart.SHROUD_MOTE_PARTICLE, across(random), height, depth(random), (random.nextDouble() - 0.5) * MOTE_SPEED, random.nextDouble() * MOTE_SPEED);
        }
    }

    private static double across(RandomSource random)
    {
        return (random.nextDouble() * 2 - 1) * OPENING_HALF;
    }

    private static double depth(RandomSource random)
    {
        return NEAR_FACE + random.nextDouble() * (FAR_FACE - NEAR_FACE);
    }

    /**
     * @param across  blocks from the middle of the opening, along the gate's lateral direction
     * @param height  blocks above the gate's floor
     * @param depth   blocks in front of the seal's face
     * @param outward speed away from the seal
     */
    private static void emit(Level level, ShroudGate gate, net.minecraft.core.particles.SimpleParticleType type, double across, double height, double depth, double outward, double rise)
    {
        int facing = gate.facing();
        int lateral = gate.lateral();
        double forward = 0.5 + depth;
        double x = gate.x() + 0.5 + Compass.dx(facing) * forward + Compass.dx(lateral) * across;
        double z = gate.z() + 0.5 + Compass.dz(facing) * forward + Compass.dz(lateral) * across;
        level.addParticle(type, x, gate.y() + height, z, Compass.dx(facing) * outward, rise, Compass.dz(facing) * outward);
    }
}
