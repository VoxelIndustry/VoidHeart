package net.voxelindustry.voidheart.client.curse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A chip of the block it came off, resting on its face, that then falls upward as if gravity had turned over. Its texels are as large as the block's.
 */
public final class RaptureRiseParticle extends SingleQuadParticle
{
    public static final float SIZE_SMALLEST = 0.08F;
    public static final float SIZE_LARGEST  = 0.3F;
    // Above 1 the sizes lean toward small, leaving a few chunky ones
    private static final float SIZE_BIAS    = 2.2F;

    private static final int   HESITATE_SHORTEST = 4;
    private static final int   HESITATE_LONGEST  = 26;
    private static final float TREMBLE           = 0.012F;

    private static final float ACCELERATION_LOWEST  = 0.007F;
    private static final float ACCELERATION_HIGHEST = 0.02F;
    private static final float DRIFT_SPEED          = 0.018F;
    private static final float DRIFT_TURN           = 0.12F;
    private static final float SPIN_FASTEST         = 0.22F;

    private static final float RISE_LOWEST    = 8;
    private static final float RISE_HIGHEST   = 20;
    // Share of its rise over which it shrinks away
    private static final float SHRINK_SHARE   = 0.35F;
    private static final int   LONGEST_LIFE   = 120;
    private static final int   RELEASE_TICKS  = 8;
    private static final float SMALLEST_DRAWN = 0.005F;

    private static final float SHADE               = 1;
    // Lets chips stay readable in the dark without being lit up. Block light is warm, so much more would stand out from the moonlit world
    private static final int   LEAST_BLOCK_LIGHT   = 2;
    private static final int   LIGHT_REFRESH_TICKS = 8;

    private final float  fullSize;
    private final float  uvSpan;
    private final float  uo;
    private final float  vo;
    private final Layer  layer;
    private final int    hesitate;
    private final float  acceleration;
    private final float  rise;
    private final double startY;
    private final double anchorX;
    private final double anchorZ;
    private final float  spin;

    private float previousSize;
    private float driftAngle;
    private int   releasedAt = -1;
    private int   light;

    private RaptureRiseParticle(ClientLevel level, double x, double y, double z, BlockState state, BlockPos pos, float size)
    {
        super(level, x, y, z, Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite());
        this.fullSize = size;
        this.quadSize = size;
        this.previousSize = size;
        this.uvSpan = Math.min(1, size * 2);
        this.uo = random.nextFloat() * (1 - uvSpan);
        this.vo = random.nextFloat() * (1 - uvSpan);
        this.layer = Layer.bySprite(sprite);
        this.hesitate = Mth.nextInt(random, HESITATE_SHORTEST, HESITATE_LONGEST);
        this.acceleration = Mth.nextFloat(random, ACCELERATION_LOWEST, ACCELERATION_HIGHEST);
        this.rise = Mth.nextFloat(random, RISE_LOWEST, RISE_HIGHEST);
        this.startY = y;
        this.anchorX = x;
        this.anchorZ = z;
        this.driftAngle = random.nextFloat() * Mth.TWO_PI;
        this.spin = (random.nextFloat() * 2 - 1) * SPIN_FASTEST;
        this.lifetime = LONGEST_LIFE;
        this.light = lightAt(BlockPos.containing(x, y, z));

        var tint = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        int color = tint == null ? -1 : tint.colorAsTerrainParticle(state, level, pos);
        setColor(SHADE * ARGB.redFloat(color), SHADE * ARGB.greenFloat(color), SHADE * ARGB.blueFloat(color));
    }

    /**
     * Rests against a face, given a point on it and its outward direction, without sinking into it. The roll from 0 to 1 picks its size.
     */
    public static RaptureRiseParticle onFace(ClientLevel level, double x, double y, double z, float outX, float outY, float outZ, float push, BlockState state, BlockPos pos, float sizeRoll)
    {
        float size = Mth.lerp((float) Math.pow(sizeRoll, SIZE_BIAS), SIZE_SMALLEST, SIZE_LARGEST);
        double reach = push + size;
        return new RaptureRiseParticle(level, x + outX * reach, y + outY * reach, z + outZ * reach, state, pos, size);
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        previousSize = quadSize;
        oRoll = roll;

        if (age++ >= lifetime)
        {
            remove();
            return;
        }

        if (age < hesitate)
        {
            x = anchorX + (random.nextFloat() * 2 - 1) * TREMBLE;
            z = anchorZ + (random.nextFloat() * 2 - 1) * TREMBLE;
        }
        else
        {
            yd += acceleration;
            driftAngle += (random.nextFloat() * 2 - 1) * DRIFT_TURN;
            x += Mth.cos(driftAngle) * DRIFT_SPEED;
            z += Mth.sin(driftAngle) * DRIFT_SPEED;
            y += yd;
            roll += spin;

            if (age % LIGHT_REFRESH_TICKS == 0)
                light = lightAt(BlockPos.containing(x, y, z));
        }

        float risen = (float) (y - startY) / rise;
        float shrinking = Mth.clamp((1 - risen) / SHRINK_SHARE, 0, 1);
        if (releasedAt >= 0)
            shrinking *= 1 - Mth.clamp((age - releasedAt) / (float) RELEASE_TICKS, 0, 1);
        quadSize = fullSize * shrinking;
        if (quadSize <= SMALLEST_DRAWN)
            remove();
    }

    /**
     * The curse is over: shrinks away shortly instead of finishing its rise.
     */
    void release()
    {
        if (releasedAt < 0)
            releasedAt = age;
    }

    private int lightAt(BlockPos pos)
    {
        int packed = level.hasChunkAt(pos) ? LightCoordsUtil.getLightCoords(level, pos) : LightCoordsUtil.FULL_SKY;
        return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(packed), LEAST_BLOCK_LIGHT), LightCoordsUtil.sky(packed));
    }

    @Override
    protected int getLightCoords(float partialTick)
    {
        return light;
    }

    @Override
    public float getQuadSize(float partialTick)
    {
        return Mth.lerp(partialTick, previousSize, quadSize);
    }

    @Override
    protected Layer getLayer()
    {
        return layer;
    }

    @Override
    protected float getU0()
    {
        return sprite.getU(uo + uvSpan);
    }

    @Override
    protected float getU1()
    {
        return sprite.getU(uo);
    }

    @Override
    protected float getV0()
    {
        return sprite.getV(vo);
    }

    @Override
    protected float getV1()
    {
        return sprite.getV(vo + uvSpan);
    }
}
