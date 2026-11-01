package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class RavenousStorageMatrixBlock extends Block implements EntityBlock
{
    public static final EnumProperty<RavenousStorageMatrixPart> PART = EnumProperty.create("part", RavenousStorageMatrixPart.class);

    public RavenousStorageMatrixBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(PART, RavenousStorageMatrixPart.BASE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    /**
     * Keeps the stored essence on the dropped item, an empty matrix drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousStorageMatrixTile matrix && !matrix.storage().isEmpty())
            stack.set(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE, matrix.storage().copy());
        return List.of(stack);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        var storage = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (storage != null && world.getBlockEntity(pos) instanceof RavenousStorageMatrixTile tile)
            tile.restoreStorage(storage);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousStorageMatrixTile(pos, state);
    }

    public enum RavenousStorageMatrixPart implements StringRepresentable
    {
        BASE("base"),
        PILLAR_TOP("pillar_top"),
        PILLAR_BOTTOM("pillar_bottom"),
        PILLAR_NORTH("pillar_north"),
        PILLAR_SOUTH("pillar_south"),
        PILLAR_EAST("pillar_east"),
        PILLAR_WEST("pillar_west");

        private final String name;

        RavenousStorageMatrixPart(String name)
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
