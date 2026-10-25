package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.*;
import static net.voxelindustry.voidheart.common.block.StateProperties.BROKEN;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class PortalFrameCoreBlock extends PortalFrameBlock
{
    public PortalFrameCoreBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(4F)
                .sound(SoundType.STONE)
                .randomTicks()
                .emissiveRendering(state -> state.getValue(BlockStateProperties.LIT)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var tileOpt = world.getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE);

        if (tileOpt.isEmpty())
            return InteractionResult.PASS;

        var tile = tileOpt.get();
        if (world.isClientSide())
            return InteractionResult.PASS;

        // Core block is probably a previously broken portal
        if (tile.isBroken())
        {
            var portalFormer = PortalFormer.tryForm(world, state, pos, hit.getDirection());
            if (portalFormer.maySucceed())
            {
                portalFormer.execute();
                if (portalFormer.success())
                {
                    tile.setBroken(false);
                    if (PortalLinker.tryRelink(player, tile))
                    {
                        player.sendOverlayMessage(Component.translatable(MODID + ".portal_relinked_successful"));
                        return InteractionResult.SUCCESS;
                    }
                }
                else
                    player.sendOverlayMessage(Component.translatable(MODID + ".portal_cannot_form_back"));
            }
            else
                player.sendOverlayMessage(Component.translatable(MODID + ".portal_cannot_form_back"));
        }
        else if (tile.getLinkedWorld() == null && tile.getPreviousLinkedWorld() != null && PortalLinker.tryRelink(player, tile))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_relinked_successful"));
            VoidHeart.PORTAL_LINK_CRITERION.trigger((ServerPlayer) player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalFrameCoreTile(pos, state);
    }

    @Override
    protected void initDefaultState()
    {
        registerDefaultState(getStateDefinition().any()
                .setValue(NORTH, FrameConnection.NONE)
                .setValue(SOUTH, FrameConnection.NONE)
                .setValue(EAST, FrameConnection.NONE)
                .setValue(WEST, FrameConnection.NONE)
                .setValue(UP, FrameConnection.NONE)
                .setValue(DOWN, FrameConnection.NONE)
                .setValue(BlockStateProperties.FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BROKEN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, BlockStateProperties.FACING, BlockStateProperties.LIT, BROKEN);
    }
}
