package net.voxelindustry.voidheart.client.armor;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.levitation.BeltLift;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import org.joml.Vector3f;

import java.util.List;

/**
 * The wings of each flying Void Armor frame in sight, kept on the client from tick to tick. How far they are open follows the flight: they unfurl when it
 * starts, pull back in on landing, and thin and fray while the lift fades after the frame cuts them. Each filament is a chain of weights ({@link WingSim})
 * drawn toward the wing's shape ({@link WingShape}) in the world, so gravity sags its tips and the wearer's turns, climbs and dives swing them.
 * Handed to {@link ArmorWingsRender} through the render state, in the frame's model space.
 */
public final class ArmorWings
{
    public static final RenderStateDataKey<Wings> KEY = RenderStateDataKey.create(() -> "voidheart:armor_wings");

    /** Ticks for the wings to open fully, and to close. */
    private static final float OPEN_TICKS  = 12;
    private static final float CLOSE_TICKS = 10;
    /** Forward speed in blocks a tick at which the wings fold back fully. */
    private static final float FULL_TRAIL  = 0.6F;
    /** Share of the gap to the new trail closed each tick, so a sudden stop eases the wings back out. */
    private static final float TRAIL_EASE  = 0.15F;

    // The weight of the filaments: stiff at the root, slack at the tip, per tick squared; gravity in blocks a tick squared; speed kept against the air a tick
    private static final float ROOT_STIFFNESS = 1.6F;
    private static final float TIP_STIFFNESS  = 0.08F;
    private static final float GRAVITY        = 0.07F;
    private static final float AIR_KEEP       = 0.82F;

    // The beat: a slow one while hovering, quicker and deeper while climbing, in cycles a tick
    private static final float BEAT_HOVER     = 1 / 44F;
    private static final float BEAT_CLIMB     = 1 / 22F;
    private static final float DEPTH_HOVER    = 0.55F;
    private static final float DEPTH_CLIMB    = 1F;
    /** Rising speed in blocks a tick at which the beat is at its quickest. */
    private static final float FULL_CLIMB     = 0.3F;

    // The air moving along the filaments, a force across the wing that runs out from the root
    private static final float RIPPLE         = 0.018F;
    private static final float RIPPLE_SPEED   = 0.12F;
    private static final float RIPPLE_SPACING = 0.45F;

    // The frame's back and legs, in model units, which the filaments slide over rather than pass through
    private static final float BODY_HALF_WIDTH = 6F / 16;
    private static final float BODY_TOP        = -0.1F;
    private static final float BODY_BOTTOM     = 1.5F;
    private static final float BODY_BACK       = 4.6F / 16;

    /** A move further than this in a tick is a teleport: the wings stand back in shape rather than whip across. */
    private static final float TELEPORT        = 4;
    /** The simulation keeps its own origin near the wearer, so floats keep their precision far from the world's. */
    private static final double RE_ANCHOR      = 32;

    /**
     * @param open  0 folded away, 1 fully spread
     * @param fray  0 whole, 1 frayed to almost nothing as the lift fades
     * @param trail 0 hovering, 1 folded back behind a wearer flying forward fast
     * @param time  ticks, for the shimmer along the filaments
     * @param seed  what keeps each wearer's filaments their own
     */
    public record Wings(float open, float fray, float trail, float time, int seed, List<Filament> filaments)
    {
        public static final Wings NONE = new Wings(0, 0, 0, 0, 0, List.of());

        public boolean shown()
        {
            return open > 0;
        }
    }

    /**
     * One filament as drawn this frame, in model space.
     *
     * @param growth how far it has grown out, 0 to 1
     * @param length its full length in model units
     * @param facing the way its ribbon faces, out of the wing's plane
     */
    public record Filament(Vector3f[] points, float growth, float length, Vector3f facing, int seed)
    {
    }

    private static final class Anim
    {
        float  open, prevOpen;
        float  trail;
        float  fray;
        float  cycle;
        float  depth = DEPTH_HOVER;
        Vec3   anchor;
        boolean seen;
        final WingSim[] sims = new WingSim[2 * WingShape.FILAMENTS];

        Anim(Vec3 anchor)
        {
            this.anchor = anchor;
            for (int i = 0; i < sims.length; i++)
                sims[i] = new WingSim(WingShape.NODES);
        }
    }

    private static final Int2ObjectOpenHashMap<Anim> ANIMS = new Int2ObjectOpenHashMap<>();

    private static final Vector3f[] TARGETS = points(WingShape.NODES);
    private static final Vector3f[] MODEL   = points(WingShape.NODES);
    private static final Vector3f[] PUSH    = points(WingShape.NODES);

    private ArmorWings()
    {
    }

    private static Vector3f[] points(int count)
    {
        var points = new Vector3f[count];
        for (int i = 0; i < count; i++)
            points[i] = new Vector3f();
        return points;
    }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(ArmorWings::tick);
    }

    private static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null)
        {
            ANIMS.clear();
            return;
        }
        if (minecraft.isPaused())
            return;
        long now = level.getGameTime();
        for (var anim : ANIMS.values())
            anim.seen = false;
        for (var player : level.players())
        {
            float target = 0;
            float fray = 0;
            if (ArmorWear.wearing(player))
            {
                var state = LevitationBelts.state(player);
                if (state.flying())
                    target = 1;
                else if (state.fading())
                {
                    // The wings last as long as the lift they give
                    float faded = (float) BeltLift.progress(state.elapsed(now));
                    target = 1 - faded;
                    fray = faded;
                }
            }
            var anim = ANIMS.get(player.getId());
            if (anim == null)
            {
                if (target <= 0)
                    continue;
                anim = new Anim(player.position());
                ANIMS.put(player.getId(), anim);
            }
            anim.seen = true;
            anim.prevOpen = anim.open;
            anim.open = anim.open < target ? Math.min(target, anim.open + 1 / OPEN_TICKS) : Math.max(target, anim.open - 1 / CLOSE_TICKS);
            anim.fray = Math.max(fray, anim.open < anim.prevOpen ? anim.fray : 0);
            anim.trail = Mth.clamp(anim.trail + (forwardSpeed(player) / FULL_TRAIL - anim.trail) * TRAIL_EASE, 0, 1);
            float climb = Mth.clamp((float) player.getDeltaMovement().y / FULL_CLIMB, 0, 1);
            anim.depth += (Mth.lerp(climb, DEPTH_HOVER, DEPTH_CLIMB) - anim.depth) * TRAIL_EASE;
            anim.cycle += Mth.lerp(climb, BEAT_HOVER, BEAT_CLIMB);
            simulate(player, anim);
        }
        ANIMS.int2ObjectEntrySet().removeIf(entry -> !entry.getValue().seen || entry.getValue().open <= 0 && entry.getValue().prevOpen <= 0);
    }

    /**
     * Runs a tick of every filament, toward the wing's shape stood where the wearer is now.
     */
    private static void simulate(Player player, Anim anim)
    {
        var feet = player.position().subtract(anim.anchor);
        if (feet.length() > RE_ANCHOR)
        {
            var shift = new Vector3f((float) feet.x, (float) feet.y, (float) feet.z).negate();
            for (var sim : anim.sims)
                sim.shift(shift);
            anim.anchor = player.position();
            feet = Vec3.ZERO;
        }
        var origin = new Vector3f((float) feet.x, (float) feet.y, (float) feet.z);
        var frame = new WingFrame(player.yBodyRot, player.getScale());
        var facing = new Vector3f();
        var across = new Vector3f();
        var zero = frame.toWorld(new Vector3f(), new Vector3f());
        int wearer = seed(player);
        float beat = WingShape.beat(anim.cycle) * anim.depth;
        WingSim.Solid body = node -> keepOffBody(frame, origin, node);

        for (int side = -1, s = 0; side <= 1; side += 2, s++)
            for (int i = 0; i < WingShape.FILAMENTS; i++)
            {
                int seed = WingShape.seed(wearer, side, i);
                float growth = WingShape.growth(i, anim.open);
                WingShape.rest(side, i, seed, growth, beat, anim.trail, MODEL);
                for (int j = 0; j < WingShape.NODES; j++)
                    frame.toWorld(MODEL[j], TARGETS[j]).add(origin);

                // The air ripples across the wing and out along each filament
                frame.toWorld(WingShape.facing(side, i, seed, facing), across).sub(zero);
                float pace = 0.8F + 0.4F * WingShape.noise(seed, 1);
                float phase = WingShape.noise(seed, 2) * Mth.TWO_PI;
                for (int j = 0; j < WingShape.NODES; j++)
                {
                    float u = j / (float) (WingShape.NODES - 1);
                    float wave = RIPPLE * u * Mth.sin(player.tickCount * RIPPLE_SPEED * pace - j * RIPPLE_SPACING + phase);
                    PUSH[j].set(across).mul(wave);
                }

                var sim = anim.sims[s * WingShape.FILAMENTS + i];
                if (sim.settled() && sim.position(0).distance(TARGETS[0]) > TELEPORT)
                    sim.reset(TARGETS);
                sim.step(TARGETS, ROOT_STIFFNESS, TIP_STIFFNESS, GRAVITY, AIR_KEEP, PUSH, body);
            }
    }

    /**
     * Slides a node out the back of the frame if it has swung into its body or legs.
     */
    private static void keepOffBody(WingFrame frame, Vector3f origin, Vector3f node)
    {
        var model = frame.toModel(new Vector3f(node).sub(origin), new Vector3f());
        if (Math.abs(model.x) >= BODY_HALF_WIDTH || model.y <= BODY_TOP || model.y >= BODY_BOTTOM || model.z >= BODY_BACK)
            return;
        model.z = BODY_BACK;
        frame.toWorld(model, node).add(origin);
    }

    private static int seed(Player player)
    {
        return player.getUUID().hashCode() * 0x9E3779B9;
    }

    /**
     * How fast the wearer moves the way their body faces, in blocks a tick; moving backward doesn't fold the wings.
     */
    private static float forwardSpeed(Player player)
    {
        var motion = player.getDeltaMovement();
        var facing = Vec3.directionFromRotation(0, player.yBodyRot);
        return (float) Math.max(0, motion.x * facing.x + motion.z * facing.z);
    }

    public static Wings of(Player player, float partialTicks)
    {
        var anim = ANIMS.get(player.getId());
        if (anim == null)
            return Wings.NONE;
        float open = Mth.lerp(partialTicks, anim.prevOpen, anim.open);
        if (open <= 0)
            return Wings.NONE;

        var feet = player.getPosition(partialTicks).subtract(anim.anchor);
        var origin = new Vector3f((float) feet.x, (float) feet.y, (float) feet.z);
        var frame = new WingFrame(Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot), player.getScale());
        int wearer = seed(player);
        var filaments = new java.util.ArrayList<Filament>(2 * WingShape.FILAMENTS);
        var world = new Vector3f();
        for (int side = -1, s = 0; side <= 1; side += 2, s++)
            for (int i = 0; i < WingShape.FILAMENTS; i++)
            {
                int seed = WingShape.seed(wearer, side, i);
                float growth = WingShape.growth(i, open);
                if (growth <= 0)
                    continue;
                var sim = anim.sims[s * WingShape.FILAMENTS + i];
                var points = new Vector3f[sim.nodes()];
                for (int j = 0; j < points.length; j++)
                    points[j] = frame.toModel(sim.shown(j, partialTicks, world).sub(origin), new Vector3f());
                // The full length, for the texture along it, from the shape at rest
                float length = WingShape.rest(side, i, seed, 1, 0, 0, MODEL);
                filaments.add(new Filament(points, growth, length, WingShape.facing(side, i, seed, new Vector3f()), seed));
            }
        return new Wings(open, anim.fray, anim.trail, player.tickCount + partialTicks, wearer, filaments);
    }
}
