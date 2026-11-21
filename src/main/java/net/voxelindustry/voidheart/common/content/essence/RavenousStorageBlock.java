package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.block.TwoHighBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.List;

/**
 * Two blocks high: a base holding the essence, and four pillars rising out of it into the block above as it fills.
 */
public class RavenousStorageBlock extends TwoHighBlock<RavenousStorageBlock.RavenousStoragePart>
{
    public static final EnumProperty<RavenousStoragePart> PART = EnumProperty.create("part", RavenousStoragePart.class);
    public static final int                               MAX_FILL = StorageFill.MAX_FILL;
    public static final IntegerProperty                   FILL = IntegerProperty.create("fill", 0, MAX_FILL);

    private static final int[]        PILLAR_CORNERS = {1, 12};
    private static final int          PILLAR_WIDTH   = 3;
    // Caps are a pixel tall and overhang their pillar by half a pixel, as in the block models
    private static final double       CAP_OVERHANG   = 0.5;
    private static final int          PILLARS_FROM   = 12;
    private static final VoxelShape   PLINTH         = Block.box(0, 0, 0, 16, PILLARS_FROM, 16);
    private static final VoxelShape[] BASE_SHAPES    = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] PILLAR_SHAPES  = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] BASE_OUTLINES   = new VoxelShape[MAX_FILL + 1];
    private static final VoxelShape[] PILLAR_OUTLINES = new VoxelShape[MAX_FILL + 1];

    /**
     * Each half collides as its share of the pillars, caps included, so it follows them as they rise. Either half
     * selects the whole storage, the outline reaching into the other block.
     */
    static
    {
        for (var fill = 0; fill <= MAX_FILL; fill++)
        {
            var top = StorageFill.pillarTop(fill);
            BASE_SHAPES[fill] = Shapes.or(PLINTH, pillars(PILLARS_FROM, top, 0));
            PILLAR_SHAPES[fill] = pillars(PILLARS_FROM, top, 16);
            BASE_OUTLINES[fill] = Shapes.or(BASE_SHAPES[fill], PILLAR_SHAPES[fill].move(0, 1, 0));
            PILLAR_OUTLINES[fill] = Shapes.or(BASE_SHAPES[fill].move(0, -1, 0), PILLAR_SHAPES[fill]);
        }
    }

    /**
     * Pillars rising from {@code from} to {@code top} pixels above the base bottom, cut to the block starting
     * {@code offset} pixels up.
     */
    private static VoxelShape pillars(int from, int top, int offset)
    {
        var shape = Shapes.empty();
        var capFrom = top - 1;
        for (var x : PILLAR_CORNERS)
        {
            for (var z : PILLAR_CORNERS)
            {
                shape = Shapes.or(shape, slice(x, x + PILLAR_WIDTH, from, capFrom, z, z + PILLAR_WIDTH, offset));
                shape = Shapes.or(shape, slice(x - CAP_OVERHANG, x + PILLAR_WIDTH + CAP_OVERHANG, capFrom, top, z - CAP_OVERHANG, z + PILLAR_WIDTH + CAP_OVERHANG, offset));
            }
        }
        return shape;
    }

    private static VoxelShape slice(double x0, double x1, double y0, double y1, double z0, double z1, int offset)
    {
        var from = Math.max(y0 - offset, 0);
        var to = Math.min(y1 - offset, 16);
        return to > from ? Block.box(x0, from, z0, x1, to, z1) : Shapes.empty();
    }

    public RavenousStorageBlock(Properties settings)
    {
        super(settings, RavenousStoragePart.BASE, RavenousStoragePart.PILLAR);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(PART, RavenousStoragePart.BASE)
                .setValue(FILL, 0)
        );
    }

    @Override
    protected EnumProperty<RavenousStoragePart> partProperty()
    {
        return PART;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.FACING, FILL);
    }

    @Override
    protected void placed(Level level, BlockPos pos, BlockState state, ItemStack stack)
    {
        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null && level.getBlockEntity(pos) instanceof RavenousStorageTile tile)
            tile.restoreStorage(storage);
    }

    /**
     * Keeps the stored essence on the dropped item, an empty storage drops a plain item so it stacks.
     */
    @Override
    protected List<ItemStack> lowerDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousStorageTile storage
                && !storage.storage().isUnattuned() && storage.storage().amount() > 0)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, storage.storage().copy());
        return List.of(stack);
    }

    /**
     * A base from before storages stood two blocks high has no pillars above, so it outlines as itself alone and keeps its essence.
     */
    @Override
    protected VoxelShape halfShape(BlockState state, boolean upper)
    {
        return (upper ? PILLAR_SHAPES : BASE_SHAPES)[state.getValue(FILL)];
    }

    @Override
    protected VoxelShape outline(BlockState state, boolean upper)
    {
        return (upper ? PILLAR_OUTLINES : BASE_OUTLINES)[state.getValue(FILL)];
    }

    @Override
    protected BlockEntity newLowerEntity(BlockPos pos, BlockState state)
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
