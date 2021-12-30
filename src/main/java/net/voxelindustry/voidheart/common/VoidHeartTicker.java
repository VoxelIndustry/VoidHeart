package net.voxelindustry.voidheart.common;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoidHeartTicker
{
    private final static Map<BlockPos, Runnable> tasksByPos = new ConcurrentHashMap<>();

    private final static Multimap<Integer, Runnable> delayedTasks = MultimapBuilder.treeKeys().arrayListValues().build();

    public static void tickWorld(ServerWorld serverWorld)
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

    public static void tickServer(MinecraftServer server)
    {
        if (delayedTasks.isEmpty())
            return;

        var delaysToDelete = new HashSet<Integer>(1);
        for (var taskByDelay : delayedTasks.entries())
        {
            if (taskByDelay.getKey() <= server.getTicks())
            {
                taskByDelay.getValue().run();
                delaysToDelete.add(taskByDelay.getKey());
            }
            else
                break;
        }

        for (var delay : delaysToDelete)
            delayedTasks.removeAll(delay);
    }

    public static void addTaskForLoadedPos(BlockPos pos, Runnable runnable)
    {
        tasksByPos.put(pos, runnable);
    }

    public static void addDelayedTask(MinecraftServer server, int delay, Runnable runnable)
    {
        delayedTasks.put(server.getTicks() + delay, runnable);
    }
}
