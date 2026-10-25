package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.lang.Math.min;
import static java.util.Collections.singletonList;

public class ExperienceSkullBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public ExperienceSkullBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(StateProperties.MODEL, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return state.getValue(StateProperties.MODEL) ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var experienceSkull = new ItemStack(VoidHeartBlocks.EXPERIENCE_SKULL);

        var tile = builder.getParameter(LootContextParams.BLOCK_ENTITY);

        experienceSkull.setDamageValue(ExperienceSkullTile.MAX_EXPERIENCE);
        if (tile instanceof ExperienceSkullTile skullTile)
        {
            experienceSkull.setDamageValue(ExperienceSkullTile.MAX_EXPERIENCE - skullTile.getExperience());
            experienceSkull.getOrCreateTag().putInt("experience", skullTile.getExperience());
        }

        return singletonList(experienceSkull);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(world, pos, state, placer, itemStack);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof ExperienceSkullTile skullTile)
            skullTile.setExperience(itemStack.getOrCreateTag().getInt("experience"));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var skull = (ExperienceSkullTile) world.getBlockEntity(pos);

        if (skull == null)
            return InteractionResult.SUCCESS;

        var increment = 10;

        if (player.isShiftKeyDown())
        {
            if (skull.getExperience() > 0)
            {
                var toRemove = min(increment, skull.getExperience());
                skull.setExperience(skull.getExperience() - toRemove);
                player.giveExperiencePoints(toRemove);

                world.playSound(player,
                        pos,
                        SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS,
                        0.1F,
                        (world.random.nextFloat() - world.random.nextFloat()) * 0.35F + 0.9F);
            }
            return InteractionResult.SUCCESS;
        }
        if (skull.getExperience() < ExperienceSkullTile.MAX_EXPERIENCE && player.totalExperience > 0)
        {
            var toAdd = min(increment, player.totalExperience);
            skull.setExperience(skull.getExperience() + toAdd);
            player.giveExperiencePoints(-toAdd);

            world.playLocalSound(pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.1F,
                    (world.random.nextFloat() - world.random.nextFloat()) * 0.35F + 0.9F,
                    true);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random)
    {
        super.animateTick(state, world, pos, random);

        if (random.nextInt(1300) == 1)
            world.playLocalSound(pos,
                    SoundEvents.GENERIC_DRINK,
                    SoundSource.BLOCKS,
                    0.5f,
                    random.nextFloat() * 0.1F + 0.9F,
                    false);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, StateProperties.MODEL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ExperienceSkullTile(pos, state);
    }
}
