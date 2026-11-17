package net.voxelindustry.voidheart.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.sin.RavenousBite;
import net.voxelindustry.voidheart.common.content.sin.Sin;

import java.util.ArrayList;
import java.util.List;

/**
 * While a Ravenous tool aims at a block it would bite, outlines every block the swing would take beside it, planned by the same rules the server eats by.
 * <p>
 * Additive {@link VoidHeartRenderTypes#GLOW} edges a hair outside each block, so they never share a plane with it and need no sorting. The plan is
 * refreshed once a tick, or when the aim moves, and holds at most 74 blocks: about 900 camera-facing ribbons a frame at the largest bite.
 */
public final class RavenousBitePreview
{
    private static final int   COLOR      = Sin.GLUTTONY.color();
    private static final int   LOW_ALPHA  = 60;
    private static final int   HIGH_ALPHA = 110;
    private static final float PULSE_RATE = 0.2F;
    private static final float HALF_WIDTH = 0.012F;
    private static final double INFLATE   = 0.004;

    private static final List<AABB> boxes = new ArrayList<>();
    private static BlockPos  plannedPos;
    private static Direction plannedFace;
    private static long      plannedAt = Long.MIN_VALUE;

    private RavenousBitePreview()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;

        var tool = player.getMainHandItem();
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!RavenousBite.canBite(player, tool, state))
            return;

        if (!pos.equals(plannedPos) || hit.getDirection() != plannedFace || level.getGameTime() != plannedAt)
        {
            plannedPos = pos.immutable();
            plannedFace = hit.getDirection();
            plannedAt = level.getGameTime();
            boxes.clear();
            for (var ring : RavenousBite.plan(level, player, tool, pos, state, hit.getDirection()))
            {
                for (var bitten : ring)
                    boxes.add(new AABB(bitten).inflate(INFLATE));
            }
        }
        if (boxes.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;
        int color = ARGB.color(Math.round(Mth.lerp(0.5F + 0.5F * Mth.sin(time * PULSE_RATE), LOW_ALPHA, HIGH_ALPHA)), COLOR);
        var drawn = List.copyOf(boxes);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var box : drawn)
                RelayLinkPreviewRender.outline(pose, buffer, box, camera, color, HALF_WIDTH);
        });
    }
}
