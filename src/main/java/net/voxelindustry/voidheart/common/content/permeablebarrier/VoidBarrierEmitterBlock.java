package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class VoidBarrierEmitterBlock extends Block implements BlockEntityProvider
{
    public VoidBarrierEmitterBlock()
    {
        super(FabricBlockSettings
                .of(Material.STONE)
                .strength(60F, 1200.0F)
                .sounds(BlockSoundGroup.STONE)
                .requiresTool());

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.NORTH)
        );
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.onPlaced(world, pos, state, placer, itemStack);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof VoidBarrierEmitterTile barrier && placer != null)
            barrier.setOwner(placer.getUuid());
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos)
    {
        float hardness = state.getHardness(world, pos);

        var tile = world.getBlockEntity(pos);
        if (tile instanceof VoidBarrierEmitterTile barrier)
        {
            if (Objects.equals(player.getUuid(), barrier.getOwner()))
                hardness = 6;
        }

        if (hardness == -1.0f)
        {
            return 0.0f;
        }
        int i = player.canHarvest(state) ? 30 : 100;
        return player.getBlockBreakingSpeed(state) / hardness / (float) i;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidBarrierEmitterTile(pos, state);
    }
}
