package net.voxelindustry.voidheart.client.model.conduit;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.function.Function;

public class ConduitUnbakedModel extends ForwardingUnbakedModel
{
    public ConduitUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState rotationContainer, Identifier modelId)
    {
        return new ConduitBakedModel(super.bake(baker, textureGetter, rotationContainer, modelId));
    }
}