package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.block.TwoHighBlock;
import org.jetbrains.annotations.Nullable;


import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A two-block azurium stake driven into the ground: the base, bound by its collar, holds the tile; the tip stands above it. Its renderer draws both,
 * leaning as they were driven in, so neither has a model of its own.
 * <p>
 * Using it with an empty hand bleeds a larger share of the flow, sneaking a smaller one.
 */
public class AzuriumSpikeBlock extends TwoHighBlock<AzuriumSpikeBlock.Part>
{
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape BASE_SHAPE    = Shapes.or(Block.box(5, 0, 5, 11, 3, 11), Block.box(6, 3, 6, 10, 16, 10));
    private static final VoxelShape TIP_SHAPE     = Block.box(6, 0, 6, 10, 15, 10);
    private static final VoxelShape BASE_OUTLINE  = Shapes.or(BASE_SHAPE, TIP_SHAPE.move(0, 1, 0));
    private static final VoxelShape TIP_OUTLINE   = Shapes.or(BASE_SHAPE.move(0, -1, 0), TIP_SHAPE);

    public AzuriumSpikeBlock(Properties properties)
    {
        super(properties, Part.BASE, Part.TIP);
    }

    @Override
    protected EnumProperty<Part> partProperty()
    {
        return PART;
    }

    @Override
    protected void placed(Level level, BlockPos pos, BlockState state, ItemStack stack)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AzuriumSpikeTile spike)
            spike.driveIn(level.getRandom());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        var base = isUpper(state) ? pos.below() : pos;
        if (!(level.getBlockEntity(base) instanceof AzuriumSpikeTile spike))
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            spike.cycleSetting(player.isShiftKeyDown() ? -1 : 1);
            player.sendOverlayMessage(Component.translatable(MODID + ".spike.setting", Math.round(LeySpikeMath.share(spike.setting()) * 100)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape halfShape(BlockState state, boolean upper)
    {
        return upper ? TIP_SHAPE : BASE_SHAPE;
    }

    @Override
    protected VoxelShape outline(BlockState state, boolean upper)
    {
        return upper ? TIP_OUTLINE : BASE_OUTLINE;
    }

    @Override
    protected BlockEntity newLowerEntity(BlockPos pos, BlockState state)
    {
        return new AzuriumSpikeTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return isLower(state) ? createTickerHelper(type, LeySpikeContent.AZURIUM_SPIKE_TILE, AzuriumSpikeTile::tick) : null;
    }

    public enum Part implements StringRepresentable
    {
        BASE("base"),
        TIP("tip");

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
