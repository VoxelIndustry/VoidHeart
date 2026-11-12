package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.voxelindustry.voidheart.common.content.underway.KeyDefence;

public class UnderwayKeyRenderState extends EntityRenderState
{
    public final ItemStackRenderState key = new ItemStackRenderState();
    public float turn;
    public float charge;
    public boolean opening;
    public float rise;
    public final float[] paneAlpha = new float[KeyDefence.PANES];
    public final float[] paneFlare = new float[KeyDefence.PANES];
}
