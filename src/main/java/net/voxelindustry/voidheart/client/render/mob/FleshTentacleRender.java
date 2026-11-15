package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.sanctum.FleshTentacle;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An eruption first shows as the floor's flesh swelling where it will come up, then the tentacle bursting up in a curl and sinking back. A grab is
 * a tentacle reaching from the gash to its victim, wrapped twice round their middle.
 */
public class FleshTentacleRender extends EntityRenderer<FleshTentacle, FleshTentacleRender.State>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/flesh_tentacle.png");
    private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
    private static final int ERUPT_SEGMENTS = 9;
    private static final int GRAB_SEGMENTS = 14;
    private static final int WRAP_SEGMENTS = 10;

    public static class State extends EntityRenderState
    {
        boolean grab;
        float age;
        int seed;
        /** For a grab, where its root and its victim's middle are, from the entity. */
        Vec3 root = Vec3.ZERO;
        Vec3 victim = Vec3.ZERO;
        float victimWidth;
        boolean hasVictim;
    }

    public FleshTentacleRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(FleshTentacle tentacle, State state, float partialTicks)
    {
        super.extractRenderState(tentacle, state, partialTicks);
        state.grab = tentacle.isGrab();
        state.age = tentacle.tickCount + partialTicks;
        state.seed = tentacle.getId();
        var mass = tentacle.owner();
        var victim = tentacle.victim();
        var at = tentacle.getPosition(partialTicks);
        state.hasVictim = state.grab && mass != null && victim != null;
        if (state.hasVictim)
        {
            var mouth = mass.mouth();
            state.root = mouth.subtract(at);
            var waist = victim.getPosition(partialTicks).add(0, victim.getBbHeight() * 0.45, 0);
            state.victim = waist.subtract(at);
            state.victimWidth = victim.getBbWidth();
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(FleshTentacle tentacle, float partialTicks)
    {
        var mass = tentacle.owner();
        var box = super.getBoundingBoxForCulling(tentacle, partialTicks).inflate(1.5, FleshTentacle.ERUPT_HEIGHT, 1.5);
        return mass == null ? box : box.minmax(mass.getBoundingBox());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        if (state.grab)
        {
            if (state.hasVictim)
                collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> grab(pose, buffer, state));
            return;
        }
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> erupt(pose, buffer, state));
    }

    private static void erupt(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, State state)
    {
        float age = state.age;
        float swell = Math.min(1, age / SanctumBalance.ERUPT_WARN_TICKS);
        float risen = FleshTentacle.risen(age);
        // The floor's flesh heaves where it will come up, faster as it nears
        if (risen <= 0)
        {
            float pulse = 0.5F + 0.5F * Mth.sin(age * (0.4F + swell * 0.6F));
            float height = 0.06F + swell * 0.18F * (0.7F + 0.3F * pulse);
            float half = 0.35F + swell * 0.35F;
            FleshTubes.box(pose, buffer, new Vec3(0, -0.05, 0), new Vec3(0, height, 0), half, half * 0.7F, state.seed, state.lightCoords);
            return;
        }
        float height = (float) FleshTentacle.ERUPT_HEIGHT * risen;
        var points = new ArrayList<Vec3>();
        for (int i = 0; i <= ERUPT_SEGMENTS; i++)
        {
            float t = (float) i / ERUPT_SEGMENTS;
            // It rises straight, then its upper half curls over like a hook
            float curl = t * t * 1.4F;
            double x = Math.sin(curl + state.seed) * 0.5 * t + Math.sin(age * 0.3F + t * 4) * 0.05;
            double z = Math.cos(curl + state.seed) * 0.5 * t - 0.5 * t * (state.seed % 2 == 0 ? 1 : -1) * t;
            points.add(new Vec3(x * risen, -0.3 + t * height, z * risen));
        }
        FleshTubes.tube(pose, buffer, points, 0.32F, 0.08F, 0.3F, state.lightCoords);
        FleshTubes.box(pose, buffer, new Vec3(0, -0.05, 0), new Vec3(0, 0.25, 0), 0.55F, 0.35F, state.seed, state.lightCoords);
    }

    private static void grab(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, State state)
    {
        float reached = FleshTentacle.reached(state.age);
        var root = state.root;
        var tip = root.add(state.victim.subtract(root).scale(reached));
        var points = new ArrayList<Vec3>();
        var span = tip.subtract(root);
        for (int i = 0; i <= GRAB_SEGMENTS; i++)
        {
            float t = (float) i / GRAB_SEGMENTS;
            // Sags and writhes along the way, pinned at both ends
            double sag = Math.sin(t * Math.PI) * (0.6 + 0.15 * Math.sin(state.age * 0.5F));
            double wave = Math.sin(t * Math.PI * 3 + state.age * 0.6F) * 0.12 * Math.sin(t * Math.PI);
            var side = span.cross(new Vec3(0, 1, 0)).normalize().scale(wave);
            points.add(root.add(span.scale(t)).add(0, -sag * reached, 0).add(side));
        }
        FleshTubes.tube(pose, buffer, points, 0.24F, 0.14F, 0.25F, state.lightCoords);
        if (reached < 1)
            return;
        // Wrapped twice round the victim's middle, rising as it goes
        List<Vec3> wrap = new ArrayList<>();
        double radius = state.victimWidth * 0.62 + 0.08;
        double start = Math.atan2(-span.z, -span.x);
        for (int i = 0; i <= WRAP_SEGMENTS * 2; i++)
        {
            double angle = start + i * Math.PI / WRAP_SEGMENTS;
            wrap.add(state.victim.add(Math.cos(angle) * radius, -0.35 + i * 0.035, Math.sin(angle) * radius));
        }
        FleshTubes.tube(pose, buffer, wrap, 0.14F, 0.08F, 0.2F, state.lightCoords);
    }
}
