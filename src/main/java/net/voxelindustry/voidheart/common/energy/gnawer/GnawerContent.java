package net.voxelindustry.voidheart.common.energy.gnawer;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Startup registrar for the Gnawer, called once by EnergySetup since it runs on void energy.
 */
public final class GnawerContent
{
    public static Block                       GNAWER;
    public static Block                       GNAWING_STAKE;
    public static BlockEntityType<GnawerTile> GNAWER_TILE;

    private GnawerContent()
    {
    }

    public static void register()
    {
        GNAWER = VoidHeartBlocks.registerBlock("gnawer", GnawerBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(2.0F, 6.0F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.METAL), true);

        GNAWING_STAKE = VoidHeartBlocks.registerBlock("gnawing_stake", GnawingStakeBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(0.3F)
                .noOcclusion()
                .noCollision()
                .pushReaction(PushReaction.POPPED)
                .sound(SoundType.AMETHYST), true);

        VoidHeartTiles.registerTile(GNAWER_TILE = FabricBlockEntityTypeBuilder.create(GnawerTile::new, GNAWER).build(), "gnawer");
    }
}
