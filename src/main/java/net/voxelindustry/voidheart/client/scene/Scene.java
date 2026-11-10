package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.beam.BeamResolver;
import net.voxelindustry.voidheart.common.content.beam.BeamSegment;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A short film of how something is built and used: steps of annotated action over real block models, written as a script and worked out from the time alone.
 * Nothing in it can be touched, and nothing in it runs: a scene has no world to fall out of step with.
 *
 * @param floor    blocks lying under the action from the first frame, neither animated nor part of what is taught
 * @param beam     the forge's beam, when the scene has one
 * @param rings    an armillary's rings, when the scene has one
 */
public record Scene(Identifier id, List<Step> steps, List<Cue> cues, List<Place> floor, @Nullable Beam beam, @Nullable VoidCube voidCube, @Nullable Rings rings)
{
    /**
     * @param view   where the camera moves to during this step
     * @param anchor what the step's text is about, in scene blocks
     * @param use    whether the step is something the player does with a right click
     */
    public record Step(String name, int ticks, SceneView view, Vec3 anchor, boolean use)
    {
    }

    /**
     * A point in time, held by step so that a step lengthened for its text keeps what happens in it together.
     */
    public record Moment(int step, int offset)
    {
        public static final Moment NEVER = new Moment(-1, 0);

        int resolve(SceneTimeline timeline)
        {
            return this == NEVER ? Integer.MAX_VALUE : timeline.at(step, offset);
        }
    }

    /** In place of a color: the one of the research's constellation. */
    public static final int THEME = -1;

    public sealed interface Cue permits Place, Clear, Ghost, Highlight, Flight, Reach, Link, Outline, Label, Click, Actor, Particles
    {
    }

    /**
     * What only its own renderer can show: blocks whose model is empty, and what machines do once they work.
     */
    public enum Role
    {
        COLLECTOR,
        SPIKE,
        ENGINE,
        FISSURE,
        /** A fissure closing over the two seconds after it starts to work. */
        FISSURE_CLOSING,
        BARRIER,
        REACTOR
    }

    /**
     * Something drawn by the renderer it has in the world, standing from one moment to another and at work from a third.
     *
     * @param works  when it starts doing what it does, never for something that only stands there
     * @param target where what it sends goes, in scene blocks, when it sends anything
     * @param rgb    the color of what it burns or carries, for those that take one
     * @param amount how much of it there is, for those that come in sizes: the width of a fissure
     */
    public record Actor(Moment from, Moment to, BlockPos pos, Role role, Moment works, @Nullable Vec3 target, int rgb, float amount) implements Cue
    {
    }

    /**
     * How a link shows what it carries.
     */
    public enum Flow
    {
        /** A plain line: a distance, or two things that belong together. */
        STILL,
        /** Pulses running from start to end, as essence does between relays. */
        PULSE,
        /** A jagged arc, as energy leaps between pylons. */
        ARC
    }

    /**
     * A block arriving. One that arrives where another already stands replaces it at once, which is how things light up.
     */
    public record Place(Moment at, BlockPos pos, BlockState state) implements Cue
    {
    }

    /**
     * A block leaving.
     */
    public record Clear(Moment at, BlockPos pos) implements Cue
    {
    }

    public record Ghost(Moment from, Moment to, BlockPos pos, BlockState state) implements Cue
    {
    }

    public record Highlight(Moment from, Moment to, BlockPos pos) implements Cue
    {
    }

    /**
     * An item appearing at start, travelling to end, and staying there until it is gone.
     */
    public record Flight(Moment from, int travel, Moment gone, ItemStack stack, Vec3 start, Vec3 end) implements Cue
    {
    }

    /**
     * How far something reaches: a dashed circle lying flat that spreads out from its middle, the way a pylon shows its field.
     *
     * @param radius in blocks from the center
     */
    public record Reach(Moment from, Moment to, Vec3 center, float radius, int rgb) implements Cue
    {
    }

    /**
     * A line from one point to another that grows from its start.
     */
    public record Link(Moment from, Moment to, Vec3 start, Vec3 end, int rgb, Flow flow) implements Cue
    {
    }

    /**
     * The edges of a box, around the room a structure takes or the blocks a gesture covers.
     */
    public record Outline(Moment from, Moment to, Vec3 min, Vec3 max, int rgb) implements Cue
    {
    }

    /**
     * A few words pinned to a point of the scene, for a number the picture cannot give by itself.
     */
    public record Label(Moment from, Moment to, Vec3 at, Component text) implements Cue
    {
    }

    /**
     * A right click landing on a point, once.
     */
    public record Click(Moment at, Vec3 pos, boolean sneak) implements Cue
    {
    }

    /**
     * Particles let out from one moment to another. Those born before the end live their lives out, and one that starts and ends at once is a single puff.
     */
    public record Particles(Moment from, Moment to, SceneEmitter emitter) implements Cue
    {
    }

    /**
     * @param view what the beam's strands look for, the scene's finished structure
     */
    public record Beam(Moment forms, Moment dissolves, SceneBeamView view, List<BeamSegment> segments)
    {
    }

    public record VoidCube(Moment warms, Moment cools)
    {
    }

    /**
     * An armillary's rings over its core, rising, turning as fast as what the armillary is doing asks, and tearing a portal open between them.
     *
     * @param spins   changes of pace, in order. Until the first, the rings turn at their steady pace with the inner ring lit
     * @param falters when the connection starts to fail, the rings stuttering worse until the portal closes
     * @param portal  the colour of the portal's rim
     */
    public record Rings(BlockPos core, Moment rises, List<Spin> spins, Moment opens, Moment falters, Moment closes, int portal)
    {
    }

    /**
     * @param warmth how lit the inner ring wants to be, from cold at 0 to glowing at 1
     */
    public record Spin(Moment at, float spin, float warmth)
    {
    }

    public String textKey(int step)
    {
        return "voidheart.scene." + id.getPath() + "." + steps.get(step).name();
    }

    public String titleKey()
    {
        return "voidheart.scene." + id.getPath();
    }

    /**
     * Times each step for the text it has to show.
     */
    public SceneTimeline timeline(java.util.function.ToIntFunction<String> textLength)
    {
        int[] durations = new int[steps.size()];
        for (int step = 0; step < durations.length; step++)
            durations[step] = SceneTimeline.duration(steps.get(step).ticks(), textLength.applyAsInt(textKey(step)));
        return new SceneTimeline(durations);
    }

    /**
     * What stands at the end of the scene.
     */
    public List<Place> finished()
    {
        var finished = new java.util.LinkedHashMap<BlockPos, Place>();
        for (var cue : cues)
        {
            if (cue instanceof Place place)
                finished.put(place.pos(), place);
            else if (cue instanceof Clear clear)
                finished.remove(clear.pos());
        }
        return List.copyOf(finished.values());
    }

    public static Builder builder(Identifier id)
    {
        return new Builder(id);
    }

    public static final class Builder
    {
        private final Identifier  id;
        private final List<Step>  steps = new ArrayList<>();
        private final List<Cue>   cues  = new ArrayList<>();
        private final List<Place> floor = new ArrayList<>();

        private BlockPos beamForge;
        private Moment   beamForms;
        private Moment   beamDissolves;
        private VoidCube voidCube;

        private BlockPos         ringsCore;
        private Moment           ringsRise;
        private final List<Spin> spins = new ArrayList<>();
        private Moment           portalOpens   = Moment.NEVER;
        private Moment           ringsFalter   = Moment.NEVER;
        private Moment           portalCloses  = Moment.NEVER;
        private int              portalRgb;

        private Builder(Identifier id)
        {
            this.id = id;
        }

        public Builder floor(int fromX, int fromZ, int toX, int toZ, int y, BlockState state)
        {
            for (int x = fromX; x <= toX; x++)
            {
                for (int z = fromZ; z <= toZ; z++)
                    floor.add(new Place(new Moment(0, 0), new BlockPos(x, y, z), state));
            }
            return this;
        }

        public Builder step(String name, int ticks, SceneView view, Vec3 anchor)
        {
            steps.add(new Step(name, ticks, view, anchor, false));
            return this;
        }

        public Builder use(String name, int ticks, SceneView view, Vec3 anchor)
        {
            steps.add(new Step(name, ticks, view, anchor, true));
            return this;
        }

        /** A moment of the step being written. */
        public Moment at(int offset)
        {
            return new Moment(steps.size() - 1, offset);
        }

        /** The start of the step after the one being written. */
        public Moment next()
        {
            return new Moment(steps.size(), 0);
        }

        public Builder place(int offset, BlockPos pos, BlockState state)
        {
            cues.add(new Place(at(offset), pos, state));
            return this;
        }

        public Builder clear(int offset, BlockPos pos)
        {
            cues.add(new Clear(at(offset), pos));
            return this;
        }

        public Builder ghost(int offset, Moment to, BlockPos pos, BlockState state)
        {
            cues.add(new Ghost(at(offset), to, pos, state));
            return this;
        }

        public Builder highlight(int offset, Moment to, BlockPos pos)
        {
            cues.add(new Highlight(at(offset), to, pos));
            return this;
        }

        public Builder flight(int offset, int travel, Moment gone, ItemStack stack, Vec3 start, Vec3 end)
        {
            cues.add(new Flight(at(offset), travel, gone, stack, start, end));
            return this;
        }

        public Builder reach(int offset, Moment to, Vec3 center, float radius, int rgb)
        {
            cues.add(new Reach(at(offset), to, center, radius, rgb));
            return this;
        }

        public Builder link(int offset, Moment to, Vec3 start, Vec3 end, int rgb, Flow flow)
        {
            cues.add(new Link(at(offset), to, start, end, rgb, flow));
            return this;
        }

        /** Around every block from one corner to the other, both included. */
        public Builder outline(int offset, Moment to, BlockPos from, BlockPos until, int rgb)
        {
            var min = BlockPos.min(from, until);
            var max = BlockPos.max(from, until);
            cues.add(new Outline(at(offset), to, Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max.offset(1, 1, 1)), rgb));
            return this;
        }

        public Builder label(int offset, Moment to, Vec3 at, Component text)
        {
            cues.add(new Label(at(offset), to, at, text));
            return this;
        }

        /** A distance: a line between two points with what it measures written at its middle. */
        public Builder measure(int offset, Moment to, Vec3 start, Vec3 end, Component text, int rgb)
        {
            link(offset, to, start, end, rgb, Flow.STILL);
            return label(offset + 6, to, start.lerp(end, 0.5), text);
        }

        public Builder actor(int offset, Moment to, BlockPos pos, Role role)
        {
            return actor(offset, to, pos, role, Moment.NEVER, null, THEME, 1);
        }

        public Builder actor(int offset, Moment to, BlockPos pos, Role role, Moment works, @Nullable Vec3 target, int rgb, float amount)
        {
            cues.add(new Actor(at(offset), to, pos, role, works, target, rgb, amount));
            return this;
        }

        public Builder particles(int offset, Moment to, SceneEmitter emitter)
        {
            cues.add(new Particles(at(offset), to, emitter));
            return this;
        }

        /** Everything the emitter lets out at once, and nothing after. */
        public Builder puff(int offset, SceneEmitter emitter)
        {
            return particles(offset, at(offset), emitter);
        }

        public Builder click(int offset, Vec3 pos)
        {
            cues.add(new Click(at(offset), pos, false));
            return this;
        }

        public Builder sneakClick(int offset, Vec3 pos)
        {
            cues.add(new Click(at(offset), pos, true));
            return this;
        }

        public Builder beam(int offset, Moment dissolves, BlockPos forge)
        {
            beamForge = forge;
            beamForms = at(offset);
            beamDissolves = dissolves;
            return this;
        }

        public Builder voidCube(int offset, Moment cools)
        {
            voidCube = new VoidCube(at(offset), cools);
            return this;
        }

        public Builder rings(int offset, BlockPos core)
        {
            ringsCore = core;
            ringsRise = at(offset);
            return this;
        }

        public Builder spin(int offset, float spin, float warmth)
        {
            spins.add(new Spin(at(offset), spin, warmth));
            return this;
        }

        public Builder portal(int offset, int rgb)
        {
            portalOpens = at(offset);
            portalRgb = rgb;
            return this;
        }

        /**
         * The connection failing from this moment, until its portal closes at closes.
         */
        public Builder falter(int offset, Moment closes)
        {
            ringsFalter = at(offset);
            portalCloses = closes;
            return this;
        }

        public Scene build()
        {
            var rings = ringsCore == null ? null : new Rings(ringsCore, ringsRise, List.copyOf(spins), portalOpens, ringsFalter, portalCloses, portalRgb);
            var scene = new Scene(id, List.copyOf(steps), List.copyOf(cues), List.copyOf(floor), null, voidCube, rings);
            if (beamForge == null)
                return scene;

            // The beam is resolved by the rules the real forge follows, so a scene whose structure would not work has no beam to show
            var view = new SceneBeamView(scene.finished());
            var resolution = BeamResolver.resolve(view, beamForge);
            return new Scene(id, scene.steps(), scene.cues(), scene.floor(), new Beam(beamForms, beamDissolves, view, resolution.segments()), voidCube, rings);
        }
    }
}
