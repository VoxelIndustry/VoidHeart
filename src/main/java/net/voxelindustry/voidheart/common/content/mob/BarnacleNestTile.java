package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BarnacleNestTile extends TileBase
{
    public static final int RELEASE_INTERVAL = 20 * 20;
    public static final int MAX_ALIVE = 3;
    public static final double PLAYER_RANGE = 16;

    private static final int CHECK_INTERVAL = 20;

    private final List<UUID> released = new ArrayList<>();
    private int ticksUntilRelease = RELEASE_INTERVAL;

    public BarnacleNestTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.BARNACLE_NEST, pos, state);
    }

    void tickServer(ServerLevel level)
    {
        if (level.getGameTime() % CHECK_INTERVAL != 0 || level.getDifficulty() == Difficulty.PEACEFUL)
            return;

        var pos = getBlockPos();
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PLAYER_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR) == null)
            return;

        ticksUntilRelease -= CHECK_INTERVAL;
        if (ticksUntilRelease > 0)
            return;

        ticksUntilRelease = RELEASE_INTERVAL;
        released.removeIf(id ->
        {
            var entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        if (released.size() < MAX_ALIVE)
            release(level, pos);
        setChanged();
    }

    private void release(ServerLevel level, BlockPos pos)
    {
        var barnacle = VoidHeartEntities.CORRUPTED_BARNACLE.create(level, EntitySpawnReason.SPAWNER);
        if (barnacle == null)
            return;

        var facing = getBlockState().getValue(BarnacleNestBlock.FACING);
        var at = pos.relative(facing);
        barnacle.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        level.addFreshEntity(barnacle);
        released.add(barnacle.getUUID());
        level.playSound(null, pos, VoidHeartSounds.NEST_RELEASE, SoundSource.HOSTILE, 1, 1);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putInt("TicksUntilRelease", ticksUntilRelease);
        output.store("Released", UUIDUtil.CODEC.listOf(), released);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        ticksUntilRelease = input.getIntOr("TicksUntilRelease", RELEASE_INTERVAL);
        released.clear();
        input.read("Released", UUIDUtil.CODEC.listOf()).ifPresent(released::addAll);
    }
}
