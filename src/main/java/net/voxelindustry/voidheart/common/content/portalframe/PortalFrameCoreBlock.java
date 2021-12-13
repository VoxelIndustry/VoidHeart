package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static java.util.Arrays.asList;
import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.*;
import static net.voxelindustry.voidheart.common.block.StateProperties.BROKEN;

public class PortalFrameCoreBlock extends PortalFrameBlock
{
    public PortalFrameCoreBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .ticksRandomly()
                .emissiveLighting((state, world, pos) -> state.get(Properties.LIT)));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

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
                            player.sendMessage(new TranslatableText(MODID + ".portal_relinked_successful"), true);
                            return ActionResult.SUCCESS;
                        }
                    }
                    else
                        player.sendMessage(new TranslatableText(MODID + ".portal_cannot_form_back"), true);
                }
                else
                    player.sendMessage(new TranslatableText(MODID + ".portal_cannot_form_back"), true);
            }
            else if (tile.getLinkedWorld() == null && tile.getPreviousLinkedWorld() != null && PortalLinker.tryRelink(player, tile))
            {
                player.sendMessage(new TranslatableText(MODID + ".portal_relinked_successful"), true);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
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

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, Builder builder)
    {
        return asList(new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS), new ItemStack(VoidHeartItems.VOID_PEARL));
    }
}
