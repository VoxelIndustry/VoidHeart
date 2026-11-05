package net.voxelindustry.voidheart.client.render.mob;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.model.mob.ArcaneEyeModel;
import net.voxelindustry.voidheart.client.model.mob.BarnacleGrowthModel;
import net.voxelindustry.voidheart.client.model.mob.CorruptedBarnacleModel;
import net.voxelindustry.voidheart.client.model.mob.CorruptedCrabModel;
import net.voxelindustry.voidheart.client.model.mob.EyeMawModel;
import net.voxelindustry.voidheart.client.model.mob.FallenElderModel;
import net.voxelindustry.voidheart.client.model.mob.FallenModel;
import net.voxelindustry.voidheart.client.model.mob.VoidGhoulModel;
import net.voxelindustry.voidheart.client.model.mob.VoidSkeletonModel;
import net.voxelindustry.voidheart.client.model.mob.WraithModel;
import net.voxelindustry.voidheart.common.content.mob.CorruptedCrab;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class VoidMobRenderers
{
    private VoidMobRenderers()
    {
    }

    public static void register()
    {
        ModelLayerRegistry.registerModelLayer(VoidSkeletonModel.LAYER, VoidSkeletonModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(VoidGhoulModel.LAYER, VoidGhoulModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(WraithModel.LAYER, WraithModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(CorruptedCrabModel.LAYER, CorruptedCrabModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(CorruptedBarnacleModel.LAYER, CorruptedBarnacleModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(BarnacleGrowthModel.LAYER, BarnacleGrowthModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(FallenModel.NOVICE, FallenModel::createNoviceLayer);
        ModelLayerRegistry.registerModelLayer(FallenModel.ADEPT, FallenModel::createAdeptLayer);
        ModelLayerRegistry.registerModelLayer(FallenElderModel.LAYER, FallenElderModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(EyeMawModel.SMALL, EyeMawModel::createSmallLayer);
        ModelLayerRegistry.registerModelLayer(EyeMawModel.LARGE, EyeMawModel::createLargeLayer);
        ModelLayerRegistry.registerModelLayer(ArcaneEyeModel.LAYER, ArcaneEyeModel::createBodyLayer);

        EntityRenderers.register(VoidHeartEntities.VOID_SKELETON, VoidSkeletonRender::new);
        EntityRenderers.register(VoidHeartEntities.VOID_GHOUL, VoidGhoulRender::new);
        EntityRenderers.register(VoidHeartEntities.WRAITH, WraithRender::new);
        EntityRenderers.register(VoidHeartEntities.WRAITH_STAIN, WraithStainRender::new);
        EntityRenderers.register(VoidHeartEntities.CORRUPTED_CRAB_SMALL, crab(CorruptedCrab.Size.SMALL));
        EntityRenderers.register(VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, crab(CorruptedCrab.Size.MEDIUM));
        EntityRenderers.register(VoidHeartEntities.CORRUPTED_CRAB_LARGE, crab(CorruptedCrab.Size.LARGE));
        EntityRenderers.register(VoidHeartEntities.CORRUPTED_BARNACLE, CorruptedBarnacleRender::new);
        EntityRenderers.register(VoidHeartEntities.FALLEN_NOVICE,
                context -> new FallenRender<>(context, FallenModel.NOVICE, texture("fallen_novice"), null));
        EntityRenderers.register(VoidHeartEntities.FALLEN_ADEPT,
                context -> new FallenRender<>(context, FallenModel.ADEPT, texture("fallen_adept"), texture("fallen_adept_glow")));
        EntityRenderers.register(VoidHeartEntities.FALLEN_ELDER, FallenElderRender::new);
        EntityRenderers.register(VoidHeartEntities.VOID_ORB, context -> new GlowQuadRender<>(context, texture("void_orb"), 0.42F));
        EntityRenderers.register(VoidHeartEntities.EYE_MAW,
                context -> new EyeMawRender<>(context, EyeMawModel.SMALL, 1, texture("eye_maw"), texture("eye_maw_glow"), 1, 0.3F));
        EntityRenderers.register(VoidHeartEntities.LARGE_EYE_MAW,
                context -> new EyeMawRender<>(context, EyeMawModel.LARGE, 2, texture("large_eye_maw"), texture("large_eye_maw_glow"), 2.25F, 1.1F));
        EntityRenderers.register(VoidHeartEntities.ARCANE_EYE, ArcaneEyeRender::new);
        EntityRenderers.register(VoidHeartEntities.ARCANE_BOLT, context -> new GlowQuadRender<>(context, texture("arcane_bolt"), 0.24F));

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) ->
        {
            if (renderer instanceof AvatarRenderer<?> avatar)
                helper.register(new BarnacleGrowthLayer(avatar, context.bakeLayer(BarnacleGrowthModel.LAYER)));
        });
    }

    private static EntityRendererProvider<CorruptedCrab> crab(CorruptedCrab.Size size)
    {
        return context -> new CorruptedCrabRender(context, size);
    }

    private static Identifier texture(String name)
    {
        return Identifier.fromNamespaceAndPath(MODID, "textures/entity/" + name + ".png");
    }
}
