package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ShoulderEyeRenderState extends LivingEntityRenderState
{
    public Vec3    renderOffset = Vec3.ZERO;
    public boolean hiddenToCamera;
    public float   bob;
    public float   blink;
    public float   pupilX;
    public float   pupilY;

    public @Nullable Vec3 beamFrom;
    public @Nullable Vec3 beamTo;
    public float          beamTicks;
}
