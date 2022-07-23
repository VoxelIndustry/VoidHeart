package net.voxelindustry.voidheart.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.voxelindustry.voidheart.common.migration.VoidHeartDataMigration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

@Mixin(NbtHelper.class)
public abstract class MixinNbtHelper
{
    @Inject(at = @At("RETURN"), method = "update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/datafixer/DataFixTypes;Lnet/minecraft/nbt/NbtCompound;II)Lnet/minecraft/nbt/NbtCompound;", cancellable = true)
    private static void voidHeart_updateDataWithFixers(DataFixer vanillaDataFixer, DataFixTypes dataFixTypes, NbtCompound inputTag$unused, int vanillaDynamicDataVersion, int vanillaRuntimeDataVersion, CallbackInfoReturnable<NbtCompound> cir)
    {
        var original = cir.getReturnValue(); // Apply after vanilla DFU
        var migrated = VoidHeartDataMigration.migrateBlockEntities(original);
        migrated.putInt(MODID + "_DataVersion", VoidHeartDataMigration.CURRENT_VERSION);
        cir.setReturnValue(migrated);
    }
}