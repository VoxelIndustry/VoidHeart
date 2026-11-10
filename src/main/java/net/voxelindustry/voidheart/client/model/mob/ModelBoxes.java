package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Since 26.3 vanilla's entity cutout draws both sides of every face. A flat box then shows two faces in one plane, and two boxes meeting in a plane both show
 * a face there, and either fights for depth. A flat box keeps one face, which shows from both sides, and of two boxes that would share a face, one sits a hair
 * proud of the other or sinks into it. {@code MobModelGeometryTest} checks every mob model for both.
 */
public final class ModelBoxes
{
    // In model pixels, far more than depth precision needs at any range a mob is drawn, and well under what the eye can see
    public static final float GAP = 0.05F;
    public static final CubeDeformation PROUD = new CubeDeformation(GAP);
    public static final CubeDeformation SUNK = new CubeDeformation(-GAP);

    // The faces a flat box keeps, named by the side they show from the outside
    public static final Set<Direction> FRONT = EnumSet.of(Direction.NORTH);
    public static final Set<Direction> BACK = EnumSet.of(Direction.SOUTH);
    public static final Set<Direction> LEFT = EnumSet.of(Direction.WEST);
    public static final Set<Direction> RIGHT = EnumSet.of(Direction.EAST);
    public static final Set<Direction> TOP = EnumSet.of(Direction.DOWN);
    public static final Set<Direction> BOTTOM = EnumSet.of(Direction.UP);

    private ModelBoxes()
    {
    }

    public static CubeDeformation proud(float x, float y, float z)
    {
        return new CubeDeformation(x * GAP, y * GAP, z * GAP);
    }
}
