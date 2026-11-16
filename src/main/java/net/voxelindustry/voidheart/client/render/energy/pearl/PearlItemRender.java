package net.voxelindustry.voidheart.client.render.energy.pearl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.energy.pearl.PearlBalance;
import net.voxelindustry.voidheart.common.energy.pearl.PearlContent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The seeds and the pearl as items are what the hung pearl looks like, drawn by its own renderer: still, since inventory slots keep what they first
 * drew, with no stem since they hang from nothing, and grown to fill the slot whatever their stage. The seed a reactor throws off is a dull speck, the
 * kindled seed the same speck with its seams lit, or the stage it had reached when taken down, and the pearl is mature. The rim turns to wherever the
 * item is seen from.
 */
public class PearlItemRender implements SpecialModelRenderer<Float>
{
    public enum Kind implements StringRepresentable
    {
        FRACTAL_SEED("fractal_seed"),
        KINDLED_SEED("kindled_seed"),
        PEARL("pearl");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }

    /** Picked for a corner of the core showing between two buds. */
    private static final float STILL_TIME  = 120;
    private static final long  STILL_SEED  = 0;
    /** How far from the middle of a block's space a still pearl reaches once grown to fill it, whatever its stage. */
    private static final float ITEM_REACH  = 0.5F;
    /** How far the rim's camera stands, in the pearl's own space. */
    private static final float CAMERA_AWAY = 4;

    private final Kind kind;

    public PearlItemRender(Kind kind)
    {
        this.kind = kind;
    }

    @Override
    public @Nullable Float extractArgument(ItemStack stack)
    {
        return switch (kind)
        {
            case FRACTAL_SEED -> 0F;
            case PEARL -> 1F;
            case KINDLED_SEED ->
            {
                var growth = stack.get(PearlContent.PEARL_GROWTH);
                // Never shown mature: a pearl taken down once grown is the pearl, not a seed
                yield growth == null ? 0F : (float) Math.clamp(growth / PearlBalance.GROWTH_TICKS, 0, 0.99);
            }
        };
    }

    @Override
    public void submit(@Nullable Float share, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor)
    {
        var grown = share == null ? 0 : share;
        var center = HangingPearlRender.center(grown);
        var scale = ITEM_REACH / HangingPearlRender.reach(grown);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-center.x, -center.y, -center.z);

        // Geometry is built later in the frame and items share this renderer, so each needs a state of its own
        var state = new HangingPearlRender.State();
        state.share = grown;
        state.time = STILL_TIME;
        state.seed = STILL_SEED;
        state.hanging = false;
        state.kindled = kind != Kind.FRACTAL_SEED;
        state.lightCoords = lightCoords;

        // The view looks down its own -Z, so the camera sits back along +Z turned into the pearl's space
        var toCamera = new Matrix3f(poseStack.last().normal()).transpose().transform(new Vector3f(0, 0, 1)).normalize();
        state.camera = new Vector3f(center).add(toCamera.mul(CAMERA_AWAY));

        HangingPearlRender.submitPearl(state, poseStack, collector);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output)
    {
        for (var x = 0; x < 2; x++)
            for (var y = 0; y < 2; y++)
                for (var z = 0; z < 2; z++)
                    output.accept(new Vector3f(x, y, z));
    }

    public record Unbaked(Kind kind) implements SpecialModelRenderer.Unbaked<Float>
    {
        public static final MapCodec<Unbaked> MAP_CODEC = Kind.CODEC.fieldOf("kind").xmap(Unbaked::new, Unbaked::kind);

        @Override
        public SpecialModelRenderer<Float> bake(SpecialModelRenderer.BakingContext context)
        {
            return new PearlItemRender(kind);
        }

        @Override
        public MapCodec<Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
