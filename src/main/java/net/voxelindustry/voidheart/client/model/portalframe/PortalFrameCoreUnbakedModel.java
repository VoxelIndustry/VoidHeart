package net.voxelindustry.voidheart.client.model.portalframe;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public class PortalFrameCoreUnbakedModel extends ForwardingUnbakedModel
{
    public PortalFrameCoreUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public Collection<SpriteIdentifier> getTextureDependencies(Function<Identifier, UnbakedModel> unbakedModelGetter, Set<Pair<String, String>> unresolvedTextureReferences)
    {
        Collection<SpriteIdentifier> dependencies = super.getTextureDependencies(unbakedModelGetter, unresolvedTextureReferences);
        dependencies.addAll(Arrays.asList(PortalFrameVeinSpriteManager.getFrameSpriteIdentifiers()));
        return dependencies;
    }

    @Override
    public BakedModel bake(ModelLoader loader, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId)
    {
        return new PortalFrameCoreBakedModel(super.bake(loader, textureGetter, rotationContainer, modelId), textureGetter);
    }
}