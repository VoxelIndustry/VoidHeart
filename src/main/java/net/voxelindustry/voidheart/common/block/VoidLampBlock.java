package net.voxelindustry.voidheart.common.block;

import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class VoidLampBlock extends Block
{
    public static IntegerProperty CORRUPTION = IntegerProperty.create("corruption", 0, 6);

    public VoidLampBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.PLANT)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .sound(SoundType.STONE)
                .lightLevel(VoidLampBlock::getLuminance));

        registerDefaultState(getStateDefinition().any()
                .setValue(CORRUPTION, 0)
                .setValue(LIT, false));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        world.setBlockAndUpdate(pos, state.setValue(LIT, !state.getValue(LIT)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        ItemStack stack = ctx.getItemInHand();

        return defaultBlockState().setValue(CORRUPTION, stack.getOrDefault(VoidHeartDataComponents.CORRUPTION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(CORRUPTION, LIT);
    }

    private static int getLuminance(BlockState state)
    {
        if (state.getValue(LIT)) return 15;

        int corruptionLevel = state.getValue(CORRUPTION);

        return switch (corruptionLevel)
        {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 10;
            case 5, 6 -> 15;
            default -> 0;
        };
    }
}
