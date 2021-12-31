package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.lang.Math.min;
import static java.util.Collections.singletonList;

public class ExperienceSkullBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = createCuboidShape(1, 1, 1, 15, 15, 15);

    public ExperienceSkullBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getStateManager().getDefaultState()
                .with(StateProperties.MODEL, false));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return state.get(StateProperties.MODEL) ? BlockRenderType.MODEL : BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, Builder builder)
    {
        var experienceSkull = new ItemStack(VoidHeartBlocks.EXPERIENCE_SKULL);

        var tile = builder.getNullable(LootContextParameters.BLOCK_ENTITY);

        experienceSkull.setDamage(ExperienceSkullTile.MAX_EXPERIENCE);
        if (tile instanceof ExperienceSkullTile skullTile)
        {
            experienceSkull.setDamage(ExperienceSkullTile.MAX_EXPERIENCE - skullTile.getExperience());
            experienceSkull.getOrCreateNbt().putInt("experience", skullTile.getExperience());
        }

        return singletonList(experienceSkull);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.onPlaced(world, pos, state, placer, itemStack);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof ExperienceSkullTile skullTile)
            skullTile.setExperience(itemStack.getOrCreateNbt().getInt("experience"));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        var skull = (ExperienceSkullTile) world.getBlockEntity(pos);

        if (skull == null)
            return ActionResult.SUCCESS;

        var increment = 10;

        if (player.isSneaking())
        {
            if (skull.getExperience() > 0)
            {
                var toRemove = min(increment, skull.getExperience());
                skull.setExperience(skull.getExperience() - toRemove);
                player.addExperience(toRemove);
            }
            return ActionResult.SUCCESS;
        }
        if (skull.getExperience() < ExperienceSkullTile.MAX_EXPERIENCE && player.totalExperience > 0)
        {
            var toAdd = min(increment, player.totalExperience);
            skull.setExperience(skull.getExperience() + toAdd);
            player.addExperience(-toAdd);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return this.getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.HORIZONTAL_FACING, StateProperties.MODEL);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new ExperienceSkullTile(pos, state);
    }
}
