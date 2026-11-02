package net.voxelindustry.voidheart.client.curse.sky;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * The uniform block of every shader drawing into the sky for a curse, declared in {@code voidheart:curse_sky.glsl}.
 */
public final class CurseSkyUniform
{
    public static final String NAME = "CurseSky";

    public static final BindGroupLayout BINDINGS = BindGroupLayout.builder()
            .withUniform(NAME, UniformType.UNIFORM_BUFFER)
            .build();

    private static final int SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().get();

    private static @Nullable DynamicGpuDataStorageMapped<Data> storage;

    private CurseSkyUniform()
    {
    }

    /**
     * Must be called once a frame with no render pass open. The meaning of the two state vectors is up to the curse's own shaders.
     */
    public static GpuBufferSlice write(SkyFrame frame, float seconds, Vector4fc stateA, Vector4fc stateB)
    {
        if (storage == null)
            storage = new DynamicGpuDataStorageMapped<>("Curse sky UBO", SIZE, GpuBuffer.USAGE_UNIFORM, 4);
        storage.endFrame();
        return storage.writeData(new Data(frame.skyView(), frame.starView(), seconds, frame.starBrightness(), stateA, stateB));
    }

    private record Data(Matrix4fc skyView, Matrix4fc starView, float seconds, float starBrightness, Vector4fc stateA, Vector4fc stateB)
            implements DynamicGpuDataStorage.DynamicGpuData
    {
        @Override
        public void write(ByteBuffer buffer)
        {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(skyView)
                    .putMat4f(starView)
                    .putVec4(seconds, starBrightness, 0, 0)
                    .putVec4(stateA)
                    .putVec4(stateB);
        }
    }
}
