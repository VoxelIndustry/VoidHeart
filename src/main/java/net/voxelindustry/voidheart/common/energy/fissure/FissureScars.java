package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Faint scars left on the client where a fissure or wound has closed, fading over about a minute. Filled by the tile as the closed block leaves the
 * client, and drawn and pruned by the seep renderer.
 */
public final class FissureScars
{
    public static final int FADE_TICKS = 1200;

    public record Scar(BlockPos pos, Direction.Axis axis, float width, boolean wound, long seed, long since)
    {
    }

    private static final List<Scar> SCARS = new ArrayList<>();

    private FissureScars()
    {
    }

    static void leave(VoidFissureTile tile)
    {
        var level = tile.getLevel();
        if (level == null || !tile.getBlockState().hasProperty(VoidFissureBlock.AXIS))
            return;
        SCARS.add(new Scar(tile.getBlockPos(), tile.getBlockState().getValue(VoidFissureBlock.AXIS), tile.size(), tile.isWound(), tile.seed(),
                tile.closingSince() + VoidFissureBalance.CLOSE_TICKS));
    }

    public static List<Scar> scars()
    {
        return SCARS;
    }

    public static void prune(long now)
    {
        SCARS.removeIf(scar -> now - scar.since() > FADE_TICKS || now < scar.since() - FADE_TICKS);
    }

    public static void clear()
    {
        SCARS.clear();
    }
}
