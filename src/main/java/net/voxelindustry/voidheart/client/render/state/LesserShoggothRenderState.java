package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class LesserShoggothRenderState extends LivingEntityRenderState
{
    /** How far through its lash it is, 0 to 1, or below 0 when not lashing. */
    public float lash = -1;
    /** What keeps one shoggoth's eyes and mouths welling up apart from another's. */
    public int seed;
    public float moving;
    public boolean feeding;
}
