package net.voxelindustry.voidheart.common.energy.creative;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * The creative test blocks, which only creative players can break, and which drop nothing.
 */
public final class CreativeEnergyContent
{
    public static Block CREATIVE_VOID_WELL;
    public static Block CREATIVE_LEY_STONE;

    public static BlockEntityType<CreativeSourceTile> CREATIVE_SOURCE;

    private CreativeEnergyContent()
    {
    }

    public static void register()
    {
        CREATIVE_VOID_WELL = VoidHeartBlocks.registerBlock("creative_void_well",
                properties -> new CreativeSourceBlock(CreativeSourceBlock.Kind.VOID_WELL, properties), creativeProperties(MapColor.COLOR_PURPLE), true);
        CREATIVE_LEY_STONE = VoidHeartBlocks.registerBlock("creative_ley_stone",
                properties -> new CreativeSourceBlock(CreativeSourceBlock.Kind.LEY_STONE, properties), creativeProperties(MapColor.COLOR_LIGHT_BLUE), true);

        VoidHeartTiles.registerTile(CREATIVE_SOURCE = FabricBlockEntityTypeBuilder.create(CreativeSourceTile::new, CREATIVE_VOID_WELL, CREATIVE_LEY_STONE).build(),
                "creative_source");
    }

    private static BlockBehaviour.Properties creativeProperties(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1F, 3600000F)
                .noLootTable()
                .lightLevel(state -> 7)
                .sound(SoundType.AMETHYST);
    }
}
