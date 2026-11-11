package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

/**
 * The black mark announcing where a wraith will come through. It only marks the spot: it grows, then fades on its own schedule.
 * A Wraith Veil leaves the same mark, on its own schedule and without the whispers.
 */
public class WraithStain extends Entity
{
    public static final int GROW_TICKS = Wraith.STAIN_TICKS;
    public static final int LINGER_TICKS = 20;
    public static final int FADE_TICKS = 40;
    public static final float RADIUS = 1.3F;

    private static final EntityDataAccessor<Direction> DATA_FACE   = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Integer>   DATA_GROW   = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>   DATA_LINGER = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float>     DATA_HALF_U = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>     DATA_HALF_V = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.FLOAT);
    // A Wraith Veil's mark opens a hole into its tunnel while that is open
    private static final EntityDataAccessor<Boolean>   DATA_OPENS  = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.BOOLEAN);

    private static final double WALL_GAP = 0.02;
    private static final int WHISPER_INTERVAL = 20;
    private static final float WHISPER_VOLUME = 1.2F;

    private boolean whispers = true;

    public WraithStain(EntityType<? extends WraithStain> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static WraithStain on(ServerLevel level, BlockPos block, Direction face)
    {
        var stain = new WraithStain(VoidHeartEntities.WRAITH_STAIN, level);
        stain.entityData.set(DATA_FACE, face);
        var center = Vec3.atCenterOf(block);
        stain.setPos(center.x + face.getStepX() * (0.5 + WALL_GAP), center.y + face.getStepY() * (0.5 + WALL_GAP), center.z + face.getStepZ() * (0.5 + WALL_GAP));
        level.addFreshEntity(stain);
        return stain;
    }

    /**
     * A Wraith Veil's mark, sized to the mouth of its tunnel.
     */
    public static WraithStain at(ServerLevel level, WallPassagePlan.Mark mark, int growTicks, int lingerTicks)
    {
        var stain = new WraithStain(VoidHeartEntities.WRAITH_STAIN, level);
        var face = mark.face();
        var center = mark.center();
        stain.entityData.set(DATA_FACE, face);
        stain.entityData.set(DATA_HALF_U, (float) mark.halfU());
        stain.entityData.set(DATA_HALF_V, (float) mark.halfV());
        stain.entityData.set(DATA_GROW, Math.max(1, growTicks));
        stain.entityData.set(DATA_LINGER, Math.max(0, lingerTicks));
        stain.entityData.set(DATA_OPENS, true);
        stain.whispers = false;
        stain.setPos(center.x + face.getStepX() * WALL_GAP, center.y + face.getStepY() * WALL_GAP, center.z + face.getStepZ() * WALL_GAP);
        level.addFreshEntity(stain);
        return stain;
    }

    public Direction face()
    {
        return entityData.get(DATA_FACE);
    }

    public int growTicks()
    {
        return entityData.get(DATA_GROW);
    }

    public int lingerTicks()
    {
        return entityData.get(DATA_LINGER);
    }

    public float halfU()
    {
        return entityData.get(DATA_HALF_U);
    }

    public float halfV()
    {
        return entityData.get(DATA_HALF_V);
    }

    public boolean opens()
    {
        return entityData.get(DATA_OPENS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_FACE, Direction.UP);
        builder.define(DATA_GROW, GROW_TICKS);
        builder.define(DATA_LINGER, LINGER_TICKS);
        builder.define(DATA_HALF_U, RADIUS);
        builder.define(DATA_HALF_V, RADIUS);
        builder.define(DATA_OPENS, false);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
            return;

        if (whispers && tickCount < growTicks() && tickCount % WHISPER_INTERVAL == 0)
            level().playSound(null, getX(), getY(), getZ(), VoidHeartSounds.WRAITH_WHISPER, SoundSource.HOSTILE, WHISPER_VOLUME, 1);
        if (tickCount > growTicks() + lingerTicks() + FADE_TICKS)
            discard();
    }

    /**
     * A Wraith Veil's mark only lasts as long as the passage, so it is never kept with the world.
     */
    @Override
    public boolean shouldBeSaved()
    {
        return whispers && super.shouldBeSaved();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
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
