package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static net.minecraft.state.property.Properties.LIT;

public class VoidLampBlock extends Block
{
    public static IntProperty CORRUPTION = IntProperty.of("corruption", 0, 6);

    public VoidLampBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.DARK_GREEN)
                .instrument(Instrument.BASEDRUM)
                .requiresTool()
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .luminance(VoidLampBlock::getLuminance));

        setDefaultState(getStateManager().getDefaultState()
                .with(CORRUPTION, 0)
                .with(LIT, false));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        world.setBlockState(pos, state.with(LIT, !state.get(LIT)));
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        ItemStack stack = ctx.getStack();

        if (!stack.hasNbt() || !stack.getNbt().contains("corruption"))
            return getDefaultState();

        return getDefaultState().with(CORRUPTION, stack.getNbt().getInt("corruption"));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(CORRUPTION, LIT);
    }

    private static int getLuminance(BlockState state)
    {
        if (state.get(LIT)) return 15;

        int corruptionLevel = state.get(CORRUPTION);

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
