package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

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
 */
public record SceneFrame(List<ShownBlock> blocks, List<ShownBlock> ghosts, List<Lit> highlights, List<ShownItem> items, float beam, boolean forming, int warm, int cool)
{
    private static final float APPEAR_TICKS    = 10;
    private static final float GHOST_FADE      = 12;
    private static final float FLASH_TICKS     = 24;
    private static final float BEAM_TICKS      = 45;
    private static final int   COOL_TICKS      = 60;

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

        return new SceneFrame(List.copyOf(blocks.values()), ghosts, highlights, items, beam, forming, warm, cool);
    }
}
