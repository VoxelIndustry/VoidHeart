package net.voxelindustry.voidheart.common;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.minecraft.server.MinecraftServer;
import java.util.HashSet;

public class VoidHeartTicker
{
    private final static Multimap<Integer, Runnable> delayedTasks = MultimapBuilder.treeKeys().arrayListValues().build();

    public static void tickServer(MinecraftServer server)
    {
        if (delayedTasks.isEmpty())
            return;

        var delaysToDelete = new HashSet<Integer>(1);
        for (var taskByDelay : delayedTasks.entries())
        {
            if (taskByDelay.getKey() <= server.getTickCount())
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

    public static void addDelayedTask(MinecraftServer server, int delay, Runnable runnable)
    {
        delayedTasks.put(server.getTickCount() + delay, runnable);
    }
}
