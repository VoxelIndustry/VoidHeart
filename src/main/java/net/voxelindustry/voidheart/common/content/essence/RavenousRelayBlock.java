package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class RavenousRelayBlock extends Block implements BlockEntityProvider
{
    public RavenousRelayBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.UP)
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousRelayTile(pos, state);
    }
}
