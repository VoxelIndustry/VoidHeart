package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumAddress;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumPlan;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;

/**
 * A door of wraith ink. Opened by a Wraith Veil in a temple's mosaic it spreads, opens and closes again like the mouth of a veil's tunnel, but
 * whoever walks into it comes out in that temple's sanctum. The one in a sanctum's porch never closes, and leads back.
 */
public class VeilGate extends Entity
{
    private static final EntityDataAccessor<Direction> DATA_FACE   = SynchedEntityData.defineId(VeilGate.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Float>     DATA_HALF_U = SynchedEntityData.defineId(VeilGate.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>     DATA_HALF_V = SynchedEntityData.defineId(VeilGate.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean>   DATA_STAYS  = SynchedEntityData.defineId(VeilGate.class, EntityDataSerializers.BOOLEAN);

    /** Ticks the ink takes to recede once a temple's door has closed. */
    public static final int FADE_TICKS = 40;
    private static final double WALL_GAP = 0.02;
    /** How far in front of the face a body counts as walking in. */
    private static final double MOUTH_DEPTH = 0.45;
    /** How open the hole must be to be passed. */
    private static final float PASSABLE = 0.6F;
    private static final float WHISPER_VOLUME = 0.6F;
    private static final float PASS_VOLUME = 0.8F;

    /** Which temple's door this is, or for a sanctum's gate, nothing: the sanctum knows its temple. */
    private long door;

    public VeilGate(EntityType<? extends VeilGate> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Opens a temple's door, for as long as a veil's tunnel would stay open. */
    public static VeilGate open(ServerLevel level, MosaicDoor door)
    {
        var gate = new VeilGate(VoidHeartEntities.VEIL_GATE, level);
        gate.door = door.key();
        gate.shape(door.facing(), MosaicDoor.COLUMNS / 2F, MosaicDoor.ROWS / 2F, false);
        var center = door.center();
        var face = door.facing();
        gate.setPos(center.x + face.getStepX() * WALL_GAP, center.y, center.z + face.getStepZ() * WALL_GAP);
        level.addFreshEntity(gate);
        level.playSound(null, center.x, center.y, center.z, VoidHeartSounds.WRAITH_WHISPER, SoundSource.PLAYERS, WHISPER_VOLUME, 0.6F);
        return gate;
    }

    /** The gate back, in a sanctum's porch. Created as the sanctum generates, and kept with the world. */
    public static VeilGate returnGate(Level level, BlockPos origin)
    {
        var gate = new VeilGate(VoidHeartEntities.VEIL_GATE, level);
        var face = SanctumPlan.GATE_FACING;
        gate.shape(face, SanctumPlan.GATE_COLUMNS / 2F, SanctumPlan.GATE_ROWS / 2F, true);
        double x = origin.getX() + SanctumPlan.GATE_X0 + SanctumPlan.GATE_COLUMNS / 2D;
        double y = origin.getY() + SanctumPlan.FLOOR_Y + SanctumPlan.GATE_ROWS / 2D;
        double z = origin.getZ() + SanctumPlan.GATE_Z + 0.5 + face.getStepZ() * (0.5 + WALL_GAP);
        gate.setPos(x, y, z);
        return gate;
    }

    private void shape(Direction face, float halfU, float halfV, boolean stays)
    {
        entityData.set(DATA_FACE, face);
        entityData.set(DATA_HALF_U, halfU);
        entityData.set(DATA_HALF_V, halfV);
        entityData.set(DATA_STAYS, stays);
    }

    public Direction face()
    {
        return entityData.get(DATA_FACE);
    }

    public float halfU()
    {
        return entityData.get(DATA_HALF_U);
    }

    public float halfV()
    {
        return entityData.get(DATA_HALF_V);
    }

    /** Whether it is a sanctum's gate, open for good. */
    public boolean stays()
    {
        return entityData.get(DATA_STAYS);
    }

    /** @return how far the hole has opened at an age, 0 to 1 */
    public static float holeOpen(boolean stays, float age)
    {
        return stays ? 1 : WallPassagePlan.Plan.holeOpen(age);
    }

    public static int closesAt()
    {
        return WallPassagePlan.Plan.totalTicks();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_FACE, Direction.NORTH);
        builder.define(DATA_HALF_U, 1.5F);
        builder.define(DATA_HALF_V, 2F);
        builder.define(DATA_STAYS, false);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!(level() instanceof ServerLevel level))
            return;

        if (!stays() && tickCount > closesAt() + FADE_TICKS)
        {
            discard();
            return;
        }
        if (holeOpen(stays(), tickCount) < PASSABLE)
            return;
        for (var player : level.getEntitiesOfClass(ServerPlayer.class, mouth(), EntitySelector.NO_SPECTATORS))
        {
            if (player.isPassenger() || player.isVehicle())
                continue;
            boolean passed = stays() ? Sanctums.leave(player, SanctumAddress.at(getBlockX(), getBlockZ()))
                                     : Sanctums.enter(player, new MosaicDoor(BlockPos.of(door), face()));
            if (passed)
                level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.WRAITH_EMERGE, SoundSource.PLAYERS, PASS_VOLUME, 0.7F);
        }
    }

    /** The space in front of the gate a body walking into it reaches. */
    public AABB mouth()
    {
        var face = face();
        double halfU = halfU(), halfV = halfV();
        double ux = Math.abs(face.getStepZ()) * halfU, uz = Math.abs(face.getStepX()) * halfU;
        var center = position();
        return new AABB(center.x - ux, center.y - halfV, center.z - uz, center.x + ux, center.y + halfV, center.z + uz)
                .expandTowards(face.getStepX() * MOUTH_DEPTH, 0, face.getStepZ() * MOUTH_DEPTH);
    }

    public Vec3 normal()
    {
        return face().getUnitVec3();
    }

    /** A temple's door lasts only as long as it is open. */
    @Override
    public boolean shouldBeSaved()
    {
        return stays() && super.shouldBeSaved();
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
        door = input.getLongOr("Door", 0);
        var face = Direction.from3DDataValue(input.getIntOr("Face", Direction.SOUTH.get3DDataValue()));
        shape(face, input.getFloatOr("HalfU", 1.5F), input.getFloatOr("HalfV", 2), input.getBooleanOr("Stays", true));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        output.putLong("Door", door);
        output.putInt("Face", face().get3DDataValue());
        output.putFloat("HalfU", halfU());
        output.putFloat("HalfV", halfV());
        output.putBoolean("Stays", stays());
    }
}
