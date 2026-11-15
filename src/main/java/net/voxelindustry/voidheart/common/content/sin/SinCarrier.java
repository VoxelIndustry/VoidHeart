package net.voxelindustry.voidheart.common.content.sin;

import org.jspecify.annotations.Nullable;

/**
 * Something other than a stack that shows a sin weapon's inlay, such as a thrown trident, whose stack never reaches the client.
 */
public interface SinCarrier
{
    @Nullable Sin voidheart$sin();
}
