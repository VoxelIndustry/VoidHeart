package net.voxelindustry.voidheart.client.model.monolith;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.client.model.ForwardingUnbakedModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Arrays.asList;

public class VoidMonolithUnbakedModel extends ForwardingUnbakedModel
{
    public VoidMonolithUnbakedModel(UnbakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    @Override
    public Collection<SpriteIdentifier> getTextureDependencies(Function<Identifier, UnbakedModel> unbakedModelGetter, Set<Pair<String, String>> unresolvedTextureReferences)
    {
        Collection<SpriteIdentifier> dependencies = super.getTextureDependencies(unbakedModelGetter, unresolvedTextureReferences);

        List<SpriteIdentifier> sprites = new ArrayList<>();
        sprites.addAll(asList(VoidMonolithSpriteManager.getMonolithSprites()));
        sprites.addAll(asList(VoidMonolithSpriteManager.getGlyphSprites()));
        dependencies.addAll(sprites);
        return dependencies;
    }

    @Override
    public BakedModel bake(ModelLoader loader, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId)
    {
        return new VoidMonolithBakedModel(super.bake(loader, textureGetter, rotationContainer, modelId));
    }
}