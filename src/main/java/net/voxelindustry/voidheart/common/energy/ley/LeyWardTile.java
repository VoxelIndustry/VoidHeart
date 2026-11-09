package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.VoidSeep;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Asks its stone for ley every tick, and holds the air still around it as strongly as supply, its line's taint and the void's seep allow.
 */
public class LeyWardTile extends TileBase
{
    public static final Set<LeyWardTile> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final int   CHECK_TICKS = 10;
    private static final float SYNC_STEP   = 0.02F;
    private static final float EASE        = 0.15F;

    private float supply;
    private float health = 1;
    private float strength;
    private float taint;
    private float seep;

    private float syncedStrength = -1;
    private float syncedHealth   = -1;

    private float ring;
    private float previousRing;

    public LeyWardTile(BlockPos pos, BlockState state)
    {
        super(LeyStoneContent.LEY_WARD_TILE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LeyWardTile ward)
    {
        var server = (ServerLevel) level;
        ward.supply = LeyPower.supply(server, pos, LeyStoneBalance.WARD_NEED);
        if ((server.getGameTime() + pos.asLong()) % CHECK_TICKS == 0 || ward.syncedHealth < 0)
        {
            ward.taint = LeySpurs.get(server).taintAt(pos);
            ward.seep = VoidSeep.strengthAt(server, pos);
            ward.health = WardStrength.health(ward.taint, ward.seep);
        }
        ward.strength = WardStrength.of(ward.supply, ward.taint, ward.seep);
        LeyWards.update(server, pos, ward.strength);

        if (Math.abs(ward.strength - ward.syncedStrength) > SYNC_STEP || Math.abs(ward.health - ward.syncedHealth) > SYNC_STEP
                || (ward.strength == 0) != (ward.syncedStrength == 0))
        {
            ward.syncedStrength = ward.strength;
            ward.syncedHealth = ward.health;
            ward.setChanged();
            ward.sync();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LeyWardTile ward)
    {
        ward.previousRing = ward.ring;
        ward.ring += (ward.supply - ward.ring) * EASE;
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        if (level.isClientSide())
            CLIENT_LOADED.add(this);
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        if (getLevel() != null && getLevel().isClientSide())
            CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (getLevel() instanceof ServerLevel server)
            LeyWards.remove(server, getBlockPos());
        else if (getLevel() != null)
            CLIENT_LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putFloat("supply", supply);
        output.putFloat("health", health);
        output.putFloat("strength", strength);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        supply = unit(input.getFloatOr("supply", 0));
        health = unit(input.getFloatOr("health", 1));
        strength = unit(input.getFloatOr("strength", 0));
    }

    private static float unit(float value)
    {
        return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public Direction facing()
    {
        return getBlockState().getValue(LeyWardBlock.FACING);
    }

    public float supply()
    {
        return supply;
    }

    /**
     * What taint and seep leave of the ward, 1 when nothing weakens it.
     */
    public float health()
    {
        return health;
    }

    public float strength()
    {
        return strength;
    }

    /**
     * The taint of the line feeding the ward, from 0 to 100. Server side only.
     */
    public float taint()
    {
        return taint;
    }

    /**
     * The void's seep where the ward stands, from 0 to 1. Server side only.
     */
    public float seep()
    {
        return seep;
    }

    public float ring(float partialTicks)
    {
        return previousRing + (ring - previousRing) * partialTicks;
    }
}
