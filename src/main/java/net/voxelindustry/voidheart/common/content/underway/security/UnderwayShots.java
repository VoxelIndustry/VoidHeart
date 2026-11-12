package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/** What the Underway's guns share: who they can see, and a bullet that flies straight along a line shown beforehand. */
public final class UnderwayShots
{
    /** A bullet passes armour but not Protection, nor a raised shield. */
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "underway_gun"));

    private UnderwayShots()
    {
    }

    public static Vec3 vec(Vector3fc point)
    {
        return new Vec3(point.x(), point.y(), point.z());
    }

    /** The breath of steam that leaves with every bolt: the Underway's guns are driven by pressure, not powder. */
    public static void puff(ServerLevel level, Vec3 muzzle)
    {
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 3, 0.04, 0.04, 0.04, 0.02);
    }

    /** @return whether nothing solid stands between a point and the middle of a body */
    public static boolean isClear(ServerLevel level, LivingEntity watcher, Vec3 from, LivingEntity body)
    {
        var to = body.getBoundingBox().getCenter();
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, watcher)).getType() == HitResult.Type.MISS;
    }

    /** @return the nearest player a unit can see from a point, who is neither in creative nor a spectator nor unseen */
    public static @Nullable Player nearestSeen(ServerLevel level, LivingEntity watcher, Vec3 from, double range)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
            return null;
        Player nearest = null;
        double best = range * range;
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(from, from).inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            double distance = player.getBoundingBox().getCenter().distanceToSqr(from);
            if (distance <= best && player.isAlive() && !player.isInvisible() && isClear(level, watcher, from, player))
            {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * Fires one bullet from a point toward another, as far as the first block or the gun's reach. It strikes the first player whose body the line passes within {@code width} of.
     *
     * @return where the bullet stopped
     */
    public static Vec3 fire(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 toward, double range, double width, float damage)
    {
        var way = toward.subtract(from);
        if (way.lengthSqr() < 1.0E-6)
            return from;
        var reach = from.add(way.normalize().scale(range));
        var end = level.clip(new ClipContext(from, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getLocation();

        Player struck = null;
        double nearest = Double.MAX_VALUE;
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(from, end).inflate(1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var hit = player.getBoundingBox().inflate(width).clip(from, end);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < nearest)
            {
                nearest = hit.get().distanceToSqr(from);
                struck = player;
                end = hit.get();
            }
        }
        level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 4, 0.05, 0.05, 0.05, 0.2);
        level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.01);
        if (struck == null)
            return end;

        // The armour piercing type also passes shields, so a shield gets its say with an ordinary hit first
        if (struck.applyItemBlocking(level, shooter.damageSources().mobAttack(shooter), damage) <= 0)
            struck.hurtServer(level, shooter.damageSources().source(DAMAGE, shooter), damage);
        return end;
    }
}
