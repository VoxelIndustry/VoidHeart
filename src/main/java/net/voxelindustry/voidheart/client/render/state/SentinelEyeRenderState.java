package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

public class SentinelEyeRenderState extends EntityRenderState
{
    /** Whether it shows anything: a searchlight is dark without power. */
    public boolean lit;
    /** The lens, from the watcher's position. */
    public Vec3 lens = Vec3.ZERO;
    public Vec3 gaze = Vec3.ZERO;
    /** The way its housing faces, as a turn around the vertical in radians. */
    public float facingYaw;
    /** How close it is to reporting someone, from 0 to 1. */
    public float notice;
    public boolean noticed;
    /** How far its cone is drawn, and how much it widens for each block of that. */
    public float coneLength;
    public float coneSpread;
}
