package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.voxelindustry.voidheart.client.particle.KleinStarDrawParticle;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStar;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarBalance;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarBurstPayload;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarItem;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarPath;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarPathPayload;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarSettings;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarTargeting;
import net.voxelindustry.voidheart.common.content.kleinstar.RadiantCover;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Everything a Klein Star shows.
 * <p>
 * The star is built in depth, from the inside out: a solid cube of boiling plasma; two larger cubes of corona filaments turning the other ways around
 * it; the plates of its casing, which start closed around it and open into two tilted orbits as it grows, dark against the light behind them; and a
 * spiked halo turned to the camera. It grows on the end of its caster's staff and is drawn there between ticks, see {@link KleinStar#center(float)}.
 * Seen through its caster's own eyes the staff is drawn in the hand, close to the camera, where no place in the world matches its end: there the
 * star is drawn past the staff's head as it shows on screen instead, and eases onto its true curve as it leaves.
 * While it grows it throws arcs at the ground and tears crumbs off the blocks around it, which wind their way in,
 * see {@link KleinStarDrawParticle}. It leaves along a curve, see {@link KleinStarPath}, drawn from the curve itself so it is smooth between ticks,
 * with a streak laid back along it. Its burst is a flare, rays thrown out from its middle, the plates flung away and the crumbs given back, then veins
 * of light on every block face that saw it, with filaments rising off the ground. The burst is not a shell around its middle: a closed dome read as a
 * shield.
 * <p>
 * Whoever holds the staff is shown where a star would burst, faintly before they call one and plainly while it grows and they steer it: a mark on
 * the spot, a thread down to the ground under it and a dashed ring as wide as the blast.
 * <p>
 * Render types. The core and the plates are {@link RenderTypes#entityCutout}: opaque and writing depth, so the star is a solid thing that hides and is
 * hidden as one, in daylight as well. The core is given full light and one normal for all its faces, so it glows evenly; the plates keep their own
 * normals and take the shading of their sides. Corona, halo, cover and filaments are {@link VoidHeartRenderTypes#radiant}, and arcs, rays and the
 * streak {@link VoidHeartRenderTypes#GLOW}: both added to what is behind, never writing depth and never culled, so their layers cross in any order
 * without flicker while the core and terrain still hide them. The ring and thread that show where it will burst are
 * {@link VoidHeartRenderTypes#MARKER}, blended rather than added, so they keep their gold on grass and sand in daylight, and the spot itself
 * is also a small diamond in {@link VoidHeartRenderTypes#GLOW_THROUGH}, which no wall hides. The cover lies {@value #COVER_LIFT} off its faces so the depth test never ties with
 * the block under it.
 * <p>
 * Per frame: about 60 quads for a star, up to {@value #MAX_ARCS} arcs of {@value #ARC_SEGMENTS} segments in two layers, {@value #BURST_RAYS} rays
 * and six plates for the few ticks of a burst, and one quad per lit face of a cover, a few hundred, found once when the burst happens and only
 * recolored after.
 */
public final class KleinStarRender
{
    private static final Identifier CORE_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/entity/klein_star_core.png");
    private static final Identifier CORONA_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/klein_star_corona.png");
    private static final Identifier HALO_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/entity/klein_star_halo.png");
    private static final Identifier SHARD_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/klein_star_shard.png");
    private static final Identifier COVER_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/radiant_cover.png");

    private static final RenderType CORE_TYPE   = RenderTypes.entityCutout(CORE_TEXTURE);
    private static final RenderType SHARD_TYPE  = RenderTypes.entityCutout(SHARD_TEXTURE);
    private static final RenderType CORONA_TYPE = VoidHeartRenderTypes.radiant(CORONA_TEXTURE);
    private static final RenderType HALO_TYPE   = VoidHeartRenderTypes.radiant(HALO_TEXTURE);
    private static final RenderType COVER_TYPE  = VoidHeartRenderTypes.radiant(COVER_TEXTURE);

    // The core, corona and cover textures hold this many frames, one above the other
    private static final int    FRAMES             = 4;

    private static final int    WHITE              = 0xFFFFFF;
    private static final int    CORE_COLOR         = 0xFFFFFF;
    private static final int    INNER_COLOR        = 0xFFE9A8;
    private static final int    OUTER_COLOR        = 0xFFC860;

    // The star: sides of its cubes against its size, and degrees a tick each turns
    private static final float  CORE_SIDE          = 0.5F;
    private static final float  CORE_SPIN          = 2.5F;
    private static final int    CORE_FRAME_TICKS   = 3;
    private static final float  INNER_SIDE         = 0.7F;
    private static final float  INNER_SPIN         = -6;
    private static final int    INNER_ALPHA        = 210;
    private static final float  OUTER_SIDE         = 0.95F;
    private static final float  OUTER_SPIN         = 4;
    private static final int    OUTER_ALPHA        = 120;
    private static final int    CORONA_FRAME_TICKS = 2;
    // Its surface swells and shrinks by this much
    private static final float  CHURN              = 0.06F;
    private static final float  CHURN_SPEED        = 0.9F;
    private static final float  HALO_RADIUS        = 1.5F;
    private static final int    HALO_ALPHA         = 110;
    private static final float  HALO_SPIN          = 0.6F;
    private static final float  GLARE_RADIUS       = 3;
    private static final int    GLARE_ALPHA        = 30;

    // The plates of its casing
    private static final int    SHARDS             = 6;
    private static final float  SHARD_SIDE         = 0.3F;
    private static final float  SHARD_THICKNESS    = 0.07F;
    // How far out they turn, against the star's size: closed over the core at first, opened once it is grown
    private static final float  SHARD_CLOSED       = 0.3F;
    private static final float  SHARD_OPEN         = 0.56F;
    private static final float  SHARD_SPIN         = 8;
    private static final float  SHARD_TILT         = 35;
    private static final float  SHARD_BOB          = 0.05F;
    // Flung by the burst, they fly this share of its radius while they shrink to nothing
    private static final int    SHARD_FLING_TICKS  = 10;
    private static final float  SHARD_FLING        = 0.9F;
    private static final float  SHARD_TUMBLE       = 40;

    // Through its caster's eyes it grows here, from the eyes along the gaze: to the side of the hand that holds the staff, up and ahead
    private static final double VIEW_ASIDE         = 2.15;
    private static final double VIEW_UP            = 0.1;
    private static final double VIEW_AHEAD         = 2.2;
    // Ticks it takes to ease from there onto its curve once it leaves
    private static final float  VIEW_EASE_TICKS    = 5;

    // Its arcs
    private static final int    MAX_ARCS           = 4;
    private static final int    ARC_SEGMENTS       = 8;
    private static final float  ARCS_FROM_SIZE     = 0.2F;
    private static final int    ARC_LIFE_MIN       = 3;
    private static final int    ARC_LIFE_SPAN      = 3;
    private static final double ARC_REACH_MIN      = 0.3;
    private static final double ARC_REACH_SPAN     = 1.7;
    private static final double ARC_MAX_DROP       = 10;
    private static final double ARC_JITTER         = 0.28;
    private static final float  ARC_CORE_WIDTH     = 0.035F;
    private static final float  ARC_GLOW_WIDTH     = 0.12F;
    private static final int    ARC_CORE_ALPHA     = 255;
    private static final int    ARC_GLOW_ALPHA     = 90;
    private static final float  SCORCH_RADIUS      = 0.5F;
    private static final int    SCORCH_ALPHA       = 150;
    private static final double SCORCH_LIFT        = 0.03;
    private static final int    ARC_SPARKS         = 2;

    // The crumbs it tears off the blocks around it
    private static final float  DRAW_FROM_SIZE     = 0.15F;
    private static final int    DRAW_PER_TICK      = 7;
    private static final double DRAW_REACH         = 5.5;
    private static final double DRAW_OFF_FACE      = 0.08;
    private static final double DRAW_SCATTER       = 0.35;

    // Its streak in flight, laid back over where it was drawn these last ticks
    private static final float  TRAIL_TICKS        = 2.6F;
    private static final float  TRAIL_WIDTH        = 0.42F;
    private static final int    TRAIL_ALPHA        = 220;

    // Where it will burst, shown to whoever steers it
    private static final int    PREVIEW_ALPHA      = 235;
    private static final int    PREVIEW_IDLE_ALPHA = 130;
    private static final int    PREVIEW_DASHES     = 32;
    private static final float  PREVIEW_WIDTH      = 0.06F;
    // The spot itself shows through walls, fainter, since what a star goes for may be behind one
    private static final float  PREVIEW_DIAMOND    = 0.22F;
    private static final int    PREVIEW_THROUGH    = 120;
    private static final float  PREVIEW_TURN       = 0.02F;
    private static final float  PREVIEW_MARK       = 0.4F;
    private static final float  PREVIEW_SPIN       = 3;
    private static final float  PREVIEW_THREAD     = 0.025F;
    private static final double PREVIEW_DROP       = 10;
    private static final double PREVIEW_LIFT       = 0.04;

    // Its burst
    private static final int    SHELL_TICKS        = 8;
    private static final int    BURST_RAYS         = 18;
    private static final int    RAY_ALPHA          = 230;
    private static final float  RAY_WIDTH          = 0.9F;
    // Each ray starts this far out along its reach, and reaches between this share of the blast and all of it
    private static final float  RAY_START          = 0.25F;
    private static final float  RAY_SHORTEST       = 0.6F;
    private static final float  FLARE_RADIUS       = 1.6F;
    private static final int    FLARE_ALPHA        = 255;
    private static final int    BURST_SPARKS       = 40;
    private static final double BURST_SPARK_SPEED  = 0.9;
    private static final int    BURST_DEBRIS       = 48;
    private static final double DEBRIS_SPEED       = 0.45;

    // What it leaves on the blocks
    private static final int    COVER_ALPHA        = 235;
    private static final float  COVER_LIFT         = 0.01F;
    private static final int    COVER_RISE_TICKS   = 5;
    private static final int    COVER_FADE_TICKS   = 40;
    private static final int    COVER_FRAME_TICKS  = 5;
    // The fading edge of the cover is this many blocks wide
    private static final double COVER_EDGE         = 1.5;
    private static final float  SHIMMER_FLOOR      = 0.35F;
    private static final float  SHIMMER_SPEED      = 0.17F;
    // The cover is gone this many blocks short of the rim of the blast, where whole faces stop being lit
    private static final double COVER_SHORT        = 0.75;
    private static final int    COVER_MOTES        = 2;
    // Filaments stand on one lit top face in this many
    private static final int    LICK_ONE_IN        = 5;
    private static final float  LICK_HEIGHT        = 0.9F;
    private static final float  LICK_HALF_WIDTH    = 0.45F;
    private static final int    LICK_ALPHA         = 170;
    private static final int    LICK_FRAME_TICKS   = 2;

    // How the scene is dimmed around it, see KleinDim
    private static final float  DIM                = 0.65F;
    private static final double DIM_FULL_RANGE     = 24;
    private static final double DIM_RANGE          = 48;
    private static final int    FLASH_TICKS        = 5;
    private static final float  FLASH              = 0.85F;
    private static final int    DIM_RELEASE_TICKS  = 20;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final class Arc
    {
        final Vec3[] points;
        // Where the star was when it threw the arc: the arc's top follows the star from there, its foot stays on the ground
        final Vec3   origin;
        final long   born;
        final int    life;

        Arc(Vec3[] points, Vec3 origin, long born, int life)
        {
            this.points = points;
            this.origin = origin;
            this.born = born;
            this.life = life;
        }

        Vec3 point(int index, Vec3 starNow)
        {
            return points[index].add(starNow.subtract(origin).scale(1 - index / (double) ARC_SEGMENTS));
        }
    }

    private static final class StarFx
    {
        final KleinStar star;
        final List<Arc> arcs = new ArrayList<>();
        long            seen;
        @Nullable KleinStarPath path;
        // The star's age when it took its path, and how long it had been flying by then
        int                     pathAge;
        int                     flyingTicks;
        // Where it was last drawn, and how far that was from where its path starts when it took it
        Vec3                    drawn;
        Vec3                    offset = Vec3.ZERO;
        // Where it was drawn lately while flying, newest last: the streak it leaves
        final ArrayDeque<Trace> trail  = new ArrayDeque<>();

        StarFx(KleinStar star)
        {
            this.star = star;
            drawn = star.center();
        }

        void take(KleinStarPath taken, int flying)
        {
            path = taken;
            pathAge = star.tickCount;
            flyingTicks = flying;
            offset = drawn.subtract(taken.from());
        }

        /**
         * Where to draw it: on the staff while it grows, then on its path, which is smooth where the positions the server sends are a tick apart.
         * A path is eased onto from wherever the star was drawn when it took it.
         */
        Vec3 center(float partialTicks)
        {
            if (path == null)
            {
                var inView = star.launched() ? null : heldInView(star, partialTicks);
                drawn = inView != null ? inView : star.center(partialTicks);
            }
            else
            {
                float since = star.tickCount - pathAge + partialTicks;
                double along = KleinStarBalance.flown(flyingTicks + since) - KleinStarBalance.flown(flyingTicks);
                float off = Math.max(0, 1 - since / VIEW_EASE_TICKS);
                drawn = path.at(along).add(offset.scale(off * off));
            }
            return drawn;
        }

        void trace(float time, Vec3 at)
        {
            if (trail.isEmpty() || time > trail.getLast().time())
                trail.addLast(new Trace(time, at));
            while (!trail.isEmpty() && time - trail.getFirst().time() > TRAIL_TICKS)
                trail.removeFirst();
        }
    }

    private record Trace(float time, Vec3 at)
    {
    }

    /**
     * A lit face.
     *
     * @param corners  its corners, lifted off the block
     * @param distance from the burst to its middle
     * @param phase    sets its veins and filaments off from its neighbors'
     * @param lick     whether filaments rise from it
     */
    private record Face(Vec3[] corners, double distance, int phase, boolean lick)
    {
    }

    /**
     * @param rays where its rays and flung plates go, each as long as the share of the blast it reaches
     */
    private record Burst(Vec3 center, float radius, long start, int coverTicks, List<Face> faces, Vec3[] rays)
    {
    }

    /**
     * How the scene around the camera should be dimmed.
     *
     * @param dim   how much darker everything but the star is
     * @param flash how far the whole view is washed out by a burst
     * @param spot  the star, or where it burst
     * @param size  how wide the star is, in blocks
     */
    public record Dim(float dim, float flash, Vec3 spot, float size)
    {
    }

    /** Everything gathered for one frame, one list of quads for each way of drawing. */
    private record Frame(Quads core, Quads shards, Quads corona, Quads halo, Quads cover, Quads glow, Quads marker, Quads through)
    {
        Frame(Vec3 camera)
        {
            this(new Quads(camera), new Quads(camera), new Quads(camera), new Quads(camera), new Quads(camera), new Quads(camera), new Quads(camera), new Quads(camera));
        }
    }

    private static final Map<Integer, StarFx> STARS  = new HashMap<>();
    // Paths that came before their star was seen
    private static final Map<Integer, KleinStarPathPayload> PENDING = new HashMap<>();
    // Where a star would burst if the player holding a staff called one now
    private static @Nullable Vec3 idleAim;
    private static final List<Burst>          BURSTS = new ArrayList<>();
    private static final Random               RANDOM = new Random();

    private static int drawn;

    private KleinStarRender()
    {
    }

    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(KleinStarBurstPayload.TYPE, (payload, context) ->
        {
            var level = context.client().level;
            if (level != null)
                burst(level, payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(KleinStarPathPayload.TYPE, (payload, context) ->
        {
            var fx = STARS.get(payload.starId());
            if (fx == null)
                PENDING.put(payload.starId(), payload);
            else if (validPath(payload))
                fx.take(new KleinStarPath(payload.points()), payload.flyingTicks());
        });
        ClientPlayNetworking.registerGlobalReceiver(KleinStarSettings.Told.TYPE, (payload, context) -> KleinStarSettings.accept(payload));
        LevelRenderEvents.COLLECT_SUBMITS.register(KleinStarRender::collectSubmits);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client.level));
    }

    public static void clear()
    {
        STARS.clear();
        BURSTS.clear();
        PENDING.clear();
        idleAim = null;
    }

    /** On leaving a server, what it said of its settings is forgotten with everything else. */
    public static void leave()
    {
        clear();
        KleinStarSettings.forget();
    }

    private static boolean validPath(KleinStarPathPayload payload)
    {
        return payload.points().size() >= 4 && (payload.points().size() - 1) % 3 == 0;
    }

    /** Where the player holding a staff is shown a star would burst, for game tests. */
    public static @Nullable Vec3 idleAim()
    {
        return idleAim;
    }

    /** How many stars are being drawn, for game tests. */
    public static int activeStars()
    {
        return STARS.size();
    }

    /** How many faces the latest burst lit, for game tests. */
    public static int litFaces()
    {
        return BURSTS.isEmpty() ? 0 : BURSTS.get(BURSTS.size() - 1).faces().size();
    }

    /** Where the latest burst happened, for game tests. */
    public static @Nullable Vec3 lastBurstCenter()
    {
        return BURSTS.isEmpty() ? null : BURSTS.get(BURSTS.size() - 1).center();
    }

    /** How many crumbs stars have torn off blocks so far, for game tests. */
    public static int drawnCrumbs()
    {
        return drawn;
    }

    /** Which arm someone raises to hold their staff out while a star grows on it: 0 for neither, 1 for the right, 2 for the left. */
    public static final RenderStateDataKey<Integer> RAISED_ARM = RenderStateDataKey.create(() -> "voidheart:klein_staff_arm");

    /**
     * @return the arm this one holds a staff with a star growing on it in, as {@link #RAISED_ARM} counts them
     */
    public static int raisedArm(Entity entity)
    {
        for (var fx : STARS.values())
            if (!fx.star.launched() && fx.star.bearerId() == entity.getId())
                return fx.star.inRightHand() ? 1 : 2;
        return 0;
    }

    /**
     * @return where the star was last drawn, which is where what it draws to itself should go
     */
    public static Vec3 drawnCenter(KleinStar star)
    {
        var fx = STARS.get(star.getId());
        return fx == null ? star.center() : fx.drawn;
    }

    /**
     * @return where a growing star shows through the eyes of the one who holds its staff, or null for anyone else and any other view
     */
    private static @Nullable Vec3 heldInView(KleinStar star, float partialTicks)
    {
        var minecraft = Minecraft.getInstance();
        var viewer = minecraft.getCameraEntity();
        if (viewer == null || viewer.getId() != star.bearerId() || !minecraft.options.getCameraType().isFirstPerson())
            return null;

        float yaw = viewer.getViewYRot(partialTicks);
        var look = Vec3.directionFromRotation(viewer.getViewXRot(partialTicks), yaw);
        var aside = Vec3.directionFromRotation(0, yaw + 90);
        var up = aside.cross(look);
        return viewer.getEyePosition(partialTicks)
                .add(aside.scale(star.inRightHand() ? VIEW_ASIDE : -VIEW_ASIDE))
                .add(up.scale(VIEW_UP))
                .add(look.scale(VIEW_AHEAD));
    }

    private static void burst(ClientLevel level, KleinStarBurstPayload payload)
    {
        var center = payload.center();
        double radius = payload.radius();
        var faces = new ArrayList<Face>();
        int reach = Mth.ceil(radius);
        var middle = BlockPos.containing(center);
        for (var pos : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach), middle.offset(reach, reach, reach)))
        {
            if (!level.getBlockState(pos).isSolidRender())
                continue;
            for (var direction : Direction.values())
                if (RadiantCover.lit(level, pos, direction, center, radius))
                    faces.add(face(pos, direction, center));
        }
        var rays = new Vec3[BURST_RAYS];
        for (int i = 0; i < BURST_RAYS; i++)
            rays[i] = randomHeading().scale(RAY_SHORTEST + RANDOM.nextDouble() * (1 - RAY_SHORTEST));
        BURSTS.add(new Burst(center, payload.radius(), level.getGameTime(), payload.coverTicks(), faces, rays));

        for (int i = 0; i < BURST_SPARKS; i++)
        {
            var heading = randomHeading().scale(BURST_SPARK_SPEED * (0.4 + RANDOM.nextDouble() * 0.6));
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, heading.x, heading.y, heading.z);
        }
        // What the star took from the blocks, it gives back
        for (int i = 0; i < BURST_DEBRIS && !faces.isEmpty(); i++)
        {
            var face = faces.get(RANDOM.nextInt(faces.size()));
            var normal = normalOf(face);
            var at = onFace(face);
            var state = level.getBlockState(BlockPos.containing(at.subtract(normal.scale(0.5))));
            if (state.isAir())
                continue;
            var away = at.subtract(center).normalize().add(normal).scale(DEBRIS_SPEED * (0.5 + RANDOM.nextDouble()));
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, away.x, away.y, away.z);
        }
    }

    private static Face face(BlockPos pos, Direction direction, Vec3 center)
    {
        var corners = GlowCube.faceCorners(direction, pos.getX(), pos.getY(), pos.getZ(), 0);
        var normal = direction.getUnitVec3i();
        var lifted = new Vec3[4];
        for (int i = 0; i < 4; i++)
            lifted[i] = new Vec3(corners[i][0] + normal.getX() * COVER_LIFT, corners[i][1] + normal.getY() * COVER_LIFT, corners[i][2] + normal.getZ() * COVER_LIFT);
        var faceCenter = Vec3.atCenterOf(pos).add(normal.getX() * 0.5, normal.getY() * 0.5, normal.getZ() * 0.5);
        int phase = Math.floorMod(pos.getX() * 7 + pos.getY() * 13 + pos.getZ() * 31, 1024);
        return new Face(lifted, faceCenter.distanceTo(center), phase, direction == Direction.UP && phase % LICK_ONE_IN == 0);
    }

    /**
     * Out of the block, since a face's corners go counter-clockwise seen from outside.
     */
    private static Vec3 normalOf(Face face)
    {
        var corners = face.corners();
        return corners[1].subtract(corners[0]).cross(corners[3].subtract(corners[0])).normalize();
    }

    private static Vec3 onFace(Face face)
    {
        var corners = face.corners();
        return corners[0].add(corners[1].subtract(corners[0]).scale(RANDOM.nextDouble())).add(corners[3].subtract(corners[0]).scale(RANDOM.nextDouble()));
    }

    private static Vec3 randomHeading()
    {
        double y = RANDOM.nextDouble() * 2 - 1;
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double flat = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(angle) * flat, y, Math.sin(angle) * flat);
    }

    private static void tick(@Nullable ClientLevel level)
    {
        if (level == null)
        {
            clear();
            return;
        }
        long now = level.getGameTime();

        for (var entity : level.entitiesForRendering())
        {
            if (!(entity instanceof KleinStar star))
                continue;
            var fx = STARS.computeIfAbsent(star.getId(), id -> new StarFx(star));
            fx.seen = now;
            var pending = PENDING.remove(star.getId());
            if (pending != null && validPath(pending))
                fx.take(new KleinStarPath(pending.points()), pending.flyingTicks());
            fx.arcs.removeIf(arc -> now - arc.born >= arc.life);
            if (star.launched())
            {
                fx.arcs.clear();
                continue;
            }
            if (fx.arcs.size() < wantedArcs(star.tickCount))
            {
                var arc = newArc(level, star, now);
                if (arc != null)
                    fx.arcs.add(arc);
            }
            drawCrumbs(level, star);
        }
        STARS.values().removeIf(fx -> fx.seen != now || fx.star.isRemoved());
        PENDING.keySet().removeIf(id -> level.getEntity(id) == null);
        idleAim = idleAim(level);

        BURSTS.removeIf(burst -> now - burst.start() > burst.coverTicks() + SHELL_TICKS);
        for (var burst : BURSTS)
        {
            if (burst.faces().isEmpty() || now - burst.start() < SHELL_TICKS)
                continue;
            for (int i = 0; i < COVER_MOTES; i++)
            {
                var face = burst.faces().get(RANDOM.nextInt(burst.faces().size()));
                if (face.distance() > coverCutoff(burst, now - burst.start()))
                    continue;
                var at = onFace(face);
                level.addParticle(ParticleTypes.WAX_ON, at.x, at.y, at.z, 0, 0.6, 0);
            }
        }
    }

    /**
     * @return where a star would burst if the player called one now, or null unless they hold a staff that could
     */
    private static @Nullable Vec3 idleAim(ClientLevel level)
    {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || player.isSpectator())
            return null;
        for (var fx : STARS.values())
            if (fx.star.bearerId() == player.getId())
                return null;

        int cost = player.hasInfiniteMaterials() ? 0 : KleinStarSettings.cost(level);
        for (var hand : InteractionHand.values())
        {
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof KleinStarItem) || player.getCooldowns().isOnCooldown(stack) || KleinStarItem.charge(stack) < cost)
                continue;
            boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
            var tip = KleinStar.tip(level, player, right, 1);
            return KleinStarTargeting.target(level, tip, player, KleinStarTargeting.visible(level, tip, player), KleinStarSettings.radius(level)).at();
        }
        return null;
    }

    private static int wantedArcs(int ticks)
    {
        float size = KleinStarBalance.size(ticks);
        if (size < ARCS_FROM_SIZE)
            return 0;
        return 1 + Math.round((MAX_ARCS - 1) * (size - ARCS_FROM_SIZE) / (KleinStarBalance.FULL_SIZE - ARCS_FROM_SIZE));
    }

    /**
     * A jagged line from the star's skin to the ground somewhere under and around it, or nothing where there is no ground to reach.
     */
    private static @Nullable Arc newArc(ClientLevel level, KleinStar star, long now)
    {
        var center = star.center();
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double reach = ARC_REACH_MIN + RANDOM.nextDouble() * ARC_REACH_SPAN;
        var above = center.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
        var hit = level.clip(new ClipContext(above, above.add(0, -ARC_MAX_DROP, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS)
            return null;

        var end = hit.getLocation();
        var span = end.subtract(center);
        double length = span.length();
        if (length < 1e-3)
            return null;
        var start = center.add(span.scale(KleinStarBalance.size(star.tickCount) * CORE_SIDE / 2 / length));

        var points = new Vec3[ARC_SEGMENTS + 1];
        double sway = ARC_JITTER * Math.min(1, length / 3);
        for (int i = 0; i <= ARC_SEGMENTS; i++)
        {
            double along = i / (double) ARC_SEGMENTS;
            var point = start.lerp(end, along);
            if (i > 0 && i < ARC_SEGMENTS)
                point = point.add((RANDOM.nextDouble() - 0.5) * 2 * sway, (RANDOM.nextDouble() - 0.5) * sway, (RANDOM.nextDouble() - 0.5) * 2 * sway);
            points[i] = point;
        }

        for (int i = 0; i < ARC_SPARKS; i++)
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.05, end.z, (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() * 0.25, (RANDOM.nextDouble() - 0.5) * 0.3);
        return new Arc(points, center, now, ARC_LIFE_MIN + RANDOM.nextInt(ARC_LIFE_SPAN));
    }

    /**
     * Crumbs torn off the blocks the star can see around it, more of them as it grows.
     */
    private static void drawCrumbs(ClientLevel level, KleinStar star)
    {
        float size = KleinStarBalance.size(star.tickCount);
        if (size < DRAW_FROM_SIZE)
            return;
        var center = star.center();
        int count = 1 + Math.round((DRAW_PER_TICK - 1) * size / KleinStarBalance.FULL_SIZE);
        for (int i = 0; i < count; i++)
        {
            var hit = level.clip(new ClipContext(center, center.add(randomHeading().scale(DRAW_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit))
                continue;
            var pos = blockHit.getBlockPos();
            var state = level.getBlockState(pos);
            if (state.isAir() || !state.shouldSpawnTerrainParticles())
                continue;
            var normal = blockHit.getDirection().getUnitVec3i();
            // Just off the face, scattered along it
            var at = hit.getLocation()
                    .add(normal.getX() * DRAW_OFF_FACE, normal.getY() * DRAW_OFF_FACE, normal.getZ() * DRAW_OFF_FACE)
                    .add((RANDOM.nextDouble() - 0.5) * DRAW_SCATTER * (1 - Math.abs(normal.getX())),
                            (RANDOM.nextDouble() - 0.5) * DRAW_SCATTER * (1 - Math.abs(normal.getY())),
                            (RANDOM.nextDouble() - 0.5) * DRAW_SCATTER * (1 - Math.abs(normal.getZ())));
            Minecraft.getInstance().particleEngine.add(new KleinStarDrawParticle(level, at.x, at.y, at.z, state, pos.immutable(), star));
            drawn++;
        }
    }

    /**
     * How far from the burst the cover still shows: all of it, then drawing in toward the middle as it ends.
     */
    private static double coverCutoff(Burst burst, float ticks)
    {
        float left = burst.coverTicks() + SHELL_TICKS - ticks;
        return (burst.radius() + COVER_EDGE) * Mth.clamp(left / COVER_FADE_TICKS, 0, 1);
    }

    /**
     * @return how to dim the scene seen from there, or null when no star is near enough to matter
     */
    public static @Nullable Dim dim(ClientLevel level, Vec3 camera, float partialTicks)
    {
        Dim strongest = null;
        for (var fx : STARS.values())
        {
            var center = fx.center(partialTicks);
            float size = KleinStarBalance.size(fx.star.tickCount + partialTicks);
            float dim = DIM * size / KleinStarBalance.FULL_SIZE * nearness(camera, center);
            if (dim > 0 && (strongest == null || dim > strongest.dim()))
                strongest = new Dim(dim, 0, center, size);
        }
        for (var burst : BURSTS)
        {
            float ticks = level.getGameTime() - burst.start() + partialTicks;
            if (ticks >= DIM_RELEASE_TICKS)
                continue;
            float near = nearness(camera, burst.center());
            float dim = DIM * (1 - ticks / DIM_RELEASE_TICKS) * near;
            float fade = Math.max(0, 1 - ticks / FLASH_TICKS);
            float flash = FLASH * fade * fade * near;
            if (near > 0 && (strongest == null || dim + flash > strongest.dim() + strongest.flash()))
                strongest = new Dim(dim, flash, burst.center(), KleinStarBalance.FULL_SIZE + burst.radius() * Math.min(1, ticks / SHELL_TICKS));
        }
        return strongest;
    }

    private static float nearness(Vec3 camera, Vec3 spot)
    {
        double distance = camera.distanceTo(spot);
        return (float) Mth.clamp((DIM_RANGE - distance) / (DIM_RANGE - DIM_FULL_RANGE), 0, 1);
    }

    private static void collectSubmits(LevelRenderContext context)
    {
        var level = Minecraft.getInstance().level;
        if (level == null || STARS.isEmpty() && BURSTS.isEmpty() && idleAim == null)
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        float time = level.getGameTime() + partialTicks;

        var frame = new Frame(camera);
        for (var fx : STARS.values())
            star(frame, fx, time, partialTicks);
        for (var burst : BURSTS)
            burst(frame, burst, time);

        var player = Minecraft.getInstance().player;
        if (player != null)
        {
            for (var fx : STARS.values())
                if (!fx.star.launched() && fx.star.bearerId() == player.getId())
                    preview(frame, level, fx.star.aim(), PREVIEW_ALPHA, time);
            if (idleAim != null)
                preview(frame, level, idleAim, PREVIEW_IDLE_ALPHA, time);
        }

        var collector = context.submitNodeCollector();
        var poseStack = context.poseStack();
        submit(collector, poseStack, CORE_TYPE, frame.core());
        submit(collector, poseStack, SHARD_TYPE, frame.shards());
        submit(collector, poseStack, CORONA_TYPE, frame.corona());
        submit(collector, poseStack, HALO_TYPE, frame.halo());
        submit(collector, poseStack, COVER_TYPE, frame.cover());
        if (!frame.glow().isEmpty())
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, frame.glow()::emitPlain);
        if (!frame.marker().isEmpty())
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, frame.marker()::emitPlain);
        if (!frame.through().isEmpty())
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW_THROUGH, frame.through()::emitPlain);
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, Quads quads)
    {
        if (!quads.isEmpty())
            collector.submitCustomGeometry(poseStack, type, quads::emitTextured);
    }

    /**
     * Where a star will burst: a mark on the spot, a thread down to the ground under it, and a dashed ring there as wide as the blast.
     */
    private static void preview(Frame frame, ClientLevel level, Vec3 at, int alpha, float time)
    {
        var below = level.clip(new ClipContext(at, at.add(0, -PREVIEW_DROP, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        double ground = (below.getType() == HitResult.Type.MISS ? at.y - 1 : below.getLocation().y) + PREVIEW_LIFT;
        float radius = KleinStarSettings.radius(level);
        int color = ARGB.color(alpha, INNER_COLOR);

        for (int dash = 0; dash < PREVIEW_DASHES; dash++)
        {
            double from = Math.PI * 2 * dash / PREVIEW_DASHES + time * PREVIEW_TURN;
            double to = from + Math.PI / PREVIEW_DASHES;
            // Turned to the camera, so a dash seen from low and far is not a sliver
            frame.marker().ribbon(new Vec3(at.x + Math.cos(from) * radius, ground, at.z + Math.sin(from) * radius),
                    new Vec3(at.x + Math.cos(to) * radius, ground, at.z + Math.sin(to) * radius), PREVIEW_WIDTH, PREVIEW_WIDTH, color, color);
        }
        frame.through().diamond(at, PREVIEW_DIAMOND, ARGB.color(alpha * PREVIEW_THROUGH / 255, INNER_COLOR));
        frame.marker().ribbon(new Vec3(at.x, ground, at.z), at, PREVIEW_THREAD, PREVIEW_THREAD, ARGB.color(alpha / 3, OUTER_COLOR), color);
        frame.halo().sprite(at, PREVIEW_MARK, time * PREVIEW_SPIN, ARGB.color(alpha, WHITE));
        frame.halo().flat(new Vec3(at.x, ground, at.z), PREVIEW_MARK, ARGB.color(alpha / 2, WHITE));
    }

    private static void star(Frame frame, StarFx fx, float time, float partialTicks)
    {
        var star = fx.star;
        float age = star.tickCount + partialTicks;
        float size = KleinStarBalance.size(age);
        if (size <= 0)
            return;
        var center = fx.center(partialTicks);
        float churn = 1 + CHURN * Mth.sin(time * CHURN_SPEED);
        float grown = size / KleinStarBalance.FULL_SIZE;
        int white = ARGB.color(255, WHITE);

        frame.core().cube(center, size * CORE_SIDE / 2, age * CORE_SPIN, age * CORE_SPIN * 0.7F, frameOf(time, CORE_FRAME_TICKS, 0), white);
        frame.corona().cube(center, size * INNER_SIDE / 2 * churn, age * INNER_SPIN, age * INNER_SPIN * 0.6F, frameOf(time, CORONA_FRAME_TICKS, 0), ARGB.color(INNER_ALPHA, WHITE));
        frame.corona().cube(center, size * OUTER_SIDE / 2 / churn, age * OUTER_SPIN * 0.5F, age * OUTER_SPIN, frameOf(time, CORONA_FRAME_TICKS, 2), ARGB.color(OUTER_ALPHA, WHITE));
        frame.halo().sprite(center, size * HALO_RADIUS * churn, time * HALO_SPIN, ARGB.color(HALO_ALPHA, WHITE));
        frame.halo().sprite(center, size * GLARE_RADIUS, -time * HALO_SPIN * 0.5F, ARGB.color(GLARE_ALPHA, OUTER_COLOR));

        // The plates, three to each of two tilted orbits turning against each other
        float orbit = size * Mth.lerp(grown * grown, SHARD_CLOSED, SHARD_OPEN);
        for (int i = 0; i < SHARDS; i++)
        {
            int ring = i % 2;
            float turn = (age * SHARD_SPIN * (ring == 0 ? 1 : -0.8F) + i * 360F / SHARDS) * Mth.DEG_TO_RAD;
            float tilt = (ring == 0 ? SHARD_TILT : -SHARD_TILT) * Mth.DEG_TO_RAD;
            var outward = new Vec3(Mth.cos(turn), 0, Mth.sin(turn)).zRot(tilt).yRot(ring * Mth.HALF_PI);
            var along = new Vec3(-Mth.sin(turn), 0, Mth.cos(turn)).zRot(tilt).yRot(ring * Mth.HALF_PI);
            var at = center.add(outward.scale(orbit * (1 + SHARD_BOB * Mth.sin(time * 0.5F + i))));
            frame.shards().plate(at, outward, along, size * SHARD_SIDE / 2, size * SHARD_THICKNESS / 2);
        }

        if (fx.path != null)
        {
            fx.trace(time, center);
            Trace newer = null;
            for (var older = fx.trail.descendingIterator(); older.hasNext(); )
            {
                var trace = older.next();
                if (newer != null)
                {
                    float near = Math.max(0, 1 - (time - newer.time()) / TRAIL_TICKS);
                    float far = Math.max(0, 1 - (time - trace.time()) / TRAIL_TICKS);
                    frame.glow().ribbon(newer.at(), trace.at(), size * TRAIL_WIDTH * near, size * TRAIL_WIDTH * far,
                            ARGB.color(Math.round(TRAIL_ALPHA * near), INNER_COLOR), ARGB.color(Math.round(TRAIL_ALPHA * far), OUTER_COLOR));
                }
                newer = trace;
            }
        }

        for (var arc : fx.arcs)
        {
            float left = 1 - (time - arc.born) / arc.life;
            if (left <= 0)
                continue;
            int core = ARGB.color(Math.round(ARC_CORE_ALPHA * left), CORE_COLOR);
            int glow = ARGB.color(Math.round(ARC_GLOW_ALPHA * left), OUTER_COLOR);
            for (int i = 0; i < ARC_SEGMENTS; i++)
            {
                frame.glow().ribbon(arc.point(i, center), arc.point(i + 1, center), ARC_CORE_WIDTH, ARC_CORE_WIDTH, core, core);
                frame.glow().ribbon(arc.point(i, center), arc.point(i + 1, center), ARC_GLOW_WIDTH, ARC_GLOW_WIDTH, glow, glow);
            }
            frame.halo().flat(arc.points[ARC_SEGMENTS].add(0, SCORCH_LIFT, 0), SCORCH_RADIUS, ARGB.color(Math.round(SCORCH_ALPHA * left), WHITE));
        }
    }

    private static int frameOf(float time, int ticksEach, int offset)
    {
        return Math.floorMod((int) (time / ticksEach) + offset, FRAMES);
    }

    private static void burst(Frame frame, Burst burst, float time)
    {
        float ticks = time - burst.start();
        if (ticks < 0)
            return;

        if (ticks < SHELL_TICKS)
        {
            float spread = ticks / SHELL_TICKS;
            float eased = 1 - (1 - spread) * (1 - spread);
            int tip = ARGB.color(0, OUTER_COLOR);
            int root = ARGB.color(Math.round(RAY_ALPHA * (1 - spread * spread)), INNER_COLOR);
            for (var ray : burst.rays())
            {
                var reach = ray.scale(burst.radius() * eased);
                frame.glow().ribbon(burst.center().add(reach.scale(RAY_START)), burst.center().add(reach), RAY_WIDTH * (1 - spread), 0, root, tip);
            }
            frame.halo().sprite(burst.center(), burst.radius() * FLARE_RADIUS * eased, time * HALO_SPIN * 4, ARGB.color(Math.round(FLARE_ALPHA * (1 - spread)), WHITE));
        }

        if (ticks < SHARD_FLING_TICKS)
        {
            float flung = ticks / SHARD_FLING_TICKS;
            float eased = 1 - (1 - flung) * (1 - flung);
            float shrink = 1 - flung;
            for (int i = 0; i < SHARDS; i++)
            {
                var heading = burst.rays()[i].normalize();
                var along = heading.cross(Math.abs(heading.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize();
                float tumble = (ticks * SHARD_TUMBLE + i * 60) * Mth.DEG_TO_RAD;
                var facing = heading.scale(Mth.cos(tumble)).add(along.scale(Mth.sin(tumble)));
                var side = along.scale(Mth.cos(tumble)).subtract(heading.scale(Mth.sin(tumble)));
                frame.shards().plate(burst.center().add(heading.scale(burst.radius() * SHARD_FLING * eased)), facing, side,
                        KleinStarBalance.FULL_SIZE * SHARD_SIDE / 2 * shrink, KleinStarBalance.FULL_SIZE * SHARD_THICKNESS / 2 * shrink);
            }
        }

        double cutoff = coverCutoff(burst, ticks);
        for (var face : burst.faces())
        {
            // The light reaches each face as the burst spreads
            float lit = ticks - (float) (face.distance() / burst.radius()) * SHELL_TICKS;
            if (lit <= 0)
                continue;
            float shown = Math.min(1, lit / COVER_RISE_TICKS) * (float) Mth.clamp((cutoff - face.distance()) / COVER_EDGE, 0, 1);
            if (shown <= 0)
                continue;

            var corners = face.corners();
            int[] colors = new int[4];
            int brightest = 0;
            for (int i = 0; i < 4; i++)
            {
                colors[i] = coverColor(burst, corners[i], time, shown);
                brightest = Math.max(brightest, ARGB.alpha(colors[i]));
            }
            if (brightest == 0)
                continue;
            frame.cover().face(corners, colors, frameOf(time, COVER_FRAME_TICKS, face.phase()));

            if (face.lick())
            {
                var middle = corners[0].add(corners[2]).scale(0.5);
                float flicker = 0.6F + 0.4F * Mth.sin(time * 0.7F + face.phase());
                int color = ARGB.color(Math.round(LICK_ALPHA * flicker * brightest / 255F), WHITE);
                int lickFrame = frameOf(time, LICK_FRAME_TICKS, face.phase());
                frame.corona().upright(middle, new Vec3(1, 0, 1).normalize(), LICK_HALF_WIDTH, LICK_HEIGHT, lickFrame, color);
                frame.corona().upright(middle, new Vec3(1, 0, -1).normalize(), LICK_HALF_WIDTH, LICK_HEIGHT, lickFrame, color);
            }
        }
    }

    /**
     * The light at one corner of a lit face: holding out from the middle and gone at the rim of the blast, and drifting in patches. It only depends on
     * where the corner is, so the faces around a corner agree on it and the cover has no seams.
     */
    private static int coverColor(Burst burst, Vec3 corner, float time, float shown)
    {
        double away = Math.min(1, corner.distanceTo(burst.center()) / (burst.radius() - COVER_SHORT));
        float falloff = (float) (1 - away * away * away * away);
        float drift = time * SHIMMER_SPEED;
        float wave = Mth.sin((float) corner.x * 1.7F + drift + Mth.sin((float) corner.z * 2.3F - drift * 0.7F) * 1.3F + (float) corner.y * 1.9F);
        float shimmer = SHIMMER_FLOOR + (1 - SHIMMER_FLOOR) * (0.5F + 0.5F * wave);
        return ARGB.color(Math.round(COVER_ALPHA * shown * falloff * shimmer), WHITE);
    }

    /**
     * Quads gathered while reading the world, drawn later in the frame. Positions are kept relative to the camera, subtracted in double. Each vertex
     * carries a place in its texture and a normal, which the plain glow leaves unused.
     */
    private static final class Quads
    {
        private final Vec3           camera;
        private final FloatArrayList positions = new FloatArrayList();
        private final FloatArrayList uvs       = new FloatArrayList();
        private final FloatArrayList normals   = new FloatArrayList();
        private final IntArrayList   colors    = new IntArrayList();

        Quads(Vec3 camera)
        {
            this.camera = camera;
        }

        boolean isEmpty()
        {
            return colors.isEmpty();
        }

        void emitPlain(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
                buffer.addVertex(pose, positions.getFloat(i * 3), positions.getFloat(i * 3 + 1), positions.getFloat(i * 3 + 2)).setColor(colors.getInt(i));
        }

        void emitTextured(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
                buffer.addVertex(pose, positions.getFloat(i * 3), positions.getFloat(i * 3 + 1), positions.getFloat(i * 3 + 2))
                        .setColor(colors.getInt(i))
                        .setUv(uvs.getFloat(i * 2), uvs.getFloat(i * 2 + 1))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, normals.getFloat(i * 3), normals.getFloat(i * 3 + 1), normals.getFloat(i * 3 + 2));
        }

        private void vertex(Vec3 at, float u, float v, Vec3 normal, int color)
        {
            positions.add((float) (at.x - camera.x));
            positions.add((float) (at.y - camera.y));
            positions.add((float) (at.z - camera.z));
            uvs.add(u);
            uvs.add(v);
            normals.add((float) normal.x);
            normals.add((float) normal.y);
            normals.add((float) normal.z);
            colors.add(color);
        }

        /**
         * A quad showing a whole texture, or one of the frames stacked in it.
         */
        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int frames, int frame, Vec3 normal, int colorA, int colorB, int colorC, int colorD)
        {
            float v0 = frame / (float) frames;
            float v1 = (frame + 1) / (float) frames;
            vertex(a, 0, v0, normal, colorA);
            vertex(b, 0, v1, normal, colorB);
            vertex(c, 1, v1, normal, colorC);
            vertex(d, 1, v0, normal, colorD);
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int frames, int frame, Vec3 normal, int color)
        {
            quad(a, b, c, d, frames, frame, normal, color, color, color, color);
        }

        /**
         * A cube turned around the vertical then tipped, both in degrees, each side showing one frame of its texture and all lit alike.
         */
        void cube(Vec3 center, float half, float yaw, float pitch, int frame, int color)
        {
            var right = new Vec3(1, 0, 0).xRot(pitch * Mth.DEG_TO_RAD).yRot(yaw * Mth.DEG_TO_RAD);
            var up = new Vec3(0, 1, 0).xRot(pitch * Mth.DEG_TO_RAD).yRot(yaw * Mth.DEG_TO_RAD);
            var forward = new Vec3(0, 0, 1).xRot(pitch * Mth.DEG_TO_RAD).yRot(yaw * Mth.DEG_TO_RAD);
            box(center, right, up, forward, half, half, half, FRAMES, frame, color, false);
        }

        /**
         * A thin plate facing along one direction, its seam along another, each side shaded by where it faces.
         */
        void plate(Vec3 center, Vec3 facing, Vec3 along, float halfSide, float halfThickness)
        {
            var across = facing.cross(along).normalize();
            box(center, along, across, facing, halfSide, halfSide, halfThickness, 1, 0, ARGB.color(255, WHITE), true);
        }

        private void box(Vec3 center, Vec3 right, Vec3 up, Vec3 forward, float halfRight, float halfUp, float halfForward, int frames, int frame, int color, boolean shaded)
        {
            var r = right.scale(halfRight);
            var u = up.scale(halfUp);
            var f = forward.scale(halfForward);
            side(center.add(r), u, f, shaded ? right : UP, frames, frame, color);
            side(center.subtract(r), u, f, shaded ? right.scale(-1) : UP, frames, frame, color);
            side(center.add(u), r, f, shaded ? up : UP, frames, frame, color);
            side(center.subtract(u), r, f, shaded ? up.scale(-1) : UP, frames, frame, color);
            side(center.add(f), r, u, shaded ? forward : UP, frames, frame, color);
            side(center.subtract(f), r, u, shaded ? forward.scale(-1) : UP, frames, frame, color);
        }

        private void side(Vec3 middle, Vec3 u, Vec3 v, Vec3 normal, int frames, int frame, int color)
        {
            quad(middle.subtract(u).subtract(v), middle.subtract(u).add(v), middle.add(u).add(v), middle.add(u).subtract(v), frames, frame, normal, color);
        }

        /**
         * A block face with a color at each corner, in the order {@link GlowCube#faceCorners} gives them.
         */
        void face(Vec3[] corners, int[] cornerColors, int frame)
        {
            quad(corners[0], corners[1], corners[2], corners[3], FRAMES, frame, UP, cornerColors[0], cornerColors[1], cornerColors[2], cornerColors[3]);
        }

        /**
         * A whole texture turned to the camera and spun in its own plane, in degrees.
         */
        void sprite(Vec3 center, float radius, float spin, int color)
        {
            var toCamera = camera.subtract(center);
            if (toCamera.lengthSqr() < 1e-6)
                return;
            toCamera = toCamera.normalize();
            var side = toCamera.cross(Math.abs(toCamera.y) > 0.99 ? new Vec3(1, 0, 0) : UP).normalize();
            var up = side.cross(toCamera);
            float angle = spin * Mth.DEG_TO_RAD;
            var u = side.scale(Mth.cos(angle) * radius).add(up.scale(Mth.sin(angle) * radius));
            var v = up.scale(Mth.cos(angle) * radius).subtract(side.scale(Mth.sin(angle) * radius));
            quad(center.subtract(u).add(v), center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v), 1, 0, UP, color);
        }

        /**
         * A whole texture lying flat.
         */
        void flat(Vec3 center, float radius, int color)
        {
            quad(center.add(-radius, 0, -radius), center.add(-radius, 0, radius), center.add(radius, 0, radius), center.add(radius, 0, -radius), 1, 0, UP, color);
        }

        /**
         * One frame of a texture standing on a point, fading out toward its top.
         */
        void upright(Vec3 foot, Vec3 across, float halfWidth, float height, int frame, int color)
        {
            var side = across.scale(halfWidth);
            var top = new Vec3(0, height, 0);
            int faded = ARGB.color(0, color);
            quad(foot.subtract(side).add(top), foot.subtract(side), foot.add(side), foot.add(side).add(top), FRAMES, frame, UP, faded, color, color, faded);
        }

        /**
         * A diamond of one color turned to the camera, for the plain glow.
         */
        void diamond(Vec3 center, float radius, int color)
        {
            var toCamera = camera.subtract(center);
            if (toCamera.lengthSqr() < 1e-6)
                return;
            toCamera = toCamera.normalize();
            var side = toCamera.cross(Math.abs(toCamera.y) > 0.99 ? new Vec3(1, 0, 0) : UP).normalize().scale(radius);
            var up = side.cross(toCamera);
            vertex(center.subtract(up), 0, 0, UP, color);
            vertex(center.add(side), 0, 1, UP, color);
            vertex(center.add(up), 1, 1, UP, color);
            vertex(center.subtract(side), 1, 0, UP, color);
        }

        /**
         * A strip between two points turned toward the camera, with a width and color at each end.
         */
        void ribbon(Vec3 from, Vec3 to, float fromWidth, float toWidth, int fromColor, int toColor)
        {
            var along = to.subtract(from);
            var side = along.cross(camera.subtract(from));
            if (side.lengthSqr() < 1e-9)
                return;
            side = side.normalize();
            vertex(from.subtract(side.scale(fromWidth)), 0, 0, UP, fromColor);
            vertex(from.add(side.scale(fromWidth)), 0, 1, UP, fromColor);
            vertex(to.add(side.scale(toWidth)), 1, 1, UP, toColor);
            vertex(to.subtract(side.scale(toWidth)), 1, 0, UP, toColor);
        }
    }
}
