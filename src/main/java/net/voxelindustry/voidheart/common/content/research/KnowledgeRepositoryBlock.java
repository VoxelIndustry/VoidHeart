package net.voxelindustry.voidheart.common.content.research;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A void pillar holding up a knowledge tablet. Feeds on items held out to it, and opens the research screen for anything else.
 */
public class KnowledgeRepositoryBlock extends Block implements EntityBlock
{
    private static final VoxelShape PILLAR_SHAPE = Shapes.or(
            box(3, 0, 3, 13, 4, 13),
            box(4, 4, 4, 12, 12, 12),
            box(3, 12, 3, 13, 16, 13)
    );
    private static final VoxelShape SHAPE = Shapes.or(PILLAR_SHAPE, box(5, 16, 7, 11, 24, 9));

    public KnowledgeRepositoryBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .lightLevel(state -> 11));
    }

    /**
     * Turns a void pillar into a repository bound to the player who placed the tablet.
     */
    public static void awaken(ServerLevel level, BlockPos pos, ServerPlayer player)
    {
        level.setBlockAndUpdate(pos, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof KnowledgeRepositoryTile repository)
            repository.setOwner(player.getUUID());

        Knowledge.bind(level.getServer(), player.getUUID(), GlobalPos.of(level.dimension(), pos));
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1, 0.6F);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return PILLAR_SHAPE;
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
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new KnowledgeRepositoryTile(pos, state);
    }
}
