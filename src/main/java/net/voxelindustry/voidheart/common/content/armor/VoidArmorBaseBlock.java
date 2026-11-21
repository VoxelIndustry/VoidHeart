package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Where a frame stands between sorties and charges: an arch three blocks wide and four high, two pillars and a lintel over a low plinth, the frame held
 * open between them by clamps at its shoulders. The plinth is the base proper and keeps the frame, its charge and its cell; the rest of the arch is parts of
 * the same block, placed and broken with it.
 * <p>
 * A frame used on any part of an empty arch docks there. Using the frame itself steps into it; using the plinth, the pillars or the lintel opens its grids
 * and racks. A rubrum cell used on the arch goes in its socket, for charging away from any pylon, and comes back out with an empty hand while no frame
 * stands there; with one docked, the cell is in the frame's screen.
 * <p>
 * The space the frame stands in has no collision, so a wearer steps into it and out again, but takes clicks on the docked frame's silhouette.
 */
public class VoidArmorBaseBlock extends Block implements EntityBlock
{
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");
    /** The way a docked frame faces: toward whoever placed the base. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Whether a pillar part stands on the frame's right, so its model turns to face the middle. */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** Where a wearer's feet stand on the plinth, above the base's own position. */
    public static final double STAND_HEIGHT = 3 / 16D;
    public static final int    HEIGHT       = 4;

    public enum Part implements StringRepresentable
    {
        /** The plinth, the base proper. */
        BASE,
        /** Where the frame stands, one block above the plinth and one above that. */
        FRAME,
        FOOT,
        SHAFT,
        CROWN,
        /** The beam across the top, the clamps holding the frame hanging from it. */
        LINTEL;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // Shapes as for a base facing north, whose frame faces north, with the left pillar at -x; turned for the others
    private static final VoxelShape PLINTH       = Shapes.or(box(1, 0, 1, 15, 2, 15), box(3, 2, 3, 13, 3, 13));
    private static final VoxelShape FOOT_SHAPE   = Shapes.or(box(0, 0, 3, 12, 3, 13), box(2, 3, 5, 10, 16, 11));
    private static final VoxelShape SHAFT_SHAPE  = box(2, 0, 5, 10, 16, 11);
    private static final VoxelShape CROWN_SHAPE  = Shapes.or(box(2, 0, 5, 10, 8, 11), box(1, 8, 4, 16, 16, 12));
    private static final VoxelShape LINTEL_SHAPE = box(0, 8, 4, 16, 16, 12);
    /** The docked frame's outline in the two blocks it stands in, for clicks only. */
    private static final VoxelShape FRAME_LOW    = box(1, 0, 3, 15, 16, 13);
    private static final VoxelShape FRAME_HIGH   = box(1, 0, 3, 15, 14, 13);

    private static final Map<Direction, Map<Part, VoxelShape[]>> SHAPES = new EnumMap<>(Direction.class);

    static
    {
        for (var facing : Direction.Plane.HORIZONTAL)
        {
            var byPart = new EnumMap<Part, VoxelShape[]>(Part.class);
            byPart.put(Part.FOOT, sides(FOOT_SHAPE, facing));
            byPart.put(Part.SHAFT, sides(SHAFT_SHAPE, facing));
            byPart.put(Part.CROWN, sides(CROWN_SHAPE, facing));
            var lintel = turn(LINTEL_SHAPE, facing, false);
            byPart.put(Part.LINTEL, new VoxelShape[] { lintel, lintel });
            byPart.put(Part.FRAME, new VoxelShape[] { turn(FRAME_LOW, facing, false), turn(FRAME_HIGH, facing, false) });
            SHAPES.put(facing, byPart);
        }
    }

    public VoidArmorBaseBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DOCKED, false).setValue(FACING, Direction.SOUTH).setValue(PART, Part.BASE).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(DOCKED, FACING, PART, RIGHT);
    }

    // The arch's layout

    /**
     * Across the arch, from its left pillar to its right one, seen from the front of the frame.
     */
    public static Direction across(Direction facing)
    {
        return facing.getClockWise();
    }

    public record Placement(BlockPos pos, Part part, boolean right)
    {
    }

    /**
     * Every part of an arch whose plinth is at that spot, the plinth first.
     */
    public static List<Placement> parts(BlockPos base, Direction facing)
    {
        var across = across(facing);
        var parts = new ArrayList<Placement>();
        parts.add(new Placement(base, Part.BASE, false));
        for (int up = 1; up < HEIGHT - 1; up++)
            parts.add(new Placement(base.above(up), Part.FRAME, false));
        parts.add(new Placement(base.above(HEIGHT - 1), Part.LINTEL, false));
        for (boolean right : new boolean[] { false, true })
        {
            var column = base.relative(across, right ? 1 : -1);
            parts.add(new Placement(column, Part.FOOT, right));
            for (int up = 1; up < HEIGHT - 1; up++)
                parts.add(new Placement(column.above(up), Part.SHAFT, right));
            parts.add(new Placement(column.above(HEIGHT - 1), Part.CROWN, right));
        }
        return parts;
    }

    /**
     * The plinth of the arch a part belongs to, or null when it stands alone.
     */
    public static @Nullable BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state)
    {
        if (!state.is(VoidArmorContent.VOID_ARMOR_BASE))
            return null;
        if (state.getValue(PART) == Part.BASE)
            return pos;
        var facing = state.getValue(FACING);
        var across = across(facing);
        for (int down = 0; down < HEIGHT; down++)
            for (int side = -1; side <= 1; side++)
            {
                var candidate = pos.below(down).relative(across, side);
                var candidateState = level.getBlockState(candidate);
                if (candidateState.is(VoidArmorContent.VOID_ARMOR_BASE) && candidateState.getValue(PART) == Part.BASE
                    && candidateState.getValue(FACING) == facing && parts(candidate, facing).stream().anyMatch(part -> part.pos().equals(pos)))
                    return candidate;
            }
        return null;
    }

    private static @Nullable VoidArmorBaseTile tileOf(BlockGetter level, BlockPos pos, BlockState state)
    {
        var base = baseOf(level, pos, state);
        return base != null && level.getBlockEntity(base) instanceof VoidArmorBaseTile tile ? tile : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var facing = context.getHorizontalDirection().getOpposite();
        var level = context.getLevel();
        for (var part : parts(context.getClickedPos(), facing))
            if (part.pos().getY() > level.getMaxY() || !level.getBlockState(part.pos()).canBeReplaced(context))
                return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        raiseArch(level, pos, state);
    }

    private static void raiseArch(Level level, BlockPos base, BlockState state)
    {
        for (var part : parts(base, state.getValue(FACING)))
            if (part.part() != Part.BASE)
                level.setBlock(part.pos(), state.setValue(PART, part.part()).setValue(RIGHT, part.right()), Block.UPDATE_ALL);
    }

    /**
     * Puts up a whole arch with its plinth at that spot, as placing the base does, over whatever stood there.
     */
    public static void build(Level level, BlockPos base, Direction facing)
    {
        var state = VoidArmorContent.VOID_ARMOR_BASE.defaultBlockState().setValue(FACING, facing);
        level.setBlockAndUpdate(base, state);
        raiseArch(level, base, state);
    }

    /**
     * Breaking any part takes down the whole arch, and the plinth drops the base with what it held.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide())
        {
            var base = baseOf(level, pos, state);
            if (base != null)
            {
                for (var part : parts(base, state.getValue(FACING)))
                    if (!part.pos().equals(pos) && !part.pos().equals(base) && level.getBlockState(part.pos()).is(this))
                        level.setBlock(part.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                if (!base.equals(pos))
                    level.destroyBlock(base, !player.preventsBlockDrops(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Takes the rest of the arch down with the plinth however it goes, by a command or an explosion as much as by a player.
     */
    public static void removeArch(Level level, BlockPos base, Direction facing)
    {
        for (var part : parts(base, facing))
            if (part.part() != Part.BASE && level.getBlockState(part.pos()).is(VoidArmorContent.VOID_ARMOR_BASE))
                level.setBlock(part.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        return state.getValue(PART) == Part.BASE ? super.getDrops(state, builder) : List.of();
    }

    public static Vec3 standAt(BlockPos pos)
    {
        return new Vec3(pos.getX() + 0.5, pos.getY() + STAND_HEIGHT, pos.getZ() + 0.5);
    }

    // Use

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var base = tileOf(level, pos, state);
        if (base == null)
            return InteractionResult.PASS;
        if (state.getValue(PART) == Part.FRAME)
            return base.hasFrame() ? stepIn(level, base, player) : InteractionResult.PASS;
        if (stack.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.getItem() instanceof VoidArmorItem && !base.hasFrame())
        {
            if (!level.isClientSide())
                base.dock(stack.consumeAndReturn(1, player));
            return InteractionResult.SUCCESS;
        }
        if (stack.is(RubrumContent.RUBRUM_CELL) && base.cell().getItem(0).isEmpty())
        {
            if (!level.isClientSide())
                base.cell().setItem(0, stack.consumeAndReturn(1, player));
            return InteractionResult.SUCCESS;
        }
        return base.hasFrame() ? open(base.getBlockPos(), player) : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        var base = tileOf(level, pos, state);
        if (base == null)
            return InteractionResult.PASS;
        if (state.getValue(PART) == Part.FRAME)
            return base.hasFrame() ? stepIn(level, base, player) : InteractionResult.PASS;
        if (base.hasFrame())
            return open(base.getBlockPos(), player);

        var held = base.cell().getItem(0);
        if (held.isEmpty())
            return InteractionResult.PASS;
        if (!level.isClientSide())
        {
            player.getInventory().placeItemBackInInventory(held, Prediction.SERVER_ONLY);
            base.cell().setItem(0, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult stepIn(Level level, VoidArmorBaseTile base, Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;
        var feet = standAt(base.getBlockPos());
        var refusal = ArmorWear.refusal(serverPlayer, base.frame(), feet);
        if (refusal != null)
        {
            serverPlayer.sendOverlayMessage(refusal);
            return InteractionResult.FAIL;
        }
        ArmorWear.stepIn(serverPlayer, base.undock(), feet);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult open(BlockPos pos, Player player)
    {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new ExtendedMenuProvider<BlockPos>()
            {
                @Override
                public BlockPos getScreenOpeningData(ServerPlayer player)
                {
                    return pos;
                }

                @Override
                public Component getDisplayName()
                {
                    return Component.translatable("item.voidheart.void_armor");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
                {
                    return new VoidArmorMenu(containerId, inventory, pos);
                }
            });
        return InteractionResult.SUCCESS;
    }

    /**
     * An arch holding a frame can't be mined, since the frame travels only worn. A creative break still removes it.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        var base = tileOf(level, pos, state);
        if (base != null && base.hasFrame() && !player.isCreative())
            return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }

    // Shapes

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        var part = state.getValue(PART);
        if (part == Part.BASE)
            return PLINTH;
        var shapes = SHAPES.get(state.getValue(FACING)).get(part);
        if (part == Part.FRAME)
        {
            // Only a docked frame takes clicks, so an empty arch is walked through and clicked through alike
            var base = tileOf(level, pos, state);
            if (base == null || !base.hasFrame())
                return Shapes.empty();
            boolean high = !level.getBlockState(pos.below()).is(this) || level.getBlockState(pos.below()).getValue(PART) != Part.BASE;
            return shapes[high ? 1 : 0];
        }
        return shapes[state.getValue(RIGHT) ? 1 : 0];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(PART) == Part.FRAME ? Shapes.empty() : getShape(state, level, pos, context);
    }

    private static VoxelShape[] sides(VoxelShape left, Direction facing)
    {
        return new VoxelShape[] { turn(left, facing, false), turn(left, facing, true) };
    }

    /**
     * A shape drawn for a base facing north, turned to face the other way, and turned half round again for the right pillar so it faces the middle.
     */
    private static VoxelShape turn(VoxelShape shape, Direction facing, boolean right)
    {
        int quarters = switch (facing)
        {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        } + (right ? 2 : 0);
        var turned = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) ->
        {
            double ax = x0, az = z0, bx = x1, bz = z1;
            for (int i = 0; i < quarters % 4; i++)
            {
                // A quarter turn clockwise seen from above: x becomes 1 - z, z becomes x
                double nax = 1 - bz, naz = ax, nbx = 1 - az, nbz = bx;
                ax = nax;
                az = naz;
                bx = nbx;
                bz = nbz;
            }
            turned[0] = Shapes.or(turned[0], Shapes.box(ax, y0, az, bx, y1, bz));
        });
        return turned[0];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == Part.BASE ? new VoidArmorBaseTile(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidArmorContent.VOID_ARMOR_BASE_TILE, VoidArmorBaseTile::tick);
    }
}
