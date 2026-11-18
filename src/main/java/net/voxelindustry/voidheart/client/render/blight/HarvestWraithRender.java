package net.voxelindustry.voidheart.client.render.blight;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.common.content.blight.Harvest;
import net.voxelindustry.voidheart.common.content.blight.HarvestWraith;
import org.jspecify.annotations.Nullable;

import java.util.WeakHashMap;

/**
 * A raised wraith drawn as the mob it was raised from, through that mob's own renderer and a stand-in copy of it kept out of the world, which follows
 * the wraith's place, turns and stride. The living renderer then draws it as a husk of wraith ink, see {@code wraith_husk.fsh}. While it rises or
 * sinks it stands partly under the ground, which hides what is below, and frays more.
 */
public class HarvestWraithRender extends EntityRenderer<HarvestWraith, HarvestWraithRender.State>
{
    /** Set on the stand-in's render state: how much more it frays, and its height, for the husk to fray from the feet up. */
    public static final RenderStateDataKey<Husk> HUSK = RenderStateDataKey.create(() -> "voidheart:wraith_husk");

    /** How much more of it frays while it is all but under the ground. */
    private static final float BURIED_FRAY = 0.25F;

    public record Husk(float fray, float height)
    {
    }

    public static class State extends EntityRenderState
    {
        @Nullable EntityRenderState               inner;
        @Nullable EntityRenderer<?, ?>            renderer;
        float                                     sink;
    }

    /** Each stand-in holds the level it was made in, so they are all let go when the client changes level or leaves, see {@link #register()}. */
    private static final WeakHashMap<HarvestWraith, LivingEntity> STAND_INS = new WeakHashMap<>();

    public static void register()
    {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> STAND_INS.clear());
        // Disconnecting may be heard off the client thread, so the clearing waits for it, after the level is let go and no stand-in is being made
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(STAND_INS::clear));
    }

    public HarvestWraithRender(EntityRendererProvider.Context context)
    {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HarvestWraith wraith, State state, float partialTicks)
    {
        super.extractRenderState(wraith, state, partialTicks);
        state.inner = null;
        state.renderer = null;

        var standIn = standIn(wraith);
        if (standIn == null)
            return;
        follow(standIn, wraith);

        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(standIn);
        @SuppressWarnings("unchecked")
        var inner = ((EntityRenderer<LivingEntity, ?>) renderer).createRenderState(standIn, partialTicks);
        float buried = Harvest.buried(wraith.age() + partialTicks, wraith.sinkStart());
        if (inner instanceof LivingEntityRenderState living)
        {
            living.walkAnimationPos = wraith.walkAnimation.position(partialTicks);
            living.walkAnimationSpeed = wraith.walkAnimation.speed(partialTicks);
            living.hasRedOverlay = false;
            living.deathTime = 0;
        }
        state.sink = buried * wraith.getBbHeight();
        inner.y -= state.sink;
        inner.setData(HUSK, new Husk(buried * BURIED_FRAY, wraith.getBbHeight()));
        state.inner = inner;
        state.renderer = renderer;
    }

    private @Nullable LivingEntity standIn(HarvestWraith wraith)
    {
        var existing = STAND_INS.get(wraith);
        if (existing != null)
            return existing;
        var type = wraith.source().orElse(null);
        if (type == null || !(type.create(wraith.level(), EntitySpawnReason.LOAD) instanceof LivingEntity created))
            return null;
        // Its render state reads an id, which only joining a world would give it
        created.setId(-wraith.getId() - 1);
        STAND_INS.put(wraith, created);
        return created;
    }

    private static void follow(LivingEntity standIn, HarvestWraith wraith)
    {
        standIn.setPos(wraith.getX(), wraith.getY(), wraith.getZ());
        standIn.xo = wraith.xo;
        standIn.yo = wraith.yo;
        standIn.zo = wraith.zo;
        standIn.xOld = wraith.xOld;
        standIn.yOld = wraith.yOld;
        standIn.zOld = wraith.zOld;
        standIn.setYRot(wraith.getYRot());
        standIn.yRotO = wraith.yRotO;
        standIn.setXRot(wraith.getXRot());
        standIn.xRotO = wraith.xRotO;
        standIn.yBodyRot = wraith.yBodyRot;
        standIn.yBodyRotO = wraith.yBodyRotO;
        standIn.yHeadRot = wraith.yHeadRot;
        standIn.yHeadRotO = wraith.yHeadRotO;
        standIn.tickCount = wraith.tickCount;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.inner == null || state.renderer == null)
            return;
        poseStack.pushPose();
        poseStack.translate(0, -state.sink, 0);
        ((EntityRenderer) state.renderer).submit(state.inner, poseStack, collector, camera);
        poseStack.popPose();
    }
}
