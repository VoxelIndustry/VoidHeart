package net.voxelindustry.voidheart.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinCarrier;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A thrown trident's stack stays on the server, so its sin is synced for its inlay to show in flight
@Mixin(ThrownTrident.class)
public abstract class MixinThrownTridentSin implements SinCarrier
{
    @Unique
    private static final EntityDataAccessor<Byte> VOIDHEART_SIN = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BYTE);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void voidheart$defineSin(SynchedEntityData.Builder builder, CallbackInfo info)
    {
        builder.define(VOIDHEART_SIN, (byte) -1);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void voidheart$syncSin(CallbackInfo info)
    {
        var self = (ThrownTrident) (Object) this;
        if (self.level().isClientSide())
            return;
        var sin = SinProficiencies.sinOf(self.getPickupItemStackOrigin());
        self.getEntityData().set(VOIDHEART_SIN, (byte) (sin == null ? -1 : sin.ordinal()));
    }

    @Override
    public Sin voidheart$sin()
    {
        var ordinal = ((ThrownTrident) (Object) this).getEntityData().get(VOIDHEART_SIN);
        return ordinal < 0 || ordinal >= Sin.values().length ? null : Sin.values()[ordinal];
    }
}
