package net.voxelindustry.voidheart.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import net.voxelindustry.voidheart.compat.iris.IrisCompat;
import net.voxelindustry.voidheart.mixin.client.KleinDimPostChainAccessor;
import net.voxelindustry.voidheart.mixin.client.KleinDimPostPassAccessor;
import org.lwjgl.system.MemoryStack;

import java.util.logging.Level;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * While a Klein Star hangs in view the rest of the scene is darkened and drained, so the star is the only bright thing left, and its burst washes the
 * view out for an instant. One pass over the finished frame, see {@code klein_dim.fsh}, that leaves alone what is nearly white already and what lies
 * within the star's glare on screen. It runs only while a star or a fresh burst is near, and not at all under Iris, whose pipeline replaces vanilla's.
 */
public final class KleinDim
{
    private static final Identifier CHAIN        = Identifier.fromNamespaceAndPath(MODID, "klein_dim");
    private static final String     UNIFORMS     = "KleinDim";
    private static final int        UNIFORM_SIZE = 32;
    // How much of the dimmed scene's color is drained at full dimming
    private static final float      DESATURATE   = 0.7F;
    // The glare left undimmed around the star, in screen heights for a star one block wide seen from one block away
    private static final float      GLARE        = 0.9F;
    private static final float      MAX_GLARE    = 1.5F;

    private static boolean failed;
    private static float   lastDim;

    private KleinDim()
    {
    }

    /** How dark the last frame was made, for game tests. */
    public static float lastDim()
    {
        return lastDim;
    }

    public static void apply(GameRenderer renderer, GraphicsResourceAllocator pool)
    {
        lastDim = 0;
        var minecraft = Minecraft.getInstance();
        if (failed || minecraft.level == null || IrisCompat.useIris())
            return;

        var camera = renderer.mainCamera().position();
        var dim = KleinStarRender.dim(minecraft.level, camera, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (dim == null || dim.dim() <= 0 && dim.flash() <= 0)
            return;

        try
        {
            var chain = minecraft.getShaderManager().getPostChain(CHAIN, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null)
                return;

            var onScreen = renderer.projectPointToScreen(dim.spot());
            float glare = onScreen.z > 1 ? 0 : (float) Math.min(MAX_GLARE, GLARE * dim.size() / Math.max(1, camera.distanceTo(dim.spot())));
            setUniforms(chain, (float) onScreen.x * 0.5F + 0.5F, (float) onScreen.y * 0.5F + 0.5F, glare, dim.dim(), dim.flash());
            chain.process(renderer.mainRenderTarget(), pool);
            lastDim = dim.dim();
        }
        catch (RuntimeException e)
        {
            // Without it the star is only less striking, so it is given up for the session rather than tried again every frame
            failed = true;
            VoidHeart.LOGGER.log(Level.WARNING, "Could not dim the scene around a Klein Star", e);
        }
    }

    private static void setUniforms(PostChain chain, float u, float v, float glare, float dim, float flash)
    {
        for (var pass : ((KleinDimPostChainAccessor) chain).voidheart$passes())
        {
            var uniforms = ((KleinDimPostPassAccessor) pass).voidheart$customUniforms();
            if (!uniforms.containsKey(UNIFORMS))
                continue;
            try (var stack = MemoryStack.stackPush())
            {
                var data = Std140Builder.onStack(stack, UNIFORM_SIZE).putVec4(u, v, glare, 0).putVec4(dim, flash, DESATURATE, 0).get();
                var old = uniforms.put(UNIFORMS, RenderSystem.getDevice().createBuffer(() -> "Klein dim", GpuBuffer.USAGE_UNIFORM, data));
                if (old != null)
                    old.close();
            }
        }
    }
}
