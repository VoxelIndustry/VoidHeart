package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.*;

/**
 * A spreading pool of bile on the floor. It burns through armour to whoever stands in it, and wears the armour down as it does, until it dries.
 */
public class BilePool extends Entity
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "corrosion"));
    /** Ticks it takes to spread out, then to dry at its end. */
    public static final int SPREAD_TICKS = 6, DRY_TICKS = 20;
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private @Nullable LivingEntity owner;

    public BilePool(EntityType<? extends BilePool> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void spill(ServerLevel level, @Nullable LivingEntity owner, Vec3 at)
    {
        var pool = new BilePool(VoidHeartEntities.BILE_POOL, level);
        pool.owner = owner;
        pool.setPos(floor(level, at));
        level.addFreshEntity(pool);
    }

    /** Settles on the floor under where it struck, a hair over it. */
    private static Vec3 floor(ServerLevel level, Vec3 at)
    {
        var pos = BlockPos.containing(at.x, at.y + 0.1, at.z);
        for (int down = 0; down < 8; down++)
        {
            var below = pos.below(down);
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            // A surface above where the glob struck is the ceiling it hit, not a floor to lie on
            if (!shape.isEmpty() && below.getY() + shape.max(Direction.Axis.Y) <= at.y + 0.2)
                return new Vec3(at.x, below.getY() + shape.max(Direction.Axis.Y) + 0.01, at.z);
        }
        return at;
    }

    /** @return how wide it has spread at an age, 0 to 1 */
    public static float spread(float age)
    {
        if (age < SPREAD_TICKS)
            return age / SPREAD_TICKS;
        return Math.max(0, Math.min(1, (POOL_TICKS - age) / DRY_TICKS));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            if (random.nextInt(4) == 0)
            {
                double angle = random.nextDouble() * Math.PI * 2, reach = random.nextDouble() * POOL_RADIUS * spread(tickCount);
                level().addParticle(ParticleTypes.ITEM_SLIME, getX() + Math.cos(angle) * reach, getY() + 0.05, getZ() + Math.sin(angle) * reach, 0, 0.02, 0);
            }
            return;
        }
        if (tickCount > POOL_TICKS)
        {
            discard();
            return;
        }
        if (tickCount % POOL_INTERVAL != 0 || !(level() instanceof ServerLevel level))
            return;
        double radius = POOL_RADIUS * spread(tickCount);
        var box = new AABB(getX() - radius, getY() - 0.2, getZ() - radius, getX() + radius, getY() + 0.6, getZ() + radius);
        DamageSource source = owner == null ? damageSources().source(DAMAGE) : damageSources().source(DAMAGE, this, owner);
        for (var player : level.getEntitiesOfClass(Player.class, box, EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            double dx = player.getX() - getX(), dz = player.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius || !player.hurtServer(level, source, POOL_DAMAGE))
                continue;
            for (var slot : ARMOR)
            {
                var stack = player.getItemBySlot(slot);
                if (stack.isDamageableItem())
                    stack.hurtAndBreak(POOL_ARMOR_WEAR, player, slot);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        return false;
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
    }
}
