package net.voxelindustry.voidheart.client.render;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Remembers whether each barrier last looked solid to the local player, to animate the moment it flips.
 */
public class BarrierTransitionTracker
{
    public static final int DURATION = 16;

    private static final int FORGET_AFTER = 200;

    private record Seen(boolean solid, float flippedAt, float lastSeen) {}

    private static final Map<BlockPos, Seen> SEEN = new HashMap<>();

    private static float lastCleanup;

    /**
     * @return the transition progress from 0 to 1 since the barrier last flipped, or -1 when it is not transitioning
     */
    public static float update(BlockPos pos, boolean solid, float time)
    {
        cleanup(time);

        var seen = SEEN.get(pos);
        if (seen == null)
        {
            // First sight of this barrier, it was not seen flipping
            SEEN.put(pos.immutable(), new Seen(solid, -DURATION, time));
            return -1;
        }

        float flippedAt = seen.solid() != solid ? time : seen.flippedAt();
        SEEN.put(pos.immutable(), new Seen(solid, flippedAt, time));

        float progress = (time - flippedAt) / DURATION;
        return progress >= 0 && progress < 1 ? progress : -1;
    }

    /**
     * @return the game tick at which the barrier last flipped, or empty when it was never seen flipping
     */
    public static OptionalLong flipTick(BlockPos pos)
    {
        var seen = SEEN.get(pos);
        return seen != null && seen.flippedAt() >= 0 ? OptionalLong.of((long) seen.flippedAt()) : OptionalLong.empty();
    }

    private static void cleanup(float time)
    {
        // Game time going backward means another world was joined
        if (time < lastCleanup)
            SEEN.clear();
        else if (time - lastCleanup >= FORGET_AFTER)
            SEEN.values().removeIf(seen -> time - seen.lastSeen() > FORGET_AFTER);
        else
            return;
        lastCleanup = time;
    }
}
