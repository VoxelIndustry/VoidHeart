package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
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
 */
public record Scene(Identifier id, List<Step> steps, List<Cue> cues, List<Place> floor, @Nullable Beam beam, @Nullable VoidCube voidCube)
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

    public sealed interface Cue permits Place, Ghost, Highlight, Flight
    {
    }

    /**
     * A block arriving. One that arrives where another already stands replaces it at once, which is how things light up.
     */
    public record Place(Moment at, BlockPos pos, BlockState state) implements Cue
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
     * @param view what the beam's strands look for, the scene's finished structure
     */
    public record Beam(Moment forms, Moment dissolves, SceneBeamView view, List<BeamSegment> segments)
    {
    }

    public record VoidCube(Moment warms, Moment cools)
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

        public Scene build()
        {
            var scene = new Scene(id, List.copyOf(steps), List.copyOf(cues), List.copyOf(floor), null, voidCube);
            if (beamForge == null)
                return scene;

            // The beam is resolved by the rules the real forge follows, so a scene whose structure would not work has no beam to show
            var view = new SceneBeamView(scene.finished());
            var resolution = BeamResolver.resolve(view, beamForge);
            return new Scene(id, scene.steps(), scene.cues(), scene.floor(), new Beam(beamForms, beamDissolves, view, resolution.segments()), voidCube);
        }
    }
}
