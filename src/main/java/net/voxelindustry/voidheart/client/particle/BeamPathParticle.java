package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.joml.Math.abs;

/**
 * Particle spiralling inside a Shatter Forge beam from a start point to a destination, at the same speed on every leg.
 */
public abstract class BeamPathParticle extends SingleQuadParticle
{
    private final Vector3dc start;
    private final Vector3dc end;

    private final double angleOffset;

    protected BeamPathParticle(ClientLevel world, double x, double y, double z, double destinationX, double destinationY, double destinationZ, TextureAtlasSprite sprite)
    {
        super(world, x, y, z, destinationX, destinationY, destinationZ, sprite);

        gravity = 0;

        this.xd = 0;
        this.yd = 0;
        this.zd = 0;

        start = new Vector3d(x, y, z);
        end = new Vector3d(destinationX, destinationY, destinationZ);

        // Same speed on every beam segment
        lifetime = Math.max(6, (int) (start.distance(end) * 2.5));

        angleOffset = random.nextBoolean() ? PI : 0;
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || isNearDestination())
        {
            remove();
            return;
        }

        double delta = age / (double) lifetime;

        var diff = end.sub(start, new Vector3d());

        var direction = diff.normalize(new Vector3d());
        var rightDirection = Math.abs(direction.y()) > 0.99 ? direction.cross(1, 0, 0, new Vector3d()) : direction.cross(0, 1, 0, new Vector3d());
        rightDirection.normalize();
        var upDirection = rightDirection.cross(direction, new Vector3d());

        double radius = 0.25;
        double pitch = 0.001;
        double angle = 0.005 * diff.length() / pitch * delta + angleOffset;

        double x = radius * cos(angle) * rightDirection.x() + (radius * sin(angle) * upDirection.x()) + (pitch * angle * direction.x());
        double y = radius * cos(angle) * rightDirection.y() + (radius * sin(angle) * upDirection.y()) + (pitch * angle * direction.y());
        double z = radius * cos(angle) * rightDirection.z() + (radius * sin(angle) * upDirection.z()) + (pitch * angle * direction.z());

        var pos = diff.mul(delta);

        setPos(start.x() + pos.x() + x, start.y() + pos.y() + y, start.z() + pos.z() + z);
    }

    private boolean isNearDestination()
    {
        return abs(x - end.x()) < 0.05 && abs(y - end.y()) < 0.05 && abs(z - end.z()) < 0.05;
    }
}
