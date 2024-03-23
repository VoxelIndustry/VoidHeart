package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.*;
import static net.voxelindustry.voidheart.common.block.StateProperties.BROKEN;

public class PortalFrameCoreBlock extends PortalFrameBlock
{
    public PortalFrameCoreBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.BLACK)
                .instrument(Instrument.BASEDRUM)
                .requiresTool()
                .strength(4F)
                .sounds(BlockSoundGroup.STONE)
                .ticksRandomly()
                .emissiveLighting((state, world, pos) -> state.get(Properties.LIT)));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        var tileOpt = world.getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE);

        if (tileOpt.isEmpty())
            return ActionResult.PASS;

        var tile = tileOpt.get();
        if (!world.isClient())
        {
            // Core block is probably a previously broken portal
            if (tile.isBroken())
            {
                DeferredRollbackWork<PortalFormerState> portalFormer = PortalFormer.tryForm(world, state, pos, hit.getSide());
                if (portalFormer.maySucceed())
                {
                    portalFormer.execute();
                    if (portalFormer.success())
                    {
                        tile.setBroken(false);
                        if (PortalLinker.tryRelink(player, tile))
                        {
                            player.sendMessage(Text.translatable(MODID + ".portal_relinked_successful"), true);
                            return ActionResult.SUCCESS;
                        }
                    }
                    else
                        player.sendMessage(Text.translatable(MODID + ".portal_cannot_form_back"), true);
                }
                else
                    player.sendMessage(Text.translatable(MODID + ".portal_cannot_form_back"), true);
            }
            else if (tile.getLinkedWorld() == null && tile.getPreviousLinkedWorld() != null && PortalLinker.tryRelink(player, tile))
            {
                player.sendMessage(Text.translatable(MODID + ".portal_relinked_successful"), true);
                VoidHeart.PORTAL_LINK_CRITERION.trigger((ServerPlayerEntity) player);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalFrameCoreTile(pos, state);
    }

    @Override
    protected void initDefaultState()
    {
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, FrameConnection.NONE)
                .with(SOUTH, FrameConnection.NONE)
                .with(EAST, FrameConnection.NONE)
                .with(WEST, FrameConnection.NONE)
                .with(UP, FrameConnection.NONE)
                .with(DOWN, FrameConnection.NONE)
                .with(Properties.FACING, Direction.NORTH)
                .with(Properties.LIT, false)
                .with(BROKEN, false)
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, Properties.FACING, Properties.LIT, BROKEN);
    }
}
