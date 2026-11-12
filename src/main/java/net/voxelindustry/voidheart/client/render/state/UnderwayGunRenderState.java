package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class UnderwayGunRenderState extends EntityRenderState
{
    public boolean awake;
    /** How far out of its housing the gun is, from 0 to 1. */
    public float out;
    /** The way the gun was built to face, as a turn around the vertical in radians. */
    public float facingYaw;
    /** The way its barrel points, in radians: around the vertical, and over the level. */
    public float yaw;
    public float elevation;
    /** The muzzle, from the gun's position. */
    public Vec3 muzzle = Vec3.ZERO;
    public @Nullable Vec3 aimTo;
    public boolean locked;
    public @Nullable Vec3 tracerTo;
    public float tracerFade;
}
