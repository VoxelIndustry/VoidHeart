package net.voxelindustry.voidheart.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.RavenousCollectorModel;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;

public class RavenousCollectorRender extends GeoBlockRenderer<RavenousCollectorTile, BlockEntityRenderState>
{
    public RavenousCollectorRender(BlockEntityRendererProvider.Context context)
    {
        super(context, new RavenousCollectorModel());
        withRenderLayer(new PlasmaLoopLayer(this));
    }

    /**
     * Plasma loops arching out of the collector core and falling back into it, like solar prominences. The fuller the
     * collector, the more numerous, taller and brighter they are.
     */
    public static class PlasmaLoopLayer extends GeoRenderLayer<RavenousCollectorTile, Void, BlockEntityRenderState>
    {
        private static final DataTicket<Plasma> PLASMA = DataTicket.create("voidheart_collector_plasma", Plasma.class);

        private static final int   MAX_LOOPS   = 9;
        private static final int   LOOP_STEPS  = 14;
        // Loops are rooted inside the model and must arch beyond the corner cubes, which reach about 0.5 once animated
        private static final float CORE_RADIUS  = 0.2F;
        private static final float MIN_HEIGHT   = 0.4F;
        private static final float EXTRA_HEIGHT = 0.25F;
        private static final Vec3  CORE_CENTER = new Vec3(0, 0.5, 0);

        public record Plasma(float fill, int color, long seed, float time)
        {
        }

        public PlasmaLoopLayer(GeoRenderer<RavenousCollectorTile, Void, BlockEntityRenderState> renderer)
        {
            super(renderer);
        }

        @Override
        public void addRenderData(RavenousCollectorTile collector, Void relatedObject, BlockEntityRenderState renderState, float partialTick)
        {
            var storage = collector.storage();
            if (storage.isUnattuned() || storage.amount() <= 0 || collector.getLevel() == null)
                return;

            renderState.addGeckolibData(PLASMA, new Plasma(
                    storage.amount() / (float) storage.maximumAmount(),
                    VoidBeamRender.aspectColor(storage.type()),
                    collector.getBlockPos().asLong(),
                    collector.getLevel().getGameTime() + partialTick));
        }

        @Override
        public void submitRenderTask(RenderPassInfo<BlockEntityRenderState> renderPassInfo, SubmitNodeCollector collector)
        {
            var plasma = renderPassInfo.getOrDefaultGeckolibData(PLASMA, null);
            if (plasma == null)
                return;

            collector.submitCustomGeometry(renderPassInfo.poseStack(), RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true), (pose, buffer) ->
            {
                var loops = 1 + Mth.ceil(plasma.fill() * (MAX_LOOPS - 1));
                for (var i = 0; i < loops; i++)
                    renderLoop(pose, buffer, plasma, i);
            });
        }

        /**
         * Each loop lives one cycle: it erupts, arches and settles back, then is reborn elsewhere on the core.
         */
        private static void renderLoop(PoseStack.Pose pose, VertexConsumer buffer, Plasma plasma, int index)
        {
            var time = plasma.time();
            var period = 70 + 60 * noise(plasma.seed(), index, 0, 0);
            var cycleTime = time / period + noise(plasma.seed(), index, 0, 1);
            var cycle = Mth.floor(cycleTime);
            var life = Mth.sin(Mth.PI * (cycleTime - cycle));
            if (life <= 0.01F)
                return;

            // Foot points on the core, around a slowly drifting normal
            var yaw = Mth.TWO_PI * noise(plasma.seed(), index, cycle, 2) + time * 0.004F;
            var pitch = (float) Math.acos(2 * noise(plasma.seed(), index, cycle, 3) - 1);
            var normal = new Vec3(Mth.sin(pitch) * Mth.cos(yaw), Mth.cos(pitch), Mth.sin(pitch) * Mth.sin(yaw));
            var reference = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            var roll = Mth.TWO_PI * noise(plasma.seed(), index, cycle, 4);
            var u = normal.cross(reference).normalize();
            var w = normal.cross(u);
            var tangent = u.scale(Mth.cos(roll)).add(w.scale(Mth.sin(roll)));
            var binormal = normal.cross(tangent);

            var spread = 0.35F + 0.5F * noise(plasma.seed(), index, cycle, 5);
            // The arch top sits about CORE_RADIUS + 1.05 * height from the center: never less than 0.55, clear of the corners.
            // Loops only swell slightly as they live and fade in and out, so they never sink back through the model
            var height = (MIN_HEIGHT + EXTRA_HEIGHT * plasma.fill()) * (0.85F + 0.15F * noise(plasma.seed(), index, cycle, 6)) * (0.92F + 0.08F * life);

            var start = CORE_CENTER.add(normal.scale(CORE_RADIUS * Mth.cos(spread))).add(tangent.scale(CORE_RADIUS * Mth.sin(spread)));
            var end = CORE_CENTER.add(normal.scale(CORE_RADIUS * Mth.cos(spread))).subtract(tangent.scale(CORE_RADIUS * Mth.sin(spread)));
            var startControl = start.add(normal.scale(1.4 * height)).add(tangent.scale(0.1 * height))
                    .add(binormal.scale(0.25 * height * Mth.sin(time * 0.11F + index)));
            var endControl = end.add(normal.scale(1.4 * height)).subtract(tangent.scale(0.1 * height))
                    .subtract(binormal.scale(0.25 * height * Mth.sin(time * 0.09F + index * 2)));

            var strength = 0.25F + 0.55F * plasma.fill();
            var width = 0.012F + 0.025F * plasma.fill();

            // A bright thread inside a faint glow
            renderArc(pose, buffer, plasma, start, startControl, endControl, end, width, strength * life, 0);
            renderArc(pose, buffer, plasma, start, startControl, endControl, end, width * 2.5F, strength * life * 0.3F, index);
        }

        private static void renderArc(PoseStack.Pose pose, VertexConsumer buffer, Plasma plasma, Vec3 start, Vec3 startControl, Vec3 endControl, Vec3 end,
                                      float width, float alpha, int offset)
        {
            float[][] previous = null;
            for (var j = 0; j <= LOOP_STEPS; j++)
            {
                var t = j / (float) LOOP_STEPS;
                var inverse = 1 - t;
                var point = start.scale(inverse * inverse * inverse)
                        .add(startControl.scale(3 * inverse * inverse * t))
                        .add(endControl.scale(3 * inverse * t * t))
                        .add(end.scale(t * t * t));
                var tangent = startControl.subtract(start).scale(inverse * inverse)
                        .add(endControl.subtract(startControl).scale(2 * inverse * t))
                        .add(end.subtract(endControl).scale(t * t));

                // Thicker and brighter at the top of the arch, thin where it is rooted in the core
                var bulge = Mth.sin(Mth.PI * t);
                var current = VoidBeamRender.crossedSection(point, tangent, width * (0.5F + 0.5F * bulge), alpha * (0.6F + 0.4F * bulge),
                        2 * t - plasma.time() * 0.06F + offset * 0.31F);
                if (previous != null)
                    VoidBeamRender.crossedStrip(pose, buffer, plasma.color(), previous, current);
                previous = current;
            }
        }

        private static float noise(long seed, int index, int cycle, int channel)
        {
            // SplitMix64 finalizer
            var hash = seed * 31 + index * 0x9E3779B97F4A7C15L + cycle * 0x632BE59BD9B4E019L + channel;
            hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
            hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
            hash ^= hash >>> 31;
            return (hash >>> 40) / (float) (1L << 24);
        }
    }
}
