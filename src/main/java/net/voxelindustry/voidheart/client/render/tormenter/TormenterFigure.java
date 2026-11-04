package net.voxelindustry.voidheart.client.render.tormenter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The droplet and its heads at one moment, as a placed tormenter and its item both show them. The droplet is opaque, and so are
 * the heads drowned in it, all taking the light they are given. A head's face stands out of the flesh and is never coplanar with
 * it, and only moving layer edges cross the rest of a head. A sated one is about 290 quads of flesh and 400 of heads.
 */
final class TormenterFigure
{
    private static final Identifier FLESH_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/malignant_tormenter.png");
    private static final Identifier HUSK_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/block/malignant_tormenter_husk.png");

    // A squat drop that fits its block and barely moves while it hungers, it only spills out once sated
    private static final float HUNGRY_HEIGHT = 0.24F;
    private static final float EMPTY_WIDTH   = 0.36F;
    private static final float EMPTY_VIGOR   = 0.2F;
    private static final float EMPTY_SPEED   = 0.25F;
    private static final float FED_WIDTH     = 0.46F;
    private static final float FED_VIGOR     = 0.7F;

    private static final int SIDES            = 4;
    private static final int COLUMNS_PER_SIDE = 2;
    private static final int HEADS_PER_COLUMN = 2;
    private static final int MOST_HEADS       = SIDES * COLUMNS_PER_SIDE * HEADS_PER_COLUMN;
    private static final int LAYERS           = TormenterDroplet.LAYERS;

    // In ticks, each divides the next so that time can wrap around without a jump
    private static final int SWELL_PERIOD = 60;
    private static final int FLOW_PERIOD  = 300;

    static final int TIME_WRAP = FLOW_PERIOD * 100;

    private static final float LATERAL_VARIATION = 0.1F;
    private static final float SIZE_VARIATION    = 0.08F;
    private static final float ROLL_RADIANS      = 0.05F;
    private static final float WRITHE_RADIANS    = 0.04F;

    private final PoseStack stack = new PoseStack();

    private boolean hasSouls;
    private float   flow;
    private float   swell;
    private float   width;
    private float   height;
    private float   vigor;
    private int     heads;
    private long    seed;
    private int     light;

    private TormenterHeads.@Nullable Head head;

    /**
     * @param time in ticks, under {@link #TIME_WRAP}
     */
    void set(@Nullable TormenterSouls souls, @Nullable Level level, long seed, float time)
    {
        var fill = souls == null ? 0 : (float) souls.souls() / TormenterSouls.REQUIRED;
        var sated = souls != null && souls.isComplete();

        this.hasSouls = souls != null;
        this.seed = seed;
        this.width = sated ? 1 : Mth.lerp(fill, EMPTY_WIDTH, FED_WIDTH);
        this.height = sated ? 1 : HUNGRY_HEIGHT;
        this.vigor = sated ? 1 : Mth.lerp(fill, EMPTY_VIGOR, FED_VIGOR);
        this.flow = time * (sated ? 1 : Mth.lerp(fill, EMPTY_SPEED, 1)) * LAYERS / FLOW_PERIOD;
        this.swell = time / SWELL_PERIOD * Mth.TWO_PI + Math.floorMod(seed * 2654435761L, 1000L) * Mth.TWO_PI / 1000;
        this.heads = Mth.ceil(fill * MOST_HEADS);
        this.head = souls == null || level == null ? null : TormenterHeads.of(souls.type(), level).orElse(null);
    }

    void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking)
    {
        this.light = light;

        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(hasSouls ? FLESH_TEXTURE : HUSK_TEXTURE), this::flesh);

        var drawnHead = head;
        if (drawnHead != null)
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(drawnHead.texture()),
                    (pose, buffer) -> heads(drawnHead, pose, buffer, false));

        if (breaking != null)
            collector.submitCustomGeometry(poseStack, ModelBakery.DESTROY_TYPES.get(breaking.progress()), (pose, buffer) ->
            {
                var decal = new SheetedDecalTextureGenerator(buffer, breaking.cameraPose(), 1);
                flesh(pose, decal);
                if (drawnHead != null)
                    heads(drawnHead, pose, decal, true);
            });
    }

    /**
     * Grows out of the middle of the floor of its block, whatever its size.
     */
    private void enterDroplet(PoseStack.Pose pose)
    {
        stack.last().set(pose);
        stack.translate(0.5F, 0, 0.5F);
        stack.scale(width, height, width);
    }

    private void flesh(PoseStack.Pose pose, VertexConsumer buffer)
    {
        enterDroplet(pose);
        TormenterDroplet.render(stack.last(), buffer, light, flow, swell, vigor);
    }

    private void heads(TormenterHeads.Head head, PoseStack.Pose pose, VertexConsumer buffer, boolean mainCubeOnly)
    {
        enterDroplet(pose);
        for (var index = 0; index < heads; index++)
        {
            var side = index % SIDES;
            var column = index / SIDES % COLUMNS_PER_SIDE;
            var row = index / (SIDES * COLUMNS_PER_SIDE);

            // Columns run out of step with each other, so that faces over the droplet never line up in rows
            var climbed = flow / LAYERS + (float) row / HEADS_PER_COLUMN + (side * 2 + column) * 0.3819F;
            var cycle = Mth.floor(climbed);
            head(head, buffer, climbed - cycle, index, side, column, cycle, mainCubeOnly);
        }
    }

    /**
     * A head draws its place and size anew for each climb, while it is under the skin and can't be seen changing.
     */
    private void head(TormenterHeads.Head head, VertexConsumer buffer, float rise, int index, int side, int column, int cycle, boolean mainCubeOnly)
    {
        var size = TormenterHeadFlow.size(rise);
        if (size <= 0)
            return;

        var random = Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32) + index * 31 + cycle * 977);
        var drawnLateral = (random & 0xFF) / 255F * 2 - 1;
        var drawnSize = (random >>> 8 & 0xFF) / 255F * 2 - 1;
        var drawnRoll = (random >>> 16 & 0xFF) / 255F * 2 - 1;

        var lateral = (column == 0 ? -1 : 1) * TormenterHeadFlow.lateral(rise) * (1 + drawnLateral * LATERAL_VARIATION);
        var writhe = Mth.sin(swell + index * 2.3F) * WRITHE_RADIANS * vigor;

        stack.pushPose();
        // Once flipped upright as mob models are, a face looks along -z: turning by a side looks out of that side
        stack.rotate(Axis.YP, side * Mth.HALF_PI);
        stack.translate(lateral, TormenterHeadFlow.height(rise), -TormenterHeadFlow.depth(rise));
        stack.rotate(Axis.XP, TormenterHeadFlow.tilt(rise));
        stack.rotate(Axis.ZP, drawnRoll * ROLL_RADIANS + writhe);
        var fit = size * (1 + drawnSize * SIZE_VARIATION) * head.scale();
        stack.scale(-fit, -fit, fit);
        stack.translate(-head.centerX(), -head.centerY(), -head.centerZ());

        // Cracks would also show on the see-through parts of an outer layer, like a zombie's hat
        for (var piece : mainCubeOnly ? head.pieces().subList(0, 1) : head.pieces())
        {
            stack.pushPose();
            stack.last().pose().mul(piece.pose());
            stack.last().normal().mul(piece.normal());
            piece.cube().compile(stack.last(), buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            stack.popPose();
        }
        stack.popPose();
    }
}
