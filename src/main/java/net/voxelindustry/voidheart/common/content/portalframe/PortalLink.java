package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable destination of a portal core. World, position and facing are always set together so a link can never be half written.
 */
public record PortalLink(ResourceKey<Level> world, BlockPos pos, Direction facing)
{
    public PortalLink
    {
        Objects.requireNonNull(world);
        Objects.requireNonNull(pos);
        Objects.requireNonNull(facing);
        pos = pos.immutable();
    }

    public static PortalLink of(PortalFrameCoreTile core)
    {
        return new PortalLink(core.getLevel().dimension(), core.getBlockPos(), core.getFacing());
    }

    public boolean pointsTo(Level level, BlockPos corePos)
    {
        return world.equals(level.dimension()) && pos.equals(corePos);
    }

    public void save(ValueOutput tag, String prefix)
    {
        tag.putString(prefix + "World", world.identifier().toString());
        tag.putLong(prefix + "Pos", pos.asLong());
        tag.putByte(prefix + "Facing", (byte) facing.get3DDataValue());
    }

    @Nullable
    public static PortalLink load(ValueInput tag, String prefix)
    {
        var worldId = tag.getString(prefix + "World").map(Identifier::tryParse).orElse(null);
        var pos = tag.getLong(prefix + "Pos");

        if (worldId == null || pos.isEmpty())
            return null;

        return new PortalLink(ResourceKey.create(Registries.DIMENSION, worldId),
                BlockPos.of(pos.get()),
                Direction.from3DDataValue(tag.getByteOr(prefix + "Facing", (byte) 0)));
    }

    @Override
    public String toString()
    {
        return "[" + world.identifier() + " " + pos.toShortString() + " " + facing.getSerializedName() + "]";
    }
}
