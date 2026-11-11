package net.voxelindustry.voidheart.client.render.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.client.model.mob.WraithBeastModel;
import net.voxelindustry.voidheart.client.render.state.WraithBeastRenderState;
import net.voxelindustry.voidheart.common.content.mob.WraithBeast;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Faint and blended while out of phase, solid with vanilla's cutout once it takes shape. The blended pass culls back faces, so each box shows only its near
 * side rather than its whole inside, and the flat cloth shows from one side only while it is faint. Its eyes glow either way. Slipping aside from a projectile shifts the
 * whole model sideways and back, and fades it a little more for that moment.
 */
public class WraithBeastRender extends MobRenderer<WraithBeast, WraithBeastRenderState, WraithBeastModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_beast.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_beast_glow.png");

    private static final float SHADOW = 0.7F;
    private static final float PHANTOM_ALPHA = 0.32F;
    private static final float DODGE_ALPHA = 0.18F;
    // In blocks, sideways at the height of the slip
    private static final double DODGE_SHIFT = 0.7;
    // In ticks after each change of phase
    private static final float SOLIDIFY_TICKS = 5;
    private static final float FADE_TICKS = 8;
    private static final float REAR_UP_TICKS = 7;
    private static final float SLAM_TICKS = 4;
    private static final float SLAM_FADE_TICKS = 10;

    public WraithBeastRender(EntityRendererProvider.Context context)
    {
        super(context, new WraithBeastModel(context.bakeLayer(WraithBeastModel.LAYER)), SHADOW);
        addLayer(new VoidGlowLayer<>(this, GLOW));
        addLayer(new InsightSilhouetteLayer<>(this, state -> state.insight));
    }

    @Override
    public WraithBeastRenderState createRenderState()
    {
        return new WraithBeastRenderState();
    }

    @Override
    public void extractRenderState(WraithBeast beast, WraithBeastRenderState state, float partialTicks)
    {
        super.extractRenderState(beast, state, partialTicks);
        float since = beast.tickCount - beast.phaseChangedTick + partialTicks;
        switch (beast.phase())
        {
            case PHANTOM ->
            {
                state.solid = 1 - Mth.clamp(since / FADE_TICKS, 0, 1);
                state.rear = 0;
                state.slam = 0;
            }
            case WINDUP ->
            {
                state.solid = beast.wasSolid ? 1 : Mth.clamp(since / SOLIDIFY_TICKS, 0, 1);
                float up = smooth(since / REAR_UP_TICKS);
                float down = smooth((since - (WraithBeast.WINDUP_TICKS - SLAM_TICKS)) / SLAM_TICKS);
                state.rear = up * (1 - down);
                state.slam = 0;
            }
            case RECOVER ->
            {
                state.solid = 1;
                state.rear = 0;
                state.slam = 1 - Mth.clamp(since / SLAM_FADE_TICKS, 0, 1);
            }
            case HELD ->
            {
                state.solid = beast.wasSolid ? 1 : Mth.clamp(since / SOLIDIFY_TICKS, 0, 1);
                state.rear = 0;
                state.slam = 0;
            }
        }

        float dodging = (beast.tickCount - beast.dodgedTick + partialTicks) / WraithBeast.DODGE_TICKS;
        state.dodge = dodging >= 0 && dodging < 1 ? Mth.sin(dodging * Mth.PI) : 0;
        state.dodgeLeft = beast.dodgeLeft;
        state.insight = InsightSight.silhouette(beast, partialTicks, state.solid < 1);
    }

    private static float smooth(float t)
    {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float alpha(WraithBeastRenderState state)
    {
        return Mth.lerp(state.solid, PHANTOM_ALPHA, 1) * (1 - state.dodge * (1 - DODGE_ALPHA));
    }

    @Override
    public Vec3 getRenderOffset(WraithBeastRenderState state)
    {
        if (state.dodge == 0)
            return super.getRenderOffset(state);
        float yaw = state.bodyRot * Mth.DEG_TO_RAD;
        double side = (state.dodgeLeft ? 1 : -1) * DODGE_SHIFT * state.dodge;
        return super.getRenderOffset(state).add(Mth.cos(yaw) * side, 0, Mth.sin(yaw) * side);
    }

    @Override
    public Identifier getTextureLocation(WraithBeastRenderState state)
    {
        return TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(WraithBeastRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing)
    {
        return alpha(state) < 1 && isBodyVisible
               ? RenderTypes.entityTranslucentCull(TEXTURE)
               : super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected int getModelTint(WraithBeastRenderState state)
    {
        float alpha = alpha(state);
        return alpha < 1 ? ARGB.color(Mth.floor(alpha * 255), 0xFFFFFF) : super.getModelTint(state);
    }

    @Override
    protected float getShadowRadius(WraithBeastRenderState state)
    {
        return super.getShadowRadius(state) * state.solid;
    }
}
