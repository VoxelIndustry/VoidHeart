package net.voxelindustry.voidheart.common.content.shroud;

import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;

/**
 * How far a shroud gate's seal has lifted. The void rises behind it from the threshold, its edge ragged in whole pixels, until the seal is gone; settling,
 * the seal comes back down from the lintel over it. Progress runs at an even pace, so turning back halfway goes on from where it was.
 */
public final class ShroudVeil
{
    /** Ticks for the seal to lift away entirely. */
    public static final int LIFT_TICKS = 60;
    /** Ticks for it to settle back entirely. */
    public static final int SETTLE_TICKS = 50;
    public static final int PIXELS = 16;
    public static final int OPENING_PIXELS = ShroudGate.SEAL_HEIGHT * PIXELS;
    public static final int COLUMNS = (2 * ShroudGate.SEAL_HALF_WIDTH + 1) * PIXELS;
    /** How far a column of the edge strays from the line, in pixels. */
    public static final int STRAY = 4;
    /** Columns between the points the edge's slow wave passes through. */
    private static final int WAVE_SPAN = 5;
    private static final long WAVE_SALT = 0x7368726F7564L;
    private static final long GRAIN_SALT = 0x677261696EL;

    private ShroudVeil()
    {
    }

    /**
     * @param progressAtChange the progress when the gate last opened or closed
     * @param sinceChange      ticks since then
     * @return 0 sealed to 1 open, at an even pace
     */
    public static float progress(boolean open, float progressAtChange, float sinceChange)
    {
        float since = Math.max(0, sinceChange);
        return open ? Math.min(1, progressAtChange + since / LIFT_TICKS) : Math.max(0, progressAtChange - since / SETTLE_TICKS);
    }

    /** @return how far the void has risen, 0 to 1, slow at both ends */
    public static float risen(float progress)
    {
        float clamped = Math.clamp(progress, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    /**
     * @param column the pixel column across the opening, 0 on the left as seen from the room
     * @param salt   what sets this gate's edge apart from others
     * @return how many pixels of that column the void fills, from the threshold up
     */
    public static int columnTop(float risen, int column, long salt)
    {
        int line = Math.round(risen * (OPENING_PIXELS + 2 * STRAY)) - STRAY;
        return Math.clamp(line + stray(column, salt), 0, OPENING_PIXELS);
    }

    /** A slow wave through a few random points, roughened by a pixel either way. */
    static int stray(int column, long salt)
    {
        int point = Math.floorDiv(column, WAVE_SPAN);
        float along = (column - point * WAVE_SPAN) / (float) WAVE_SPAN;
        int from = waveAt(point, salt);
        int to = waveAt(point + 1, salt);
        int wave = Math.round(from + (to - from) * along);
        int grain = DaedalusHash.pick(DaedalusHash.mix(salt ^ GRAIN_SALT, column, 0, 0), 3) - 1;
        return Math.clamp(wave + grain, -STRAY, STRAY);
    }

    private static int waveAt(int point, long salt)
    {
        return DaedalusHash.pick(DaedalusHash.mix(salt ^ WAVE_SALT, point, 0, 0), 2 * STRAY - 1) - (STRAY - 1);
    }

    /** @return the height of the void's edge over the threshold in blocks, where smoke leaves it */
    public static float edgeHeight(float risen)
    {
        return risen * ShroudGate.SEAL_HEIGHT;
    }
}
