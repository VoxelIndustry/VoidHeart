package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.block.TwoHighBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Two blocks high: a maw holding the essence and burning it, and an open chamber above where the burning essence is pulled down into it.
 */
public class RavenousEngineBlock extends TwoHighBlock<RavenousEngineBlock.Part>
{
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final int[] POST_CORNERS = {1, 12};
    private static final int   POST_WIDTH   = 3;

    private static final VoxelShape MAW_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(1, 3, 1, 15, 13, 15),
            Block.box(0, 13, 0, 16, 15, 16),
            posts(15, 16));
    private static final VoxelShape CHAMBER_SHAPE = Shapes.or(posts(0, 15), caps(15, 16));
    private static final VoxelShape MAW_OUTLINE     = Shapes.or(MAW_SHAPE, CHAMBER_SHAPE.move(0, 1, 0));
    private static final VoxelShape CHAMBER_OUTLINE = Shapes.or(MAW_SHAPE.move(0, -1, 0), CHAMBER_SHAPE);

    private static VoxelShape posts(double from, double to)
    {
        var shape = Shapes.empty();
        for (var x : POST_CORNERS)
        {
            for (var z : POST_CORNERS)
                shape = Shapes.or(shape, Block.box(x, from, z, x + POST_WIDTH, to, z + POST_WIDTH));
        }
        return shape;
    }

    // A pixel tall, overhanging their post by half a pixel, as in the model
    private static VoxelShape caps(double from, double to)
    {
        var shape = Shapes.empty();
        for (var x : POST_CORNERS)
        {
            for (var z : POST_CORNERS)
                shape = Shapes.or(shape, Block.box(x - 0.5, from, z - 0.5, x + POST_WIDTH + 0.5, to, z + POST_WIDTH + 0.5));
        }
        return shape;
    }

    public RavenousEngineBlock(Properties properties)
    {
        super(properties, Part.MAW, Part.CHAMBER);
    }

    @Override
    protected EnumProperty<Part> partProperty()
    {
        return PART;
    }

    @Override
    protected void placed(Level level, BlockPos pos, BlockState state, ItemStack stack)
    {
        var tank = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (tank != null && level.getBlockEntity(pos) instanceof RavenousEngineTile engine)
            engine.restoreTank(tank);
    }

    /**
     * Keeps the essence on the dropped item. An empty engine drops a plain item so it stacks.
     */
    @Override
    protected List<ItemStack> lowerDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousEngineTile engine && !engine.tank().isEmpty())
            stack.set(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE, engine.tank().copy());
        return List.of(stack);
    }

    @Override
    protected VoxelShape halfShape(BlockState state, boolean upper)
    {
        return upper ? CHAMBER_SHAPE : MAW_SHAPE;
    }

    @Override
    protected VoxelShape outline(BlockState state, boolean upper)
    {
        return upper ? CHAMBER_OUTLINE : MAW_OUTLINE;
    }

    @Override
    protected BlockEntity newLowerEntity(BlockPos pos, BlockState state)
    {
        return new RavenousEngineTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return isLower(state) ? createTickerHelper(type, RavenousEngineContent.RAVENOUS_ENGINE_TILE, RavenousEngineTile::tick) : null;
    }

    public enum Part implements StringRepresentable
    {
        MAW("maw"),
        CHAMBER("chamber");

        private final String name;

        Part(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }
}
