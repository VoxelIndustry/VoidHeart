package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.voxelindustry.steamlayer.common.utils.TagSerializable;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class PortalFormerState implements TagSerializable<CompoundTag>
{
    private BlockPos  from   = BlockPos.ORIGIN;
    private BlockPos  to     = BlockPos.ORIGIN;
    private Direction facing = Direction.UP;

    @Override
    public CompoundTag toTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putLong("fromPos", from.asLong());
        tag.putLong("toPos", to.asLong());
        tag.putInt("facing", facing.getId());
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag)
    {
        from = BlockPos.fromLong(tag.getLong("fromPos"));
        to = BlockPos.fromLong(tag.getLong("toPos"));
        facing = Direction.byId(tag.getInt("facing"));
    }

    public boolean areShapeEquals(PortalFormerState otherState)
    {
        if (otherState == null)
            return false;

        Direction facing = getFacing();

        if (facing.getAxis().isHorizontal() != otherState.getFacing().getAxis().isHorizontal())
            return false;

        if (facing.getAxis().isVertical() && facing == otherState.getFacing())
            return false;

        if (facing.getAxis().isHorizontal())
            return getWidth() == otherState.getWidth()
                    && getHeight() == otherState.getHeight()
                    && facing.getAxis().isHorizontal() == otherState.getFacing().getAxis().isHorizontal();
        else
            return (getWidth() == otherState.getWidth() || getWidth() == otherState.getHeight())
                    && (getHeight() == otherState.getWidth() || getHeight() == otherState.getHeight());
    }

    public int getWidth()
    {
        switch (getFacing().getAxis())
        {
            case Z:
            case Y:
                return getTo().getX() - getFrom().getX();
            case X:
            default:
                return getTo().getZ() - getFrom().getZ();
        }
    }

    public int getHeight()
    {
        switch (getFacing().getAxis())
        {
            case X:
            case Z:
                return getTo().getY() - getFrom().getY();
            case Y:
            default:
                return getTo().getZ() - getFrom().getZ();
        }
    }

    public static PortalFormerState of(BlockPos from, BlockPos to, Direction facing)
    {
        return new PortalFormerState(from, to, facing);
    }
}
