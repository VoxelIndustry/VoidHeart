package net.voxelindustry.voidheart.common;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoidHeartTicker
{
    private final static Map<BlockPos, Runnable> tasksByPos = new ConcurrentHashMap<>();

    public static void tick(ServerWorld serverWorld)
    {
        if (tasksByPos.isEmpty())
            return;

        List<BlockPos> toRemove = new ArrayList<>();

        tasksByPos.forEach((pos, task) ->
        {
            if (serverWorld.isChunkLoaded(pos))
            {
                task.run();
                toRemove.add(pos);
            }
        });

        toRemove.forEach(tasksByPos::remove);
    }

    public static void addTaskForLoadedPos(BlockPos pos, Runnable runnable)
    {
        tasksByPos.put(pos, runnable);
    }
}
