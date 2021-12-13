package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3f;
import net.voxelindustry.steamlayer.common.utils.TagSerializable;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class PortalFormerState implements TagSerializable<NbtCompound>
{
    private BlockPos  from   = BlockPos.ORIGIN;
    private BlockPos  to     = BlockPos.ORIGIN;
    private Direction facing = Direction.UP;

    private Vec3f center = Vec3f.ZERO;

    public PortalFormerState(BlockPos from, BlockPos to, Direction facing)
    {
        this.from = from;
        this.to = to;
        this.facing = facing;
    }

    @Override
    public NbtCompound toTag()
    {
        NbtCompound tag = new NbtCompound();
        tag.putLong("fromPos", from.asLong());
        tag.putLong("toPos", to.asLong());
        tag.putInt("facing", facing.getId());
        return tag;
    }

    @Override
    public void fromTag(NbtCompound tag)
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

    public Vec3f getCenter()
    {
        if (center == Vec3f.ZERO)
            center = new Vec3f(
                    (getTo().getX() - getFrom().getX()) / 2F + getFrom().getX(),
                    (getTo().getY() - getFrom().getY()) / 2F + getFrom().getY(),
                    (getTo().getZ() - getFrom().getZ()) / 2F + getFrom().getZ()
            );
        return center;
    }

    public static PortalFormerState of(BlockPos from, BlockPos to, Direction facing)
    {
        return new PortalFormerState(from, to, facing);
    }

    public boolean isCorner(BlockPos pos)
    {
        if (pos.equals(from) || pos.equals(to))
            return true;

        if (facing.getAxis() == Axis.X)
        {
            return pos.getZ() == from.getZ() && pos.getY() == to.getY() ||
                    pos.getZ() == to.getZ() && pos.getY() == from.getY();
        }
        if (facing.getAxis() == Axis.Z)
        {
            return pos.getX() == from.getX() && pos.getY() == to.getY() ||
                    pos.getX() == to.getX() && pos.getY() == from.getY();
        }
        return pos.getZ() == from.getZ() && pos.getX() == to.getX() ||
                pos.getZ() == to.getZ() && pos.getX() == from.getX();
    }
}
