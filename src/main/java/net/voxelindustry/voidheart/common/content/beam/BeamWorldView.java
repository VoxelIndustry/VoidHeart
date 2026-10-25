package net.voxelindustry.voidheart.common.content.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

/**
 * Read-only view of the blocks the beam resolver cares about. Lets the resolver be unit tested without a Level.
 */
public interface BeamWorldView
{
    BeamNodeKind kindAt(BlockPos pos);

    /**
     * Aspect a collector is attuned to, {@code EssenceRegistry.EMPTY} if unattuned. Only called on COLLECTOR positions.
     */
    EssenceType collectorAspect(BlockPos pos);

    /**
     * Output face of a focus. Only called on FOCUS positions.
     */
    Direction focusFacing(BlockPos pos);

    /**
     * Horizontal axis of a forge. Only called on FORGE positions.
     */
    Direction.Axis forgeAxis(BlockPos pos);
}
