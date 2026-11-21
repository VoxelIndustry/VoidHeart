package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A low voidstone basin. Shards used on it sink in, and an empty hand takes out what has grown.
 */
public class CrystalCradleBlock extends Block implements EntityBlock
{
    /** The basin floor, in pixels, where the bundle rises from. */
    public static final float FLOOR = 2;

    private static final VoxelShape SHAPE = Shapes.join(Block.box(1, 0, 1, 15, 6, 15), Block.box(3, FLOOR, 3, 13, 6, 13), BooleanOp.ONLY_FIRST);

    public CrystalCradleBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof CrystalCradleTile cradle)
            cradle.claim(player.getUUID());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var shardKind = CrystalBundles.kindOfShard(stack);
        var bundle = CrystalBundles.bundleOf(stack);
        if (shardKind == null && bundle == null)
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof CrystalCradleTile cradle))
            return InteractionResult.SUCCESS;

        cradle.claim(player.getUUID());
        // Regrowing would let a bundle that drew taint in start over empty
        if (CrystalBundles.soakOf(stack) > 0)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".crystal_cradle.refused.soaked"));
            return InteractionResult.FAIL;
        }
        var habit = CrystalBundles.habitOf(stack);
        CrystalKind kind;
        int worth;
        if (shardKind != null)
        {
            kind = shardKind;
            worth = Math.min(stack.getCount(), Math.max(1, cradle.room()));
        }
        else
        {
            kind = CrystalBundles.kindOfBundle(bundle);
            worth = CrystalBundles.sizeOf(bundle).worth();
        }

        var refusal = cradle.seed(kind, worth, habit);
        if (refusal != CradleGrowth.Refusal.NONE)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".crystal_cradle.refused." + refusal.name().toLowerCase(Locale.ROOT)));
            return InteractionResult.FAIL;
        }
        stack.consume(shardKind != null ? worth : 1, player);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof CrystalCradleTile cradle))
            return InteractionResult.SUCCESS;
        var items = cradle.harvest();
        if (items.isEmpty())
            return InteractionResult.PASS;
        for (var item : items)
            player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
        level.playSound(null, pos, SoundEvents.MEDIUM_AMETHYST_BUD_BREAK, SoundSource.BLOCKS, 1F, 1F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CrystalCradleTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, CrystalContent.CRYSTAL_CRADLE_TILE, level.isClientSide() ? CrystalCradleTile::clientTick : CrystalCradleTile::serverTick);
    }
}
