package net.voxelindustry.voidheart.common.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.block.StateProperties.*;

public class VoidMonolithBlock extends Block
{
    private static final Identifier FOUND_TABLET = Identifier.fromNamespaceAndPath(MODID, "knowledge_tablet");

    public VoidMonolithBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .sound(SoundType.STONE));

        registerDefaultState(getStateDefinition().any()
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(BROKEN, false)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved)
    {
        if (state.getValue(BROKEN) && world.getRandom().nextInt(5) == 0)
            world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.BLOCKS, 1, 1);
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (!state.getValue(BROKEN))
            return singletonList(new ItemStack(VoidHeartBlocks.VOID_MONOLITH));

        // Dungeons and the Daedalus hold hundreds of monoliths, which would make tablets too easy to farm
        var dimension = builder.getLevel().dimension();
        if (dimension.equals(VoidDungeons.WORLD_KEY) || dimension.equals(VoidDaedalus.WORLD_KEY))
            return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE));

        if (builder.getLevel().getRandom().nextFloat() < tabletChance(builder.getOptionalParameter(LootContextParams.THIS_ENTITY)))
            return List.of(new ItemStack(VoidHeartBlocks.VOIDSTONE), new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
        return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE));
    }

    /**
     * Generous until the breaker has held a first tablet, which starts their research.
     */
    private static float tabletChance(@Nullable Entity breaker)
    {
        var config = VoidHeartConfig.get();
        if (!(breaker instanceof ServerPlayer player))
            return config.knowledgeTabletChance;

        var advancement = player.level().getServer().getAdvancements().get(FOUND_TABLET);
        boolean found = advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone();
        return found ? config.knowledgeTabletChance : config.knowledgeFirstTabletChance;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos posFrom, BlockState newState, RandomSource random)
    {
        switch (direction)
        {
            case DOWN ->
            {
                Boolean down = state.getValue(DOWN);
                if (down && !newState.is(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.setValue(DOWN, false);
                else if (!down && newState.is(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.setValue(DOWN, true);
            }
            case UP ->
            {
                Boolean up = state.getValue(UP);
                if (up && !newState.is(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.setValue(UP, false);
                else if (!up && newState.is(VoidHeartBlocks.VOID_MONOLITH))
                    state = state.setValue(UP, true);
            }
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(UP, DOWN, BlockStateProperties.LIT, BROKEN);
    }
}
