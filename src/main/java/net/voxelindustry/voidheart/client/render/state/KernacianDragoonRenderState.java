package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import org.jspecify.annotations.Nullable;

public class KernacianDragoonRenderState extends LivingEntityRenderState
{
    public float split;
    public float shutters;
    public float swell;
    public float recoil;
    public float kick;
    public final float[] grown = new float[DragoonAdaptation.Kind.values().length];
    public @Nullable Vec3 beamFrom;
    public @Nullable Vec3 beamTo;
    public float beamTicks;
}
