package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Keeps what is left of it on the dropped item, a fresh one drops a plain item so it stacks.
 */
public class MeatProxyBlock extends RepositoryUpgradeBlock<MeatProxyTile>
{
    private static final double FRESH = 1E-3;

    public MeatProxyBlock(Properties properties, VoxelShape shape)
    {
        super(properties, shape, () -> VoidHeartTiles.MEAT_PROXY, MeatProxyTile::new, null);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MeatProxyTile proxy && proxy.getLeft() < Knowledge.proxySeconds() - FRESH)
            stack.set(VoidHeartDataComponents.PROXY_ENDURANCE, proxy.getLeft());
        return List.of(stack);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        var left = stack.get(VoidHeartDataComponents.PROXY_ENDURANCE);
        if (left != null && world.getBlockEntity(pos) instanceof MeatProxyTile proxy)
            proxy.restore(left);
    }
}
