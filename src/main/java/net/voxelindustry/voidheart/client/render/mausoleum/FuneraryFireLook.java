package net.voxelindustry.voidheart.client.render.mausoleum;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Whether the fire drawn on an entity is a wisp's green fire, carried on its render state from extraction to the flame renderer.
 */
public final class FuneraryFireLook
{
    public static final RenderStateDataKey<Boolean> GREEN = RenderStateDataKey.create(() -> "voidheart:funerary_fire");

    private FuneraryFireLook()
    {
    }
}
