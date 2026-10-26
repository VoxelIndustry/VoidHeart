package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class RavenousStorageMatrixBlock extends Block implements EntityBlock
{
    public static final EnumProperty<RavenousStorageMatrixPart> PART = EnumProperty.create("part", RavenousStorageMatrixPart.class);

    public RavenousStorageMatrixBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(PART, RavenousStorageMatrixPart.BASE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousStorageMatrixTile(pos, state);
    }

    public enum RavenousStorageMatrixPart implements StringRepresentable
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

        public String getSerializedName()
        {
            return this.name;
        }
    }
}
