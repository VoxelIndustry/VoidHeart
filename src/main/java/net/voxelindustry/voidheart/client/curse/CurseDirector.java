package net.voxelindustry.voidheart.client.curse;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.voxelindustry.voidheart.client.curse.sky.SkyFrame;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.CursePayloads;
import net.voxelindustry.voidheart.common.curse.CurseTimings;
import org.jspecify.annotations.Nullable;

/**
 * Runs the curse the server struck this player with.
 */
public final class CurseDirector
{
    private static @Nullable ClientCurse active;
    private static @Nullable SkyFrame    skyFrame;

    private CurseDirector()
    {
    }

    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(CursePayloads.Begin.TYPE, (payload, context) -> begin(payload.kind(), payload.seed()));
        ClientPlayNetworking.registerGlobalReceiver(CursePayloads.End.TYPE, (payload, context) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(CurseDirector::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(CurseDirector::collectSubmits);
    }

    private static void begin(CurseKind kind, long seed)
    {
        clear();
        int duration = CurseTimings.duration(kind, seed);
        active = switch (kind)
        {
            case ECHO -> new EchoCurse(seed, duration);
            case OMEN -> new OmenCurse(seed, duration);
            case RAPTURE -> new RaptureCurse(seed, duration);
            case VIGIL -> new VigilCurse(seed, duration);
        };
    }

    public static void clear()
    {
        if (active != null)
            active.onEnd();
        active = null;
    }

    public static @Nullable ClientCurse active()
    {
        return active;
    }

    private static void tick(Minecraft minecraft)
    {
        if (active == null || minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || !minecraft.level.tickRateManager().runsNormally())
            return;

        active.advance(minecraft, minecraft.player);
        if (active.isFinished())
            clear();
    }

    private static void collectSubmits(LevelRenderContext context)
    {
        if (active != null)
            active.collectSubmits(context);
    }

    public static boolean hidesStars()
    {
        return active != null && active.hidesStars();
    }

    public static void prepareSky(SkyRenderState state)
    {
        skyFrame = null;
        if (active == null)
            return;

        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        skyFrame = SkyFrame.of(state, partialTicks);
        active.prepareSky(state, skyFrame);
    }

    public static void drawSky(RenderPass renderPass)
    {
        if (active != null && skyFrame != null)
            active.drawSky(renderPass, skyFrame);
    }
}
