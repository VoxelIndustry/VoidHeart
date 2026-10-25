package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.core.GlobalPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.PortalLinkStart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.Objects;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinker
{
    public static boolean voidIronEyeInteract(PortalFrameTile portalFrameTile,
                                              Level world,
                                              BlockPos pos,
                                              Direction direction,
                                              Player player,
                                              ItemStack voidIronEye)
    {
        var linkStart = voidIronEye.get(VoidHeartDataComponents.PORTAL_LINK);

        Optional<DeferredRollbackWork<PortalFormerState>> portalFormer = Optional.empty();

        // If the block is not a frame OR is a frame but not a core OR is a frame but is not linked to any core
        // Then it is cleared for frame creation and will act as the core of the new portal
        if (portalFrameTile == null || !portalFrameTile.isCore() || portalFrameTile.getLinkedCores().isEmpty())
        {
            portalFormer = Optional.of(PortalFormer.tryForm(world,
                    portalFrameTile == null ? VoidHeartBlocks.PORTAL_FRAME_CORE.defaultBlockState().setValue(BlockStateProperties.FACING, direction) : portalFrameTile.getBlockState(),
                    pos,
                    direction));

            if (!portalFormer.get().maySucceed())
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".portal_form_error"));
                return false;
            }
        }

        if (linkStart != null)
        {
            if (!Objects.equals(linkStart.owner(), player.getUUID()))
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".portal_not_owner"));
                return false;
            }

            var firstPos = linkStart.firstPos().pos();

            var externalWorld = world.getServer().getLevel(linkStart.firstPos().dimension());
            var linkedPortal = externalWorld.getBlockEntity(firstPos);
            var firstFacing = linkStart.firstFacing();

            return linkPortalCores(world,
                    pos,
                    direction,
                    portalFrameTile,
                    voidIronEye,
                    portalFormer,
                    player,
                    firstPos,
                    externalWorld,
                    linkedPortal,
                    firstFacing);
        }
        else
        {
            PortalFrameCoreTile coreFrame = null;
            if (portalFormer.isPresent())
            {
                if (portalFrameTile == null)
                {
                    world.setBlockAndUpdate(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.defaultBlockState().setValue(BlockStateProperties.FACING, direction));
                    coreFrame = (PortalFrameCoreTile) world.getBlockEntity(pos);
                    portalFrameTile = coreFrame;
                }
                else
                    coreFrame = (PortalFrameCoreTile) portalFrameTile;

                coreFrame.setPortalState(portalFormer.get().getState());
                portalFormer.get().execute();
            }

            if (coreFrame == null)
                coreFrame = ((PortalFrameCoreTile) portalFrameTile);

            voidIronEye.set(VoidHeartDataComponents.PORTAL_LINK, new PortalLinkStart(
                    GlobalPos.of(portalFrameTile.getLevel().dimension(), portalFrameTile.getBlockPos()),
                    coreFrame.getFacing(),
                    player.getUUID(),
                    player.getScoreboardName()));
        }
        return true;
    }

    public static boolean linkPortalCores(
            Level currentWorld,
            BlockPos currentPos,
            Direction currentDirection,
            PortalFrameTile portalFrameTile,
            ItemStack voidPearl,
            Optional<DeferredRollbackWork<PortalFormerState>> portalFormer,
            Player player,
            BlockPos linkedPos,
            ServerLevel linkedWorld,
            BlockEntity linkedPortal,
            Direction linkedFacing)
    {
        if (!(linkedPortal instanceof PortalFrameTile))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos"));
            return false;
        }
        if (((PortalFrameTile) linkedPortal).isCore() && ((PortalFrameCoreTile) linkedPortal).isBroken())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
            return false;
        }
        if (arePortalShapesIncompatible(portalFrameTile, portalFormer, (PortalFrameCoreTile) linkedPortal))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_shape_differ"));
            return false;
        }

        PortalFrameCoreTile coreTile = null;
        if (portalFormer.isPresent())
        {
            if (portalFrameTile == null || !portalFrameTile.isCore())
            {
                currentWorld.setBlockAndUpdate(currentPos, VoidHeartBlocks.PORTAL_FRAME_CORE.defaultBlockState().setValue(BlockStateProperties.FACING, currentDirection));
                coreTile = (PortalFrameCoreTile) currentWorld.getBlockEntity(currentPos);
            }
            else
                coreTile = (PortalFrameCoreTile) portalFrameTile;

            coreTile.setPortalState(portalFormer.get().getState());
            portalFormer.get().execute();
        }

        if (coreTile == null)
            coreTile = (PortalFrameCoreTile) portalFrameTile;

        coreTile.setLinkedPos(linkedPos);
        coreTile.setLinkedWorld(linkedWorld.dimension().identifier());
        coreTile.setLinkedFacing(linkedFacing);
        coreTile.setOwnerPlayerUUID(player.getUUID());

        coreTile.linkPortal();

        voidPearl.shrink(1);

        var linkedPortalCore = (PortalFrameCoreTile) linkedPortal;
        linkedPortalCore.setLinkedPos(coreTile.getBlockPos());
        linkedPortalCore.setLinkedWorld(coreTile.getLevel().dimension().identifier());
        linkedPortalCore.setLinkedFacing(coreTile.getFacing());
        linkedPortalCore.setOwnerPlayerUUID(player.getUUID());

        linkedPortalCore.linkPortal();

        player.sendOverlayMessage(Component.translatable(MODID + ".link_successful"));

        VoidHeart.PORTAL_LINK_CRITERION.trigger((ServerPlayer) player);
        return true;
    }

    private static boolean arePortalShapesIncompatible(PortalFrameTile portalFrameTile, Optional<DeferredRollbackWork<PortalFormerState>> portalFormer, PortalFrameCoreTile linkedPortal)
    {
        return portalFormer
                .map(portalFormerStateDeferredRollbackWork ->
                {
                    return portalFormerStateDeferredRollbackWork.getState().areShapeIncompatible(linkedPortal.getPortalState());
                })
                .orElseGet(() ->
                {
                    if (portalFrameTile instanceof PortalFrameCoreTile core)
                        return core.getPortalState().areShapeIncompatible(linkedPortal.getPortalState());
                    return true;
                });
    }

    public static boolean tryRelink(Player player, PortalFrameCoreTile core)
    {
        if (core.getLinkedWorld() != null || core.getPreviousLinkedWorld() == null)
            return false;

        var previouslyLinkedPortalOpt = core.getPreviouslyLinkedPortal();

        if (previouslyLinkedPortalOpt.isEmpty() || previouslyLinkedPortalOpt.get().getLinkedWorld() != null)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
            return false;
        }

        var previouslyLinkedPortal = previouslyLinkedPortalOpt.get();
        if (previouslyLinkedPortal.isBroken())
        {
            var portalFormer = PortalFormer.tryForm(
                    previouslyLinkedPortal.getLevel(),
                    previouslyLinkedPortal.getBlockState(),
                    previouslyLinkedPortal.getBlockPos(),
                    previouslyLinkedPortal.getFacing()
            );
            if (portalFormer.maySucceed())
            {
                portalFormer.execute();
                if (!portalFormer.success())
                {
                    player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
                    return false;
                }
            }
            else
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
                return false;
            }
        }
        if (core.getPortalState().areShapeIncompatible(previouslyLinkedPortal.getPortalState()))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_shape_differ"));
            return false;
        }

        core.setLinkedWorld(core.getPreviousLinkedWorld());
        core.setLinkedPos(core.getPreviousLinkedPos());
        core.setLinkedFacing(core.getPreviousLinkedFacing());

        previouslyLinkedPortal.setLinkedWorld(core.getLevel().dimension().identifier());
        previouslyLinkedPortal.setLinkedPos(core.getBlockPos());
        previouslyLinkedPortal.setLinkedFacing(core.getFacing());
        previouslyLinkedPortal.setOwnerPlayerUUID(core.getOwnerPlayerUUID());

        core.linkPortal();
        previouslyLinkedPortal.linkPortal();
        return true;
    }
}
