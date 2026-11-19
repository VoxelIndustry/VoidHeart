package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.common.content.blight.EclipseLinks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The health a threaded mob actually loses, once armor, enchantments and absorption have had their say and a killing blow included, is what the
 * crossbow blight's link shares out, see {@link EclipseLinks#shareFrom}.
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityEclipseShare
{
    @WrapMethod(method = "actuallyHurt")
    private void voidheart$share(ServerLevel level, DamageSource source, float damage, Operation<Void> original)
    {
        var self = (LivingEntity) (Object) this;
        float before = self.getHealth();
        original.call(level, source, damage);
        float lost = before - self.getHealth();
        if (lost > 0)
            EclipseLinks.shareFrom(level, self, source, lost);
    }
}
