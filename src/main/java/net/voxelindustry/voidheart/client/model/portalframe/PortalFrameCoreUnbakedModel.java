package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.function.Function;

public class PortalFrameCoreUnbakedModel extends ForwardingUnbakedModel
{
    public PortalFrameCoreUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState rotationContainer, Identifier modelId)
    {
        return new PortalFrameCoreBakedModel(super.bake(baker, textureGetter, rotationContainer, modelId), textureGetter);
    }
}