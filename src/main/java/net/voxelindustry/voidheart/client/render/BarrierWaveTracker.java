package net.voxelindustry.voidheart.client.render;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierWavePayload;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierStructure;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side timing of the link wave, which spreads one block per tick from the block that joined a structure.
 */
public class BarrierWaveTracker
{
    public static final int TICKS_PER_BLOCK = 1;
    public static final int WAVE_DURATION   = 8;

    private static final Map<BlockPos, Long> WAVE_STARTS = new HashMap<>();

    public static void handle(BarrierWavePayload payload, ClientPlayNetworking.Context context)
    {
        var level = context.client().level;
        if (level == null)
            return;

        long now = level.getGameTime();
        WAVE_STARTS.entrySet().removeIf(entry -> now - entry.getValue() > WAVE_DURATION);
        PermeableBarrierStructure.collect(level, payload.origin())
                .forEach((pos, distance) -> WAVE_STARTS.put(pos, now + (long) distance * TICKS_PER_BLOCK));
    }

    /**
     * @return the wave brightness at this block, from 0 to 1
     */
    public static float getIntensity(BlockPos pos, float time)
    {
        var start = WAVE_STARTS.get(pos);
        if (start == null)
            return 0;

        float progress = (time - start) / WAVE_DURATION;
        if (progress > 1)
            WAVE_STARTS.remove(pos);
        if (progress < 0 || progress > 1)
            return 0;
        return (float) Math.sin(progress * Math.PI);
    }
}
