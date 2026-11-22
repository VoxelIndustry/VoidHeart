package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class FloatingItemRenderState extends BlockEntityRenderState
{
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float worldTime;
    public double height;
    /** How far the item has risen out of an Officiant's fold, 1 at rest. */
    public float rise = 1;
}
