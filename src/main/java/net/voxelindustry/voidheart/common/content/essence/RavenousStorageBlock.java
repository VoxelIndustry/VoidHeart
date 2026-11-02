package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import java.util.List;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class RavenousStorageBlock extends Block implements EntityBlock
{
    public static final EnumProperty<RavenousStoragePart> PART = EnumProperty.create("part", RavenousStoragePart.class);

    public RavenousStorageBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(PART, RavenousStoragePart.BASE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING, PART);
    }

    /**
     * Keeps the stored essence on the dropped item, an empty storage drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousStorageTile storage
                && !storage.storage().isUnattuned() && storage.storage().amount() > 0)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, storage.storage().copy());
        return List.of(stack);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null && world.getBlockEntity(pos) instanceof RavenousStorageTile tile)
            tile.restoreStorage(storage);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousStorageTile(pos, state);
    }

    public enum RavenousStoragePart implements StringRepresentable
    {
        BASE("base"),
        PILLAR("pillar");

        private final String name;

        RavenousStoragePart(String name)
        {
            this.name = name;
        }

        public String toString()
        {
            return this.name;
        }

        public String getSerializedName()
        {
            return this.name;
        }
    }
}
