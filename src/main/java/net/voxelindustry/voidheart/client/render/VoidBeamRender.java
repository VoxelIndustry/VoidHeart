package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState.StrandDraw;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState.VeilDraw;
import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.beam.BeamNodeKind;
import net.voxelindustry.voidheart.common.content.beam.BeamSegment;
import net.voxelindustry.voidheart.common.content.beam.LevelBeamWorldView;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Void beams of a crafting Shatter Forge: twisting veils between item sources, foci and the forge, hung on silk
 * strands stretched from every monolith pair along the tracks.
 */
public class VoidBeamRender
{
    static final Identifier VEIL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_veil.png");

    private static final int FULL_BRIGHT = 15728880;

    static final int UNATTUNED_COLOR = 0x5EF2A6;
    private static final int STILLNESS_COLOR = 0x7FB8FF;
    private static final int FIERY_COLOR     = 0xFF8A3D;

    private static final int VEILS_PER_LAYER = 6;
    private static final int VEIL_COUNT      = VEILS_PER_LAYER * 2;

    private static final float   STEP           = 0.25F;
    private static final float   HEAD_LENGTH    = 1.2F;
    private static final float   STRAND_LEAD    = 2.5F;
    private static final float[] STRAND_HEIGHTS = {0.45F, 1.2F, 1.95F, 2.7F};
    private static final int     STRAND_STEPS   = 10;

    // Corners and vertical edge midpoints of the collector core
    private static final float[] COLLECTOR_ANCHOR_HEIGHTS = {0.25F, 0.5F, 0.75F};

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final Map<ShatterForgeTile, Layout> LAYOUTS = new WeakHashMap<>();

    private record Path(Vec3 from, Vec3 to, float length, float startDistance, int[] colors, int coreColor, float radius,
                        boolean taperFrom, boolean taperTo)
    {
    }

    /**
     * @param distance beam path distance from the forge to where the strand meets the beam
     */
    private record Strand(Vec3 anchor, Vec3 target, int color, float distance, float seed)
    {
    }

    /**
     * @param maxDistance longest beam path from a source to the forge
     */
    private record Layout(List<BeamSegment> source, List<BlockPos> collectors, List<Path> paths, List<Strand> strands, float maxDistance)
    {
    }

    private record Share(int color, int amount)
    {
    }

    static void extract(ShatterForgeTile forge, ShatterForgeRenderState state, float partialTicks)
    {
        state.veils.clear();
        state.strands.clear();

        var formation = forge.getBeamFormation(partialTicks);
        var segments = forge.getDisplayedBeamSegments();
        if (formation <= 0 || segments.isEmpty())
            return;

        var collectors = forge.getDrainingCollectors();
        var layout = LAYOUTS.get(forge);
        if (layout == null || layout.source() != segments || layout.collectors() != collectors)
        {
            layout = computeLayout(forge.getLevel(), forge.getBlockPos(), segments, collectors);
            LAYOUTS.put(forge, layout);
        }

        var eased = formation * formation * (3 - 2 * formation);
        if (forge.isCrafting())
            extractForming(state, layout, eased);
        else
            extractDissolving(state, layout, eased);
    }

    /**
     * The front travels from the farthest source to the forge, overshooting so its flared head ends inside the forge.
     */
    private static void extractForming(ShatterForgeRenderState state, Layout layout, float eased)
    {
        var front = (layout.maxDistance() + HEAD_LENGTH) * (1 - eased) - HEAD_LENGTH;

        for (var path : layout.paths())
        {
            var headOffset = path.startDistance() - front;
            if (headOffset > 0)
                state.veils.add(veilDraw(path, headOffset, Float.NEGATIVE_INFINITY));
        }

        // Strands start stretching ahead of the front and catch the beam as it passes them
        for (var strand : layout.strands())
        {
            var growth = Math.min(1F, (strand.distance() + STRAND_LEAD - front) / STRAND_LEAD);
            if (growth > 0)
                state.strands.add(strandDraw(strand, growth));
        }
    }

    /**
     * The tail leaves the sources first and drains along the beam into the forge.
     */
    private static void extractDissolving(ShatterForgeRenderState state, Layout layout, float eased)
    {
        var tail = (layout.maxDistance() + 2 * HEAD_LENGTH) * eased - HEAD_LENGTH;

        for (var path : layout.paths())
        {
            var tailOffset = path.startDistance() - tail;
            if (tailOffset < path.length())
                state.veils.add(veilDraw(path, Float.POSITIVE_INFINITY, tailOffset));
        }

        // Strands let go of the beam and fall back to their anchors as the tail passes them
        for (var strand : layout.strands())
        {
            var growth = Math.min(1F, (tail - strand.distance()) / STRAND_LEAD + 0.5F);
            if (growth > 0)
                state.strands.add(strandDraw(strand, growth));
        }
    }

    private static StrandDraw strandDraw(Strand strand, float growth)
    {
        return new StrandDraw(strand.anchor(), strand.target(), strand.color(), growth, strand.seed());
    }

    private static VeilDraw veilDraw(Path path, float headOffset, float tailOffset)
    {
        return new VeilDraw(path.from(), path.to(), path.length(), headOffset, tailOffset, path.colors(), path.coreColor(),
                path.radius(), path.taperFrom(), path.taperTo());
    }

    static void submit(ShatterForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.veils.isEmpty() && state.strands.isEmpty())
            return;

        var time = state.worldTime;
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VEIL_TEXTURE, true), (pose, buffer) ->
        {
            for (var strand : state.strands)
                renderStrand(pose, buffer, strand, time);
            for (var veil : state.veils)
                renderVeil(pose, buffer, veil, time);
        });
    }

    ////////////
    // LAYOUT //
    ////////////

    /**
     * @param collectors collectors the recipe draws from, the only ones lending strands to the beam
     */
    private static Layout computeLayout(Level level, BlockPos origin, List<BeamSegment> segments, List<BlockPos> collectors)
    {
        var view = new LevelBeamWorldView(level);
        var trackY = origin.getY();

        // The whole beam graph lies on the forge level, collectors only sit above it
        var distances = new HashMap<BlockPos, Float>();
        distances.put(origin, 0F);
        for (var pass = 0; pass < segments.size(); pass++)
        {
            for (var segment : segments)
            {
                var from = onTrack(segment.from(), trackY);
                var to = onTrack(segment.to(), trackY);
                if (distances.containsKey(to) && !distances.containsKey(from))
                    distances.put(from, distances.get(to) + horizontalLength(from, to));
            }
        }

        var paths = new ArrayList<Path>();
        var strands = new ArrayList<Strand>();
        var visitedCells = new HashSet<BlockPos>();
        var visitedCollectors = new HashSet<BlockPos>();
        var maxDistance = 0F;

        for (var segment : segments)
        {
            var from = onTrack(segment.from(), trackY);
            var to = onTrack(segment.to(), trackY);
            var length = horizontalLength(from, to);
            if (length < 1.0E-3F)
                continue;

            var toDistance = distances.getOrDefault(to, 0F);
            var startDistance = toDistance + length;
            maxDistance = Math.max(maxDistance, startDistance);

            var fromPoint = beamPoint(segment.from(), origin);
            var toPoint = beamPoint(segment.to(), origin);
            var colors = veilColors(segment.composition());
            // Wide enough for the spiralling item particles to travel inside the inner veils
            var radius = 0.36F + Math.min(Math.max(segment.composition().total() - 2, 0) * 0.03F, 0.14F);

            // A retuning collector continues the beam, it does not pinch it
            paths.add(new Path(fromPoint, toPoint, length, startDistance, colors, coreColor(colors), radius,
                    view.kindAt(segment.from()) != BeamNodeKind.COLLECTOR,
                    view.kindAt(segment.to()) != BeamNodeKind.COLLECTOR));

            var stepX = Integer.signum(from.getX() - to.getX());
            var stepZ = Integer.signum(from.getZ() - to.getZ());
            var along = new Vec3(stepX, 0, stepZ);
            var side = new Vec3(-stepZ, 0, stepX);
            for (var f = 0; f <= (int) length; f++)
            {
                var cell = to.offset(stepX * f, 0, stepZ * f);
                if (!visitedCells.add(cell))
                    continue;
                if (view.kindAt(cell.offset(-stepZ * 2, 0, stepX * 2)) != BeamNodeKind.MONOLITH
                        || view.kindAt(cell.offset(stepZ * 2, 0, -stepX * 2)) != BeamNodeKind.MONOLITH)
                    continue;

                addPairStrands(strands, toPoint.lerp(fromPoint, f / length), side, along, toDistance + f, radius);
            }

            // Collectors drained by the craft lend their aspect: their strands carry the colour into the beam
            var travel = along.scale(-1);
            for (var end : List.of(segment.from(), segment.to()))
            {
                if (!collectors.contains(end) || !visitedCollectors.add(end))
                    continue;
                var aspect = view.collectorAspect(end);
                if (aspect == null || aspect == EssenceRegistry.EMPTY)
                    continue;

                var atFrom = end.equals(segment.from());
                addCollectorStrands(strands, end, origin, atFrom ? fromPoint : toPoint, travel, end.getY() - trackY >= 2,
                        atFrom ? startDistance : toDistance, aspectColor(aspect), radius);
            }
        }
        return new Layout(segments, collectors, paths, strands, maxDistance);
    }

    private static BlockPos onTrack(BlockPos pos, int trackY)
    {
        return new BlockPos(pos.getX(), trackY, pos.getZ());
    }

    private static float horizontalLength(BlockPos from, BlockPos to)
    {
        return (float) Math.sqrt(from.distSqr(to));
    }

    /**
     * Source items, focus prisms and the forge output all float at the beam height.
     */
    private static Vec3 beamPoint(BlockPos pos, BlockPos origin)
    {
        return new Vec3(pos.getX() - origin.getX() + 0.5, ShatterForgeTile.BEAM_HEIGHT, pos.getZ() - origin.getZ() + 0.5);
    }

    /**
     * Monoliths only lend unattuned energy, their strands keep the unattuned colour whatever the beam becomes.
     */
    private static void addPairStrands(List<Strand> strands, Vec3 center, Vec3 sideways, Vec3 along, float distance, float radius)
    {
        var index = 0;
        for (var side = -1; side <= 1; side += 2)
        {
            for (var edge = -1; edge <= 1; edge += 2)
            {
                for (var height : STRAND_HEIGHTS)
                {
                    // Vertical edges of the inner face of each monolith column
                    var edgePoint = center.add(sideways.scale(side * 1.5)).add(along.scale(edge * 0.5));
                    var anchor = new Vec3(edgePoint.x, height, edgePoint.z);
                    var target = center
                            .add(along.scale(edge * 0.18))
                            .add(anchor.subtract(center).normalize().scale(radius * 0.7));

                    strands.add(new Strand(anchor, target, UNATTUNED_COLOR, distance, index * 1.7F + distance));
                    index++;
                }
            }
        }
    }

    /**
     * Strands from the corners and vertical edges of a collector core. A collector above a monolith pair hangs its
     * strands down onto the beam, a collector emitting a track reaches forward along the beam toward the forge.
     *
     * @param travel horizontal direction the beam flows in at the collector
     */
    private static void addCollectorStrands(List<Strand> strands, BlockPos collector, BlockPos origin, Vec3 beamPoint, Vec3 travel,
                                            boolean hanging, float distance, int color, float radius)
    {
        var base = new Vec3(collector.getX() - origin.getX(), collector.getY() - origin.getY(), collector.getZ() - origin.getZ());
        var index = 0;
        for (var y : COLLECTOR_ANCHOR_HEIGHTS)
        {
            for (var x = 0; x <= 1; x++)
            {
                for (var z = 0; z <= 1; z++)
                {
                    var anchor = base.add(0.25 + x * 0.5, y, 0.25 + z * 0.5);
                    var reach = hanging ? (index % 2 == 0 ? -0.45 : 0.45) : 0.9 + index * 0.12;
                    var meeting = beamPoint.add(travel.scale(reach));
                    var target = meeting.add(anchor.subtract(meeting).normalize().scale(radius * 0.7));

                    strands.add(new Strand(anchor, target, color, distance - (float) Math.max(0, reach), index * 2.3F + distance));
                    index++;
                }
            }
        }
    }

    ////////////
    // COLORS //
    ////////////

    /**
     * Colors are never blended: each component of the beam owns a share of the veils matching its share of the energy.
     * Shares are spread evenly around the beam and across both layers.
     *
     * @return inner layer colors followed by outer layer colors
     */
    private static int[] veilColors(BeamComposition composition)
    {
        var shares = new ArrayList<Share>();
        if (composition.unattuned() > 0)
            shares.add(new Share(UNATTUNED_COLOR, composition.unattuned()));
        composition.attuned().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                .forEach(entry -> shares.add(new Share(aspectColor(entry.getKey()), entry.getValue())));

        var colors = new int[VEIL_COUNT];
        if (shares.isEmpty())
        {
            Arrays.fill(colors, UNATTUNED_COLOR);
            return colors;
        }

        // Largest remainder, every component keeps at least one veil
        var total = shares.stream().mapToInt(Share::amount).sum();
        var exact = new float[shares.size()];
        var counts = new int[shares.size()];
        var assigned = 0;
        for (var i = 0; i < shares.size(); i++)
        {
            exact[i] = VEIL_COUNT * shares.get(i).amount() / (float) total;
            counts[i] = Math.max(1, (int) exact[i]);
            assigned += counts[i];
        }
        while (assigned < VEIL_COUNT)
        {
            var best = 0;
            for (var i = 1; i < counts.length; i++)
                if (exact[i] - counts[i] > exact[best] - counts[best])
                    best = i;
            counts[best]++;
            assigned++;
        }
        while (assigned > VEIL_COUNT)
        {
            var worst = -1;
            for (var i = 0; i < counts.length; i++)
                if (counts[i] > 1 && (worst < 0 || exact[i] - counts[i] < exact[worst] - counts[worst]))
                    worst = i;
            counts[worst]--;
            assigned--;
        }

        record Slot(float position, int color)
        {
        }
        var slots = new ArrayList<Slot>();
        for (var i = 0; i < shares.size(); i++)
            for (var k = 0; k < counts[i]; k++)
                slots.add(new Slot((k + 0.5F + i * 0.13F) / counts[i], shares.get(i).color()));
        slots.sort(Comparator.comparingDouble(Slot::position));

        // Consecutive slots alternate layers, with the order flipping every pair so neither layer gets a single color
        var filled = new int[2];
        for (var j = 0; j < VEIL_COUNT; j++)
        {
            var layer = (j + j / 2) % 2;
            colors[layer * VEILS_PER_LAYER + filled[layer]++] = slots.get(j).color();
        }
        return colors;
    }

    static int aspectColor(EssenceType aspect)
    {
        return switch (aspect.identifier().getPath())
        {
            case "stillness" -> STILLNESS_COLOR;
            case "fiery" -> FIERY_COLOR;
            default -> UNATTUNED_COLOR;
        };
    }

    /**
     * Pale version of the most present color.
     */
    private static int coreColor(int[] colors)
    {
        var counts = new HashMap<Integer, Integer>();
        var dominant = colors[0];
        for (var color : colors)
        {
            var count = counts.merge(color, 1, Integer::sum);
            if (count > counts.get(dominant))
                dominant = color;
        }
        var red = (dominant >> 16 & 0xFF) + (255 - (dominant >> 16 & 0xFF)) * 0.55F;
        var green = (dominant >> 8 & 0xFF) + (255 - (dominant >> 8 & 0xFF)) * 0.55F;
        var blue = (dominant & 0xFF) + (255 - (dominant & 0xFF)) * 0.55F;
        return (int) red << 16 | (int) green << 8 | (int) blue;
    }

    ///////////
    // VEILS //
    ///////////

    private static void renderVeil(PoseStack.Pose pose, VertexConsumer buffer, VeilDraw veil, float time)
    {
        var axis = veil.to().subtract(veil.from());
        var direction = axis.normalize();
        var side = direction.cross(Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : UP).normalize();
        var other = direction.cross(side);

        var start = Math.max(0, veil.tailOffset());
        var visible = Math.min(veil.length(), veil.headOffset()) - start;
        if (visible <= 0)
            return;
        var steps = Math.max(1, Mth.ceil(visible / STEP));

        for (var layer = 0; layer < 2; layer++)
        {
            for (var i = 0; i < VEILS_PER_LAYER; i++)
            {
                var color = veil.colors()[layer * VEILS_PER_LAYER + i];
                float[] previous = null;
                for (var j = 0; j <= steps; j++)
                {
                    var current = veilEdges(veil, axis, side, other, layer, i, start + visible * j / steps, time);
                    if (previous != null)
                        strip(pose, buffer, color, previous, current);
                    previous = current;
                }
            }
        }

        float[] previousSide = null;
        float[] previousOther = null;
        for (var j = 0; j <= steps; j++)
        {
            var s = start + visible * j / steps;
            var center = veil.from().add(axis.scale(s / veil.length()));
            var fade = headFade(veil, s) * tailFade(veil, s);
            var radius = veil.radius() * 0.2F * taper(veil, s) * (0.9F + 0.1F * Mth.sin(time * 0.3F + s * 2));
            var alpha = 0.65F * fade;
            var v = 0.4F * (s - time * 0.08F);

            var currentSide = edges(center.subtract(side.scale(radius)), center.add(side.scale(radius)), alpha, v);
            var currentOther = edges(center.subtract(other.scale(radius)), center.add(other.scale(radius)), alpha, v);
            if (previousSide != null)
            {
                strip(pose, buffer, veil.coreColor(), previousSide, currentSide);
                strip(pose, buffer, veil.coreColor(), previousOther, currentOther);
            }
            previousSide = currentSide;
            previousOther = currentOther;
        }
    }

    /**
     * A veil is a curved band wrapped around the beam axis: both layers twist in opposite directions, breathe and fold
     * so the beam seems to wring itself.
     */
    private static float[] veilEdges(VeilDraw veil, Vec3 axis, Vec3 side, Vec3 other, int layer, int index, float s, float time)
    {
        var slot = layer * VEILS_PER_LAYER + index;
        var center = veil.from().add(axis.scale(s / veil.length()));
        var headFade = headFade(veil, s);
        var tailFade = tailFade(veil, s);
        var fade = headFade * tailFade;

        // The head flares as it unfurls, the tail loosens as it dissipates
        var radius = veil.radius() * (layer == 0 ? 0.55F : 1F) * taper(veil, s) * (1 + 0.9F * (1 - headFade)) * (1 + 0.5F * (1 - tailFade))
                * (1 + 0.18F * Mth.sin(2.3F * s - 0.11F * time + slot * 1.7F));

        var twist = layer == 0 ? 1.6F * s + 0.09F * time : -(1.1F * s + 0.06F * time);
        var angle = index * Mth.TWO_PI / VEILS_PER_LAYER + layer * Mth.PI / VEILS_PER_LAYER + twist
                + 0.35F * Mth.sin(0.9F * s + 0.05F * time + index);
        var halfWidth = Mth.PI / VEILS_PER_LAYER * (0.6F + 0.25F * Mth.sin(1.3F * s + 0.07F * time + slot));

        var start = center.add(side.scale(radius * Mth.cos(angle - halfWidth))).add(other.scale(radius * Mth.sin(angle - halfWidth)));
        var end = center.add(side.scale(radius * Mth.cos(angle + halfWidth))).add(other.scale(radius * Mth.sin(angle + halfWidth)));

        var alpha = (layer == 0 ? 0.55F : 0.32F) * fade;
        var v = 0.6F * (s - time * (layer == 0 ? 0.05F : 0.035F)) + slot * 0.37F;
        return edges(start, end, alpha, v);
    }

    /**
     * @return 1 behind the head of the beam, down to 0 at its tip
     */
    private static float headFade(VeilDraw veil, float s)
    {
        var fade = Mth.clamp((veil.headOffset() - s) / HEAD_LENGTH, 0F, 1F);
        return fade * fade * (3 - 2 * fade);
    }

    /**
     * @return 1 ahead of the tail of a dissolving beam, down to 0 at its end
     */
    private static float tailFade(VeilDraw veil, float s)
    {
        var fade = Mth.clamp((s - veil.tailOffset()) / HEAD_LENGTH, 0F, 1F);
        return fade * fade * (3 - 2 * fade);
    }

    /**
     * Pinches the beam into the items it links and the prisms of the foci it crosses.
     */
    private static float taper(VeilDraw veil, float s)
    {
        var taper = 1F;
        if (veil.taperFrom())
            taper = Math.min(taper, endTaper(s));
        if (veil.taperTo())
            taper = Math.min(taper, endTaper(veil.length() - s));
        return taper;
    }

    private static float endTaper(float distance)
    {
        var t = Mth.clamp(distance / 0.8F, 0F, 1F);
        return 0.45F + 0.55F * t * t * (3 - 2 * t);
    }

    /////////////
    // STRANDS //
    /////////////

    /**
     * Silk strand sagging between a monolith edge and the beam, tapering as it merges in.
     */
    private static void renderStrand(PoseStack.Pose pose, VertexConsumer buffer, StrandDraw strand, float time)
    {
        var anchor = strand.anchor();
        var target = strand.target();
        var span = (float) anchor.distanceTo(target);
        var sway = 0.06F * Mth.sin(time * 0.07F + strand.seed());
        var control = anchor.add(target).scale(0.5).add(0, -0.12F * span + sway, 0);

        var steps = Math.max(1, Mth.ceil(STRAND_STEPS * strand.growth()));
        float[][] previous = null;
        for (var j = 0; j <= steps; j++)
        {
            var u = strand.growth() * j / steps;
            var point = bezier(anchor, control, target, u);
            var tangent = control.subtract(anchor).scale(1 - u).add(target.subtract(control).scale(u));

            var halfWidth = Mth.lerp(u, 0.075F, 0.025F) / 2;
            var alpha = 0.55F * (1 - 0.35F * u);
            if (strand.growth() < 1)
                alpha *= Mth.clamp((strand.growth() - u) / 0.15F, 0F, 1F);
            var v = u * span * 0.8F - time * 0.05F + strand.seed();

            var current = crossedSection(point, tangent, halfWidth, alpha, v);
            if (previous != null)
                crossedStrip(pose, buffer, strand.color(), previous, current);
            previous = current;
        }
    }

    private static Vec3 bezier(Vec3 start, Vec3 control, Vec3 end, float u)
    {
        var inverse = 1 - u;
        return start.scale(inverse * inverse).add(control.scale(2 * inverse * u)).add(end.scale(u * u));
    }

    /**
     * Cross-section of a filament: two crossed bands around the curve, so it reads as round from every side.
     */
    static float[][] crossedSection(Vec3 point, Vec3 tangent, float halfWidth, float alpha, float v)
    {
        var direction = tangent.normalize();
        var side = direction.cross(Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : UP).normalize();
        var other = direction.cross(side);
        return new float[][]{
                edges(point.subtract(side.scale(halfWidth)), point.add(side.scale(halfWidth)), alpha, v),
                edges(point.subtract(other.scale(halfWidth)), point.add(other.scale(halfWidth)), alpha, v)
        };
    }

    static void crossedStrip(PoseStack.Pose pose, VertexConsumer buffer, int color, float[][] a, float[][] b)
    {
        strip(pose, buffer, color, a[0], b[0]);
        strip(pose, buffer, color, a[1], b[1]);
    }

    //////////////
    // GEOMETRY //
    //////////////

    /**
     * Cross-section of a band: both edge points, then its alpha and texture v.
     */
    private static float[] edges(Vec3 start, Vec3 end, float alpha, float v)
    {
        return new float[]{(float) start.x, (float) start.y, (float) start.z, (float) end.x, (float) end.y, (float) end.z, alpha, v};
    }

    /**
     * Band piece between two cross-sections, drawn on both faces.
     */
    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, int color, float[] a, float[] b)
    {
        if (a[6] <= 0 && b[6] <= 0)
            return;

        vertex(pose, buffer, color, a[0], a[1], a[2], 0, a[7], a[6]);
        vertex(pose, buffer, color, a[3], a[4], a[5], 1, a[7], a[6]);
        vertex(pose, buffer, color, b[3], b[4], b[5], 1, b[7], b[6]);
        vertex(pose, buffer, color, b[0], b[1], b[2], 0, b[7], b[6]);

        vertex(pose, buffer, color, b[0], b[1], b[2], 0, b[7], b[6]);
        vertex(pose, buffer, color, b[3], b[4], b[5], 1, b[7], b[6]);
        vertex(pose, buffer, color, a[3], a[4], a[5], 1, a[7], a[6]);
        vertex(pose, buffer, color, a[0], a[1], a[2], 0, a[7], a[6]);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, float alpha)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0, 1, 0);
    }
}
