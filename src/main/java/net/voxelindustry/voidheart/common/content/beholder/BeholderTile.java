package net.voxelindustry.voidheart.common.content.beholder;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

/**
 * Sees by ley: how far it sees follows how much of the ley it needs reaches it, and it is blind under a tenth of it.
 */
public class BeholderTile extends TileBase
{
    /** Ticks over which the supply is averaged before the sight changes. */
    private static final int   SUPPLY_WINDOW = 20;
    /** Supply changes smaller than this aren't worth telling clients about. */
    private static final float SYNC_STEP     = 0.05F;

    @Getter
    private Optional<UUID> owner = Optional.empty();

    /**
     * Share of the ley it needs that reaches it, averaged over the last second.
     */
    @Getter
    private float   supply;
    private float   supplySum;
    private int     supplyTicks;
    private boolean sampled;

    /**
     * Where the floating eye looks, eased toward what it watches on the client.
     */
    public float eyeYaw;
    public float eyePitch;
    public long lastEyeNanos;

    public BeholderTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.BEHOLDER, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BeholderTile beholder)
    {
        if (level instanceof ServerLevel serverLevel)
            beholder.tickServer(serverLevel);
    }

    /**
     * Asks for its ley every tick, as ley can't be stored. The first answer counts at once, so a new beholder sees from its first tick.
     */
    private void tickServer(ServerLevel level)
    {
        float now = LeyPower.supply(level, worldPosition, MachineEnergyBalance.BEHOLDER_NEED);
        if (!sampled)
        {
            sampled = true;
            setSupply(now);
            return;
        }

        supplySum += now;
        if (++supplyTicks < SUPPLY_WINDOW)
            return;
        setSupply(supplySum / supplyTicks);
        supplySum = 0;
        supplyTicks = 0;
    }

    private void setSupply(float next)
    {
        boolean blindChanged = (sight() > 0) != (MachineEnergy.beholderSight(next) > 0);
        boolean moved = Math.abs(next - supply) >= SYNC_STEP || next >= 1 && supply < 1 || next <= 0 && supply > 0;
        if (!blindChanged && !moved)
            return;
        supply = next;
        setChanged();
        sync();
    }

    /**
     * Share of its full range it sees, from 0 when blind to 1.
     */
    public float sight()
    {
        return MachineEnergy.beholderSight(supply);
    }

    public MachinePower power()
    {
        if (supply >= 1)
            return MachinePower.POWERED;
        return sight() > 0 ? MachinePower.WEAK : MachinePower.UNPOWERED;
    }

    /**
     * Share of its range the beholder at pos sees, 0 if there is none loaded there.
     */
    public static float sightAt(ServerLevel level, BlockPos pos)
    {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BeholderTile beholder ? beholder.sight() : 0;
    }

    public void setOwner(Optional<UUID> owner)
    {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        owner = nbt.read("owner", UUIDUtil.CODEC);
        supply = nbt.getFloatOr("supply", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        owner.ifPresent(id -> nbt.store("owner", UUIDUtil.CODEC, id));
        nbt.putFloat("supply", supply);
    }
}
