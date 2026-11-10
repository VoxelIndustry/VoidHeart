package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryMotion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Everything a scene shows at one tick.
 *
 * @param beam     how formed the beam is, 0 when there is none to draw
 * @param forming  whether the beam is coming or going, which it draws differently
 * @param warm     ticks since the altar's void began warming, 0 when it does not show
 * @param cool     ticks left of the void's shrinking, 0 unless it is shrinking
 * @param rings    the armillary's rings, null when the scene has none
 */
public record SceneFrame(List<ShownBlock> blocks, List<ShownBlock> ghosts, List<Lit> highlights, List<ShownItem> items, float beam, boolean forming, int warm, int cool,
                         @Nullable ShownRings rings)
{
    private static final float APPEAR_TICKS    = 10;
    private static final float GHOST_FADE      = 12;
    private static final float FLASH_TICKS     = 24;
    private static final float BEAM_TICKS      = 45;
    private static final int   COOL_TICKS      = 60;
    private static final float PORTAL_OPEN_TICKS  = 45;
    private static final float PORTAL_CLOSE_TICKS = 30;

    /**
     * @param appear how far along its arrival it is, 1 when it has settled. For a ghost, how visible it is
     */
    public record ShownBlock(BlockPos pos, BlockState state, float appear)
    {
    }

    public record Lit(BlockPos pos, float strength)
    {
    }

    public record ShownItem(ItemStack stack, double x, double y, double z)
    {
    }

    /**
     * @param formation how far the rings have risen, from 0 to 1
     * @param angles    each ring's turn, in radians
     * @param warmth    how lit the inner ring is
     * @param falter    how badly the rings stutter, from 0 to 1
     * @param portal    how open the portal is, from 0 to 1
     */
    public record ShownRings(float formation, float[] angles, float warmth, float falter, float portal)
    {
    }

    /**
     * A highlight points something out once, rising and falling a single time however long its step lasts.
     */
    static float flash(float since)
    {
        return since <= 0 || since >= FLASH_TICKS ? 0 : (float) Math.sin(since / FLASH_TICKS * Math.PI);
    }

    public static SceneFrame of(Scene scene, SceneTimeline timeline, float ticks)
    {
        var blocks = new LinkedHashMap<BlockPos, ShownBlock>();
        var ghosts = new ArrayList<ShownBlock>();
        var highlights = new ArrayList<Lit>();
        var items = new ArrayList<ShownItem>();

        for (var cue : scene.cues())
        {
            switch (cue)
            {
                case Scene.Place place ->
                {
                    float since = ticks - place.at().resolve(timeline);
                    if (since < 0)
                        continue;
                    var standing = blocks.get(place.pos());
                    float appear = standing != null ? standing.appear() : Math.min(1, since / APPEAR_TICKS);
                    blocks.put(place.pos(), new ShownBlock(place.pos(), place.state(), appear));
                }
                case Scene.Ghost ghost ->
                {
                    float since = ticks - ghost.from().resolve(timeline);
                    float left = ghost.to().resolve(timeline) - ticks;
                    if (since >= 0 && left > 0)
                        ghosts.add(new ShownBlock(ghost.pos(), ghost.state(), Math.min(1, Math.min(since, left) / GHOST_FADE)));
                }
                case Scene.Highlight highlight ->
                {
                    float strength = flash(ticks - highlight.from().resolve(timeline));
                    if (strength > 0 && ticks < highlight.to().resolve(timeline))
                        highlights.add(new Lit(highlight.pos(), strength));
                }
                case Scene.Flight flight ->
                {
                    float since = ticks - flight.from().resolve(timeline);
                    if (since < 0 || ticks >= flight.gone().resolve(timeline))
                        continue;
                    float t = flight.travel() <= 0 ? 1 : Math.min(1, since / flight.travel());
                    double eased = t * t * (3 - 2 * t);
                    items.add(new ShownItem(flight.stack(),
                            flight.start().x + (flight.end().x - flight.start().x) * eased,
                            flight.start().y + (flight.end().y - flight.start().y) * eased,
                            flight.start().z + (flight.end().z - flight.start().z) * eased));
                }
            }
        }

        float beam = 0;
        boolean forming = true;
        if (scene.beam() != null)
        {
            float since = ticks - scene.beam().forms().resolve(timeline);
            float after = ticks - scene.beam().dissolves().resolve(timeline);
            forming = after < 0;
            if (since >= 0)
                beam = forming ? Math.min(1, since / BEAM_TICKS) : Math.max(0, 1 - after / BEAM_TICKS);
        }

        int warm = 0;
        int cool = 0;
        if (scene.voidCube() != null)
        {
            int since = (int) (ticks - scene.voidCube().warms().resolve(timeline));
            int after = (int) (ticks - scene.voidCube().cools().resolve(timeline));
            if (since >= 0 && after < COOL_TICKS)
            {
                warm = Math.max(1, since);
                cool = after >= 0 ? COOL_TICKS - after : 0;
            }
        }

        var rings = scene.rings() == null ? null : rings(scene.rings(), timeline, ticks);
        return new SceneFrame(List.copyOf(blocks.values()), ghosts, highlights, items, beam, forming, warm, cool, rings);
    }

    /**
     * The rings carry their speed from tick to tick as a real armillary's do, so they are played from the start up to the tick asked for.
     * That is a few thousand steps of arithmetic at most, cheaper than any block the scene draws.
     */
    static ShownRings rings(Scene.Rings script, SceneTimeline timeline, float ticks)
    {
        int rises = script.rises().resolve(timeline);
        int falters = script.falters().resolve(timeline);
        int closes = script.closes().resolve(timeline);
        var spinAt = new int[script.spins().size()];
        for (int index = 0; index < spinAt.length; index++)
            spinAt[index] = script.spins().get(index).at().resolve(timeline);

        float formation = 0;
        float spin = ArmillaryMotion.STEADY_SPIN;
        float warmth = 1;
        var angles = new float[ArmillaryMotion.RINGS];
        var previous = new float[ArmillaryMotion.RINGS];
        float previousFormation = 0;
        float previousWarmth = warmth;

        int last = (int) Math.floor(ticks) + 1;
        for (int tick = 0; tick <= last; tick++)
        {
            float wantedSpin = ArmillaryMotion.STEADY_SPIN;
            float wantedWarmth = 1;
            for (int index = 0; index < spinAt.length && spinAt[index] <= tick; index++)
            {
                wantedSpin = script.spins().get(index).spin();
                wantedWarmth = script.spins().get(index).warmth();
            }

            previousFormation = formation;
            previousWarmth = warmth;
            System.arraycopy(angles, 0, previous, 0, angles.length);

            formation = ArmillaryMotion.form(formation, tick >= rises);
            spin = ArmillaryMotion.easeSpin(spin, ArmillaryMotion.faltering(wantedSpin, falter(tick, falters, closes)));
            warmth = ArmillaryMotion.warm(warmth, wantedWarmth);
            for (int ring = 0; ring < angles.length; ring++)
                angles[ring] += ArmillaryMotion.ringSpeed(ring) * spin * formation;
        }

        float partial = ticks - (last - 1);
        var shown = new float[angles.length];
        for (int ring = 0; ring < angles.length; ring++)
            shown[ring] = previous[ring] + (angles[ring] - previous[ring]) * partial;

        float opened = Math.clamp((ticks - script.opens().resolve(timeline)) / PORTAL_OPEN_TICKS, 0, 1);
        float closing = Math.clamp((ticks - closes) / PORTAL_CLOSE_TICKS, 0, 1);
        return new ShownRings(previousFormation + (formation - previousFormation) * partial, shown, previousWarmth + (warmth - previousWarmth) * partial,
                falter(ticks, falters, closes), Math.min(opened, 1 - closing));
    }

    /**
     * Worsening from the moment the connection starts to fail until it closes, then gone, since the rings go back to scanning.
     */
    static float falter(float ticks, int falters, int closes)
    {
        if (ticks < falters || ticks >= closes)
            return 0;
        return Math.clamp((ticks - falters) / Math.max(1, closes - falters), 0, 1);
    }
}
