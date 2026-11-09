package net.voxelindustry.voidheart.common.energy.grid;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Startup registrar for W1 Void grid, called once by EnergySetup: the azurium pylon, the grid's tick, and the real void network.
 */
public final class VoidGridContent
{
    public static Block AZURIUM_PYLON;

    public static BlockEntityType<AzuriumPylonTile> AZURIUM_PYLON_TILE;

    private VoidGridContent()
    {
    }

    public static void register()
    {
        AZURIUM_PYLON = VoidHeartBlocks.registerBlock("azurium_pylon", AzuriumPylonBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F, 6F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), true);
        VoidHeartTiles.registerTile(AZURIUM_PYLON_TILE = FabricBlockEntityTypeBuilder.create(AzuriumPylonTile::new, AZURIUM_PYLON).build(), "azurium_pylon");

        VoidEnergy.install(VoidGrid.PROVIDER);
        ServerTickEvents.START_LEVEL_TICK.register(VoidGrid::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VoidGrid.clear());

        // A bundle mined off a pylon's claws comes back whole, whatever the tool
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
        {
            if (player.isCreative() || !(level instanceof ServerLevel serverLevel) || PylonBundle.of(state) == PylonBundle.NONE
                    || !level.getBlockState(pos.below()).is(AZURIUM_PYLON))
                return true;
            return !AzuriumPylonBlock.releaseBundle(serverLevel, pos);
        });
    }

    /**
     * /voidheart energy grid: the pylons covering where the player stands, for review.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(Commands.literal("grid").executes(context ->
        {
            var source = context.getSource();
            var level = source.getLevel();
            var pos = BlockPos.containing(source.getPosition());
            var grid = VoidGrid.get(level);
            if (grid == null || grid.layout().pylons().isEmpty())
            {
                source.sendSuccess(() -> Component.translatable("voidheart.energy.grid.none"), false);
                return 0;
            }

            var covering = VoidGrid.pylonsCovering(level, pos);
            source.sendSuccess(() -> Component.translatable("voidheart.energy.grid.summary",
                    grid.layout().pylons().size(), grid.layout().arcs().length, covering.size()), false);
            for (var pylon : covering)
            {
                long throughput = level.getBlockEntity(pylon.pos()) instanceof AzuriumPylonTile tile ? tile.throughput() : 0;
                source.sendSuccess(() -> Component.translatable("voidheart.energy.grid.pylon",
                        pylon.pos().toShortString(), throughput, pylon.cap(), pylon.radius()), false);
            }
            return covering.size();
        }));
    }
}
