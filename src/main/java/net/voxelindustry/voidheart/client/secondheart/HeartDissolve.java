package net.voxelindustry.voidheart.client.secondheart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.mixin.client.RenderSetupTexturesAccessor;
import net.voxelindustry.voidheart.mixin.client.RenderTypeStateAccessor;
import net.voxelindustry.voidheart.mixin.client.TextureBindingAccessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The second heart's dissolve and reform on a player's model, armour and held items, see {@code second_heart_dissolve.fsh}. A front runs from
 * the wound across the body, leaving nothing behind it, with motes lifting off; the reform runs it back from the chest, motes streaming in.
 * <p>
 * Render type: vanilla's entity cutout with the dissolve's own shaders, opaque and writing depth, one per texture and draw slot. The shader needs
 * the wound and the body's place in the draw's own space, which no vertex carries, so each draw slot's texture matrix holds them, written as the
 * player is submitted and read when its layers draw later in the same frame. Glint and outline layers are left out while dissolving.
 * <p>
 * Per frame: the player's usual geometry through one more pipeline, and about three motes a tick per dissolving player.
 */
public final class HeartDissolve
{
    public static final RenderStateDataKey<Dissolve> DISSOLVE = RenderStateDataKey.create(() -> "voidheart:second_heart_dissolve");

    /** How far beyond 1 the front runs, so its rim has left the body when the dissolve ends. */
    private static final float RIM_OVERRUN  = 0.08F;
    /** Matches {@code REACH} in the shader: blocks from the origin that count as the far end of the body. */
    private static final float REACH        = 2.0F;
    private static final int   SLOTS        = 8;

    private static final int   MOTES_PER_TICK = 4;
    private static final int   MOTE_TRIES     = 24;
    private static final float MOTE_BAND      = 0.12F;
    private static final double MOTE_LIFT     = 0.035;
    private static final double MOTE_SPREAD   = 0.025;
    private static final double MOTE_INFALL   = 0.45;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/second_heart_dissolve"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/second_heart_dissolve"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/second_heart_dissolve"))
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    private static final Matrix4f[]         SLOT_MATRICES   = new Matrix4f[SLOTS];
    private static final TextureTransform[] SLOT_TRANSFORMS = new TextureTransform[SLOTS];
    private static final Map<Identifier, RenderType[]> TYPES = new HashMap<>();
    private static int nextSlot;

    static
    {
        for (int i = 0; i < SLOTS; i++)
        {
            var matrix = SLOT_MATRICES[i] = new Matrix4f().zero();
            SLOT_TRANSFORMS[i] = new TextureTransform("voidheart_second_heart_" + i, () -> new Matrix4f(matrix));
        }
    }

    private HeartDissolve()
    {
    }

    /**
     * @param front  how far the front has run, 0 to just past 1
     * @param reform whether the body is coming back rather than going
     * @param origin where the front starts, from the player's feet along world axes
     */
    public record Dissolve(float front, boolean reform, Vec3 origin, float seed)
    {
    }

    public static @Nullable Dissolve extract(Entity entity, float partialTick)
    {
        if (!(entity instanceof Player player))
            return null;
        var sequence = SecondHeart.sequence(player);
        if (sequence == null)
            return null;
        float elapsed = sequence.elapsed(player.level().getGameTime(), partialTick);
        if (elapsed < 0 || elapsed >= HeartRules.BURST_TICK)
            return null;
        float seed = (sequence.start() % 997) * 0.37F;
        if (elapsed < HeartRules.DISSOLVE_TICKS)
            return new Dissolve(elapsed / HeartRules.DISSOLVE_TICKS * (1 + RIM_OVERRUN), false, sequence.wound(), seed);
        float reform = (elapsed - HeartRules.DISSOLVE_TICKS) / HeartRules.REFORM_TICKS;
        return new Dissolve(reform * (1 + RIM_OVERRUN), true, chest(player), seed);
    }

    private static Vec3 chest(Player player)
    {
        return new Vec3(0, player.getBbHeight() * 0.6, 0);
    }

    /**
     * Called as the renderer starts, while the pose stack holds only the entity's place in the draw.
     */
    public static SubmitNodeCollector wrap(SubmitNodeCollector collector, LivingEntityRenderState state, PoseStack poseStack)
    {
        var dissolve = state.getData(DISSOLVE);
        if (dissolve == null)
            return collector;

        int slot = nextSlot;
        nextSlot = (nextSlot + 1) % SLOTS;
        var pose = poseStack.last().pose();
        var anchor = pose.transformPosition(new Vector3f());
        var origin = pose.transformPosition(new Vector3f((float) dissolve.origin().x, (float) dissolve.origin().y, (float) dissolve.origin().z));
        float unitsPerBlock = pose.getColumn(0, new Vector3f()).length();

        var matrix = SLOT_MATRICES[slot];
        matrix.zero();
        matrix.setColumn(0, new org.joml.Vector4f(origin, dissolve.front()));
        matrix.setColumn(1, new org.joml.Vector4f(anchor, unitsPerBlock));
        matrix.setColumn(2, new org.joml.Vector4f(dissolve.seed(), dissolve.reform() ? 1 : 0, 0, 0));
        return new DissolvingCollector(collector, slot);
    }

    /**
     * @return the dissolve's render type drawing what this one draws, or null to leave a layer out while dissolving
     */
    static @Nullable RenderType dissolveType(RenderType original, int slot)
    {
        var texture = textureOf(original);
        if (texture == null)
            return null;
        var types = TYPES.computeIfAbsent(texture, key -> new RenderType[SLOTS]);
        if (types[slot] == null)
            types[slot] = create(texture, slot);
        return types[slot];
    }

    private static RenderType create(Identifier texture, int slot)
    {
        return RenderType.create(MODID + "_second_heart_dissolve", RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .setTextureTransform(SLOT_TRANSFORMS[slot])
                .createRenderSetup());
    }

    private static @Nullable Identifier textureOf(RenderType type)
    {
        if (type.isOutline() || type.pipeline().getLocation().getPath().contains("glint"))
            return null;
        var setup = (RenderSetupTexturesAccessor) (Object) ((RenderTypeStateAccessor) type).voidheart$state();
        var binding = setup.voidheart$textures().get("Sampler0");
        if (binding == null)
            return null;
        var texture = ((TextureBindingAccessor) binding).voidheart$location();
        return texture.getPath().contains("glint") ? null : texture;
    }

    /**
     * Motes lift off the front as the body goes, and stream in toward it as the body comes back.
     */
    public static void tickMotes(Minecraft minecraft)
    {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused())
            return;
        var random = level.getRandom();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (var player : level.players())
        {
            // The player's own motes would fly in their face, and their view is going dark anyway
            if (firstPerson && player == minecraft.getCameraEntity())
                continue;
            var dissolve = extract(player, 0);
            if (dissolve == null)
                continue;
            var feet = player.position();
            float width = player.getBbWidth(), height = player.getBbHeight();
            int spawned = 0;
            for (int i = 0; i < MOTE_TRIES && spawned < MOTES_PER_TICK; i++)
            {
                var local = new Vec3((random.nextFloat() - 0.5F) * width, random.nextFloat() * height, (random.nextFloat() - 0.5F) * width);
                float at = (float) Mth.clamp(local.distanceTo(dissolve.origin()) / REACH, 0, 1) * 0.72F + random.nextFloat() * 0.28F;
                if (Math.abs(at - dissolve.front()) > MOTE_BAND)
                    continue;
                spawned++;
                var at3 = feet.add(local);
                var out = new Vec3(local.x, 0, local.z);
                out = out.lengthSqr() > 1.0E-4 ? out.normalize() : Vec3.ZERO;
                if (!dissolve.reform())
                    level.addParticle(VoidHeart.SECOND_HEART_MOTE_PARTICLE, at3.x, at3.y, at3.z,
                            out.x * MOTE_SPREAD + (random.nextFloat() - 0.5F) * MOTE_SPREAD, MOTE_LIFT * (0.5 + random.nextFloat()),
                            out.z * MOTE_SPREAD + (random.nextFloat() - 0.5F) * MOTE_SPREAD);
                else
                {
                    // Starts out from the body and falls in to where the front is rebuilding it
                    var from = at3.add(out.scale(MOTE_INFALL)).add(0, MOTE_INFALL * (random.nextFloat() - 0.3F), 0);
                    var toward = at3.subtract(from).scale(1.0 / HeartMoteParticle.INFALL_TICKS);
                    level.addParticle(VoidHeart.SECOND_HEART_INFALL_PARTICLE, from.x, from.y, from.z, toward.x, toward.y, toward.z);
                }
            }
        }
    }
}
