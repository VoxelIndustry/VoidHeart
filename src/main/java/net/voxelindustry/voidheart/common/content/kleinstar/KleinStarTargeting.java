package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What a Klein Star may burst on in a world, and where that leaves its burst, see {@link KleinStarAim}. The same on the server, which decides, and on
 * the client of someone only holding the staff, which shows them where a star would go.
 */
public final class KleinStarTargeting
{
    /**
     * @param at     where to burst
     * @param anchor the one the star goes for, which it follows on its way, or null when it only goes where its caster looks
     */
    public record Target(Vec3 at, @Nullable LivingEntity anchor)
    {
    }

    private KleinStarTargeting()
    {
    }

    /**
     * @return the enemies in range that either the star or its bearer can see
     */
    public static List<LivingEntity> visible(Level level, Vec3 star, LivingEntity bearer)
    {
        double range = KleinStarBalance.SEARCH_RANGE;
        var eyes = bearer.getEyePosition();
        var seen = new ArrayList<LivingEntity>();
        for (var living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(star, range * 2, range * 2, range * 2),
                living -> living instanceof Enemy && living != bearer && living.isAlive() && !living.isSpectator()))
        {
            var middle = living.getBoundingBox().getCenter();
            if (middle.distanceTo(star) <= range && (sees(level, star, middle, bearer) || sees(level, eyes, middle, bearer)))
                seen.add(living);
        }
        return seen;
    }

    public static boolean sees(Level level, Vec3 from, Vec3 to, @Nullable Entity looker)
    {
        var context = looker == null
                ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker);
        return level.clip(context).getType() == HitResult.Type.MISS;
    }

    /**
     * Where a star on the end of its bearer's staff would burst, steered by where its bearer looks: among those it may go for if there are any,
     * else wherever its bearer looks.
     */
    public static Target target(Level level, Vec3 star, LivingEntity bearer, Collection<LivingEntity> candidates, double radius)
    {
        var living = new ArrayList<LivingEntity>();
        var marks = new ArrayList<KleinStarAim.Mark>();
        for (var candidate : candidates)
        {
            if (!candidate.isAlive() || candidate.level() != level)
                continue;
            var middle = candidate.getBoundingBox().getCenter();
            if (middle.distanceTo(star) > KleinStarBalance.SEARCH_RANGE)
                continue;
            living.add(candidate);
            marks.add(new KleinStarAim.Mark(middle, candidate.getHealth()));
        }

        var aim = KleinStarAim.aim(marks, radius, new KleinStarAim.Gaze(bearer.getEyePosition(), bearer.getLookAngle()));
        return aim == null ? new Target(alongGaze(level, bearer), null) : new Target(aim.at(), living.get(aim.anchor()));
    }

    /**
     * Where a star already on its way should burst now: still on the one it went for, among whatever stands around it by then.
     */
    public static Vec3 around(Level level, LivingEntity anchor, double radius)
    {
        var middle = anchor.getBoundingBox().getCenter();
        var marks = new ArrayList<KleinStarAim.Mark>();
        marks.add(new KleinStarAim.Mark(middle, anchor.getHealth()));
        for (var living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(middle, radius * 4, radius * 4, radius * 4),
                living -> living instanceof Enemy && living != anchor && living.isAlive() && !living.isSpectator()))
            marks.add(new KleinStarAim.Mark(living.getBoundingBox().getCenter(), living.getHealth()));
        return KleinStarAim.settle(marks, 0, radius, null);
    }

    /**
     * With nothing to burst on, a star goes where its bearer looks: to the first block in the way, a little short of it, or as far as it may.
     */
    public static Vec3 alongGaze(Level level, LivingEntity bearer)
    {
        var eyes = bearer.getEyePosition();
        var look = bearer.getLookAngle();
        var far = eyes.add(look.scale(KleinStarBalance.SEARCH_RANGE));
        var hit = level.clip(new ClipContext(eyes, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bearer));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation().subtract(look.scale(KleinStarBalance.BURST_BACKOFF));
    }
}
