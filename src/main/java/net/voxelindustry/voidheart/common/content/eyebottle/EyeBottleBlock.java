package net.voxelindustry.voidheart.common.content.eyebottle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;

public class EyeBottleBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875),
            VoxelShapes.cuboid(0.375, 0, 0.375, 0.625, 0.25, 0.625),
            VoxelShapes.cuboid(0.3125, 0.375, 0.3125, 0.6875, 0.4375, 0.6875)
    );

    public EyeBottleBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.WHITE)
                .strength(2F)
                .sounds(BlockSoundGroup.GLASS)
                .luminance(state -> state.get(Properties.LIT) ? 11 : 0));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.LIT, false));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options)
    {
        super.appendTooltip(stack, world, tooltip, options);

        tooltip.add(Text.translatable(MODID + ".eye_bottle.lore"));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (placer instanceof PlayerEntity player)
        {
            if (world.getBlockEntity(pos) instanceof EyeBottleTile bottle)
                bottle.setPlayerID(player.getUuid());
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity user, Hand hand, BlockHitResult hit)
    {
        if (world.isClient())
            return ActionResult.SUCCESS;

        var voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
        var voidPocketState = getVoidPocketState(voidWorld);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof EyeBottleTile bottle)
        {
            var playerUUID = bottle.getPlayerID();

            if (voidPocketState.hasPocket(playerUUID))
            {
                var destinationPos = Vec3d.ofCenter(voidPocketState.getPosForPlayer(playerUUID).up());

                VoidHeartTicker.addDelayedTask(world.getServer(), 100, () ->
                {
                    ((ServerPlayerEntity) user).teleport(voidWorld,
                            destinationPos.getX(),
                            destinationPos.getY(),
                            destinationPos.getZ(),
                            user.getHeadYaw(),
                            user.getPitch(0));
                });
                user.sendMessage(Text.translatable(MODID + ".teleport_in_progress", 5), true);
            }
            else
                user.sendMessage(Text.translatable(MODID + ".no_pocket_for_a_player", bottle.getPlayerProfile().getName()), true);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.LIT);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new EyeBottleTile(pos, state);
    }
}
