package net.voxelindustry.voidheart.client.armor;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where a frame's model space sits in the world, as a living entity's renderer stands it: scaled by the entity's scale, turned to its body, flipped,
 * scaled by the player model's own 0.9375 and dropped so the feet meet the ground. Pure, so it is unit tested.
 * <p>
 * Model space is one unit a block before those scales, y down, the back toward +z, the body's pivot at the neck. World offsets are from the entity's feet.
 */
public final class WingFrame
{
    /** A living thing's renderer stands its model this far down from its pivot. */
    public static final float MODEL_DROP   = 1.501F;
    public static final float PLAYER_SCALE = 0.9375F;

    private final Quaternionf turn;
    private final Quaternionf back;
    private final float       scale;

    /**
     * @param bodyYaw the body's yaw in degrees, as the entity keeps it
     * @param scale   the entity's own scale
     */
    public WingFrame(float bodyYaw, float scale)
    {
        turn = new Quaternionf().rotationY((float) Math.toRadians(180 - bodyYaw));
        back = new Quaternionf(turn).conjugate();
        this.scale = scale;
    }

    /**
     * A model point as an offset from the entity's feet, written into {@code out}.
     */
    public Vector3f toWorld(Vector3f model, Vector3f out)
    {
        out.set(-model.x * PLAYER_SCALE, -(model.y - MODEL_DROP) * PLAYER_SCALE, model.z * PLAYER_SCALE);
        return turn.transform(out).mul(scale);
    }

    /**
     * An offset from the entity's feet as a model point, written into {@code out}.
     */
    public Vector3f toModel(Vector3f world, Vector3f out)
    {
        out.set(world).div(scale);
        back.transform(out);
        return out.set(-out.x / PLAYER_SCALE, -out.y / PLAYER_SCALE + MODEL_DROP, out.z / PLAYER_SCALE);
    }
}
