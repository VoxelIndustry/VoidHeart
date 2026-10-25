package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class RavenousStorageBlock extends Block implements BlockEntityProvider
{
    public static final EnumProperty<RavenousStoragePart> PART = EnumProperty.of("part", RavenousStoragePart.class);

    public RavenousStorageBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.UP)
                .with(PART, RavenousStoragePart.BASE)
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.FACING, PART);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousStorageTile(pos, state);
    }

    public enum RavenousStoragePart implements StringIdentifiable
    {
        BASE("base"),
        PILLAR("pillar");

        private final String name;

        RavenousStoragePart(String name)
        {
            this.name = name;
        }

        public String toString()
        {
            return this.name;
        }

        public String asString()
        {
            return this.name;
        }
    }
}
