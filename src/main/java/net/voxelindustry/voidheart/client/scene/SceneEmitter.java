package net.voxelindustry.voidheart.client.scene;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A fountain of particles written as arithmetic: when each is born, where it is at any age and how it looks then all follow from its number.
 * Nothing is spawned and nothing ticks, so a scene held on a tick or jumped to shows the very specks that playing up to it would have.
 *
 * @param rate  births a tick for as long as the emitter runs
 * @param burst how many more are born at once as it starts, for a puff
 * @param life  ticks a particle lasts
 * @param vary  how much lives differ, as a share of life either way
 */
public record SceneEmitter(Look look, Path path, float rate, int burst, float life, float vary)
{
    // Crumbs are a quarter of their item's picture each way
    public static final int CRUMB_SLICES = 4;

    private static final float SIZE_VARY    = 0.2F;
    private static final float BECOMES_FROM = 0.38F;
    private static final float BECOMES_SPAN = 0.24F;
    private static final float LIFT_SHARE   = 0.5F;

    public sealed interface Sprite permits Frames, Crumbs
    {
    }

    /**
     * Pictures of the particle atlas, named as the game's own particle files name them.
     *
     * @param aged whether a particle plays them through over its life, where otherwise each keeps one drawn by lot
     */
    public record Frames(List<Identifier> frames, boolean aged) implements Sprite
    {
    }

    /**
     * Pieces of an item's own picture, the way the altar and the forge crumble what they take.
     *
     * @param becomes what the crumbs turn into partway, for something changed as it travels
     */
    public record Crumbs(ItemStack stack, @Nullable ItemStack becomes) implements Sprite
    {
    }

    /**
     * @param rgb     its tint, or Scene.THEME
     * @param size    half the side of its square in blocks, as the game's own particles count theirs
     * @param end     the share of its size it has left as it dies
     * @param fadeIn  the share of its life it takes to show
     * @param fadeOut the share of its life it takes to go
     */
    public record Look(Sprite sprite, int rgb, float size, float end, float alpha, float fadeIn, float fadeOut)
    {
    }

    public sealed interface Path permits Drift, Stream, Spiral
    {
    }

    /**
     * Born somewhere in a box, thrown, then pulled: smoke, sparks, a puff.
     *
     * @param box     how far from the origin a particle may be born, along each axis
     * @param scatter how much each particle's throw differs, along each axis
     * @param pull    what speeds it up every tick: weight, or the lift of hot air
     */
    public record Drift(Vec3 origin, Vec3 box, Vec3 velocity, Vec3 scatter, Vec3 pull) implements Path
    {
    }

    /**
     * From somewhere in a box to a point over its life, along a line bent a little differently for each.
     *
     * @param bow how far the line bends, as a share of its length
     */
    public record Stream(Vec3 start, Vec3 box, Vec3 end, float bow) implements Path
    {
    }

    /**
     * Winding around a straight line from start to end, as what a forge's beam carries does.
     *
     * @param twist how far it turns for each block travelled, in radians
     */
    public record Spiral(Vec3 start, Vec3 end, float radius, float twist) implements Path
    {
    }

    /**
     * How long after it stops the last of its particles is gone.
     */
    float longest()
    {
        return life * (1 + vary);
    }

    /**
     * @param seed   what tells this emitter's rolls from another's
     * @param since  ticks since it started
     * @param lasted ticks it runs for. Particles born before then live their lives out
     */
    void shown(long seed, float since, float lasted, List<SceneFrame.ShownParticle> into)
    {
        if (since < 0 || since >= lasted + longest())
            return;

        for (int index = 0; index < burst; index++)
            add(seed, index, since, into);
        if (rate <= 0)
            return;

        long first = Math.max(0, (long) Math.floor((since - longest()) * rate) - 1);
        long last = (long) Math.floor(Math.min(since, lasted) * rate);
        for (long index = first; index <= last; index++)
        {
            // Births fall anywhere within their turn, since specks leaving on a beat read as a machine counting
            float born = (index + roll(seed, burst + index, 0)) / rate;
            if (born < lasted && born <= since)
                add(seed, burst + index, since - born, into);
        }
    }

    private void add(long seed, long index, float age, List<SceneFrame.ShownParticle> into)
    {
        float lives = life * (1 + vary * (roll(seed, index, 1) * 2 - 1));
        if (age >= lives)
            return;

        float progress = age / lives;
        var at = switch (path)
        {
            case Drift drift -> drift(drift, seed, index, age);
            case Stream stream -> stream(stream, seed, index, progress);
            case Spiral spiral -> spiral(spiral, seed, index, progress);
        };

        float size = look.size() * (1 + (look.end() - 1) * progress) * (1 - SIZE_VARY + 2 * SIZE_VARY * roll(seed, index, 2));
        float fadeIn = look.fadeIn() <= 0 ? 1 : Math.min(1, progress / look.fadeIn());
        float fadeOut = look.fadeOut() <= 0 ? 1 : Math.min(1, (1 - progress) / look.fadeOut());

        int frame = switch (look.sprite())
        {
            case Frames frames -> frames.aged() ? Math.min(frames.frames().size() - 1, (int) (progress * frames.frames().size()))
                    : (int) (roll(seed, index, 3) * frames.frames().size());
            case Crumbs crumbs ->
            {
                int slice = (int) (roll(seed, index, 3) * CRUMB_SLICES * CRUMB_SLICES);
                boolean became = crumbs.becomes() != null && progress >= BECOMES_FROM + BECOMES_SPAN * roll(seed, index, 4);
                yield became ? slice + CRUMB_SLICES * CRUMB_SLICES : slice;
            }
        };
        into.add(new SceneFrame.ShownParticle(look.sprite(), frame, at.x, at.y, at.z, size, look.alpha() * fadeIn * fadeOut, look.rgb()));
    }

    private static Vec3 drift(Drift drift, long seed, long index, float age)
    {
        var born = drift.origin().add(
                drift.box().x * spread(seed, index, 5), drift.box().y * spread(seed, index, 6), drift.box().z * spread(seed, index, 7));
        var thrown = drift.velocity().add(
                drift.scatter().x * bell(seed, index, 8), drift.scatter().y * bell(seed, index, 10), drift.scatter().z * bell(seed, index, 12));
        return born.add(thrown.scale(age)).add(drift.pull().scale(age * age / 2));
    }

    private static Vec3 stream(Stream stream, long seed, long index, float progress)
    {
        var start = stream.start().add(
                stream.box().x * spread(seed, index, 5), stream.box().y * spread(seed, index, 6), stream.box().z * spread(seed, index, 7));
        var along = stream.end().subtract(start);
        double distance = along.length();
        if (distance < 1.0E-4)
            return start;

        var side = sideOf(along);
        var lift = side.cross(along).normalize();
        var bend = start.add(along.scale(0.5))
                .add(side.scale(distance * stream.bow() * spread(seed, index, 8)))
                .add(lift.scale(distance * stream.bow() * LIFT_SHARE * spread(seed, index, 9)));

        double left = 1 - progress;
        return start.scale(left * left).add(bend.scale(2 * left * progress)).add(stream.end().scale(progress * progress));
    }

    private static Vec3 spiral(Spiral spiral, long seed, long index, float progress)
    {
        var along = spiral.end().subtract(spiral.start());
        double distance = along.length();
        if (distance < 1.0E-4)
            return spiral.start();

        var side = sideOf(along);
        var lift = side.cross(along).normalize();
        // Half of them start across the line from the other half
        double angle = spiral.twist() * distance * progress + (roll(seed, index, 5) < 0.5F ? Math.PI : 0);
        return spiral.start().add(along.scale(progress))
                .add(side.scale(spiral.radius() * Math.cos(angle)))
                .add(lift.scale(spiral.radius() * Math.sin(angle)));
    }

    /**
     * Level and across the way travelled, or along X for what travels straight up or down.
     */
    private static Vec3 sideOf(Vec3 along)
    {
        var side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8)
            side = along.cross(new Vec3(1, 0, 0));
        return side.normalize();
    }

    /** Evenly from -1 to 1. */
    private static float spread(long seed, long index, int channel)
    {
        return roll(seed, index, channel) * 2 - 1;
    }

    /** From -1 to 1 and more often near 0, as throws are. */
    private static float bell(long seed, long index, int channel)
    {
        return roll(seed, index, channel) + roll(seed, index, channel + 1) - 1;
    }

    private static float roll(long seed, long index, int channel)
    {
        long mixed = seed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L + channel * 0x94D049BB133111EBL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xff51afd7ed558ccdL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return ((mixed ^ (mixed >>> 33)) & 0xFFFFFF) / (float) 0x1000000;
    }
}
