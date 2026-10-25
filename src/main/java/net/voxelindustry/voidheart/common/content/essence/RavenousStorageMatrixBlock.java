package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class RavenousStorageMatrixBlock extends Block implements BlockEntityProvider
{
    public static final EnumProperty<RavenousStorageMatrixPart> PART = EnumProperty.of("part", RavenousStorageMatrixPart.class);

    public RavenousStorageMatrixBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getStateManager().getDefaultState()
                .with(PART, RavenousStorageMatrixPart.BASE)
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousStorageTile(pos, state);
    }

    public enum RavenousStorageMatrixPart implements StringIdentifiable
    {
        BASE("base"),
        PILLAR_TOP("pillar_top"),
        PILLAR_BOTTOM("pillar_bottom"),
        PILLAR_NORTH("pillar_north"),
        PILLAR_SOUTH("pillar_south"),
        PILLAR_EAST("pillar_east"),
        PILLAR_WEST("pillar_west");

        private final String name;

        RavenousStorageMatrixPart(String name)
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
