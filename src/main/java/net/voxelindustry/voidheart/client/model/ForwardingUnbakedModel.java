package net.voxelindustry.voidheart.client.model;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.function.Function;

public abstract class ForwardingUnbakedModel implements UnbakedModel
{
    protected UnbakedModel wrapped;

    @Override
    public Collection<Identifier> getModelDependencies()
    {
        return wrapped.getModelDependencies();
    }

    @Override
    public void setParents(Function<Identifier, UnbakedModel> modelLoader)
    {
        wrapped.setParents(modelLoader);
    }

    @Override
    public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId)
    {
        return wrapped.bake(baker, textureGetter, rotationContainer, modelId);
    }
}