package net.voxelindustry.voidheart.common.content.beholder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A voidstone pedestal under a floating eye, watching the land around it for the Oculus Tables of its owner.
 * It keeps its own chunk loaded, so what stands near it stays in sight.
 */
public class BeholderBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(5, 2, 5, 11, 7, 11),
            Block.box(4, 9, 4, 12, 15, 12));

    public BeholderBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (oldState.is(this) || !(level instanceof ServerLevel serverLevel))
            return;
        var tile = level.getBlockEntity(pos) instanceof BeholderTile beholder ? beholder : null;
        BeholderRegistry.get(serverLevel).put(pos, tile == null ? Optional.empty() : tile.getOwner());
        serverLevel.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(level, pos, state, placer, itemStack);

        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player) || !(level.getBlockEntity(pos) instanceof BeholderTile tile))
            return;
        tile.setOwner(Optional.of(player.getUUID()));
        BeholderRegistry.get(serverLevel).put(pos, tile.getOwner());
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        var registry = BeholderRegistry.get(level);
        registry.remove(pos);

        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (!registry.anyInChunk(chunkX, chunkZ))
            level.setChunkForced(chunkX, chunkZ, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BeholderTile(pos, state);
    }
}
