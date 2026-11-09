package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for W2 Rubrum storage, called once by EnergySetup: the construct, its frame, the cell and the cell socket.
 */
public final class RubrumContent
{
    public static Block RUBRUM_CONSTRUCT_CORE;
    public static Block RUBRUM_CONSTRUCT_FRAME;
    public static Item  RUBRUM_CELL;
    public static Block CELL_SOCKET;

    public static BlockEntityType<RubrumConstructTile> RUBRUM_CONSTRUCT;
    public static BlockEntityType<CellSocketTile>      CELL_SOCKET_TILE;

    /** Energy and bundles a broken construct core carries. */
    public static DataComponentType<ConstructContents> CONSTRUCT_CONTENTS;
    /** Energy held by a rubrum cell. */
    public static DataComponentType<Long>              VOID_CHARGE;

    public static SimpleParticleType RUBRUM_MOTE;

    private RubrumContent()
    {
    }

    public static void register()
    {
        CONSTRUCT_CONTENTS = component("rubrum_construct", ConstructContents.CODEC);
        VOID_CHARGE = component("void_charge", Codec.LONG);

        RUBRUM_CONSTRUCT_CORE = VoidHeartBlocks.registerBlock("rubrum_construct_core", RubrumConstructBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .lightLevel(state -> 3)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), RubrumConstructItem::new);
        RUBRUM_CONSTRUCT_FRAME = VoidHeartBlocks.registerBlock("rubrum_construct_frame", ConstructFrameBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), true);
        CELL_SOCKET = VoidHeartBlocks.registerBlock("cell_socket", CellSocketBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(2.5F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), true);
        RUBRUM_CELL = VoidHeartItems.registerItem("rubrum_cell", properties -> new RubrumCellItem(properties.stacksTo(1)));

        VoidHeartTiles.registerTile(RUBRUM_CONSTRUCT = FabricBlockEntityTypeBuilder.create(RubrumConstructTile::new, RUBRUM_CONSTRUCT_CORE).build(),
                "rubrum_construct");
        VoidHeartTiles.registerTile(CELL_SOCKET_TILE = FabricBlockEntityTypeBuilder.create(CellSocketTile::new, CELL_SOCKET).build(), "cell_socket");

        RUBRUM_MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MODID, "rubrum_mote"),
                FabricParticleTypes.simple(false));
    }

    /**
     * Adds this package's subcommands under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        RubrumCommands.register(energy);
    }

    private static <T> DataComponentType<T> component(String name, Codec<T> codec)
    {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MODID, name),
                DataComponentType.<T>builder()
                        .persistent(codec)
                        .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec))
                        .build());
    }
}
