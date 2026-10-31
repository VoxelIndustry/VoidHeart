package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oculus.OculusControlPayload.Action;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableBlock;
import org.jspecify.annotations.Nullable;

/**
 * A vertical strip of buttons floating just outside one outer edge of a table, facing away from it.
 *
 * @param cell   table block whose edge the strip stands on
 * @param facing side of that block the strip faces, toward the viewer
 * @param center middle of the strip
 */
record OculusControlPanel(BlockPos cell, Direction facing, Vec3 center)
{
    static final float OUTSET = 0.2F;
    static final float HEIGHT = 0.5F;
    static final float BUTTON = 0.22F;
    static final float GAP = 0.04F;
    static final float WIDTH = Button.values().length * BUTTON + (Button.values().length - 1) * GAP;
    /**
     * How much closer another edge must be before the strip moves there, so it does not flicker between two.
     */
    private static final double SWITCH_MARGIN = 0.75;

    enum Button
    {
        ZOOM_OUT(Action.ZOOM_OUT),
        ZOOM_IN(Action.ZOOM_IN),
        FOLLOW(Action.CYCLE_FOLLOW),
        LEY_LINES(Action.TOGGLE_LEY_LINES);

        final Action action;

        Button(Action action)
        {
            this.action = action;
        }

        /**
         * Horizontal center of the button along the strip, from its middle.
         */
        float offset()
        {
            return -WIDTH / 2 + ordinal() * (BUTTON + GAP) + BUTTON / 2;
        }
    }

    record Hit(Button button, double distance)
    {
    }

    /**
     * Unit vector along the strip, from the viewer's left to right.
     */
    Vec3 right()
    {
        return new Vec3(facing.getStepZ(), 0, -facing.getStepX());
    }

    Vec3 normal()
    {
        return new Vec3(facing.getStepX(), 0, facing.getStepZ());
    }

    /**
     * The outer edge nearest the viewer. The previous strip is kept unless another edge is clearly closer.
     */
    static @Nullable OculusControlPanel place(OculusFootprint footprint, @Nullable OculusControlPanel previous, Vec3 viewer)
    {
        OculusControlPanel nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double previousDistance = Double.MAX_VALUE;

        for (int cellZ = 0; cellZ < footprint.depth(); cellZ++)
        {
            for (int cellX = 0; cellX < footprint.width(); cellX++)
            {
                int x = footprint.minX() + cellX;
                int z = footprint.minZ() + cellZ;
                if (!footprint.contains(x, z))
                    continue;

                for (var facing : Direction.Plane.HORIZONTAL)
                {
                    if (footprint.contains(x + facing.getStepX(), z + facing.getStepZ()))
                        continue;

                    double edgeX = x + 0.5 + facing.getStepX() * 0.5;
                    double edgeZ = z + 0.5 + facing.getStepZ() * 0.5;
                    double distance = (viewer.x - edgeX) * (viewer.x - edgeX) + (viewer.z - edgeZ) * (viewer.z - edgeZ);
                    var cell = new BlockPos(x, footprint.y(), z);

                    if (previous != null && previous.cell.equals(cell) && previous.facing == facing)
                        previousDistance = distance;
                    if (distance < nearestDistance)
                    {
                        nearestDistance = distance;
                        nearest = new OculusControlPanel(cell, facing, new Vec3(
                                edgeX + facing.getStepX() * OUTSET,
                                footprint.y() + OculusTableBlock.HEIGHT + HEIGHT,
                                edgeZ + facing.getStepZ() * OUTSET));
                    }
                }
            }
        }

        if (previous != null && previousDistance != Double.MAX_VALUE
                && Math.sqrt(previousDistance) <= Math.sqrt(nearestDistance) + SWITCH_MARGIN)
            return previous;
        return nearest;
    }

    /**
     * The button a ray points at, seen from the front of the strip.
     */
    @Nullable Hit hit(Vec3 origin, Vec3 direction, double maxDistance)
    {
        var normal = normal();
        double facingDot = direction.dot(normal);
        if (facingDot >= 0)
            return null;

        double distance = center.subtract(origin).dot(normal) / facingDot;
        if (distance < 0 || distance > maxDistance)
            return null;

        var point = origin.add(direction.scale(distance)).subtract(center);
        double along = point.dot(right());
        double up = point.y;
        if (Math.abs(up) > BUTTON / 2)
            return null;

        for (var button : Button.values())
        {
            if (Math.abs(along - button.offset()) <= BUTTON / 2)
                return new Hit(button, distance);
        }
        return null;
    }
}
