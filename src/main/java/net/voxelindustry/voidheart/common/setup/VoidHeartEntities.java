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
import net.voxelindustry.voidheart.common.content.mob.ArcaneBolt;
import net.voxelindustry.voidheart.common.content.mob.ArcaneEye;
import net.voxelindustry.voidheart.common.content.mob.Centurion;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.CorruptedCrab;
import net.voxelindustry.voidheart.common.content.mob.EyeMaw;
import net.voxelindustry.voidheart.common.content.mob.FallenAdept;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;
import net.voxelindustry.voidheart.common.content.mob.FallenElder;
import net.voxelindustry.voidheart.common.content.mob.FallenNovice;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.mob.LargeEyeMaw;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith;
import net.voxelindustry.voidheart.common.content.mob.WraithScarab;
import net.voxelindustry.voidheart.common.content.mob.VoidGhoul;
import net.voxelindustry.voidheart.common.content.mob.VoidOrb;
import net.voxelindustry.voidheart.common.content.mob.VoidSkeleton;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartEntities
{
    public static EntityType<ShoulderEyeEntity> SHOULDER_EYE;

    public static EntityType<VoidSkeleton> VOID_SKELETON;
    public static EntityType<VoidGhoul> VOID_GHOUL;
    public static EntityType<Wraith> WRAITH;
    public static EntityType<WraithStain> WRAITH_STAIN;
    public static EntityType<CorruptedCrab> CORRUPTED_CRAB_SMALL;
    public static EntityType<CorruptedCrab> CORRUPTED_CRAB_MEDIUM;
    public static EntityType<CorruptedCrab> CORRUPTED_CRAB_LARGE;
    public static EntityType<CorruptedBarnacle> CORRUPTED_BARNACLE;
    public static EntityType<FallenNovice> FALLEN_NOVICE;
    public static EntityType<FallenAdept> FALLEN_ADEPT;
    public static EntityType<FallenElder> FALLEN_ELDER;
    public static EntityType<VoidOrb> VOID_ORB;
    public static EntityType<EyeMaw> EYE_MAW;
    public static EntityType<LargeEyeMaw> LARGE_EYE_MAW;
    public static EntityType<ArcaneEye> ARCANE_EYE;
    public static EntityType<ArcaneBolt> ARCANE_BOLT;
    public static EntityType<StalkingWraith> STALKING_WRAITH;
    public static EntityType<WraithScarab> WRAITH_SCARAB;
    public static EntityType<FallenBishop> FALLEN_BISHOP;
    public static EntityType<MalignantBoulder> MALIGNANT_BOULDER;
    public static EntityType<Legionary> LEGIONARY;
    public static EntityType<Centurion> CENTURION;
    public static EntityType<KernacianDragoon> KERNACIAN_DRAGOON;

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

        VOID_SKELETON = register("void_skeleton", EntityType.Builder.of(VoidSkeleton::new, MobCategory.MONSTER)
                .sized(0.6F, 1.99F)
                .eyeHeight(1.74F)
                .clientTrackingRange(8));
        FabricDefaultAttributeRegistry.register(VOID_SKELETON, VoidSkeleton.createAttributes());

        VOID_GHOUL = register("void_ghoul", EntityType.Builder.of(VoidGhoul::new, MobCategory.MONSTER)
                .sized(0.6F, 1.75F)
                .eyeHeight(1.45F)
                .clientTrackingRange(8));
        FabricDefaultAttributeRegistry.register(VOID_GHOUL, VoidGhoul.createAttributes());

        WRAITH = register("wraith", EntityType.Builder.of(Wraith::new, MobCategory.MONSTER)
                .sized(0.6F, 2)
                .eyeHeight(1.75F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .fireImmune());
        FabricDefaultAttributeRegistry.register(WRAITH, Wraith.createAttributes());

        WRAITH_STAIN = register("wraith_stain", EntityType.Builder.<WraithStain>of(WraithStain::new, MobCategory.MISC)
                .sized(0.1F, 0.1F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .fireImmune()
                .noSave()
                .noSummon()
                .noLootTable());

        WRAITH_SCARAB = register("wraith_scarab", EntityType.Builder.of(WraithScarab::new, MobCategory.MONSTER)
                .sized(0.55F, 0.4F)
                .eyeHeight(0.3F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .fireImmune());
        FabricDefaultAttributeRegistry.register(WRAITH_SCARAB, WraithScarab.createAttributes());

        CORRUPTED_CRAB_SMALL = registerCrab("corrupted_crab_small", CorruptedCrab.Size.SMALL);
        CORRUPTED_CRAB_MEDIUM = registerCrab("corrupted_crab_medium", CorruptedCrab.Size.MEDIUM);
        CORRUPTED_CRAB_LARGE = registerCrab("corrupted_crab_large", CorruptedCrab.Size.LARGE);

        CORRUPTED_BARNACLE = register("corrupted_barnacle", EntityType.Builder.of(CorruptedBarnacle::new, MobCategory.MONSTER)
                .sized(0.5F, 0.35F)
                .eyeHeight(0.25F)
                .clientTrackingRange(8)
                .updateInterval(2));
        FabricDefaultAttributeRegistry.register(CORRUPTED_BARNACLE, CorruptedBarnacle.createAttributes());

        FALLEN_NOVICE = register("fallen_novice", EntityType.Builder.of(FallenNovice::new, MobCategory.MONSTER)
                .sized(0.5F, 1.4F)
                .eyeHeight(1.22F)
                .clientTrackingRange(8));
        FabricDefaultAttributeRegistry.register(FALLEN_NOVICE, FallenNovice.createAttributes());

        FALLEN_ADEPT = register("fallen_adept", EntityType.Builder.of(FallenAdept::new, MobCategory.MONSTER)
                .sized(0.6F, 1.45F)
                .eyeHeight(1.26F)
                .clientTrackingRange(8));
        FabricDefaultAttributeRegistry.register(FALLEN_ADEPT, FallenAdept.createAttributes());

        FALLEN_ELDER = register("fallen_elder", EntityType.Builder.of(FallenElder::new, MobCategory.MONSTER)
                .sized(0.9F, 2.6F)
                .eyeHeight(2.25F)
                .clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(FALLEN_ELDER, FallenElder.createAttributes());

        VOID_ORB = register("void_orb", EntityType.Builder.<VoidOrb>of(VoidOrb::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .noSummon()
                .noLootTable());

        registerEyes();
        registerDaedalusMobs();
    }

    private static void registerDaedalusMobs()
    {
        STALKING_WRAITH = register("stalking_wraith", EntityType.Builder.of(StalkingWraith::new, MobCategory.MONSTER)
                .sized(0.9F, 2.5F)
                .eyeHeight(2.2F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .fireImmune());
        FabricDefaultAttributeRegistry.register(STALKING_WRAITH, StalkingWraith.createAttributes());

        FALLEN_BISHOP = register("fallen_bishop", EntityType.Builder.of(FallenBishop::new, MobCategory.MONSTER)
                .sized(0.8F, 2.6F)
                .eyeHeight(2.05F)
                .clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(FALLEN_BISHOP, FallenBishop.createAttributes());

        MALIGNANT_BOULDER = register("malignant_boulder", EntityType.Builder.of(MalignantBoulder::new, MobCategory.MONSTER)
                .sized(1.9F, 2.9F)
                .eyeHeight(2.5F)
                .clientTrackingRange(10)
                .fireImmune());
        FabricDefaultAttributeRegistry.register(MALIGNANT_BOULDER, MalignantBoulder.createAttributes());

        LEGIONARY = register("legionary", EntityType.Builder.of(Legionary::new, MobCategory.MONSTER)
                .sized(0.7F, 2.2F)
                .eyeHeight(1.95F)
                .clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(LEGIONARY, Legionary.createAttributes());

        CENTURION = register("centurion", EntityType.Builder.of(Centurion::new, MobCategory.MONSTER)
                .sized(0.9F, 2.7F)
                .eyeHeight(2.4F)
                .clientTrackingRange(10));
        FabricDefaultAttributeRegistry.register(CENTURION, Centurion.createAttributes());

        KERNACIAN_DRAGOON = register("kernacian_dragoon", EntityType.Builder.of(KernacianDragoon::new, MobCategory.MONSTER)
                .sized(1, 2.6F)
                .eyeHeight(2.2F)
                .clientTrackingRange(10)
                .updateInterval(1));
        FabricDefaultAttributeRegistry.register(KERNACIAN_DRAGOON, KernacianDragoon.createAttributes());
    }

    private static void registerEyes()
    {
        EYE_MAW = register("eye_maw", EntityType.Builder.of(EyeMaw::new, MobCategory.MONSTER)
                .sized(0.6F, 0.6F)
                .eyeHeight(0.3F)
                .clientTrackingRange(8)
                .updateInterval(2));
        FabricDefaultAttributeRegistry.register(EYE_MAW, EyeMaw.createAttributes());

        LARGE_EYE_MAW = register("large_eye_maw", EntityType.Builder.of(LargeEyeMaw::new, MobCategory.MONSTER)
                .sized(2.25F, 2.25F)
                .eyeHeight(1.125F)
                .clientTrackingRange(10)
                .updateInterval(2));
        FabricDefaultAttributeRegistry.register(LARGE_EYE_MAW, LargeEyeMaw.createAttributes());

        ARCANE_EYE = register("arcane_eye", EntityType.Builder.of(ArcaneEye::new, MobCategory.MONSTER)
                .sized(0.75F, 0.75F)
                .eyeHeight(0.375F)
                .clientTrackingRange(8)
                .updateInterval(2));
        FabricDefaultAttributeRegistry.register(ARCANE_EYE, ArcaneEye.createAttributes());

        ARCANE_BOLT = register("arcane_bolt", EntityType.Builder.<ArcaneBolt>of(ArcaneBolt::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .noSummon()
                .noLootTable());
    }

    private static EntityType<CorruptedCrab> registerCrab(String name, CorruptedCrab.Size size)
    {
        var type = register(name, EntityType.Builder.<CorruptedCrab>of((entityType, level) -> new CorruptedCrab(entityType, level, size), MobCategory.MONSTER)
                .sized(size.width, size.height)
                .eyeHeight(size.height * 0.8F)
                .clientTrackingRange(8)
                .updateInterval(2));
        FabricDefaultAttributeRegistry.register(type, CorruptedCrab.createAttributes(size));
        return type;
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder)
    {
        var key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
