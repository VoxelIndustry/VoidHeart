package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrawlerRenderState extends LivingEntityRenderState
{
    public final Quaternionf surfaceRotation = new Quaternionf();
    public final Vector3f surfaceOffset = new Vector3f();
    public boolean clinging;
    public boolean aggressive;
    public float attack;
    public float stride;
    public float strideAmount;
}
