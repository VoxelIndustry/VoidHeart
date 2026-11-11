package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

/**
 * The foot of a trunk a Treant hides in, looking like any dark oak log. The Treant fills it and the two logs above, and comes out when a player comes near or
 * the bark is broken, leaving that stretch of the trunk stripped bare. It drops nothing, as the wood was the Treant.
 */
public class DormantTreantBlock extends Block implements EntityBlock
{
    public static final int HEIGHT = 3;

    public DormantTreantBlock(Properties properties)
    {
        super(properties);
    }

    /**
     * Turns the trunk it hid in to bare wood and sends the Treant out on the side facing the player, or the first open side.
     */
    public static @Nullable Treant awaken(ServerLevel level, BlockPos pos, @Nullable Player target, boolean keepBase)
    {
        for (int up = keepBase ? 0 : 1; up < HEIGHT; up++)
        {
            var at = pos.above(up);
            var state = level.getBlockState(at);
            if (state.is(VoidHeartBlocks.DORMANT_TREANT) || state.is(Blocks.DARK_OAK_LOG))
                level.setBlock(at, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        }

        var treant = VoidHeartEntities.TREANT.create(level, EntitySpawnReason.TRIGGERED);
        if (treant == null)
            return null;

        var exit = exitSide(level, pos, target);
        treant.setPersistenceRequired();
        treant.emergeFrom(Vec3.atBottomCenterOf(pos), exit.toYRot(), target);
        treant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.TRIGGERED, null);
        level.addFreshEntity(treant);
        return treant;
    }

    /**
     * The open side closest to the player's bearing, so it comes out at them, or toward them anyway if every side is shut.
     */
    static Direction exitSide(LevelReader level, BlockPos pos, @Nullable Player target)
    {
        float bearing = target == null ? 0 : (float) (Mth.atan2(target.getZ() - pos.getZ() - 0.5, target.getX() - pos.getX() - 0.5) * Mth.RAD_TO_DEG) - 90;
        var toward = Direction.fromYRot(bearing);
        Direction best = null;
        float bestTurn = Float.MAX_VALUE;
        for (var side : Direction.Plane.HORIZONTAL)
        {
            float turn = Math.abs(Mth.degreesDifference(bearing, side.toYRot()));
            if (turn < bestTurn && isOpen(level, pos.relative(side)))
            {
                best = side;
                bestTurn = turn;
            }
        }
        return best == null ? toward : best;
    }

    private static boolean isOpen(LevelReader level, BlockPos pos)
    {
        for (int up = 0; up < HEIGHT; up++)
        {
            if (!level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (level instanceof ServerLevel server && !player.isCreative())
            awaken(server, pos, player, false);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion)
    {
        super.wasExploded(level, pos, explosion);
        awaken(level, pos, null, false);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        // Picking it in survival gives nothing away
        return includeData ? super.getCloneItemStack(level, pos, state, true) : new ItemStack(Blocks.DARK_OAK_LOG);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new DormantTreantTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != VoidHeartTiles.DORMANT_TREANT)
            return null;
        return (tickLevel, pos, tickState, tile) -> ((DormantTreantTile) tile).tickServer((ServerLevel) tickLevel);
    }
}
