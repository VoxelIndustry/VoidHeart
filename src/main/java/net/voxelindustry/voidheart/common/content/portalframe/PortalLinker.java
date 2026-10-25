package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
        var tag = voidIronEye.getOrCreateTag();

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
                player.displayClientMessage(Component.translatable(MODID + ".portal_form_error"), true);
                return false;
            }
        }

        if (tag.contains("firstPos"))
        {
            if (!Objects.equals(tag.getUUID("owner"), player.getUUID()))
            {
                player.displayClientMessage(Component.translatable(MODID + ".portal_not_owner"), true);
                return false;
            }

            var firstPos = BlockPos.of(tag.getLong("firstPos"));
            var firstDimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("firstDimension")));

            var externalWorld = world.getServer().getLevel(firstDimension);
            var linkedPortal = externalWorld.getBlockEntity(firstPos);
            var firstFacing = Direction.from3DDataValue(tag.getByte("firstFacing"));

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

            tag.putLong("firstPos", portalFrameTile.getBlockPos().asLong());
            tag.putString("firstDimension", portalFrameTile.getLevel().dimension().location().toString());
            tag.putByte("firstFacing", (byte) coreFrame.getFacing().ordinal());
            tag.putUUID("owner", player.getUUID());
            tag.putString("ownerName", player.getScoreboardName());
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
            player.displayClientMessage(Component.translatable(MODID + ".no_portal_at_pos"), true);
            return false;
        }
        if (((PortalFrameTile) linkedPortal).isCore() && ((PortalFrameCoreTile) linkedPortal).isBroken())
        {
            player.displayClientMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"), true);
            return false;
        }
        if (arePortalShapesIncompatible(portalFrameTile, portalFormer, (PortalFrameCoreTile) linkedPortal))
        {
            player.displayClientMessage(Component.translatable(MODID + ".portal_shape_differ"), true);
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
        coreTile.setLinkedWorld(linkedWorld.dimension().location());
        coreTile.setLinkedFacing(linkedFacing);
        coreTile.setOwnerPlayerUUID(player.getUUID());

        coreTile.linkPortal();

        voidPearl.shrink(1);

        var linkedPortalCore = (PortalFrameCoreTile) linkedPortal;
        linkedPortalCore.setLinkedPos(coreTile.getBlockPos());
        linkedPortalCore.setLinkedWorld(coreTile.getLevel().dimension().location());
        linkedPortalCore.setLinkedFacing(coreTile.getFacing());
        linkedPortalCore.setOwnerPlayerUUID(player.getUUID());

        linkedPortalCore.linkPortal();

        player.displayClientMessage(Component.translatable(MODID + ".link_successful"), true);

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
            player.displayClientMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"), true);
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
                    player.displayClientMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"), true);
                    return false;
                }
            }
            else
            {
                player.displayClientMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"), true);
                return false;
            }
        }
        if (core.getPortalState().areShapeIncompatible(previouslyLinkedPortal.getPortalState()))
        {
            player.displayClientMessage(Component.translatable(MODID + ".portal_shape_differ"), true);
            return false;
        }

        core.setLinkedWorld(core.getPreviousLinkedWorld());
        core.setLinkedPos(core.getPreviousLinkedPos());
        core.setLinkedFacing(core.getPreviousLinkedFacing());

        previouslyLinkedPortal.setLinkedWorld(core.getLevel().dimension().location());
        previouslyLinkedPortal.setLinkedPos(core.getBlockPos());
        previouslyLinkedPortal.setLinkedFacing(core.getFacing());
        previouslyLinkedPortal.setOwnerPlayerUUID(core.getOwnerPlayerUUID());

        core.linkPortal();
        previouslyLinkedPortal.linkPortal();
        return true;
    }
}
