package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FallenBishopRenderState extends LivingEntityRenderState
{
    public float raise;
    public float kneel;
    public float attack;
    public @Nullable Vec3 lanceFrom;
    public @Nullable Vec3 lanceTo;
    public float lanceTicks;
    public @Nullable Vec3 gatherAt;
    public float gather;
    public float gatherTicks;
}
