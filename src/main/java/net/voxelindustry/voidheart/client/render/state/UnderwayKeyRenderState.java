package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class UnderwayKeyRenderState extends EntityRenderState
{
    public final ItemStackRenderState key = new ItemStackRenderState();
    public float turn;
    public float charge;
    public boolean opening;
}
