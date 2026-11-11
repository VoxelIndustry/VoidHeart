package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TreantRenderState extends LivingEntityRenderState
{
    // Ticks since it began to come out of its trunk, 0 once it stands free
    public float emerge;
    public float sweep;
}
