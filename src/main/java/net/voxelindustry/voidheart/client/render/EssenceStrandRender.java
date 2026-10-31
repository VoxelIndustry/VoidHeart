package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Writhing strands carrying essence from a source into a target, ripples travelling along them toward the target.
 */
public final class EssenceStrandRender
{
    private static final int STRAND_STEPS = 16;

    /**
     * @param rootInset    distance from the source center to where the strands are rooted, toward the target
     * @param rootSpread   radius of the ring the strands are rooted on
     * @param targetSpread radius of the ring the strands converge on inside the target
     * @param growth       0 when retracted into the source, 1 when reaching the target
     */
    public record Bundle(Vec3 source, Vec3 target, float rootInset, float rootSpread, float targetSpread, float growth, int color, long seed,
                         int strands)
    {
    }

    private EssenceStrandRender()
    {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer buffer, Bundle bundle, float time)
    {
        for (var i = 0; i < bundle.strands(); i++)
            renderStrand(pose, buffer, bundle, i, time);
    }

    private static void renderStrand(PoseStack.Pose pose, VertexConsumer buffer, Bundle bundle, int index, float time)
    {
        var toTarget = bundle.target().subtract(bundle.source());
        var length = (float) toTarget.length();
        var direction = toTarget.normalize();
        var side = direction.cross(Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        var other = direction.cross(side);

        var rootAngle = Mth.TWO_PI * index / bundle.strands() + RavenousCollectorRender.PlasmaLoopLayer.noise(bundle.seed(), index, 0, 0);
        var anchor = bundle.source()
                .add(direction.scale(bundle.rootInset()))
                .add(side.scale(bundle.rootSpread() * Mth.cos(rootAngle)))
                .add(other.scale(bundle.rootSpread() * Mth.sin(rootAngle)));
        var target = bundle.target()
                .add(side.scale(bundle.targetSpread() * Mth.cos(rootAngle)))
                .add(other.scale(bundle.targetSpread() * Mth.sin(rootAngle)));

        // Writhing: each strand bows sideways and its bow slowly rolls around the axis
        var bowAngle = rootAngle + time * (0.05F + 0.03F * RavenousCollectorRender.PlasmaLoopLayer.noise(bundle.seed(), index, 0, 1));
        var bow = side.scale(Mth.cos(bowAngle)).add(other.scale(Mth.sin(bowAngle))).scale(0.18 * length * (0.6 + 0.4 * Mth.sin(time * 0.13F + index)));
        var control = anchor.add(target).scale(0.5).add(bow);

        var growth = bundle.growth();
        var steps = Math.max(1, Mth.ceil(STRAND_STEPS * growth));
        float[][] previous = null;
        for (var j = 0; j <= steps; j++)
        {
            var u = growth * j / steps;
            var inverse = 1 - u;
            var point = anchor.scale(inverse * inverse).add(control.scale(2 * inverse * u)).add(target.scale(u * u));
            var tangent = control.subtract(anchor).scale(inverse).add(target.subtract(control).scale(u));

            // Ripples travel along the strand toward the target as essence is carried
            var ripple = 0.75F + 0.25F * Mth.sin(u * length * 4 - time * 0.4F + index);
            var halfWidth = Mth.lerp(u, 0.035F, 0.015F) * ripple;
            var alpha = 0.6F * ripple;
            if (growth < 1)
                alpha *= Mth.clamp((growth - u) / 0.12F, 0F, 1F);

            var current = VoidBeamRender.crossedSection(point, tangent, halfWidth, alpha, u * length - time * 0.12F + index * 0.37F);
            if (previous != null)
                VoidBeamRender.crossedStrip(pose, buffer, bundle.color(), previous, current);
            previous = current;
        }
    }
}
