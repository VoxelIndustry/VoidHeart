package net.voxelindustry.voidheart.client.curse;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import net.voxelindustry.voidheart.client.curse.sky.SkyFrame;
import net.voxelindustry.voidheart.client.curse.sky.StarField;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class OmenCurse extends ClientCurse
{
    private static final RenderPipeline STARS_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_omen"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen"))
            .withVertexBinding(0, StarField.VERTEX_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
            .build();

    private @Nullable GpuBufferSlice uniform;

    public OmenCurse(long seed, int duration)
    {
        super(seed, duration);
    }

    @Override
    public boolean hidesStars()
    {
        return true;
    }

    @Override
    public void prepareSky(SkyRenderState state, SkyFrame frame)
    {
        StarField.prepare();
        uniform = CurseSkyUniform.write(frame, (age + frame.partialTicks()) / 20F, new Vector4f(), new Vector4f());
    }

    @Override
    public void drawSky(RenderPass renderPass, SkyFrame frame)
    {
        if (uniform != null)
            StarField.draw(renderPass, STARS_PIPELINE, uniform);
    }
}
