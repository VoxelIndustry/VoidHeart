package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A drop that swells where it hangs, lets go and falls straight down to the surface found for it when it formed, standing along its fall whichever way it is seen from.
 * With no surface in reach it fades away on the way down.
 */
final class VigilDropParticle extends SingleQuadParticle
{
    private static final int   HANG_TICKS_MIN   = 5;
    private static final int   HANG_TICKS_EXTRA = 10;
    private static final float HANG_FROM        = 0.3F;

    private static final double GRAVITY    = 0.045;
    private static final double TOP_SPEED  = 0.7;
    private static final int    FALL_TICKS = 30;

    private static final float END_FADE_TICKS = 5;
    private static final float NEAR_FADE_FROM = 0.4F;
    private static final float NEAR_FADE_TO   = 1;

    // How far the head of the drop is from the middle of its quad, as a share of the quad's half-size
    private static final float HEAD_REACH = 0.9F;
    // Share of its height above or below the camera that it leans toward it
    private static final float TILT       = 0.3F;

    private final VigilCurse curse;
    private final double     hangY;
    private final double     surface;
    private final float      size;
    private final int        hangTicks;

    VigilDropParticle(ClientLevel level, double x, double y, double z, double surface, float size, TextureAtlasSprite sprite, VigilCurse curse)
    {
        super(level, x, y, z, sprite);
        this.curse = curse;
        this.surface = surface;
        this.size = size;
        hangY = y;
        hangTicks = HANG_TICKS_MIN + random.nextInt(HANG_TICKS_EXTRA);
        lifetime = hangTicks + FALL_TICKS;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        xd = 0;
        yd = 0;
        zd = 0;
        setPos(x, y - size * HANG_FROM, z);
        xo = this.x;
        yo = this.y;
        zo = this.z;
        float shade = OmenDropParticle.randomShade(random);
        setColor(shade, shade, shade);
    }

    Vec3 position()
    {
        return new Vec3(x, y, z);
    }

    @Override
    public void tick()
    {
        // Taken away along with the curse, it must not land anymore
        if (removed)
            return;

        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime)
        {
            remove();
            return;
        }

        if (age < hangTicks)
        {
            // The tail stays where it hangs from as the drop swells below it
            setPos(x, hangY - size * swell(age), z);
            return;
        }

        yd = Math.max(yd - GRAVITY, -TOP_SPEED);
        double next = y + yd;
        if (next - size * HEAD_REACH <= surface)
        {
            curse.land(level, x, surface, z);
            remove();
            return;
        }
        setPos(x, next, z);
    }

    private float swell(float ticks)
    {
        return Mth.lerp(Mth.clamp(ticks / hangTicks, 0, 1), HANG_FROM, 1);
    }

    @Override
    public float getQuadSize(float partialTick)
    {
        return size * swell(age + partialTick);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick)
    {
        var eye = camera.position();
        double dx = Mth.lerp(partialTick, xo, x) - eye.x;
        double dy = Mth.lerp(partialTick, yo, y) - eye.y;
        double dz = Mth.lerp(partialTick, zo, z) - eye.z;

        // Facing the camera, as the particle pipeline culls back faces
        float yaw = (float) Mth.atan2(-dx, -dz);
        float tilt = TILT * (float) Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        var rotation = new Quaternionf().rotationY(yaw).rotateX(tilt);

        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        alpha = Mth.clampedMap(distance, NEAR_FADE_FROM, NEAR_FADE_TO, 0, 1) * Mth.clamp((lifetime - age) / END_FADE_TICKS, 0, 1);

        extractRotatedQuad(state, camera, rotation, partialTick);
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return OmenDropParticle.LIGHT;
    }
}
