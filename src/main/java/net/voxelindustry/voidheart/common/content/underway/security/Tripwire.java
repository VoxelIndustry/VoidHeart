package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

/**
 * A tripwire of the Underway: a red beam at ankle height from an emitter low on a wall to the wall across. It runs on the emergency circuit. Whoever walks through it drops the grating of the pit it guards, shuts the doors around and sounds the alarm.
 * <p>
 * The beam shows, so it can be jumped. The emitter is a simple thing with no eye of its own: breaking it tells nobody.
 */
public class Tripwire extends MountedUnit
{
    public static final float SIZE = 0.3F;
    /** How high over the foot of its block its lens is, and how far behind the middle of the block the emitter sits against its wall. */
    public static final double LENS_HEIGHT = 0.25;
    public static final double BACK = 0.28;
    /** From its own middle to its lens. */
    private static final double LENS_OUT = BACK - 0.25 + 0.01;
    private static final int MEASURE_EVERY = 20;

    private static final EntityDataAccessor<Float> DATA_REACH = SynchedEntityData.defineId(Tripwire.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED = SynchedEntityData.defineId(Tripwire.class, EntityDataSerializers.BOOLEAN);

    private int quiet;

    public Tripwire(EntityType<? extends Tripwire> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, SecurityBalance.TRIPWIRE_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_REACH, 0F);
        entityData.define(DATA_ARMED, true);
    }

    /** @return how far its beam reaches before it meets a wall */
    public float reach()
    {
        return entityData.get(DATA_REACH);
    }

    /** @return whether its beam is lit. It goes dark for a moment after it was broken. */
    public boolean isArmed()
    {
        return entityData.get(DATA_ARMED);
    }

    public Vec3 lens()
    {
        return getBoundingBox().getCenter().add(facing().scale(LENS_OUT));
    }

    public Vec3 end()
    {
        return lens().add(facing().scale(reach()));
    }

    @Override
    protected boolean reports()
    {
        return false;
    }

    @Override
    protected void guard(ServerLevel level, boolean awake)
    {
        var lens = lens();
        if (tickCount % MEASURE_EVERY == 1 || reach() <= 0)
        {
            var far = lens.add(facing().scale(SecurityBalance.TRIPWIRE_REACH));
            var hit = level.clip(new ClipContext(lens, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
            entityData.set(DATA_REACH, (float) hit.distanceTo(lens));
        }
        if (quiet > 0)
        {
            if (--quiet == 0)
                entityData.set(DATA_ARMED, true);
            return;
        }
        var end = end();
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(lens, end).inflate(0.1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var body = player.getBoundingBox();
            if (!body.contains(lens) && body.clip(lens, end).isEmpty())
                continue;
            quiet = SecurityBalance.TRIPWIRE_RESET_TICKS;
            entityData.set(DATA_ARMED, false);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIPWIRE_CLICK_ON, getSoundSource(), 1.5F, 0.6F);
            link().trip(level, blockPosition());
            return;
        }
    }
}
