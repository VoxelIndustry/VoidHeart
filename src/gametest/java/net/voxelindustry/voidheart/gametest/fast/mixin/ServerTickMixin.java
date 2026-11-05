package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.LockSupport;

/**
 * Lets the server start its next tick as soon as the client asks for one, instead of at the next 50 ms mark.
 * <p>
 * Between ticks it runs every queued task, chunk tasks included, which vanilla only does while time is left. While
 * background threads still have work, such as generating, reading or saving chunks, it waits for them up to the
 * real 50 ms a tick would have had. Within a tick, saving and unloading chunks get a few real milliseconds.
 */
@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin
{
    @Unique
    private static final long TICK_NANOS = 50_000_000L;
    @Unique
    private static final long TICK_WORK_NANOS = 5_000_000L;

    @Shadow
    private long nextTickTimeNanos;
    @Shadow
    private long delayedTasksMaxNextTickTimeNanos;
    @Shadow
    private volatile boolean isReady;

    @Unique
    private boolean voidheart$drainingTasks;
    @Unique
    private long voidheart$tickWorkDeadline;

    @Shadow
    protected abstract boolean pollTask();

    @Inject(method = "processPacketsAndTick", at = @At("HEAD"))
    private void voidheart$startTickWork(boolean sprinting, CallbackInfo ci)
    {
        voidheart$tickWorkDeadline = System.nanoTime() + TICK_WORK_NANOS;
    }

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"))
    private void voidheart$startDraining(CallbackInfo ci)
    {
        voidheart$drainingTasks = FastGameTests.ENABLED && isReady;
    }

    @Inject(method = "waitUntilNextTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;runAllTasks()V", shift = At.Shift.AFTER))
    private void voidheart$skipWait(CallbackInfo ci)
    {
        if (!voidheart$drainingTasks)
            return;

        long deadline = System.nanoTime() + TICK_NANOS;
        while (System.nanoTime() < deadline && voidheart$backgroundBusy())
        {
            if (!pollTask())
                LockSupport.parkNanos(200_000L);
        }

        voidheart$drainingTasks = false;
        voidheart$tickWorkDeadline = 0;
        nextTickTimeNanos = Util.getNanos();
        delayedTasksMaxNextTickTimeNanos = nextTickTimeNanos;
    }

    @Inject(method = "haveTime", at = @At("HEAD"), cancellable = true)
    private void voidheart$haveTime(CallbackInfoReturnable<Boolean> cir)
    {
        if (voidheart$drainingTasks)
            cir.setReturnValue(true);
        else if (FastGameTests.ENABLED && isReady)
            cir.setReturnValue(System.nanoTime() < voidheart$tickWorkDeadline);
    }

    @Unique
    private boolean voidheart$backgroundBusy()
    {
        if (Util.backgroundExecutor().service() instanceof ForkJoinPool pool && !pool.isQuiescent())
            return true;
        if (Util.ioPool().service() instanceof ThreadPoolExecutor io && io.getActiveCount() > 0)
            return true;

        for (var level : ((MinecraftServer) (Object) this).getAllLevels())
        {
            var chunks = (ChunkMapAccessor) level.getChunkSource().chunkMap;
            if (chunks.voidheart$worldgen().hasWork() || chunks.voidheart$light().hasWork())
                return true;
        }
        return false;
    }
}
