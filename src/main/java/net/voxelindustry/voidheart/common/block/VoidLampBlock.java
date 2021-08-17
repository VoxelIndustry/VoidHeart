package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;

import static net.minecraft.state.property.Properties.LIT;

public class VoidLampBlock extends Block
{
    public static IntProperty CORRUPTION = IntProperty.of("corruption", 0, 6);

    public VoidLampBlock()
    {
        super(Settings.of(Material.STONE)
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

        if (state.get(LIT))
        {
            world.setBlockState(pos, state.with(LIT, false));
            return ActionResult.SUCCESS;
        }

        if (world.getRegistryKey() == VoidHeart.VOID_WORLD_KEY)
        {
            world.setBlockState(pos, state.with(LIT, true));
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void appendStacks(ItemGroup group, DefaultedList<ItemStack> list)
    {
        CORRUPTION.getValues().stream().forEach(corruption ->
        {
            ItemStack stack = new ItemStack(this);
            stack.getOrCreateNbt().putInt("corruption", corruption);
            list.add(stack);
        });
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

        switch (corruptionLevel)
        {
            case 0:
                return 0;
            case 1:
                return 3;
            case 2:
                return 5;
            case 3:
                return 7;
            case 4:
                return 10;
            case 5:
            case 6:
                return 15;
        }
        return 0;
    }
}
