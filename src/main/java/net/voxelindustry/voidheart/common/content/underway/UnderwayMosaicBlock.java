package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;

/**
 * A tile of a temple's mosaic, set into the wall like the shell around it. Each shows one piece of the picture, counted row by row from its top left, on every side: the wall hides all but the side facing the temple.
 */
public class UnderwayMosaicBlock extends Block
{
    public static final int TILES = UnderwayTemple.COLUMNS * UnderwayTemple.ROWS;
    public static final IntegerProperty TILE = IntegerProperty.create("tile", 0, TILES - 1);
    /** Whether it lost some of its tesserae. */
    public static final BooleanProperty CHIPPED = BooleanProperty.create("chipped");

    public UnderwayMosaicBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(TILE, 0).setValue(CHIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(TILE, CHIPPED);
    }
}
