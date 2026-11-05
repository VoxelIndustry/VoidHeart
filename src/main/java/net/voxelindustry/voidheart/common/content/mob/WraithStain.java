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
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

/**
 * The black mark announcing where a wraith will come through. It only marks the spot: it grows, then fades on its own schedule.
 */
public class WraithStain extends Entity
{
    public static final int GROW_TICKS = Wraith.STAIN_TICKS;
    public static final int LINGER_TICKS = 20;
    public static final int FADE_TICKS = 40;
    public static final float RADIUS = 1.3F;

    private static final EntityDataAccessor<Direction> DATA_FACE = SynchedEntityData.defineId(WraithStain.class, EntityDataSerializers.DIRECTION);

    private static final double WALL_GAP = 0.02;
    private static final int WHISPER_INTERVAL = 20;
    private static final float WHISPER_VOLUME = 1.2F;

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

    public Direction face()
    {
        return entityData.get(DATA_FACE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_FACE, Direction.UP);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
            return;

        if (tickCount < GROW_TICKS && tickCount % WHISPER_INTERVAL == 0)
            level().playSound(null, getX(), getY(), getZ(), VoidHeartSounds.WRAITH_WHISPER, SoundSource.HOSTILE, WHISPER_VOLUME, 1);
        if (tickCount > GROW_TICKS + LINGER_TICKS + FADE_TICKS)
            discard();
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
