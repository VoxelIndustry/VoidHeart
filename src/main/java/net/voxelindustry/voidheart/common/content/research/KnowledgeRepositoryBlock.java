package net.voxelindustry.voidheart.common.content.research;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Map;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A lectern corrupted by the knowledge tablet laid on it. Feeds on items held out to it, and opens the research screen for anything else.
 */
public class KnowledgeRepositoryBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape COLLISION_SHAPE = Shapes.or(Block.column(16, 0, 2), Block.column(8, 2, 14));
    // The vanilla lectern's steps, each one pixel taller for the tablet lying on the plateau
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.boxZ(16, 10, 15, 1, 5.333333),
            Block.boxZ(16, 12, 17, 5.333333, 9.666667),
            Block.boxZ(16, 14, 19, 9.666667, 14),
            COLLISION_SHAPE
    ));

    private static final double PLATEAU_HEIGHT       = 0.9;
    private static final int    TABLET_DUST_COLOR    = 0x36C5B0;
    private static final float  TABLET_DUST_SIZE     = 1.2F;
    private static final int    TABLET_DUST_COUNT    = 40;
    private static final int    INK_COUNT            = 8;
    private static final int    REVERSE_PORTAL_COUNT = 80;
    private static final int    ENCHANT_COUNT        = 40;

    public KnowledgeRepositoryBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .lightLevel(state -> 11));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * An empty lectern swallows every click before the held item is asked, so the tablet has to get there first.
     * A lectern holding a book is left alone, the tablet would otherwise have to decide what becomes of the book.
     */
    public static InteractionResult corruptLectern(Player player, Level world, InteractionHand hand, BlockHitResult hit)
    {
        var pos = hit.getBlockPos();
        var state = world.getBlockState(pos);
        var stack = player.getItemInHand(hand);

        if (player.isSpectator() || !stack.is(VoidHeartItems.KNOWLEDGE_TABLET) || !state.is(Blocks.LECTERN) || state.getValue(LecternBlock.HAS_BOOK) || !world.mayInteract(player, pos))
            return InteractionResult.PASS;

        if (world instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer)
        {
            awaken(level, pos, serverPlayer, state);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Turns a lectern into a repository bound to the player who placed the tablet.
     */
    private static void awaken(ServerLevel level, BlockPos pos, ServerPlayer player, BlockState lectern)
    {
        level.setBlockAndUpdate(pos, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState().setValue(FACING, lectern.getValue(LecternBlock.FACING)));
        if (level.getBlockEntity(pos) instanceof KnowledgeRepositoryTile repository)
            repository.setOwner(player.getUUID());

        Knowledge.bind(level.getServer(), player.getUUID(), GlobalPos.of(level.dimension(), pos));
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1, 0.6F);
        emitCorruption(level, pos, lectern);
    }

    private static void emitCorruption(ServerLevel level, BlockPos pos, BlockState lectern)
    {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + PLATEAU_HEIGHT;
        double z = pos.getZ() + 0.5;

        // The wood gives way, the void rises through it and the tablet flares on top
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(lectern));
        level.sendParticles(ParticleTypes.SQUID_INK, x, pos.getY() + 0.5, z, INK_COUNT, 0.3, 0.4, 0.3, 0.03);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 0.5, z, REVERSE_PORTAL_COUNT, 0.35, 0.5, 0.35, 0.06);
        level.sendParticles(new DustParticleOptions(TABLET_DUST_COLOR, TABLET_DUST_SIZE), x, y, z, TABLET_DUST_COUNT, 0.4, 0.1, 0.3, 0);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.4, z, ENCHANT_COUNT, 0.3, 0.3, 0.3, 0.6);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state)
    {
        return COLLISION_SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state)
    {
        return true;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level) || !(world.getBlockEntity(pos) instanceof KnowledgeRepositoryTile repository))
            return InteractionResult.SUCCESS;

        if (repository.getOwner().isEmpty())
            repository.setOwner(player.getUUID());
        if (!repository.getOwner().get().equals(player.getUUID()))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".knowledge.not_owner"));
            return InteractionResult.SUCCESS;
        }

        // The last repository used is the one its owner is bound to, so they never add up
        Knowledge.bind(level.getServer(), player.getUUID(), GlobalPos.of(level.dimension(), pos));

        if (Knowledge.valueOf(heldStack) > 0)
            Knowledge.feed(serverPlayer, heldStack);
        else
            openScreen(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }

    private static void openScreen(ServerPlayer player, BlockPos pos)
    {
        player.openMenu(new ExtendedMenuProvider<BlockPos>()
        {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer player)
            {
                return pos;
            }

            @Override
            public Component getDisplayName()
            {
                return Component.translatable("block.voidheart.knowledge_repository");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
            {
                return new KnowledgeRepositoryMenu(containerId, inventory, pos);
            }
        });
        Knowledge.sendSnapshot(player);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new KnowledgePayloads.Recipes(RecipeSheet.collect(player.level().getServer())));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return world.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.KNOWLEDGE_REPOSITORY, KnowledgeRepositoryTile::tick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new KnowledgeRepositoryTile(pos, state);
    }
}
