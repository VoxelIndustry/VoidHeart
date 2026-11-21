package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Part;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Eight of these placed as a 2x2x2 cube join into one Ravenous Storage holding 128 times as much. Each block keeps its
 * share of the essence, and carries it when broken out, which leaves the other seven loose.
 */
public class GreatRavenousStorageBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final IntegerProperty    FILL = IntegerProperty.create("fill", 0, StorageFill.MAX_FILL);

    private static final VoxelShape[][] SHAPES   = new VoxelShape[Part.values().length][StorageFill.MAX_FILL + 1];
    private static final VoxelShape[][] OUTLINES = new VoxelShape[Part.values().length][StorageFill.MAX_FILL + 1];

    /**
     * Each block collides as its own quarter. Any of them selects the whole cube.
     */
    static
    {
        for (var part : Part.values())
        {
            for (var fill = 0; fill <= StorageFill.MAX_FILL; fill++)
                SHAPES[part.ordinal()][fill] = shape(GreatStorageLayout.boxes(part, fill));
        }
        for (var part : Part.CORNERS)
        {
            for (var fill = 0; fill <= StorageFill.MAX_FILL; fill++)
            {
                var outline = Shapes.empty();
                for (var other : Part.CORNERS)
                    outline = Shapes.or(outline, SHAPES[other.ordinal()][fill].move(other.dx() - part.dx(), other.dy() - part.dy(), other.dz() - part.dz()));
                OUTLINES[part.ordinal()][fill] = outline;
            }
        }
        for (var fill = 0; fill <= StorageFill.MAX_FILL; fill++)
        {
            SHAPES[Part.LOOSE.ordinal()][fill] = Shapes.block();
            OUTLINES[Part.LOOSE.ordinal()][fill] = Shapes.block();
        }
    }

    private static VoxelShape shape(List<GreatStorageLayout.Box> boxes)
    {
        var shape = Shapes.empty();
        for (var box : boxes)
            shape = Shapes.or(shape, Block.box(box.x0(), box.y0(), box.z0(), box.x1(), box.y1(), box.z1()));
        return shape.optimize();
    }

    public GreatRavenousStorageBlock(Properties settings)
    {
        super(settings);

        registerDefaultState(getStateDefinition().any()
                .setValue(PART, Part.LOOSE)
                .setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART, FILL);
    }

    /**
     * The cube's lowest north-west block, seen from one of its blocks.
     */
    public static BlockPos origin(BlockPos pos, Part part)
    {
        return pos.offset(-part.dx(), -part.dy(), -part.dz());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide())
            return;

        var share = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (share != null && level.getBlockEntity(pos) instanceof GreatRavenousStorageTile tile)
            tile.restoreShare(share);
        tryJoin(level, pos, placer instanceof Player player ? player : null);
    }

    /**
     * Joins the first whole cube of loose blocks this one completes. A cube whose blocks hold different aspects stays
     * loose, and the player is told why.
     */
    public static boolean tryJoin(Level level, BlockPos pos, @Nullable Player player)
    {
        var refused = false;
        for (var corner : Part.CORNERS)
        {
            var origin = origin(pos, corner);
            var blocks = looseCube(level, origin);
            if (blocks == null)
                continue;

            var aspect = GreatStorageShares.commonAspect(GreatRavenousStorageTile.aspects(blocks), GreatRavenousStorageTile.amounts(blocks),
                    EssenceRegistry.EMPTY);
            if (aspect == null)
            {
                refused = true;
                continue;
            }

            GreatRavenousStorageTile.join(blocks, aspect);
            var fill = StorageFill.fillOf(Math.toIntExact(sum(GreatRavenousStorageTile.amounts(blocks))), GreatRavenousStorageTile.CAPACITY);
            for (var index = 0; index < blocks.length; index++)
            {
                var member = blocks[index].getBlockPos();
                level.setBlock(member, level.getBlockState(member).setValue(PART, Part.CORNERS.get(index)).setValue(FILL, fill), Block.UPDATE_ALL);
            }
            level.playSound(null, origin.offset(1, 1, 1), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1, 0.6F);
            return true;
        }
        if (refused && player != null)
            player.sendOverlayMessage(Component.translatable(MODID + ".great_ravenous_storage.refused"));
        return false;
    }

    private static long sum(int[] amounts)
    {
        long sum = 0;
        for (var amount : amounts)
            sum += amount;
        return sum;
    }

    /**
     * The eight loose blocks of a cube starting at {@code origin}, in the order of {@link Part#CORNERS}, or null when one
     * is missing or already part of a cube.
     */
    private static GreatRavenousStorageTile @Nullable [] looseCube(Level level, BlockPos origin)
    {
        var blocks = new GreatRavenousStorageTile[GreatStorageLayout.BLOCKS];
        for (var index = 0; index < blocks.length; index++)
        {
            var part = Part.CORNERS.get(index);
            var member = origin.offset(part.dx(), part.dy(), part.dz());
            var state = level.getBlockState(member);
            if (!(state.getBlock() instanceof GreatRavenousStorageBlock) || state.getValue(PART) != Part.LOOSE
                    || !(level.getBlockEntity(member) instanceof GreatRavenousStorageTile tile))
                return null;
            blocks[index] = tile;
        }
        return blocks;
    }

    /**
     * A block leaving the cube, however it goes, leaves the other seven loose with their shares.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);

        var part = state.getValue(PART);
        if (part == Part.LOOSE)
            return;
        var origin = origin(pos, part);
        for (var other : Part.CORNERS)
        {
            var member = origin.offset(other.dx(), other.dy(), other.dz());
            var memberState = level.getBlockState(member);
            if (memberState.is(this) && memberState.getValue(PART) == other)
                level.setBlock(member, memberState.setValue(PART, Part.LOOSE).setValue(FILL, 0), Block.UPDATE_ALL);
        }
    }

    /**
     * The block keeps its share on the dropped item. One holding nothing drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof GreatRavenousStorageTile tile
                && !tile.share().isUnattuned() && tile.share().amount() > 0)
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, tile.share().copy());
        return List.of(stack);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return OUTLINES[state.getValue(PART).ordinal()][state.getValue(FILL)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES[state.getValue(PART).ordinal()][state.getValue(FILL)];
    }

    /**
     * Whether a block belongs to the cube held at {@code holder}, which relays see through to reach it.
     */
    public static boolean isPartOf(BlockState state, BlockPos pos, BlockPos holder)
    {
        if (!(state.getBlock() instanceof GreatRavenousStorageBlock) || state.getValue(PART) == Part.LOOSE)
            return false;
        return origin(pos, state.getValue(PART)).equals(holder);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GreatRavenousStorageTile(pos, state);
    }
}
