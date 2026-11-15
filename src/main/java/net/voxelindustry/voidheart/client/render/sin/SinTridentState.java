package net.voxelindustry.voidheart.client.render.sin;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The inlay texture a thrown trident's render state carries, added to the vanilla render state by a mixin.
 */
public interface SinTridentState
{
    void voidheart$setInlay(@Nullable Identifier inlay);

    @Nullable Identifier voidheart$inlay();
}
