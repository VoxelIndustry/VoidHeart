package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import static net.voxelindustry.voidheart.common.block.StateProperties.*;

public class PortalFrameCoreBlock extends PortalFrameBlock
{
    public PortalFrameCoreBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .emissiveLighting((state, world, pos) -> state.get(Properties.LIT)));
    }

    @Override
    protected void initDefaultState()
    {
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(UP, false)
                .with(DOWN, false)
                .with(Properties.FACING, Direction.NORTH)
                .with(Properties.LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, Properties.FACING, Properties.LIT);
    }
}
