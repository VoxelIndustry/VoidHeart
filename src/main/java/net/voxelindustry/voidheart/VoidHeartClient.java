package net.voxelindustry.voidheart;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.voxelindustry.voidheart.client.model.VoidHeartModelLoadingPlugin;
import net.voxelindustry.voidheart.client.particle.AltarItemParticle;
import net.voxelindustry.voidheart.client.particle.AltarVoidFillingParticle;
import net.voxelindustry.voidheart.client.particle.GeometryParticle;
import net.voxelindustry.voidheart.client.particle.PortalFrameParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeItemParticle;
import net.voxelindustry.voidheart.client.particle.ShatterForgeRuneParticle;
import net.voxelindustry.voidheart.client.render.ExperienceSkullRender;
import net.voxelindustry.voidheart.client.render.EyeBottleRender;
import net.voxelindustry.voidheart.client.render.FloatingItemRender;
import net.voxelindustry.voidheart.client.render.RavenousCollectorRender;
import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRender;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.*;

public class VoidHeartClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ModelLoadingPlugin.register(new VoidHeartModelLoadingPlugin());

        BlockEntityRenderers.register(VoidHeartTiles.VOID_PILLAR, ctx -> new FloatingItemRender<>(ctx, VoidPillarTile::getStack, 1.1));
        BlockEntityRenderers.register(VoidHeartTiles.VOID_ALTAR, VoidAltarRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.VOID_HEART, VoidHeartRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EXPERIENCE_SKULL, ExperienceSkullRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.EYE_BOTTLE, EyeBottleRender::new);
        BlockEntityRenderers.register(VoidHeartTiles.SHATTER_FORGE, ctx -> new FloatingItemRender<>(ctx, ShatterForgeTile::getStack, 1));
        BlockEntityRenderers.register(VoidHeartTiles.RAVENOUS_COLLECTOR, RavenousCollectorRender::new);

        ParticleGroupRegistry.register(GeometryParticle.GEOMETRY, GeometryParticle.Group::new);

        var particles = ParticleProviderRegistry.getInstance();
        particles.register(
                ALTAR_VOID_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new AltarVoidFillingParticle(level, x, y, z, xAux, yAux, zAux, options.getSpeed(), spriteSet));

        particles.register(
                ALTAR_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new AltarItemParticle(level, x, y, z, xAux, yAux, zAux, options.getStack(), options.getFirstPointBezier(), options.getSecondPointBezier()));

        particles.register(
                PORTAL_FRAME_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new PortalFrameParticle(level, x, y, z, spriteSet, options.direction(), options.width(), options.height()));

        particles.register(
                SHATTER_FORGE_RUNE_PARTICLE,
                spriteSet ->
                        (options, level, x, y, z, xAux, yAux, zAux, random) ->
                                new ShatterForgeRuneParticle(level, x, y, z, spriteSet, options.axis()));

        particles.register(
                SHATTER_FORGE_ITEM_PARTICLE,
                (options, level, x, y, z, xAux, yAux, zAux, random) ->
                        new ShatterForgeItemParticle(level, x, y, z, xAux, yAux, zAux, options.input(), options.output()));
    }
}
