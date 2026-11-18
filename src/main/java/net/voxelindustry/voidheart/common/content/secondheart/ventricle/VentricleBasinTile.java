package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.BASIN_TANK;

/**
 * One of the Twin Ventricle's two basins, which relays fill like any receiver. What it takes follows the walls around it: Suffering inside
 * rubrum-veined walls, Contravoid inside azurium-veined ones, and nothing until it stands in either. Nothing can be drawn back out.
 * <p>
 * Its contents are synced, coarsely, since the chamber draws how full it is.
 */
public class VentricleBasinTile extends TileBase implements IEssenceReceiver
{
    /** Fill changes smaller than this, out of the tank, wait for the next one before syncing. */
    private static final int SYNC_STEP = BASIN_TANK / 32;

    private EssenceStorage tank = new EssenceStorage(EssenceRegistry.EMPTY, BASIN_TANK);
    private int            syncedAmount;

    public VentricleBasinTile(BlockPos pos, BlockState state)
    {
        super(TwinVentricleContent.VENTRICLE_BASIN_TILE, pos, state);
    }

    /**
     * @return what the walls around call for, or null when they call for nothing yet
     */
    public @Nullable EssenceType calledFor()
    {
        if (level == null)
            return null;
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            var part = TwinVentricleContent.partAt(level, worldPosition.relative(direction));
            if (part == VentricleLayout.Part.RUBRUM_WALL)
                return EssenceRegistry.SUFFERING;
            if (part == VentricleLayout.Part.AZURIUM_WALL)
                return EssenceRegistry.CONTRAVOID;
        }
        return null;
    }

    public boolean holdsContravoid()
    {
        return tank.amount() > 0 && tank.type() == EssenceRegistry.CONTRAVOID;
    }

    public EssenceType held()
    {
        return tank.amount() > 0 ? tank.type() : EssenceRegistry.EMPTY;
    }

    public int amount()
    {
        return tank.amount();
    }

    public float fill()
    {
        return tank.amount() / (float) BASIN_TANK;
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        if (type != calledFor())
            return 0;
        if (tank.amount() > 0 && tank.type() != type)
            return 0;
        return tank.maximumAmount() - tank.amount();
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        if (amount <= 0 || spaceFor(type) <= 0)
            return 0;
        if (tank.amount() == 0 && tank.type() != type)
            tank.type(type);
        int received = tank.fill(type, amount);
        if (received > 0)
            changed();
        return received;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }

    /**
     * @return whether it holds what its walls call for, at least this much
     */
    boolean holds(int amount)
    {
        var called = calledFor();
        return called != null && tank.type() == called && tank.amount() >= amount;
    }

    void burn(int amount)
    {
        if (amount <= 0 || tank.amount() == 0)
            return;
        tank.drain(tank.type(), amount);
        if (tank.amount() == 0)
            tank.type(EssenceRegistry.EMPTY);
        changed();
    }

    /** For commands and tests. */
    public int fillWith(EssenceType type, int amount)
    {
        return receive(type, amount);
    }

    private void changed()
    {
        setChanged();
        if (Math.abs(tank.amount() - syncedAmount) >= SYNC_STEP || tank.amount() == 0 || tank.amount() == BASIN_TANK)
        {
            syncedAmount = tank.amount();
            sync();
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        tank = tag.read("tank", EssenceStorage.CODEC)
                .filter(read -> read.amount() > 0 && (read.type() == EssenceRegistry.SUFFERING || read.type() == EssenceRegistry.CONTRAVOID))
                .map(read ->
                {
                    var fresh = new EssenceStorage(read.type(), BASIN_TANK);
                    fresh.fill(read.type(), Math.min(read.amount(), BASIN_TANK));
                    return fresh;
                })
                .orElseGet(() -> new EssenceStorage(EssenceRegistry.EMPTY, BASIN_TANK));
        syncedAmount = tank.amount();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        if (tank.amount() > 0)
            tag.store("tank", EssenceStorage.CODEC, tank);
    }
}
