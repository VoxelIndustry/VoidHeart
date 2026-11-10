package net.voxelindustry.voidheart.client.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where a scene's camera stands.
 *
 * @param yaw   turn around the vertical, in degrees
 * @param pitch how far it looks down, in degrees
 * @param span  how many blocks the shorter side of the stage holds
 * @param x     the point it looks at, in scene blocks
 * @param y     likewise
 * @param z     likewise
 */
public record SceneView(float yaw, float pitch, float span, float x, float y, float z)
{
    public SceneView lerp(SceneView to, float t)
    {
        return new SceneView(yaw + (to.yaw - yaw) * t, pitch + (to.pitch - pitch) * t, span + (to.span - span) * t,
                x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }

    /**
     * From scene blocks to stage units, with +X to the right of the stage, +Y down it and the stage's middle at 0.
     */
    public Matrix4f matrix()
    {
        return new Matrix4f()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(-pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-x, -y, -z);
    }

    public float blockSize(int stageWidth, int stageHeight)
    {
        return Math.min(stageWidth, stageHeight) / span;
    }

    /**
     * Where a point of the scene lands, in the same units as the stage's size.
     */
    public float[] project(float pointX, float pointY, float pointZ, int stageWidth, int stageHeight)
    {
        var at = matrix().transformPosition(new Vector3f(pointX, pointY, pointZ));
        float size = blockSize(stageWidth, stageHeight);
        return new float[] { stageWidth / 2F + at.x * size, stageHeight / 2F + at.y * size };
    }

    /**
     * How far back a point of the scene lies, in blocks. The stage's depth runs negative towards the viewer, so of two points the lower one is in front.
     */
    public float depth(float pointX, float pointY, float pointZ)
    {
        return matrix().transformPosition(new Vector3f(pointX, pointY, pointZ)).z;
    }
}
