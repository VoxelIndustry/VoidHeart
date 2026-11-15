package net.voxelindustry.voidheart.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mobs placed at generation are built on a worldgen thread, where the search for a free id reads the chunk map's entities while the server thread changes them, and can crash. Such a mob is only saved into its chunk, without its id, so off the server thread any fresh number of the counter does.
 */
@Mixin(ServerLevel.class)
public class MixinServerLevelWorldgenEntityId
{
    @Shadow
    @Final
    private static AtomicInteger ENTITY_COUNTER;

    @Inject(method = "getNextEntityId()I", at = @At("HEAD"), cancellable = true)
    private void countOffServerThread(CallbackInfoReturnable<Integer> callbackInfoReturnable)
    {
        if (((ServerLevel) (Object) this).getServer().isSameThread())
            return;
        int id;
        do
            id = ENTITY_COUNTER.incrementAndGet();
        while (id == 0);
        callbackInfoReturnable.setReturnValue(id);
    }
}
