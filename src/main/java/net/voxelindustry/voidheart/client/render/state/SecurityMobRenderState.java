package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What every walking machine of the Underway shows: how far it has woken, whether its section has power, and the line and streak of its bolts. */
public class SecurityMobRenderState extends LivingEntityRenderState
{
    /** How far it has woken, from cold at 0 to under pressure at 1. */
    public float wake;
    /** Whether its section has power. */
    public boolean live;
    public float attack;
    /** Where its bolts leave from, from its position. */
    public Vec3 muzzle = Vec3.ZERO;
    public @Nullable Vec3 aimTo;
    public boolean locked;
    public @Nullable Vec3 tracerTo;
    public float tracerFade;
    /** How far it has swollen and whitened, as a sapper does while its fuse burns, from 0 to 1. */
    public float swell;

    public static class Mantlet extends SecurityMobRenderState
    {
        /** How far the slit in its shield is open, from 0 to 1. */
        public float slit;
    }

    public static class RamCart extends SecurityMobRenderState
    {
        /** How far its wheels have turned, in radians. */
        public float roll;
        /** Whether it builds steam for a charge, which makes it shake. */
        public boolean windingUp;
    }

    public static class Fitter extends SecurityMobRenderState
    {
        /** How far its arm is raised to its work, from 0 to 1. */
        public float reach;
    }
}
