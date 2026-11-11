package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class WraithBeastRenderState extends LivingEntityRenderState
{
    // From 0 out of phase to 1 fully solid
    public float solid;
    // Front raised, from 0 standing to 1 fully reared
    public float rear;
    // Just after the hooves came down, from 1 at the blow to 0
    public float slam;
    // How far through slipping aside from a projectile, 0 when it isn't
    public float dodge;
    public boolean dodgeLeft;
    public float insight;
}
