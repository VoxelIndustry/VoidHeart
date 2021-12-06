package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinker
{
    public static boolean voidPearlInteract(PortalFrameTile portalFrameTile,
                                            World world,
                                            BlockPos pos,
                                            Direction direction,
                                            PlayerEntity player,
                                            ItemStack voidPearl)
    {
        var tag = voidPearl.getOrCreateNbt();

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
                player.sendMessage(new TranslatableText(MODID + ".portal_form_error"), true);
                return false;
            }
        }

        if (tag.contains("firstPos"))
        {
            var firstPos = BlockPos.fromLong(tag.getLong("firstPos"));
            var firstDimension = RegistryKey.of(Registry.WORLD_KEY, new Identifier(tag.getString("firstDimension")));

            var externalWorld = world.getServer().getWorld(firstDimension);
            var linkedPortal = externalWorld.getBlockEntity(firstPos);
            var firstFacing = Direction.byId(tag.getByte("firstFacing"));

            return linkPortalCores(world,
                    pos,
                    direction,
                    portalFrameTile,
                    voidPearl,
                    portalFormer,
                    player,
                    firstPos,
                    externalWorld,
                    linkedPortal,
                    firstFacing);
        }
        else
        {
            if (portalFormer.isPresent())
            {
                if (portalFrameTile == null)
                {
                    world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, direction));
                    portalFrameTile = (PortalFrameTile) world.getBlockEntity(pos);
                }

                portalFrameTile.setPortalState(portalFormer.get().getState());
                portalFormer.get().execute();
            }

            tag.putLong("firstPos", portalFrameTile.getPos().asLong());
            tag.putString("firstDimension", portalFrameTile.getWorld().getRegistryKey().getValue().toString());
            tag.putByte("firstFacing", (byte) portalFrameTile.getFacing().ordinal());
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
        if (linkedPortal instanceof PortalFrameTile)
        {
            if (((PortalFrameTile) linkedPortal).isBroken())
            {
                player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
                return false;
            }
            if (arePortalShapesNotEquals(portalFrameTile, portalFormer, (PortalFrameTile) linkedPortal))
            {
                player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                return false;
            }

            if (portalFormer.isPresent())
            {
                if (portalFrameTile == null || !portalFrameTile.isCore())
                {
                    currentWorld.setBlockState(currentPos, VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState().with(Properties.FACING, currentDirection));
                    portalFrameTile = (PortalFrameTile) currentWorld.getBlockEntity(currentPos);
                }

                portalFrameTile.setPortalState(portalFormer.get().getState());
                portalFormer.get().execute();
            }

            portalFrameTile.setLinkedPos(linkedPos);
            portalFrameTile.setLinkedWorld(linkedWorld.getRegistryKey().getValue());
            portalFrameTile.setLinkedFacing(linkedFacing);
            portalFrameTile.linkPortal(VoidHeart.useImmersivePortal());
            portalFrameTile.getWorld().setBlockState(portalFrameTile.getPos(), portalFrameTile.getCachedState().with(Properties.LIT, true));

            voidPearl.decrement(1);

            ((PortalFrameTile) linkedPortal).setLinkedPos(portalFrameTile.getPos());
            ((PortalFrameTile) linkedPortal).setLinkedWorld(portalFrameTile.getWorld().getRegistryKey().getValue());
            ((PortalFrameTile) linkedPortal).setLinkedFacing(portalFrameTile.getFacing());
            ((PortalFrameTile) linkedPortal).linkPortal(VoidHeart.useImmersivePortal());
            linkedWorld.setBlockState(portalFrameTile.getLinkedPos(), linkedPortal.getCachedState().with(Properties.LIT, true));

            player.sendMessage(new TranslatableText(MODID + ".link_successful"), true);
            return true;
        }
        else
        {
            player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos"), true);
            return false;
        }
    }

    private static boolean arePortalShapesNotEquals(PortalFrameTile portalFrameTile, Optional<DeferredRollbackWork<PortalFormerState>> portalFormer, PortalFrameTile linkedPortal)
    {
        if (portalFormer.isPresent())
            return !portalFormer.get().getState().areShapeEquals(linkedPortal.getPortalState());

        return portalFrameTile != null && !portalFrameTile.getPortalState().areShapeEquals(linkedPortal.getPortalState());
    }

    public static boolean tryRelink(PlayerEntity player, PortalFrameTile core)
    {
        if (!core.isCore() || core.getLinkedWorld() != null || core.getPreviousLinkedWorld() == null)
            return false;

        PortalFrameTile previouslyLinkedPortal = core.getPreviouslyLinkedPortal();

        if (previouslyLinkedPortal == null || previouslyLinkedPortal.getLinkedWorld() != null)
        {
            player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
            return false;
        }

        if (previouslyLinkedPortal.isBroken())
        {
            DeferredRollbackWork<PortalFormerState> portalFormer = PortalFormer.tryForm(previouslyLinkedPortal.getWorld(), previouslyLinkedPortal.getCachedState(), previouslyLinkedPortal.getPos(), previouslyLinkedPortal.getFacing());
            if (portalFormer.maySucceed())
            {
                portalFormer.execute();
                if (!portalFormer.success())
                {
                    player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
                    return false;
                }
            }
            else
            {
                player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
                return false;
            }
        }
        if (!core.getPortalState().areShapeEquals(previouslyLinkedPortal.getPortalState()))
        {
            player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
            return false;
        }

        core.setLinkedWorld(core.getPreviousLinkedWorld());
        core.setLinkedPos(core.getPreviousLinkedPos());
        core.setLinkedFacing(core.getPreviousLinkedFacing());

        previouslyLinkedPortal.setLinkedWorld(core.getWorld().getRegistryKey().getValue());
        previouslyLinkedPortal.setLinkedPos(core.getPos());
        previouslyLinkedPortal.setLinkedFacing(core.getFacing());

        previouslyLinkedPortal.getWorld().setBlockState(previouslyLinkedPortal.getPos(), previouslyLinkedPortal.getCachedState().with(Properties.LIT, true));
        core.getWorld().setBlockState(core.getPos(), core.getWorld().getBlockState(core.getPos())
                .with(Properties.LIT, true)
                .with(StateProperties.BROKEN, false)
        );

        core.linkPortal(VoidHeart.useImmersivePortal());
        previouslyLinkedPortal.linkPortal(VoidHeart.useImmersivePortal());
        return true;
    }
}
