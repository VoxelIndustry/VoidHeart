package net.voxelindustry.voidheart.common.content.eyebottle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;

public class EyeBottleBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875),
            Shapes.box(0.375, 0, 0.375, 0.625, 0.25, 0.625),
            Shapes.box(0.3125, 0.375, 0.3125, 0.6875, 0.4375, 0.6875)
    );

    public EyeBottleBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.SNOW)
                .strength(2F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 11 : 0));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options)
    {
        super.appendHoverText(stack, world, tooltip, options);

        tooltip.add(Component.translatable(MODID + ".eye_bottle.lore"));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(world, pos, state, placer, itemStack);

        if (placer instanceof Player player)
        {
            if (world.getBlockEntity(pos) instanceof EyeBottleTile bottle)
                bottle.setPlayerID(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player user, InteractionHand hand, BlockHitResult hit)
    {
        if (world.isClientSide())
            return InteractionResult.SUCCESS;

        var voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        var voidPocketState = getVoidPocketState(voidWorld);

        var tile = world.getBlockEntity(pos);

        if (tile instanceof EyeBottleTile bottle)
        {
            var playerUUID = bottle.getPlayerID();

            if (voidPocketState.hasPocket(playerUUID))
            {
                var destinationPos = Vec3.atCenterOf(voidPocketState.getPosForPlayer(playerUUID).above());

                VoidHeartTicker.addDelayedTask(world.getServer(), 100, () ->
                {
                    ((ServerPlayer) user).teleportTo(voidWorld,
                            destinationPos.x(),
                            destinationPos.y(),
                            destinationPos.z(),
                            user.getYHeadRot(),
                            user.getViewXRot(0));
                });
                user.displayClientMessage(Component.translatable(MODID + ".teleport_in_progress", 5), true);
            }
            else
                user.displayClientMessage(Component.translatable(MODID + ".no_pocket_for_a_player", bottle.getPlayerProfile().getName()), true);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new EyeBottleTile(pos, state);
    }
}
