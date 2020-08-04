package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

        // Core block is probably a previously broken portal
        if (!tile.isCore())
            PortalFormer.tryForm(world, state, pos, hit.getSide()).execute();

        return super.onUse(state, world, pos, player, hand, hit);
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
