package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
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
    public static boolean voidPieceInteract(PortalFrameTile portalFrameTile,
                                            World world,
                                            BlockPos pos,
                                            Direction direction,
                                            PlayerEntity player,
                                            ItemStack voidPiece,
                                            boolean isInPocket)
    {
        CompoundTag tag = voidPiece.getOrCreateTag();

        Optional<DeferredRollbackWork<PortalFormerState>> portalFormer = Optional.empty();

        if (portalFrameTile == null || !portalFrameTile.isCore())
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

        if (isInPocket)
        {
            if (tag.contains("externalPos"))
            {
                BlockPos externalPos = BlockPos.fromLong(tag.getLong("externalPos"));
                RegistryKey<World> externalDimension = RegistryKey.of(Registry.DIMENSION, new Identifier(tag.getString("externalDimension")));

                ServerWorld externalWorld = world.getServer().getWorld(externalDimension);
                BlockEntity linkedPortal = externalWorld.getBlockEntity(externalPos);
                Direction externalFacing = Direction.byId(tag.getByte("externalFacing"));

                return linkPortalCores(world,
                        pos,
                        direction,
                        portalFrameTile,
                        voidPiece,
                        portalFormer,
                        player,
                        externalPos,
                        externalWorld,
                        linkedPortal,
                        externalFacing);
            }
            else if (!tag.contains("pocketPos"))
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

                tag.putLong("pocketPos", portalFrameTile.getPos().asLong());
                tag.putByte("pocketFacing", (byte) portalFrameTile.getFacing().ordinal());
                player.sendMessage(new TranslatableText(MODID + ".link_started_pocket"), true);
            }
            else
                return false;
            return true;
        }

        // Not in pocket

        if (tag.contains("pocketPos"))
        {
            BlockPos externalPos = BlockPos.fromLong(tag.getLong("pocketPos"));
            ServerWorld externalWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
            BlockEntity linkedPortal = externalWorld.getBlockEntity(externalPos);
            Direction externalFacing = Direction.byId(tag.getByte("pocketFacing"));

            return linkPortalCores(world,
                    pos,
                    direction,
                    portalFrameTile,
                    voidPiece,
                    portalFormer,
                    player,
                    externalPos,
                    externalWorld,
                    linkedPortal,
                    externalFacing);
        }
        else if (!tag.contains("externalPos"))
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

            tag.putLong("externalPos", portalFrameTile.getPos().asLong());
            tag.putString("externalDimension", portalFrameTile.getWorld().getRegistryKey().getValue().toString());
            tag.putByte("externalFacing", (byte) portalFrameTile.getFacing().ordinal());
            player.sendMessage(new TranslatableText(MODID + ".link_started_outside"), true);
            return true;
        }
        return false;
    }

    public static boolean linkPortalCores(
            World currentWorld,
            BlockPos currentPos,
            Direction currentDirection,
            PortalFrameTile portalFrameTile,
            ItemStack voidPiece,
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
            if (portalFormer.isPresent() && !portalFormer.get().getState().areShapeEquals(((PortalFrameTile) linkedPortal).getPortalState())
                    || portalFrameTile != null && !portalFrameTile.getPortalState().areShapeEquals(((PortalFrameTile) linkedPortal).getPortalState()))
            {
                player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
                return false;
            }

            if (portalFormer.isPresent())
            {
                if (portalFrameTile == null)
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

            voidPiece.decrement(1);

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

    public static void tryRelink(PlayerEntity player, PortalFrameTile core)
    {
        if (!core.isCore() || core.getLinkedWorld() != null || core.getPreviousLinkedWorld() == null)
            return;

        PortalFrameTile previouslyLinkedPortal = core.getPreviouslyLinkedPortal();

        if (previouslyLinkedPortal == null || previouslyLinkedPortal.getLinkedWorld() != null)
            return;

        if (previouslyLinkedPortal.isBroken())
        {
            DeferredRollbackWork<PortalFormerState> portalFormer = PortalFormer.tryForm(previouslyLinkedPortal.getWorld(), previouslyLinkedPortal.getCachedState(), previouslyLinkedPortal.getPos(), previouslyLinkedPortal.getFacing());
            if (portalFormer.maySucceed())
            {
                portalFormer.execute();
                if (!portalFormer.success())
                {
                    player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
                    return;
                }
            }
            else
            {
                player.sendMessage(new TranslatableText(MODID + ".no_portal_at_pos_broken"), true);
                return;
            }
        }
        if (!core.getPortalState().areShapeEquals(previouslyLinkedPortal.getPortalState()))
        {
            player.sendMessage(new TranslatableText(MODID + ".portal_shape_differ"), true);
            return;
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
    }
}
