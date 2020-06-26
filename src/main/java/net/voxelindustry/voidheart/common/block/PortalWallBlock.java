package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.tile.PortalWallTile;

public class PortalWallBlock extends Block implements BlockEntityProvider
{
    public PortalWallBlock()
    {
        super(Settings.of(Material.STONE)
                .noCollision()
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        PortalWallTile tile = (PortalWallTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

        if (world.isClient())
            return ActionResult.SUCCESS;

        tile.tryForm(hit.getSide());

        return ActionResult.SUCCESS;
    }

    @Override
    public BlockEntity createBlockEntity(BlockView world)
    {
        return new PortalWallTile();
    }
}
