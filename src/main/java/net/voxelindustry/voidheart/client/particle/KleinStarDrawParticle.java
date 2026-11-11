package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStar;

/**
 * A crumb of a block torn loose by a growing Klein Star. It wears the block's own texture, winds its way in toward the star rather than falling,
 * heats to the star's color as it nears and is gone when it reaches it. Added to the particle engine directly, since only the client that draws
 * the star decides where they come from.
 */
public class KleinStarDrawParticle extends TerrainParticle
{
    private static final int   LIFE_MIN     = 12;
    private static final int   LIFE_SPAN    = 10;
    private static final float SIZE         = 1.1F;
    // How much it winds sideways against how fast it closes in, strongest as it leaves the block
    private static final float SWIRL        = 0.9F;
    private static final float SHRINK       = 0.55F;
    private static final float SWALLOWED_AT = 0.2F;
    // The star's pale gold, which it takes on over the second half of its way
    private static final float HOT_RED      = 1;
    private static final float HOT_GREEN    = 0.91F;
    private static final float HOT_BLUE     = 0.66F;

    private final KleinStar star;
    private final Vec3      axis;
    private final float     baseSize;
    private final float     baseRed;
    private final float     baseGreen;
    private final float     baseBlue;

    public KleinStarDrawParticle(ClientLevel level, double x, double y, double z, BlockState state, BlockPos pos, KleinStar star)
    {
        super(level, x, y, z, 0, 0, 0, state, pos);
        this.star = star;
        gravity = 0;
        hasPhysics = false;
        xd = 0;
        yd = 0;
        zd = 0;
        lifetime = LIFE_MIN + random.nextInt(LIFE_SPAN);
        quadSize *= SIZE;
        baseSize = quadSize;
        baseRed = rCol;
        baseGreen = gCol;
        baseBlue = bCol;
        axis = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || star.isRemoved() || star.launched())
        {
            remove();
            return;
        }

        var toStar = KleinStarRender.drawnCenter(star).subtract(x, y, z);
        double distance = toStar.length();
        if (distance < SWALLOWED_AT)
        {
            remove();
            return;
        }

        float progress = age / (float) lifetime;
        // Closing in so as to arrive as its time runs out, faster and faster
        double closing = distance / Math.max(1, lifetime - age + 1);
        var inward = toStar.scale(closing / distance);
        var sideways = toStar.cross(axis);
        if (sideways.lengthSqr() > 1e-6)
            inward = inward.add(sideways.normalize().scale(closing * SWIRL * (1 - progress)));
        x += inward.x;
        y += inward.y;
        z += inward.z;

        float heat = Mth.clamp(progress * 2 - 1, 0, 1);
        rCol = Mth.lerp(heat, baseRed, HOT_RED);
        gCol = Mth.lerp(heat, baseGreen, HOT_GREEN);
        bCol = Mth.lerp(heat, baseBlue, HOT_BLUE);
        quadSize = baseSize * (1 - SHRINK * progress);
    }

    @Override
    protected int getLightCoords(float partialTicks)
    {
        // Lit by the star once it is close to it
        return age * 2 > lifetime ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTicks);
    }
}
