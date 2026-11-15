package net.voxelindustry.voidheart.client.render.sin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The trident's 3D model in hand, with its prongs inlaid in its sin metal: the vanilla trident, then its inlay drawn again over the same geometry.
 */
public class SinTridentRender implements SpecialModelRenderer<Identifier>
{
    public static final Identifier FLARE = overlay("flare");

    private final TridentSpecialRenderer trident;
    private final TridentModel           model;

    public SinTridentRender(TridentModel model)
    {
        this.model = model;
        trident = new TridentSpecialRenderer(model);
    }

    public static Identifier overlay(String name)
    {
        return Identifier.fromNamespaceAndPath(MODID, "textures/entity/sin_trident/" + name + ".png");
    }

    /**
     * @return the inlay texture of a sin, or of its flare while it flares
     */
    public static @Nullable Identifier overlayOf(@Nullable Sin sin)
    {
        if (sin == null)
            return null;
        return SinFlare.isFlaring(sin) ? FLARE : overlay(sin.getSerializedName());
    }

    /**
     * Draws the inlay over a trident model already drawn with this pose.
     */
    public static void submitInlay(TridentModel model, Identifier overlay, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, int outlineColor)
    {
        collector.order(1).submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entityCutout(overlay), lightCoords, overlayCoords, -1, null, outlineColor);
    }

    @Override
    public void submit(@Nullable Identifier overlay, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor)
    {
        trident.submit(poseStack, collector, lightCoords, overlayCoords, hasFoil, outlineColor);
        if (overlay != null)
            submitInlay(model, overlay, poseStack, collector, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output)
    {
        trident.getExtents(output);
    }

    @Override
    public @Nullable Identifier extractArgument(ItemStack stack)
    {
        return overlayOf(SinProficiencies.sinOf(stack));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier>
    {
        public static final Identifier      ID        = Identifier.fromNamespaceAndPath(MODID, "sin_trident");
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Identifier> bake(SpecialModelRenderer.BakingContext context)
        {
            return new SinTridentRender(new TridentModel(context.entityModelSet().bakeLayer(ModelLayers.TRIDENT)));
        }

        @Override
        public MapCodec<Unbaked> type()
        {
            return MAP_CODEC;
        }
    }
}
