package net.voxelindustry.voidheart.common.energy.ley;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.LeyPower;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance.GROWER_HEIGHT;
import static net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance.GROWER_RADIUS;

/**
 * Asks its stone for ley every tick and spends it on crops and saplings around it, giving them extra random ticks, so they grow and fail by vanilla's own
 * rules, light and all. At full supply that doubles their growth.
 */
public class LeyGrowerTile extends TileBase
{
    public static final Set<LeyGrowerTile> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final int   VOLUME    = GrowerTicks.volume(GROWER_RADIUS, GROWER_HEIGHT);
    private static final float SYNC_STEP = 0.02F;
    private static final float EASE      = 0.15F;

    private final GrowerTicks ticks = new GrowerTicks();

    private float supply;
    private float syncedSupply = -1;

    private float pool;
    private float previousPool;

    public LeyGrowerTile(BlockPos pos, BlockState state)
    {
        super(LeyStoneContent.LEY_GROWER_TILE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LeyGrowerTile grower)
    {
        var server = (ServerLevel) level;
        grower.supply = LeyPower.supply(server, pos, LeyStoneBalance.GROWER_NEED);

        int picks = grower.ticks.next(VOLUME, server.getGameRules().get(GameRules.RANDOM_TICK_SPEED), LeyStoneBalance.GROWER_BOOST, grower.supply);
        for (int i = 0; i < picks; i++)
            quicken(server, pos.offset(
                    server.getRandom().nextIntBetweenInclusive(-GROWER_RADIUS, GROWER_RADIUS),
                    server.getRandom().nextIntBetweenInclusive(-GROWER_HEIGHT, GROWER_HEIGHT),
                    server.getRandom().nextIntBetweenInclusive(-GROWER_RADIUS, GROWER_RADIUS)));

        if (Math.abs(grower.supply - grower.syncedSupply) > SYNC_STEP || (grower.supply == 0) != (grower.syncedSupply == 0))
        {
            grower.syncedSupply = grower.supply;
            grower.setChanged();
            grower.sync();
        }
    }

    private static void quicken(ServerLevel level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        if (!state.is(LeyStoneContent.GROWER_BOOSTS) || !state.isRandomlyTicking())
            return;

        state.randomTick(level, pos, level.getRandom());
        if (level.getBlockState(pos) != state)
        {
            var glint = new LeyStonePayloads.GrowerGlint(pos);
            PlayerLookup.tracking(level, pos).forEach(player -> ServerPlayNetworking.send(player, glint));
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LeyGrowerTile grower)
    {
        grower.previousPool = grower.pool;
        grower.pool += (grower.supply - grower.pool) * EASE;
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
        if (getLevel() != null && getLevel().isClientSide())
            CLIENT_LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putFloat("supply", supply);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        float saved = input.getFloatOr("supply", 0);
        supply = Float.isFinite(saved) ? Math.clamp(saved, 0, 1) : 0;
    }

    public float supply()
    {
        return supply;
    }

    public float pool(float partialTicks)
    {
        return previousPool + (pool - previousPool) * partialTicks;
    }
}
