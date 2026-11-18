package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.Registry;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout.Part;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Twin Ventricle's blocks, block entities and tears, registered once at startup.
 */
public final class TwinVentricleContent
{
    public static Block HEART_CRADLE;
    public static Block VENTRICLE_BASIN;
    public static Block RUBRUM_VEINED_VOIDSTONE;
    public static Block AZURIUM_VEINED_VOIDSTONE;
    public static Block RAVENOUS_GOLD_VALVE;

    public static BlockEntityType<HeartCradleTile>    HEART_CRADLE_TILE;
    public static BlockEntityType<VentricleBasinTile> VENTRICLE_BASIN_TILE;

    public static EntityType<VentricleTear> VENTRICLE_TEAR;

    private TwinVentricleContent()
    {
    }

    private static BlockBehaviour.Properties stone(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 9F)
                .sound(SoundType.STONE);
    }

    public static void register()
    {
        HEART_CRADLE = VoidHeartBlocks.registerBlock("heart_cradle", HeartCradleBlock::new, stone(MapColor.COLOR_PURPLE).noOcclusion(), true);
        VENTRICLE_BASIN = VoidHeartBlocks.registerBlock("ventricle_basin", VentricleBasinBlock::new, stone(MapColor.COLOR_BLACK).noOcclusion(), true);
        RUBRUM_VEINED_VOIDSTONE = VoidHeartBlocks.registerBlock("rubrum_veined_voidstone", Block::new, stone(MapColor.COLOR_RED), true);
        AZURIUM_VEINED_VOIDSTONE = VoidHeartBlocks.registerBlock("azurium_veined_voidstone", Block::new, stone(MapColor.COLOR_LIGHT_BLUE), true);
        RAVENOUS_GOLD_VALVE = VoidHeartBlocks.registerBlock("ravenous_gold_valve", Block::new,
                stone(MapColor.GOLD).strength(4F, 9F).sound(SoundType.METAL), true);

        VoidHeartTiles.registerTile(HEART_CRADLE_TILE = FabricBlockEntityTypeBuilder.create(HeartCradleTile::new, HEART_CRADLE).build(), "heart_cradle");
        VoidHeartTiles.registerTile(VENTRICLE_BASIN_TILE = FabricBlockEntityTypeBuilder.create(VentricleBasinTile::new, VENTRICLE_BASIN).build(),
                "ventricle_basin");

        var tearId = Identifier.fromNamespaceAndPath(MODID, "ventricle_tear");
        VENTRICLE_TEAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, tearId, EntityType.Builder.<VentricleTear>of(VentricleTear::new, MobCategory.MISC)
                .sized(0.6F, 2.6F)
                .clientTrackingRange(6)
                .updateInterval(20)
                .noSave()
                .fireImmune()
                .noSummon()
                .noLootTable()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, tearId)));
    }

    public static @Nullable Part partAt(BlockGetter level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        if (state.is(VoidHeartBlocks.VOIDSTONE_POLISHED))
            return Part.DAIS;
        if (state.is(RAVENOUS_GOLD_VALVE))
            return Part.VALVE;
        if (state.is(VENTRICLE_BASIN))
            return Part.BASIN;
        if (state.is(RUBRUM_VEINED_VOIDSTONE))
            return Part.RUBRUM_WALL;
        if (state.is(AZURIUM_VEINED_VOIDSTONE))
            return Part.AZURIUM_WALL;
        return null;
    }
}
