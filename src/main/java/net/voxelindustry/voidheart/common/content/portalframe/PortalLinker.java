package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.extern.log4j.Log4j2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.PortalLinkStart;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every check is done before the world is modified, a link is either fully created on both cores or not at all.
 */
@Log4j2
public class PortalLinker
{
    public static boolean voidIronEyeInteract(@Nullable PortalFrameTile portalFrameTile,
                                              Level world,
                                              BlockPos pos,
                                              Direction direction,
                                              Player player,
                                              ItemStack voidIronEye)
    {
        var linkStart = voidIronEye.get(VoidHeartDataComponents.PORTAL_LINK);

        if (portalFrameTile instanceof PortalFrameCoreTile core && core.isLinked())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_already_linked"));
            return false;
        }

        // Turning a frame of an existing portal into a new core would break that portal
        if (portalFrameTile != null && !portalFrameTile.isCore() && portalFrameTile.pruneLinkedCores())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_frame_in_use"));
            return false;
        }

        Optional<DeferredRollbackWork<PortalFormerState>> portalFormer = Optional.empty();

        // A core that is already formed is reused as is, anything else must form a new portal around the clicked block
        if (!(portalFrameTile instanceof PortalFrameCoreTile formedCore) || !formedCore.isFormed() || formedCore.isBroken())
        {
            BlockState state = world.getBlockState(pos);
            portalFormer = Optional.of(PortalFormer.tryForm(world, state, pos, direction));

            if (!portalFormer.get().maySucceed())
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".portal_form_error"));
                return false;
            }
        }

        if (linkStart == null)
        {
            var coreFrame = formCore(world, pos, portalFrameTile, portalFormer);
            if (coreFrame == null)
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".portal_form_error"));
                return false;
            }

            voidIronEye.set(VoidHeartDataComponents.PORTAL_LINK, new PortalLinkStart(
                    GlobalPos.of(coreFrame.getLevel().dimension(), coreFrame.getBlockPos()),
                    coreFrame.getFacing(),
                    player.getUUID(),
                    player.getScoreboardName()));
            return true;
        }

        if (!Objects.equals(linkStart.owner(), player.getUUID()))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_not_owner"));
            return false;
        }

        var linkedWorld = world.getServer().getLevel(linkStart.firstPos().dimension());
        var linkedPortal = linkedWorld == null ? Optional.<PortalFrameCoreTile>empty() :
                linkedWorld.getBlockEntity(linkStart.firstPos().pos(), VoidHeartTiles.PORTAL_FRAME_CORE);

        return linkPortalCores(world, pos, portalFrameTile, voidIronEye, portalFormer, player, linkedPortal.orElse(null));
    }

    public static boolean linkPortalCores(
            Level currentWorld,
            BlockPos currentPos,
            @Nullable PortalFrameTile portalFrameTile,
            ItemStack voidPearl,
            Optional<DeferredRollbackWork<PortalFormerState>> portalFormer,
            Player player,
            @Nullable PortalFrameCoreTile linkedPortal)
    {
        if (linkedPortal == null)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos"));
            return false;
        }
        if (linkedPortal.getLevel() == currentWorld && linkedPortal.getBlockPos().equals(currentPos))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_link_self"));
            return false;
        }
        if (linkedPortal.isBroken() || !linkedPortal.isFormed())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
            return false;
        }
        if (linkedPortal.isLinked())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_already_linked"));
            return false;
        }
        if (arePortalShapesIncompatible(portalFrameTile, portalFormer, linkedPortal))
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_shape_differ"));
            return false;
        }
        if (!linkedPortal.canBeLinked() || portalFormer.isEmpty() && !((PortalFrameCoreTile) portalFrameTile).canBeLinked())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_interior_obstructed"));
            return false;
        }

        var coreTile = formCore(currentWorld, currentPos, portalFrameTile, portalFormer);
        if (coreTile == null || !coreTile.canBeLinked())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_form_error"));
            return false;
        }

        coreTile.setOwnerPlayerUUID(player.getUUID());
        linkedPortal.setOwnerPlayerUUID(player.getUUID());
        connect(coreTile, linkedPortal);

        voidPearl.shrink(1);

        player.sendOverlayMessage(Component.translatable(MODID + ".link_successful"));

        VoidHeart.PORTAL_LINK_CRITERION.trigger((ServerPlayer) player);
        return true;
    }

    /**
     * @return the formed core at pos, or null if the formation failed
     */
    @Nullable
    private static PortalFrameCoreTile formCore(Level world,
                                                BlockPos pos,
                                                @Nullable PortalFrameTile portalFrameTile,
                                                Optional<DeferredRollbackWork<PortalFormerState>> portalFormer)
    {
        if (portalFormer.isEmpty())
            return portalFrameTile instanceof PortalFrameCoreTile core ? core : null;

        portalFormer.get().execute();
        if (!portalFormer.get().success())
        {
            log.error("Portal formation failed after validation. pos={}, state={}", pos, portalFormer.get().getState());
            return null;
        }

        return world.getBlockEntity(pos, VoidHeartTiles.PORTAL_FRAME_CORE)
                .filter(PortalFrameCoreTile::isFormed)
                .orElse(null);
    }

    private static void connect(PortalFrameCoreTile first, PortalFrameCoreTile second)
    {
        first.setLink(PortalLink.of(second));
        second.setLink(PortalLink.of(first));

        first.linkPortal();
        second.linkPortal();

        log.debug("Linked portals {} <-> {}", first, second);
    }

    private static boolean arePortalShapesIncompatible(@Nullable PortalFrameTile portalFrameTile, Optional<DeferredRollbackWork<PortalFormerState>> portalFormer, PortalFrameCoreTile linkedPortal)
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
        if (core.isLinked() || core.getPreviousLink() == null)
            return false;

        var previouslyLinkedPortalOpt = core.findPreviouslyLinkedPortal();

        if (previouslyLinkedPortalOpt.isEmpty())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
            return false;
        }

        var previouslyLinkedPortal = previouslyLinkedPortalOpt.get();
        if (previouslyLinkedPortal.isBroken() || !previouslyLinkedPortal.isFormed())
        {
            var portalFormer = PortalFormer.tryForm(
                    previouslyLinkedPortal.getLevel(),
                    previouslyLinkedPortal.getBlockState(),
                    previouslyLinkedPortal.getBlockPos(),
                    previouslyLinkedPortal.getFacing()
            );
            if (!portalFormer.maySucceed())
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".no_portal_at_pos_broken"));
                return false;
            }

            portalFormer.execute();
            if (!portalFormer.success())
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
        if (!core.canBeLinked() || !previouslyLinkedPortal.canBeLinked())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".portal_interior_obstructed"));
            return false;
        }

        previouslyLinkedPortal.setOwnerPlayerUUID(core.getOwnerPlayerUUID());
        connect(core, previouslyLinkedPortal);
        return true;
    }
}
