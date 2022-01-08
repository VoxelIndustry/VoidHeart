package net.voxelindustry.voidheart.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.ChunkSerializer;
import net.voxelindustry.voidheart.common.migration.VoidHeartDataMigration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer
{
    @ModifyVariable(at = @At(value = "INVOKE", target = "net/minecraft/nbt/NbtCompound.putInt(Ljava/lang/String;I)V", ordinal = 0), method = "serialize(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;)Lnet/minecraft/nbt/NbtCompound;", name = "nbtCompound")
    private static NbtCompound fabric_addModDataVersions(NbtCompound input)
    {
        input.putInt(MODID + "_DataVersion", VoidHeartDataMigration.CURRENT_VERSION);
        return input;
    }
}