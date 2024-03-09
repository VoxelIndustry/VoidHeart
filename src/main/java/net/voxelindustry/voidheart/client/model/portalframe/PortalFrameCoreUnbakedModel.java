package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.function.Function;

public class PortalFrameCoreUnbakedModel extends ForwardingUnbakedModel
{
    public PortalFrameCoreUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId)
    {
        return new PortalFrameCoreBakedModel(super.bake(baker, textureGetter, rotationContainer, modelId), textureGetter);
    }
}