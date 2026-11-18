package net.voxelindustry.voidheart.client.render.blight;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.blight.EclipseBolt;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/** The crossbow blight's bolt: an arrow's model in near-black, its head and fletching a dull violet. */
public class EclipseBoltRender extends ArrowRenderer<EclipseBolt, ArrowRenderState>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/eclipse_bolt.png");

    public EclipseBoltRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    protected Identifier getTextureLocation(ArrowRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public ArrowRenderState createRenderState()
    {
        return new ArrowRenderState();
    }
}
