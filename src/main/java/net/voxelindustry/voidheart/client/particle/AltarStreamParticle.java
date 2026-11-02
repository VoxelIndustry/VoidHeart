package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.voxelindustry.steamlayer.math.Bezier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static java.lang.Math.abs;

/**
 * Particle streaming into a Void Altar along a bezier curve.
 */
public abstract class AltarStreamParticle extends SingleQuadParticle
{
    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;

    private final Vector3fc[] controlPoints;

    protected AltarStreamParticle(ClientLevel world,
                                  double x,
                                  double y,
                                  double z,
                                  double destinationX,
                                  double destinationY,
                                  double destinationZ,
                                  Vector3fc firstPointBezier,
                                  Vector3fc secondPointBezier,
                                  TextureAtlasSprite sprite)
    {
        super(world, x, y, z, destinationX, destinationY, destinationZ, sprite);

        gravity = 0;

        this.xd = destinationX - x;
        this.yd = destinationY - y;
        this.zd = destinationZ - z;

        this.destinationX = destinationX;
        this.destinationY = destinationY;
        this.destinationZ = destinationZ;

        lifetime = 30;

        controlPoints = new Vector3fc[]
                {
                        new Vector3f((float) x, (float) y, (float) z),
                        firstPointBezier,
                        secondPointBezier,
                        new Vector3f((float) destinationX, (float) destinationY, (float) destinationZ)
                };
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || (isNearDestination()))
        {
            remove();
            return;
        }

        double delta = age / (double) lifetime;

        var pos = Bezier.pointOfBezier4f(controlPoints, (float) delta);

        setPos(pos.x(), pos.y(), pos.z());
    }

    private boolean isNearDestination()
    {
        return abs(x - destinationX) < 0.05 && abs(y - destinationY) < 0.05 && abs(z - destinationZ) < 0.05;
    }
}
