package net.voxelindustry.voidheart.client.model.conduit;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.function.Function;

public class ConduitUnbakedModel extends ForwardingUnbakedModel
{
    public ConduitUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId)
    {
        return new ConduitBakedModel(super.bake(baker, textureGetter, rotationContainer, modelId));
    }
}