package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class VileFleshMassRenderState extends LivingEntityRenderState
{
    // How far through each move it is, 0 to 1, or below 0 when not making it
    public float sweep = -1;
    public float grab = -1;
    public float spit = -1;
    public float bite = -1;
    public float feed = -1;
    public boolean awake;
    /** How far it has come apart, 0 to 1. */
    public float dying;
}
