package net.voxelindustry.voidheart.common.content.heart;

import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VoidHeartBlock extends Block implements EntityBlock
{
    private final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 14, 14);

    public VoidHeartBlock()
    {
        super(Properties
                .of()
                .noCollission()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .isValidSpawn(((state, world, pos, type) -> false))
                .lightLevel(unused -> 11));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!world.isClientSide())
            ((VoidHeartTile) world.getBlockEntity(pos)).playerHit(player);

        if (player.isCreative() && !world.isClientSide())
        {
            var debugText = Component.literal("§7====== §3Heart Inspection §7=====\n");

            var tile = world.getBlockEntity(pos);

            if (tile instanceof VoidHeartTile heart)
            {
                debugText.append(Component.literal("§2Tile healthy\n"));

                var playerID = heart.getPlayerID();

                var gameProfileOpt = world.getServer().getProfileCache().get(playerID);

                if (gameProfileOpt.isPresent())
                    debugText.append(Component.literal("§6Owner: §7" + heart.getPlayerID() + " (§3" + gameProfileOpt.get().getName() + "§7)\n"));
                else
                    debugText.append(Component.literal("§6Owner: " + heart.getPlayerID() + " (ERROR RETRIEVING PROFILE)\n"));

                var heartData = VoidPocketState.getVoidPocketState(world).getHeartData(heart.getPlayerID());

                if (heartData == null)
                    debugText.append(Component.literal("§cUnable to retrieve heart data\n"));
                else
                    debugText.append(heartData.debugPrint());
            }
            else
                debugText.append(Component.literal("§cNo tile found\n"));

            debugText.append(Component.literal("§7==========================="));

            player.sendSystemMessage(debugText);
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.VOID_HEART, VoidHeartTile::tick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidHeartTile(pos, state);
    }
}
