package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.render.sin.SinTridentState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownTridentRenderState.class)
public abstract class MixinThrownTridentRenderStateSin implements SinTridentState
{
    @Unique
    private Identifier voidheart$inlay;

    @Override
    public void voidheart$setInlay(Identifier inlay)
    {
        voidheart$inlay = inlay;
    }

    @Override
    public Identifier voidheart$inlay()
    {
        return voidheart$inlay;
    }
}
