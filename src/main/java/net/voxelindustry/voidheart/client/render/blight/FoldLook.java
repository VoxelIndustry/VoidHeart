package net.voxelindustry.voidheart.client.render.blight;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.blight.BlightBalance;
import net.voxelindustry.voidheart.common.content.blight.Punctured;
import net.voxelindustry.voidheart.common.content.blight.Wound;

import java.util.List;

/**
 * The timeline of an Unseaming as seen: the slits run together into one crack, then the target goes black and collapses into it, turning a little, until it
 * snaps back when the fold ends.
 */
public final class FoldLook
{
    /** Ticks the slits take to run into one crack. */
    public static final float CRACK_TICKS   = 2;
    /** How small the target gets, as a share of its size, before it snaps back. */
    public static final float SMALLEST      = 0.04F;
    /** Degrees the target turns as it collapses. */
    public static final float SPIN_DEGREES  = 50;
    /** The collapse is done this many ticks before the fold ends, holding at a point until the snap. */
    public static final float HOLD_AT_POINT = 1;

    /** Set on the render state of a target being folded. */
    public static final RenderStateDataKey<Boolean> FOLDED = RenderStateDataKey.create(() -> "voidheart:unseaming_fold");

    private FoldLook()
    {
    }

    public static float age(Punctured.Fold fold, float now)
    {
        return now - fold.start();
    }

    /** How far the collapse is, from 0 untouched to 1 at a point. */
    public static float collapse(float age)
    {
        float span = BlightBalance.UNSEAMING_FOLD_TICKS - HOLD_AT_POINT - CRACK_TICKS;
        float t = Mth.clamp((age - CRACK_TICKS) / span, 0, 1);
        // Slow to start, then gone fast, as if the crack swallowed it
        return t * t * t;
    }

    public static float scale(float age)
    {
        return Mth.lerp(collapse(age), 1, SMALLEST);
    }

    public static float spin(float age)
    {
        return collapse(age) * SPIN_DEGREES;
    }

    /** Where the crack opens, in the target's own frame: the middle of the slits that made it, as share of its width and height. */
    public static float[] crackCenter(List<Wound.Slit> slits)
    {
        if (slits.isEmpty())
            return new float[] { 0, 0.5F, 0 };
        float side = 0, height = 0, depth = 0;
        for (var slit : slits)
        {
            side += slit.side();
            height += slit.height();
            depth += slit.depth();
        }
        return new float[] { side / slits.size(), height / slits.size(), depth / slits.size() };
    }

    /** A point of the target's own frame in the world, with its body turned by bodyYaw degrees. */
    public static Vec3 toWorld(Entity entity, Vec3 feet, float bodyYaw, float side, float height, float depth)
    {
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        double localX = side * entity.getBbWidth();
        double localZ = depth * entity.getBbWidth();
        return feet.add(localX * cos - localZ * sin, height * entity.getBbHeight(), localX * sin + localZ * cos);
    }
}
