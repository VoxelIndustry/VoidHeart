package net.voxelindustry.voidheart.client.model;

import java.util.Collection;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;

public abstract class ForwardingUnbakedModel implements UnbakedModel
{
    protected UnbakedModel wrapped;

    @Override
    public Collection<Identifier> getDependencies()
    {
        return wrapped.getDependencies();
    }

    @Override
    public void resolveParents(Function<Identifier, UnbakedModel> modelLoader)
    {
        wrapped.resolveParents(modelLoader);
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState rotationContainer, Identifier modelId)
    {
        return wrapped.bake(baker, textureGetter, rotationContainer, modelId);
    }
}