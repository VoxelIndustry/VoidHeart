package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A drop falling straight through the air, standing along its fall whichever way it is seen from.
 * Only the heightmap stops it, so that it never reaches under a roof, and it costs no collision checks.
 */
final class OmenDropParticle extends SingleQuadParticle
{
    // A fixed moderate light, so that the drops can be seen at night without glowing
    static final int LIGHT = LightCoordsUtil.pack(9, 0);

    private static final float SHADE_MIN = 0.78F;

    // How far the head of the drop is from the middle of its quad, as a share of the quad's half-size
    private static final float HEAD_REACH     = 0.9F;
    private static final int   MAX_LIFETIME   = 70;
    private static final float END_FADE_TICKS = 3;

    // Closer than this a drop stops growing on screen, however near it passes, and only the last of that distance fades it
    private static final float NEAR_SIZE_FROM = 8;
    private static final float NEAR_FADE_FROM = 0.8F;
    private static final float NEAR_FADE_TO   = 1.6F;

    // Share of its height above or below the camera that it leans toward it. Seen from below a drop mostly stays upright, foreshortening into a round blob
    private static final float TILT = 0.3F;

    private final OmenCurse curse;
    private final float     size;
    private final float     reach;

    OmenDropParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float size, TextureAtlasSprite sprite, OmenCurse curse)
    {
        super(level, x, y, z, sprite);
        this.curse = curse;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        lifetime = MAX_LIFETIME;
        this.size = size;
        quadSize = size;
        reach = size * HEAD_REACH;
        float shade = randomShade(random);
        setColor(shade, shade, shade);
    }

    static float randomShade(RandomSource random)
    {
        return Mth.lerp(random.nextFloat(), SHADE_MIN, 1);
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

        double nextX = x + xd;
        double nextY = y + yd;
        double nextZ = z + zd;
        int blockX = Mth.floor(nextX);
        int blockZ = Mth.floor(nextZ);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
        double foot = nextY - reach;
        if (foot <= top)
        {
            if (y - reach < top && (blockX != Mth.floor(x) || blockZ != Mth.floor(z)))
            {
                // The column it slants into rises above it. Whether it's a wall to splash on or the air under an overhang, this is its end
                var pos = BlockPos.containing(nextX, foot, nextZ);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                    curse.splash(level, x, foot, z);
                remove();
                return;
            }

            double surface = surfaceAt(new BlockPos(blockX, top - 1, blockZ), nextX - blockX, nextZ - blockZ);
            if (foot <= surface)
            {
                setPos(nextX, surface + reach, nextZ);
                curse.land(level, nextX, surface, nextZ);
                remove();
                return;
            }
        }
        setPos(nextX, nextY, nextZ);
    }

    private double surfaceAt(BlockPos pos, double insideX, double insideZ)
    {
        double blockTop = level.getBlockState(pos).getCollisionShape(level, pos).max(Direction.Axis.Y, insideX, insideZ);
        double fluidTop = level.getFluidState(pos).getHeight(level, pos);
        return pos.getY() + Math.max(blockTop, fluidTop);
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
        float sideways = (float) (xd * Mth.cos(yaw) - zd * Mth.sin(yaw));
        float tilt = TILT * (float) Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        var rotation = new Quaternionf().rotationY(yaw).rotateX(tilt).rotateZ((float) Mth.atan2(sideways, -yd));

        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nearFade = Mth.clampedMap(distance, NEAR_FADE_FROM, NEAR_FADE_TO, 0, 1);
        float endFade = Mth.clamp((lifetime - age) / END_FADE_TICKS, 0, 1);
        alpha = nearFade * endFade;
        quadSize = size * Math.min(distance / NEAR_SIZE_FROM, 1);

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
        return LIGHT;
    }
}
