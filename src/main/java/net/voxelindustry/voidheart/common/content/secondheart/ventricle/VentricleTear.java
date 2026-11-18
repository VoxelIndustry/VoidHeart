package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * A slit the Twin Ventricle's beat cuts in the air: it opens from a line, holds while what it called steps out, then zips shut from both ends.
 * Only the server knows what it brings, and it is never saved, since a tear outlives no rite.
 */
public class VentricleTear extends Entity
{
    public static final String WAVE_TAG = "voidheart_ventricle_wave";

    private final List<Identifier> bringing = new ArrayList<>();
    private @Nullable UUID         target;

    public VentricleTear(EntityType<? extends VentricleTear> type, Level level)
    {
        super(type, level);
        noPhysics = true;
    }

    public void bring(List<EntityType<? extends Mob>> types, UUID target)
    {
        for (var type : types)
            bringing.add(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        this.target = target;
    }

    /**
     * @return how open the slit is, 0 a line and 1 fully open
     */
    public float openness(float partialTick)
    {
        return opennessAt(tickCount + partialTick);
    }

    public static float opennessAt(float age)
    {
        if (age < TEAR_OPEN_TICKS)
            return age / TEAR_OPEN_TICKS;
        return 1;
    }

    /**
     * @return how much of the slit's length is left, 1 whole and 0 zipped shut from both ends
     */
    public static float lengthAt(float age)
    {
        float closing = age - (TEAR_TICKS - TEAR_CLOSE_TICKS);
        if (closing <= 0)
            return 1;
        return Math.max(0, 1 - closing / TEAR_CLOSE_TICKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level() instanceof ServerLevel level)
        {
            if (tickCount == 1)
                level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.VENTRICLE_TEAR, getSoundSource(), 1, 0.9F + random.nextFloat() * 0.2F);
            if (tickCount == TEAR_RELEASE_TICK)
                release(level);
            if (tickCount >= TEAR_TICKS)
                discard();
        }
    }

    private void release(ServerLevel level)
    {
        var victim = target == null ? null : level.getPlayerByUUID(target);
        // Legionaries are paired before any is finalized, since one finalized alone calls a partner of its own
        var mobs = new ArrayList<Mob>();
        Legionary waiting = null;
        for (var id : bringing)
        {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob))
                continue;
            if (mob instanceof Legionary legionary)
            {
                if (waiting == null)
                    waiting = legionary;
                else
                {
                    legionary.pairWith(waiting);
                    waiting = null;
                }
            }
            mobs.add(mob);
        }
        for (var mob : mobs)
        {
            mob.snapTo(getX() + (random.nextDouble() - 0.5) * 0.4, getY(), getZ() + (random.nextDouble() - 0.5) * 0.4, random.nextFloat() * 360, 0);
            mob.setPersistenceRequired();
            mob.addTag(WAVE_TAG);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            level.addFreshEntity(mob);
            if (victim instanceof Player player && !player.isCreative() && !player.isSpectator())
                mob.setTarget(player);
        }
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        return false;
    }

    @Override
    public boolean isPickable()
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
