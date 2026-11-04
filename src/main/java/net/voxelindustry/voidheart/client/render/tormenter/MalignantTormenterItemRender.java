package net.voxelindustry.voidheart.client.render.tormenter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterDropletShape;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The item is what its block looks like at one moment: still, since inventory slots keep what they first drew. A sated droplet
 * comes down to the height of a block, the others already fit one.
 */
public class MalignantTormenterItemRender implements SpecialModelRenderer<TormenterSouls>
{
    // Picked for a face looking out of the front and another rising under it
    private static final float STILL_TIME = 40;
    private static final long  STILL_SEED = 0;

    private static final float SATED_SCALE = 1F / TormenterDropletShape.HEIGHT;

    // Heads of an unsated one reach a little out of its block
    private static final float MOST_REACH = 0.25F;

    @Override
    public void submit(@Nullable TormenterSouls souls, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor)
    {
        // Geometry is built later in the frame and items share this renderer, so each needs a figure of its own
        var figure = new TormenterFigure();
        figure.set(souls, Minecraft.getInstance().level, STILL_SEED, STILL_TIME);

        poseStack.pushPose();
        if (souls != null && souls.isComplete())
        {
            poseStack.translate(0.5F, 0, 0.5F);
            poseStack.scale(SATED_SCALE, SATED_SCALE, SATED_SCALE);
            poseStack.translate(-0.5F, 0, -0.5F);
        }
        figure.submit(poseStack, collector, lightCoords, null);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output)
    {
        for (var x = 0; x < 2; x++)
            for (var y = 0; y < 2; y++)
                for (var z = 0; z < 2; z++)
                    output.accept(new Vector3f(x == 0 ? -MOST_REACH : 1 + MOST_REACH, y, z == 0 ? -MOST_REACH : 1 + MOST_REACH));
    }

    @Override
    public @Nullable TormenterSouls extractArgument(ItemStack stack)
    {
        return stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<TormenterSouls>
    {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<TormenterSouls> bake(SpecialModelRenderer.BakingContext context)
        {
            return new MalignantTormenterItemRender();
        }

        @Override
        public MapCodec<Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
