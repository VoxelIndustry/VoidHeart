package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.concurrent.CompletableFuture;

import static net.voxelindustry.voidheart.common.setup.VoidHeartEntities.*;

public class EntityTypeTagsProvider extends FabricTagsProvider.EntityTypeTagsProvider
{
    public EntityTypeTagsProvider(FabricPackOutput output, CompletableFuture<Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(Provider registries)
    {
        builder(VoidHeartTags.MOBS)
                .addTag(VoidHeartTags.MOBS_VOID_DUNGEON)
                .addTag(VoidHeartTags.MOBS_DAEDALUS);

        tag(VoidHeartTags.MOBS_VOID_DUNGEON,
                VOID_SKELETON, VOID_GHOUL, WRAITH,
                CORRUPTED_CRAB_SMALL, CORRUPTED_CRAB_MEDIUM, CORRUPTED_CRAB_LARGE, CORRUPTED_BARNACLE,
                FALLEN_NOVICE, FALLEN_ADEPT, FALLEN_ELDER,
                EYE_MAW, LARGE_EYE_MAW, ARCANE_EYE, TREANT);
        tag(VoidHeartTags.MOBS_DAEDALUS,
                STALKING_WRAITH, FALLEN_BISHOP, MALIGNANT_BOULDER, LEGIONARY, CENTURION, KERNACIAN_DRAGOON);

        tag(VoidHeartTags.MOBS_FALLEN, FALLEN_NOVICE, FALLEN_ADEPT, FALLEN_ELDER, FALLEN_BISHOP);
        tag(VoidHeartTags.MOBS_WRAITHS, WRAITH, STALKING_WRAITH);
        tag(VoidHeartTags.MOBS_CRABS, CORRUPTED_CRAB_SMALL, CORRUPTED_CRAB_MEDIUM, CORRUPTED_CRAB_LARGE);
        tag(VoidHeartTags.MOBS_PESTS, CORRUPTED_BARNACLE)
                .addTag(VoidHeartTags.MOBS_CRABS);
        tag(VoidHeartTags.MOBS_EYES, EYE_MAW, LARGE_EYE_MAW, ARCANE_EYE);
        tag(VoidHeartTags.MOBS_CONSTRUCTS, MALIGNANT_BOULDER, LEGIONARY, CENTURION);
        tag(VoidHeartTags.MOBS_UNDEAD, VOID_SKELETON, VOID_GHOUL)
                .addTag(VoidHeartTags.MOBS_WRAITHS)
                .addTag(VoidHeartTags.MOBS_FALLEN);
        tag(VoidHeartTags.MOBS_BOSSES, KERNACIAN_DRAGOON);

        builder(EntityTypeTags.UNDEAD)
                .setReplace(false)
                .addTag(VoidHeartTags.MOBS_UNDEAD);
        builder(EntityTypeTags.ARTHROPOD)
                .setReplace(false)
                .addTag(VoidHeartTags.MOBS_PESTS);

        builder(VoidHeartTags.SALT_BURNED)
                .addTag(VoidHeartTags.MOBS_FALLEN);
        builder(VoidHeartTags.TORMENTER_REFUSES)
                .add(vanilla("ender_dragon"))
                .add(vanilla("wither"));
    }

    private TagAppender<EntityType<?>> tag(TagKey<EntityType<?>> tag, EntityType<?>... types)
    {
        var appender = builder(tag);
        for (var type : types)
            appender.add(BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).orElseThrow());
        return appender;
    }

    private static ResourceKey<EntityType<?>> vanilla(String name)
    {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.withDefaultNamespace(name));
    }
}
