package net.voxelindustry.voidheart.common.content.permeablebarrier;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class VoidBarrierEmitterBlock extends Block implements EntityBlock
{
    public VoidBarrierEmitterBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(60F, 1200.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops());

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.NORTH)
        );
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(world, pos, state, placer, itemStack);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof VoidBarrierEmitterTile barrier && placer != null)
            barrier.setOwner(placer.getUUID());
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos)
    {
        float hardness = state.getDestroySpeed(world, pos);

        var tile = world.getBlockEntity(pos);
        if (tile instanceof VoidBarrierEmitterTile barrier)
        {
            if (Objects.equals(player.getUUID(), barrier.getOwner()))
                hardness = 6;
        }

        if (hardness == -1.0f)
        {
            return 0.0f;
        }
        int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
        return player.getDestroySpeed(state) / hardness / (float) i;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidBarrierEmitterTile(pos, state);
    }
}
