package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Placed under a Void Altar, under the source pillar of a Shatter Forge or under a crafting table, it works that station for its placer. Used, it
 * says what it serves and what it is doing.
 */
public class OfficiantBlock extends Block implements EntityBlock
{
    public OfficiantBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof OfficiantTile officiant)
            officiant.setOwner(player.getUUID());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof OfficiantTile officiant))
            return InteractionResult.SUCCESS;

        var kind = officiant.stationKind();
        if (kind == null)
            player.sendOverlayMessage(Component.translatable(MODID + ".officiant.no_station"));
        else if (officiant.getClaim() == null)
            player.sendOverlayMessage(Component.translatable(MODID + ".officiant.idle." + kind.name().toLowerCase(Locale.ROOT)));
        else
            player.sendOverlayMessage(Component.translatable(MODID + ".officiant.working", officiant.getClaim().recipe()));
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new OfficiantTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, WorksContent.OFFICIANT_TILE, OfficiantTile::tick);
    }
}
