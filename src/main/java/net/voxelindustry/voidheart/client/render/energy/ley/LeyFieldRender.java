package net.voxelindustry.voidheart.client.render.energy.ley;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;

/**
 * Everything ley draws across the land rather than on its blocks: spur seams and their motes, ward motes, settling motes and grower glints, all additive
 * glow; and the dashed rings, blended so they keep their hue.
 */
final class LeyFieldRender
{
    private LeyFieldRender()
    {
    }

    static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null)
        {
            clear();
            return;
        }
        long time = level.getGameTime();
        LeySeams.tick(level, time);
        LeyWardMotes.tick(level, time);
        LeyBursts.tick(time);
        LeyRings.tick(minecraft, time);
    }

    static void clear()
    {
        LeySeams.clear();
        LeyWardMotes.clear();
        LeyBursts.clear();
        LeyRings.clear();
    }

    static void collectSubmits(LevelRenderContext context)
    {
        if (Minecraft.getInstance().level == null)
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        float time = levelState.gameTime + partialTicks;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            LeySeams.draw(pose, buffer, camera, time, partialTicks);
            LeyWardMotes.draw(pose, buffer, camera, time);
            LeyBursts.draw(pose, buffer, camera, time, partialTicks);
        });
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) ->
                LeyRings.draw(pose, buffer, camera, time));
    }
}
