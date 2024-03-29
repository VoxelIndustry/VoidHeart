package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.compat.immportal.ImmersivePortalCompat;

import java.util.Objects;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinker
{
    public static boolean voidIronEyeInteract(PortalFrameTile portalFrameTile,
                                              World world,
                                              BlockPos pos,
                                              Direction direction,
                                              PlayerEntity player,
                                              ItemStack voidIronEye)
    {
        var tag = voidIronEye.getOrCreateNbt();

        Optional<DeferredRollbackWork<PortalFormerState>> portalFormer = Optional.empty();

        // If the block is not a frame OR is a frame but not a core OR is a frame but is not linked to any core
        // Then it is cleared for frame creation and will act as the core of the new portal
        if (portalFrameTile == null || !portalFrameTile.isCore() || portalFrameTile.getLinkedCores().isEmpty())
        {
            portalFormer = Optional.of(PortalFormer.tryForm(world,
                    portalFrameTile == null ? VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction) : portalFrameTile.getCachedState(),
                    pos,
                    direction));

            if (!portalFormer.get().maySucceed())
            {
                player.sendMessage(Text.translatable(MODID + ".portal_form_error"), true);
                return false;
            }
        }

        if (tag.contains("firstPos"))
        {
            if (!Objects.equals(tag.getUuid("owner"), player.getUuid()))
            {
                player.sendMessage(Text.translatable(MODID + ".portal_not_owner"), true);
                return false;
            }

            var firstPos = BlockPos.fromLong(tag.getLong("firstPos"));
            var firstDimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(tag.getString("firstDimension")));

            var externalWorld = world.getServer().getWorld(firstDimension);
            var linkedPortal = externalWorld.getBlockEntity(firstPos);
            var firstFacing = Direction.byId(tag.getByte("firstFacing"));

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
                    world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
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

            tag.putLong("firstPos", portalFrameTile.getPos().asLong());
            tag.putString("firstDimension", portalFrameTile.getWorld().getRegistryKey().getValue().toString());
            tag.putByte("firstFacing", (byte) coreFrame.getFacing().ordinal());
            tag.putUuid("owner", player.getUuid());
            tag.putString("ownerName", player.getEntityName());
        }
        return true;
    }

    public static boolean linkPortalCores(
            World currentWorld,
            BlockPos currentPos,
            Direction currentDirection,
            PortalFrameTile portalFrameTile,
            ItemStack voidPearl,
            Optional<DeferredRollbackWork<PortalFormerState>> portalFormer,
            PlayerEntity player,
            BlockPos linkedPos,
            ServerWorld linkedWorld,
            BlockEntity linkedPortal,
            Direction linkedFacing)
    {
        if (!(linkedPortal instanceof PortalFrameTile))
        {
            player.sendMessage(Text.translatable(MODID + ".no_portal_at_pos"), true);
            return false;
        }
        if (((PortalFrameTile) linkedPortal).isCore() && ((PortalFrameCoreTile) linkedPortal).isBroken())
        {
            player.sendMessage(Text.translatable(MODID + ".no_portal_at_pos_broken"), true);
            return false;
        }
        if (arePortalShapesIncompatible(portalFrameTile, portalFormer, (PortalFrameCoreTile) linkedPortal))
        {
            player.sendMessage(Text.translatable(MODID + ".portal_shape_differ"), true);
            return false;
        }

        PortalFrameCoreTile coreTile = null;
        if (portalFormer.isPresent())
        {
            if (portalFrameTile == null || !portalFrameTile.isCore())
            {
                currentWorld.setBlockState(currentPos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, currentDirection));
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
        coreTile.setLinkedWorld(linkedWorld.getRegistryKey().getValue());
        coreTile.setLinkedFacing(linkedFacing);
        coreTile.setOwnerPlayerUUID(player.getUuid());

        coreTile.linkPortal(ImmersivePortalCompat.useImmersivePortal());

        voidPearl.decrement(1);

        var linkedPortalCore = (PortalFrameCoreTile) linkedPortal;
        linkedPortalCore.setLinkedPos(coreTile.getPos());
        linkedPortalCore.setLinkedWorld(coreTile.getWorld().getRegistryKey().getValue());
        linkedPortalCore.setLinkedFacing(coreTile.getFacing());
        linkedPortalCore.setOwnerPlayerUUID(player.getUuid());

        linkedPortalCore.linkPortal(ImmersivePortalCompat.useImmersivePortal());

        player.sendMessage(Text.translatable(MODID + ".link_successful"), true);

        VoidHeart.PORTAL_LINK_CRITERION.trigger((ServerPlayerEntity) player);
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

    public static boolean tryRelink(PlayerEntity player, PortalFrameCoreTile core)
    {
        if (core.getLinkedWorld() != null || core.getPreviousLinkedWorld() == null)
            return false;

        var previouslyLinkedPortalOpt = core.getPreviouslyLinkedPortal();

        if (previouslyLinkedPortalOpt.isEmpty() || previouslyLinkedPortalOpt.get().getLinkedWorld() != null)
        {
            player.sendMessage(Text.translatable(MODID + ".no_portal_at_pos_broken"), true);
            return false;
        }

        var previouslyLinkedPortal = previouslyLinkedPortalOpt.get();
        if (previouslyLinkedPortal.isBroken())
        {
            var portalFormer = PortalFormer.tryForm(
                    previouslyLinkedPortal.getWorld(),
                    previouslyLinkedPortal.getCachedState(),
                    previouslyLinkedPortal.getPos(),
                    previouslyLinkedPortal.getFacing()
            );
            if (portalFormer.maySucceed())
            {
                portalFormer.execute();
                if (!portalFormer.success())
                {
                    player.sendMessage(Text.translatable(MODID + ".no_portal_at_pos_broken"), true);
                    return false;
                }
            }
            else
            {
                player.sendMessage(Text.translatable(MODID + ".no_portal_at_pos_broken"), true);
                return false;
            }
        }
        if (core.getPortalState().areShapeIncompatible(previouslyLinkedPortal.getPortalState()))
        {
            player.sendMessage(Text.translatable(MODID + ".portal_shape_differ"), true);
            return false;
        }

        core.setLinkedWorld(core.getPreviousLinkedWorld());
        core.setLinkedPos(core.getPreviousLinkedPos());
        core.setLinkedFacing(core.getPreviousLinkedFacing());

        previouslyLinkedPortal.setLinkedWorld(core.getWorld().getRegistryKey().getValue());
        previouslyLinkedPortal.setLinkedPos(core.getPos());
        previouslyLinkedPortal.setLinkedFacing(core.getFacing());
        previouslyLinkedPortal.setOwnerPlayerUUID(core.getOwnerPlayerUUID());

        core.linkPortal(ImmersivePortalCompat.useImmersivePortal());
        previouslyLinkedPortal.linkPortal(ImmersivePortalCompat.useImmersivePortal());
        return true;
    }
}
