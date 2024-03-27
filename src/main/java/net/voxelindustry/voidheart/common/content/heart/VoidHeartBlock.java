package net.voxelindustry.voidheart.common.content.heart;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.block.BlockWithEntity.checkType;

public class VoidHeartBlock extends Block implements BlockEntityProvider
{
    private final VoxelShape SHAPE = Block.createCuboidShape(2, 2, 2, 14, 14, 14);

    public VoidHeartBlock()
    {
        super(Settings
                .create()
                .noCollision()
                .strength(-1.0F, 3600000.0F)
                .dropsNothing()
                .allowsSpawning(((state, world, pos, type) -> false))
                .luminance(unused -> 11));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (!world.isClient())
            ((VoidHeartTile) world.getBlockEntity(pos)).playerHit(player);

        if (player.isCreative() && !world.isClient())
        {
            var debugText = Text.literal("§7====== §3Heart Inspection §7=====\n");

            var tile = world.getBlockEntity(pos);

            if (tile instanceof VoidHeartTile heart)
            {
                debugText.append(Text.literal("§2Tile healthy\n"));

                var playerID = heart.getPlayerID();

                var gameProfileOpt = world.getServer().getUserCache().getByUuid(playerID);

                if (gameProfileOpt.isPresent())
                    debugText.append(Text.literal("§6Owner: §7" + heart.getPlayerID() + " (§3" + gameProfileOpt.get().getName() + "§7)\n"));
                else
                    debugText.append(Text.literal("§6Owner: " + heart.getPlayerID() + " (ERROR RETRIEVING PROFILE)\n"));

                var heartData = VoidPocketState.getVoidPocketState(world).getHeartData(heart.getPlayerID());

                if (heartData == null)
                    debugText.append(Text.literal("§cUnable to retrieve heart data\n"));
                else
                    debugText.append(heartData.debugPrint());
            }
            else
                debugText.append(Text.literal("§cNo tile found\n"));

            debugText.append(Text.literal("§7==========================="));

            player.sendMessage(debugText);
        }

        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return checkType(type, VoidHeartTiles.VOID_HEART, VoidHeartTile::tick);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidHeartTile(pos, state);
    }
}
