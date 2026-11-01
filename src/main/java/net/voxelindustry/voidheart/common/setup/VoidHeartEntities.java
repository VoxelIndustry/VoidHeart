package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartEntities
{
    public static EntityType<ShoulderEyeEntity> SHOULDER_EYE;

    public static void registerEntities()
    {
        SHOULDER_EYE = register("shoulder_eye", EntityType.Builder.of(ShoulderEyeEntity::new, MobCategory.MISC)
                .sized(ShoulderEyeEntity.SIZE, ShoulderEyeEntity.SIZE)
                .eyeHeight(ShoulderEyeEntity.SIZE / 2)
                .clientTrackingRange(8)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .noSummon()
                .noLootTable());
        FabricDefaultAttributeRegistry.register(SHOULDER_EYE, ShoulderEyeEntity.createAttributes());
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder)
    {
        var key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
