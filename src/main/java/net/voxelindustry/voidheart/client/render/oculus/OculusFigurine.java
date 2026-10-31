package net.voxelindustry.voidheart.client.render.oculus;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight;
import org.jspecify.annotations.Nullable;

/**
 * The player a table follows, standing on the hologram as a small figure under their pin. Its pose is taken from the
 * player every frame, so walking, swinging, sneaking, swimming or flying show on the table as they happen.
 */
public final class OculusFigurine
{
    /**
     * Table blocks the figure stands tall at least, where the true scale would leave it a speck.
     */
    private static final float MIN_HEIGHT = 0.22F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static int drawnLastFrame;

    /**
     * @param feet  where the player's feet stand on the hologram, in world coordinates
     * @param scale world blocks of the figure per world block of the player
     */
    private record Figure(Player player, Vec3 feet, float scale)
    {
        float height()
        {
            return EntityTypes.PLAYER.getHeight() * scale;
        }
    }

    private OculusFigurine()
    {
    }

    /**
     * The figure of the player a table follows, when this client sees them and the table sees where they stand.
     */
    private static @Nullable Figure figure(OculusProjector projector, float partialTick)
    {
        var sight = projector.sight();
        if (projector.view().asleep() || sight == null || !(projector.followed() instanceof Player player) || player.isRemoved())
            return null;

        var position = player.getPosition(partialTick);
        if (sight.resolutionAt(position.x, position.z) == OculusSight.Resolution.UNSEEN)
            return null;
        // Above or below the window, the figure stands on its top or bottom rather than vanishing
        var feet = projector.toHologram(position.x, position.y, position.z);
        if (feet == null)
            feet = projector.toHologramClamped(position.x, position.y, position.z);
        if (feet == null)
            return null;

        float scale = Math.max(projector.inverseScale(), MIN_HEIGHT / EntityTypes.PLAYER.getHeight());
        return new Figure(player, feet, scale);
    }

    /**
     * How far above the feet of the followed player their pin floats, so it stays over the figure.
     */
    static float pinLift(OculusProjector projector, float partialTick)
    {
        var figure = figure(projector, partialTick);
        return figure == null ? 0 : figure.height();
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        drawnLastFrame = 0;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var camera = context.levelState().cameraRenderState;
        var poseStack = context.poseStack();
        for (var projector : OculusRenderer.projectors())
        {
            var figure = figure(projector, camera.cameraEntityPartialTicks);
            if (figure == null)
                continue;

            var state = dispatcher.extractEntity(figure.player(), camera.cameraEntityPartialTicks);
            // Only the body: the pin names them, and nothing of the real world under the figure should show
            state.nameTag = null;
            state.scoreText = null;
            state.leashStates = null;
            state.shadowPieces.clear();
            state.shadowRadius = 0;
            state.displayFireAnimation = false;
            state.outlineColor = 0;
            state.lightCoords = FULL_BRIGHT;

            var offset = figure.feet().subtract(camera.pos);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.scale(figure.scale(), figure.scale(), figure.scale());
            dispatcher.submit(state, camera, 0, 0, 0, poseStack, context.submitNodeCollector());
            poseStack.popPose();
            drawnLastFrame++;
        }
    }

    /**
     * How many figures were drawn last frame, for tests.
     */
    public static int drawnLastFrame()
    {
        return drawnLastFrame;
    }
}
