package net.voxelindustry.voidheart.client.render.mob;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.model.mob.AutomatonModel;
import net.voxelindustry.voidheart.client.model.mob.FitterModel;
import net.voxelindustry.voidheart.client.model.mob.MantletModel;
import net.voxelindustry.voidheart.client.model.mob.RamCartModel;
import net.voxelindustry.voidheart.client.model.mob.SapperModel;
import net.voxelindustry.voidheart.client.render.SentinelEyeRender;
import net.voxelindustry.voidheart.client.render.TripwireRender;
import net.voxelindustry.voidheart.client.render.TurretWellRender;
import net.voxelindustry.voidheart.client.render.UnderwayGunRender;
import net.voxelindustry.voidheart.client.render.state.AutomatonRenderState;
import net.voxelindustry.voidheart.client.render.state.SecurityMobRenderState;
import net.voxelindustry.voidheart.common.content.underway.security.Automaton;
import net.voxelindustry.voidheart.common.content.underway.security.Fitter;
import net.voxelindustry.voidheart.common.content.underway.security.Mantlet;
import net.voxelindustry.voidheart.common.content.underway.security.RamCart;
import net.voxelindustry.voidheart.common.content.underway.security.Sapper;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

/** The renderers of everything that guards the Underway: what is mounted on the fort, and the machines that walk, roll and crawl through it. */
public final class UnderwaySecurityRenderers
{
    /** How far a mantlet's head turns from the way its shield faces. */
    private static final float MANTLET_HEAD_TURN = 40;

    private UnderwaySecurityRenderers()
    {
    }

    public static void register()
    {
        ModelLayerRegistry.registerModelLayer(AutomatonModel.LAYER, AutomatonModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(MantletModel.LAYER, MantletModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(RamCartModel.LAYER, RamCartModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(SapperModel.LAYER, SapperModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(FitterModel.LAYER, FitterModel::createBodyLayer);

        EntityRenderers.register(VoidHeartEntities.SENTINEL_EYE, context -> new SentinelEyeRender<>(context, false));
        EntityRenderers.register(VoidHeartEntities.SEARCHLIGHT, context -> new SentinelEyeRender<>(context, true));
        EntityRenderers.register(VoidHeartEntities.TURRET_CUPOLA, context -> new UnderwayGunRender<>(context, true));
        EntityRenderers.register(VoidHeartEntities.EMBRASURE_GUN, context -> new UnderwayGunRender<>(context, false));
        EntityRenderers.register(VoidHeartEntities.TRIPWIRE, TripwireRender::new);
        EntityRenderers.register(VoidHeartEntities.TURRET_WELL, TurretWellRender::new);

        EntityRenderers.register(VoidHeartEntities.AUTOMATON, context -> new SecurityMobRender<Automaton, AutomatonRenderState, AutomatonModel>(
                context, new AutomatonModel(context.bakeLayer(AutomatonModel.LAYER)), 0.45F, "automaton", AutomatonRenderState::new, (automaton, state, partialTicks) ->
        {
            state.raise = Mth.lerp(partialTicks, automaton.raiseO, automaton.raise);
            state.locked = automaton.isAimLocked();
        }));
        EntityRenderers.register(VoidHeartEntities.MANTLET, context -> new SecurityMobRender<Mantlet, SecurityMobRenderState.Mantlet, MantletModel>(
                context, new MantletModel(context.bakeLayer(MantletModel.LAYER)), 0.7F, "mantlet", SecurityMobRenderState.Mantlet::new, (mantlet, state, partialTicks) ->
        {
            // The whole machine is drawn the way its shield faces, which lags behind the way it walks
            float shield = Mth.rotLerp(partialTicks, mantlet.shieldO, mantlet.shield);
            state.yRot = Mth.clamp(Mth.wrapDegrees(mantlet.getYHeadRot() - shield), -MANTLET_HEAD_TURN, MANTLET_HEAD_TURN);
            state.bodyRot = shield;
            state.slit = Mth.lerp(partialTicks, mantlet.slitO, mantlet.slit);
            state.locked = mantlet.isAimLocked();
        }));
        EntityRenderers.register(VoidHeartEntities.RAM_CART, context -> new SecurityMobRender<RamCart, SecurityMobRenderState.RamCart, RamCartModel>(
                context, new RamCartModel(context.bakeLayer(RamCartModel.LAYER)), 0.8F, "ram_cart", SecurityMobRenderState.RamCart::new, (cart, state, partialTicks) ->
        {
            // It stands along its track whatever its body and head think they face
            state.bodyRot = cart.getYRot();
            state.roll = Mth.lerp(partialTicks, cart.rollO, cart.roll);
            state.windingUp = cart.phase() == RamCart.Phase.WIND_UP;
        }));
        EntityRenderers.register(VoidHeartEntities.SAPPER, context -> new SecurityMobRender<Sapper, SecurityMobRenderState, SapperModel>(
                context, new SapperModel(context.bakeLayer(SapperModel.LAYER)), 0.3F, "sapper", SecurityMobRenderState::new,
                (sapper, state, partialTicks) -> state.swell = Mth.lerp(partialTicks, sapper.litO, sapper.lit)));
        EntityRenderers.register(VoidHeartEntities.FITTER, context -> new SecurityMobRender<Fitter, SecurityMobRenderState.Fitter, FitterModel>(
                context, new FitterModel(context.bakeLayer(FitterModel.LAYER)), 0.45F, "fitter", SecurityMobRenderState.Fitter::new,
                (fitter, state, partialTicks) -> state.reach = Mth.lerp(partialTicks, fitter.reachO, fitter.reach)));
    }
}
