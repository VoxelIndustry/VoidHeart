package net.voxelindustry.voidheart.common.setup;

import net.voxelindustry.voidheart.common.content.mob.VoidSkeletonHeadBlock;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.fabricmc.fabric.impl.datagen.FabricDataGenHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.block.CustomStairsBlock;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchorBlock;
import net.voxelindustry.voidheart.common.block.VoidLampBlock;
import net.voxelindustry.voidheart.common.block.VoidMonolithBlock;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarBlock;
import net.voxelindustry.voidheart.common.content.beam.BeamFocusBlock;
import net.voxelindustry.voidheart.common.content.depths.AttachedGrowthBlock;
import net.voxelindustry.voidheart.common.content.depths.SmokerVentBlock;
import net.voxelindustry.voidheart.common.content.depths.StrandPlantBlock;
import net.voxelindustry.voidheart.common.content.depths.TubeWormsBlock;
import net.voxelindustry.voidheart.common.content.door.VoidDoorBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlockItem;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageItemBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleBlock;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartBlock;
import net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterBlock;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableBlock;
import net.voxelindustry.voidheart.common.content.beholder.BeholderBlock;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreBlock;
import net.voxelindustry.voidheart.common.content.portalframe.VoidStoneBricksBlock;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorBlock;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullBlock;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullItemBlock;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.VoidConduitBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlockItem;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterPartBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    public static Block PORTAL_INTERIOR;
    public static Block PORTAL_FRAME;
    public static Block PORTAL_FRAME_CORE;

    public static Block POCKET_WALL;
    public static Block BARNACLE_NEST;
    public static Block VOID_SKELETON_HEAD;
    public static Block VOIDSTONE;
    public static Block VOIDSTONE_STAIRS;
    public static Block VOIDSTONE_SLAB;
    public static Block VOIDSTONE_WALL;
    public static Block VOIDSTONE_CHISELED;

    public static Block VOIDSTONE_POLISHED;
    public static Block VOIDSTONE_POLISHED_STAIRS;
    public static Block VOIDSTONE_POLISHED_SLAB;
    public static Block VOIDSTONE_POLISHED_WALL;

    public static Block VOIDSTONE_BRICKS;
    public static Block VOIDSTONE_BRICKS_STAIRS;
    public static Block VOIDSTONE_BRICKS_SLAB;
    public static Block VOIDSTONE_BRICKS_WALL;

    public static Block VOIDSTONE_BRICKS_VERTICAL;

    public static Block VOIDSTONE_BRICKS_CRACKED;
    public static Block VOIDSTONE_BRICKS_CRACKED_STAIRS;
    public static Block VOIDSTONE_BRICKS_CRACKED_SLAB;
    public static Block VOIDSTONE_BRICKS_CRACKED_WALL;

    public static Block VOIDSTONE_TILE;
    public static Block VOIDSTONE_TILE_SMALL;
    public static Block VOIDSTONE_TILE_SMALL_STAIRS;
    public static Block VOIDSTONE_TILE_SMALL_SLAB;
    public static Block VOIDSTONE_TILE_SMALL_WALL;

    public static Block VOIDSTONE_TILE_SMALL_CARVED;
    public static Block VOIDSTONE_TILE_SMALL_CHISELED;
    public static Block ELDRITCH_VOIDSTONE;
    public static Block VOIDSTONE_WRITHING;
    public static Block VOIDSTONE_WRITHING_BLOSSOM;
    public static Block VOIDSTONE_WRITHING_MAW;
    public static Block VOIDSTONE_PILLAR;
    public static Block VOIDSTONE_PILLAR_SLAB;
    public static Block VOIDSTONE_PILLAR_STAIRS;
    public static Block VOIDSTONE_WEATHERED_PILLAR;

    public static Block VOID_LAMP;

    public static Block VOID_ALTAR;
    public static Block VOID_PILLAR;
    public static Block KNOWLEDGE_REPOSITORY;
    public static Block KNOWLEDGE_COLUMN;

    public static Block VOID_HEART;

    public static Block VOID_MONOLITH;
    public static Block VOID_MONOLITH_CAP;

    public static Block VOID_DOOR;
    public static Block OLD_ANCHOR;
    public static Block PERMEABLE_BARRIER;
    public static Block OCULUS_TABLE;
    public static Block BEHOLDER;

    public static Block EXPERIENCE_SKULL;
    public static Block MENDING_ALTAR;

    public static Block INVENTORY_INSERTER;

    public static Block EYE_BOTTLE;

    public static Block SHATTER_FORGE;
    public static Block VOID_CONDUIT;
    public static Block BEAM_FOCUS;

    public static Block MALIGNANT_TORMENTER;
    public static Block MALIGNANT_TORMENTER_PART;

    public static Block SMALL_AZURIUM_BUNDLE;
    public static Block MEDIUM_AZURIUM_BUNDLE;
    public static Block LARGE_AZURIUM_BUNDLE;
    public static Block SMALL_RUBRUM_BUNDLE;
    public static Block MEDIUM_RUBRUM_BUNDLE;
    public static Block LARGE_RUBRUM_BUNDLE;
    public static Block SMALL_MEMORIAM_BUNDLE;
    public static Block MEDIUM_MEMORIAM_BUNDLE;
    public static Block LARGE_MEMORIAM_BUNDLE;

    public static Block ARROGANT_IRON_BLOCK;
    public static Block CUT_ARROGANT_IRON;
    public static Block CUT_ARROGANT_IRON_STAIRS;
    public static Block CUT_ARROGANT_IRON_SLAB;
    public static Block CUT_ARROGANT_IRON_WALL;

    public static Block RAVENOUS_GOLD_BLOCK;
    public static Block CUT_RAVENOUS_GOLD;
    public static Block CUT_RAVENOUS_GOLD_STAIRS;
    public static Block CUT_RAVENOUS_GOLD_SLAB;
    public static Block CUT_RAVENOUS_GOLD_WALL;

    public static Block SEETHING_STEEL_BLOCK;
    public static Block CUT_SEETHING_STEEL;
    public static Block CUT_SEETHING_STEEL_STAIRS;
    public static Block CUT_SEETHING_STEEL_SLAB;
    public static Block CUT_SEETHING_STEEL_WALL;

    public static Block COVETOUS_COPPER_BLOCK;
    public static Block CUT_COVETOUS_COPPER;
    public static Block CUT_COVETOUS_COPPER_STAIRS;
    public static Block CUT_COVETOUS_COPPER_SLAB;
    public static Block CUT_COVETOUS_COPPER_WALL;

    public static Block LISTLESS_LEAD_BLOCK;
    public static Block CUT_LISTLESS_LEAD;
    public static Block CUT_LISTLESS_LEAD_STAIRS;
    public static Block CUT_LISTLESS_LEAD_SLAB;
    public static Block CUT_LISTLESS_LEAD_WALL;

    public static Block RAVENOUS_COLLECTOR;
    public static Block RAVENOUS_STORAGE;
    public static Block RAVENOUS_STORAGE_MATRIX;
    public static Block RAVENOUS_RELAY;

    public static Block ABYSSAL_ROCK;
    public static Block ABYSSAL_ROCK_STAIRS;
    public static Block ABYSSAL_ROCK_SLAB;
    public static Block ABYSSAL_ROCK_WALL;
    public static Block ABYSSAL_SILT;
    public static Block SULFIDE_CRUST;
    public static Block SMOKER_VENT;
    public static Block PALLID_CORAL_BLOCK;
    public static Block VIOLET_CORAL_BLOCK;
    public static Block LUMEN_CORAL_BLOCK;
    public static Block PALLID_CORAL;
    public static Block VIOLET_CORAL;
    public static Block LUMEN_CORAL;
    public static Block STILL_KELP;
    public static Block DRIFT_ALGAE;
    public static Block GLOW_ALGAE;
    public static Block GIANT_TUBE_WORMS;
    public static Block STILL_ANEMONE;
    public static Block BARNACLE_CRUST;

    public static final List<Block> MINEABLE_BLOCKS = new ArrayList<>();

    public static void registerBlocks()
    {
        PORTAL_INTERIOR = registerBlock("portal_interior", PortalInteriorBlock::new, BlockBehaviour.Properties.of(), false);
        PORTAL_FRAME = registerBlock("portal_frame", PortalFrameBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(4F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE), false);
        PORTAL_FRAME_CORE = registerBlock("portal_frame_core", PortalFrameCoreBlock::new, BlockBehaviour.Properties.of(), false);

        VOID_ALTAR = registerBlock("void_altar", VoidAltarBlock::new, BlockBehaviour.Properties.of(), true);
        VOID_PILLAR = registerBlock("void_pillar", VoidPillarBlock::new, BlockBehaviour.Properties.of(), true);
        KNOWLEDGE_REPOSITORY = registerBlock("knowledge_repository", KnowledgeRepositoryBlock::new, BlockBehaviour.Properties.of(), false);
        KNOWLEDGE_COLUMN = registerBlock("knowledge_column", KnowledgeColumnBlock::new, BlockBehaviour.Properties.of(), true);

        VOID_HEART = registerBlock("void_heart_block", VoidHeartBlock::new, BlockBehaviour.Properties.of(), false);

        POCKET_WALL = registerBlock("pocket_wall", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .strength(-1.0F, 3600000.0F)
                        .noLootTable()
                        .isValidSpawn((state, world, pos, type) -> false), false);

        VOIDSTONE = registerBlock("voidstone", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(2F, 6.0F), true);

        VOIDSTONE_POLISHED = registerBlock("voidstone_polished", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOIDSTONE_BRICKS = registerBlock("voidstone_bricks", VoidStoneBricksBlock::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_BRICKS_CRACKED = registerBlock("voidstone_bricks_cracked", VoidStoneBricksBlock::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_CHISELED = registerBlock("voidstone_chiseled", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOIDSTONE_BRICKS_VERTICAL = registerBlock("voidstone_bricks_vertical", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOIDSTONE_TILE = registerBlock("voidstone_tile", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_TILE_SMALL = registerBlock("voidstone_tile_small", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_TILE_SMALL_CARVED = registerBlock("voidstone_tile_small_carved", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_TILE_SMALL_CHISELED = registerBlock("voidstone_tile_small_chiseled", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        ELDRITCH_VOIDSTONE = registerBlock("eldritch_voidstone", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOIDSTONE_WRITHING = registerBlock("voidstone_writhing", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_WRITHING_BLOSSOM = registerBlock("voidstone_writhing_blossom", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_WRITHING_MAW = registerBlock("voidstone_writhing_maw", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOIDSTONE_PILLAR = registerBlock("voidstone_pillar", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);
        VOIDSTONE_WEATHERED_PILLAR = registerBlock("voidstone_pillar_weathered", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)
                        .strength(1.5F, 6.0F), true);

        VOID_MONOLITH = registerBlock("void_monolith", VoidMonolithBlock::new, BlockBehaviour.Properties.of(), true);
        VOID_MONOLITH_CAP = registerBlock("void_monolith_cap", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BLACK)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .strength(1.5F, 6.0F)
                        .sound(SoundType.STONE), true);

        VOID_LAMP = registerBlock("void_lamp", VoidLampBlock::new, BlockBehaviour.Properties.of(), true);

        VOID_DOOR = registerBlock("void_door", VoidDoorBlock::new, BlockBehaviour.Properties.of(), true);

        OLD_ANCHOR = registerBlock("old_anchor", OldAnchorBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(-1F, 3600000F)
                .noLootTable()
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), false);

        PERMEABLE_BARRIER = registerBlock("permeable_barrier", PermeableBarrierBlock::new, BlockBehaviour.Properties.of(), true);

        OCULUS_TABLE = registerBlock("oculus_table", OculusTableBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .lightLevel(state -> 7)
                .sound(SoundType.STONE), true);

        BEHOLDER = registerBlock("beholder", BeholderBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .lightLevel(state -> 5)
                .noOcclusion()
                .sound(SoundType.STONE), true);

        BARNACLE_NEST = registerBlock("barnacle_nest", BarnacleNestBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .requiresCorrectToolForDrops()
                .strength(10, 3)
                .noOcclusion()
                .sound(SoundType.SLIME_BLOCK), true);
        VOID_SKELETON_HEAD = registerBlock("void_skeleton_head", VoidSkeletonHeadBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.SKELETON)
                .strength(1)
                .noOcclusion()
                .pushReaction(PushReaction.POPPED)
                .sound(SoundType.BONE_BLOCK),
                (block, properties) -> new BlockItem(block, properties.component(DataComponents.EQUIPPABLE,
                        Equippable.builder(EquipmentSlot.HEAD).setSwappable(false).build())));

        EXPERIENCE_SKULL = registerBlock("experience_skull", ExperienceSkullBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), ExperienceSkullItemBlock::new);
        MENDING_ALTAR = registerBlock("mending_altar", MendingAltarBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), true);

        INVENTORY_INSERTER = registerBlock("inventory_inserter", InventoryInserterBlock::new, BlockBehaviour.Properties.of(),
                (block, properties) -> new LoreBlockItem(block, properties, MODID + ".inventory_inserter.lore1", MODID + ".inventory_inserter.lore2"));

        EYE_BOTTLE = registerBlock("eye_bottle", EyeBottleBlock::new, BlockBehaviour.Properties.of(),
                (block, properties) -> new LoreBlockItem(block, properties, MODID + ".eye_bottle.lore"));

        SHATTER_FORGE = registerBlock("shatter_forge", ShatterForgeBlock::new, BlockBehaviour.Properties.of(), true);
        VOID_CONDUIT = registerBlock("void_conduit", VoidConduitBlock::new, BlockBehaviour.Properties.of(), true);
        BEAM_FOCUS = registerBlock("beam_focus", BeamFocusBlock::new, BlockBehaviour.Properties.of(), true);

        ARROGANT_IRON_BLOCK = registerBlock("arrogant_iron_block", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        CUT_ARROGANT_IRON = registerBlock("cut_arrogant_iron", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        RAVENOUS_GOLD_BLOCK = registerBlock("ravenous_gold_block", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_GREEN)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        CUT_RAVENOUS_GOLD = registerBlock("cut_ravenous_gold", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_GREEN)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        SEETHING_STEEL_BLOCK = registerBlock("seething_steel_block", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_RED)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .lightLevel(state -> 4)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        CUT_SEETHING_STEEL = registerBlock("cut_seething_steel", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_RED)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .lightLevel(state -> 4)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        COVETOUS_COPPER_BLOCK = registerBlock("covetous_copper_block", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_ORANGE)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        CUT_COVETOUS_COPPER = registerBlock("cut_covetous_copper", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_ORANGE)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        LISTLESS_LEAD_BLOCK = registerBlock("listless_lead_block", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_GRAY)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);
        CUT_LISTLESS_LEAD = registerBlock("cut_listless_lead", Block::new, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_GRAY)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops().strength(4F, 10.0F), true);

        RAVENOUS_COLLECTOR = registerBlock("ravenous_collector", RavenousCollectorBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), RavenousStorageItemBlock::new);
        RAVENOUS_STORAGE = registerBlock("ravenous_storage", RavenousStorageBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), RavenousStorageBlockItem::new);
        RAVENOUS_STORAGE_MATRIX = registerBlock("ravenous_storage_matrix", RavenousStorageMatrixBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), RavenousStorageBlockItem::new);
        RAVENOUS_RELAY = registerBlock("ravenous_relay", RavenousRelayBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .sound(SoundType.STONE), true);

        MALIGNANT_TORMENTER = registerBlock("malignant_tormenter", MalignantTormenterBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .sound(SoundType.SLIME_BLOCK), MalignantTormenterBlockItem::new);
        // Only ever placed by a tormenter, so it has no item
        var tormenterPart = Identifier.fromNamespaceAndPath(MODID, "malignant_tormenter_part");
        MALIGNANT_TORMENTER_PART = Registry.register(BuiltInRegistries.BLOCK, tormenterPart, new MalignantTormenterPartBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, tormenterPart))
                .mapColor(MapColor.COLOR_RED)
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .noLootTable()
                .isValidSpawn(VoidHeartBlocks::never)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.SLIME_BLOCK)));

        registerDepthsBlocks();

        SMALL_AZURIUM_BUNDLE = registerSmallBundle("azurium", MapColor.COLOR_BLUE, 1, 6);
        MEDIUM_AZURIUM_BUNDLE = registerMediumBundle("azurium", MapColor.COLOR_BLUE, 3, 10);
        LARGE_AZURIUM_BUNDLE = registerLargeBundle("azurium", MapColor.COLOR_BLUE, 5, 15);
        SMALL_RUBRUM_BUNDLE = registerSmallBundle("rubrum", MapColor.FIRE, 4, 5);
        MEDIUM_RUBRUM_BUNDLE = registerMediumBundle("rubrum", MapColor.FIRE, 7, 9);
        LARGE_RUBRUM_BUNDLE = registerLargeBundle("rubrum", MapColor.FIRE, 10, 14);
        SMALL_MEMORIAM_BUNDLE = registerSmallBundle("memoriam", MapColor.COLOR_ORANGE, 1, 6);
        MEDIUM_MEMORIAM_BUNDLE = registerMediumBundle("memoriam", MapColor.COLOR_ORANGE, 3, 10);
        LARGE_MEMORIAM_BUNDLE = registerLargeBundle("memoriam", MapColor.COLOR_ORANGE, 5, 14);

        VOIDSTONE_PILLAR_SLAB = generateSlab(VOIDSTONE_PILLAR, "voidstone_pillar");
        VOIDSTONE_PILLAR_STAIRS = generateStairs(VOIDSTONE_PILLAR, "voidstone_pillar");

        VOIDSTONE_STAIRS = generateStairs(VOIDSTONE, "voidstone");
        VOIDSTONE_SLAB = generateSlab(VOIDSTONE, "voidstone");
        VOIDSTONE_WALL = generateWall(VOIDSTONE, "voidstone");

        VOIDSTONE_POLISHED_STAIRS = generateStairs(VOIDSTONE_POLISHED, "voidstone_polished");
        VOIDSTONE_POLISHED_SLAB = generateSlab(VOIDSTONE_POLISHED, "voidstone_polished");
        VOIDSTONE_POLISHED_WALL = generateWall(VOIDSTONE_POLISHED, "voidstone_polished");

        VOIDSTONE_BRICKS_STAIRS = generateStairs(VOIDSTONE_BRICKS, "voidstone_bricks");
        VOIDSTONE_BRICKS_WALL = generateWall(VOIDSTONE_BRICKS, "voidstone_bricks");
        VOIDSTONE_BRICKS_SLAB = generateSlab(VOIDSTONE_BRICKS, "voidstone_bricks");

        VOIDSTONE_BRICKS_CRACKED_STAIRS = generateStairs(VOIDSTONE_BRICKS_CRACKED, "voidstone_bricks_cracked");
        VOIDSTONE_BRICKS_CRACKED_WALL = generateWall(VOIDSTONE_BRICKS_CRACKED, "voidstone_bricks_cracked");
        VOIDSTONE_BRICKS_CRACKED_SLAB = generateSlab(VOIDSTONE_BRICKS_CRACKED, "voidstone_bricks_cracked");

        VOIDSTONE_TILE_SMALL_STAIRS = generateStairs(VOIDSTONE_TILE_SMALL, "voidstone_tile_small");
        VOIDSTONE_TILE_SMALL_SLAB = generateSlab(VOIDSTONE_TILE_SMALL, "voidstone_tile_small");
        VOIDSTONE_TILE_SMALL_WALL = generateWall(VOIDSTONE_TILE_SMALL, "voidstone_tile_small");

        CUT_ARROGANT_IRON_STAIRS = generateStairs(CUT_ARROGANT_IRON, "cut_arrogant_iron");
        CUT_ARROGANT_IRON_SLAB = generateSlab(CUT_ARROGANT_IRON, "cut_arrogant_iron");
        CUT_ARROGANT_IRON_WALL = generateWall(CUT_ARROGANT_IRON, "cut_arrogant_iron");

        CUT_RAVENOUS_GOLD_STAIRS = generateStairs(CUT_RAVENOUS_GOLD, "cut_ravenous_gold");
        CUT_RAVENOUS_GOLD_SLAB = generateSlab(CUT_RAVENOUS_GOLD, "cut_ravenous_gold");
        CUT_RAVENOUS_GOLD_WALL = generateWall(CUT_RAVENOUS_GOLD, "cut_ravenous_gold");

        CUT_SEETHING_STEEL_STAIRS = generateStairs(CUT_SEETHING_STEEL, "cut_seething_steel");
        CUT_SEETHING_STEEL_SLAB = generateSlab(CUT_SEETHING_STEEL, "cut_seething_steel");
        CUT_SEETHING_STEEL_WALL = generateWall(CUT_SEETHING_STEEL, "cut_seething_steel");

        CUT_COVETOUS_COPPER_STAIRS = generateStairs(CUT_COVETOUS_COPPER, "cut_covetous_copper");
        CUT_COVETOUS_COPPER_SLAB = generateSlab(CUT_COVETOUS_COPPER, "cut_covetous_copper");
        CUT_COVETOUS_COPPER_WALL = generateWall(CUT_COVETOUS_COPPER, "cut_covetous_copper");

        CUT_LISTLESS_LEAD_STAIRS = generateStairs(CUT_LISTLESS_LEAD, "cut_listless_lead");
        CUT_LISTLESS_LEAD_SLAB = generateSlab(CUT_LISTLESS_LEAD, "cut_listless_lead");
        CUT_LISTLESS_LEAD_WALL = generateWall(CUT_LISTLESS_LEAD, "cut_listless_lead");
    }

    private static void registerDepthsBlocks()
    {
        ABYSSAL_ROCK = registerBlock("abyssal_rock", Block::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE)
                .strength(2F, 6.0F), true);
        ABYSSAL_ROCK_STAIRS = generateStairs(ABYSSAL_ROCK, "abyssal_rock");
        ABYSSAL_ROCK_SLAB = generateSlab(ABYSSAL_ROCK, "abyssal_rock");
        ABYSSAL_ROCK_WALL = generateWall(ABYSSAL_ROCK, "abyssal_rock");

        ABYSSAL_SILT = registerBlock("abyssal_silt", Block::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.CLAY)
                .sound(SoundType.MUD)
                .strength(0.6F), true);

        SULFIDE_CRUST = registerBlock("sulfide_crust", Block::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.BASALT)
                .lightLevel(state -> 2)
                .strength(2.5F, 6.0F), true);
        SMOKER_VENT = registerBlock("smoker_vent", SmokerVentBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.BASALT)
                .lightLevel(state -> 6)
                .strength(2.5F, 6.0F), true);

        PALLID_CORAL_BLOCK = registerCoralBlock("pallid_coral_block", MapColor.QUARTZ, 0);
        VIOLET_CORAL_BLOCK = registerCoralBlock("violet_coral_block", MapColor.COLOR_PURPLE, 0);
        LUMEN_CORAL_BLOCK = registerCoralBlock("lumen_coral_block", MapColor.DIAMOND, 7);
        PALLID_CORAL = registerGrowth("pallid_coral", MapColor.QUARTZ, 0, 14, 14);
        VIOLET_CORAL = registerGrowth("violet_coral", MapColor.COLOR_PURPLE, 0, 14, 14);
        LUMEN_CORAL = registerGrowth("lumen_coral", MapColor.DIAMOND, 8, 14, 14);
        GLOW_ALGAE = registerGrowth("glow_algae", MapColor.COLOR_CYAN, 6, 2, 16);
        BARNACLE_CRUST = registerGrowth("barnacle_crust", MapColor.TERRACOTTA_WHITE, 0, 2, 16);
        STILL_ANEMONE = registerGrowth("still_anemone", MapColor.COLOR_PINK, 5, 10, 12);

        STILL_KELP = registerBlock("still_kelp", properties -> new StrandPlantBlock(Direction.UP, properties), plantProperties(MapColor.COLOR_GREEN, 0), true);
        DRIFT_ALGAE = registerBlock("drift_algae", properties -> new StrandPlantBlock(Direction.DOWN, properties), plantProperties(MapColor.COLOR_CYAN, 0), true);
        GIANT_TUBE_WORMS = registerBlock("giant_tube_worms", TubeWormsBlock::new, plantProperties(MapColor.COLOR_RED, 0), true);
    }

    private static Block registerCoralBlock(String name, MapColor color, int light)
    {
        return registerBlock(name, Block::new, BlockBehaviour.Properties.of()
                .mapColor(color)
                .requiresCorrectToolForDrops()
                .sound(SoundType.CORAL_BLOCK)
                .lightLevel(state -> light)
                .strength(1.5F, 6.0F), true);
    }

    private static Block registerGrowth(String name, MapColor color, int light, float height, float width)
    {
        return registerBlock(name, properties -> new AttachedGrowthBlock(height, width, properties), plantProperties(color, light), true);
    }

    private static BlockBehaviour.Properties plantProperties(MapColor color, int light)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .noCollision()
                .noOcclusion()
                .instabreak()
                .lightLevel(state -> light)
                .pushReaction(PushReaction.POPPED)
                .sound(SoundType.WET_GRASS);
    }

    private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean inCreativeTab)
    {
        return registerBlock(name, factory, properties, BlockItem::new, inCreativeTab);
    }

    private static Block registerBlock(String name,
                                       Function<BlockBehaviour.Properties, Block> factory,
                                       BlockBehaviour.Properties properties,
                                       BiFunction<Block, Item.Properties, Item> itemFactory)
    {
        return registerBlock(name, factory, properties, itemFactory, true);
    }

    private static Block registerBlock(String name,
                                       Function<BlockBehaviour.Properties, Block> factory,
                                       BlockBehaviour.Properties properties,
                                       BiFunction<Block, Item.Properties, Item> itemFactory,
                                       boolean inCreativeTab)
    {
        var identifier = Identifier.fromNamespaceAndPath(MODID, name);
        var block = Registry.register(BuiltInRegistries.BLOCK, identifier, factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, identifier))));
        var item = Registry.register(BuiltInRegistries.ITEM, identifier,
                itemFactory.apply(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, identifier)).useBlockDescriptionPrefix()));

        if (inCreativeTab)
            VoidHeartItems.CREATIVE_TAB_ITEMS.add(item);
        if (FabricDataGenHelper.ENABLED && block.defaultBlockState().requiresCorrectToolForDrops())
            MINEABLE_BLOCKS.add(block);

        return block;
    }

    private static Block registerSmallBundle(String crystal, MapColor color, int light, float height)
    {
        return registerBundle("small_" + crystal + "_bundle", color, SoundType.SMALL_AMETHYST_BUD, light, height, 4);
    }

    private static Block registerMediumBundle(String crystal, MapColor color, int light, float height)
    {
        return registerBundle("medium_" + crystal + "_bundle", color, SoundType.MEDIUM_AMETHYST_BUD, light, height, 2);
    }

    private static Block registerLargeBundle(String crystal, MapColor color, int light, float height)
    {
        return registerBundle("large_" + crystal + "_bundle", color, SoundType.AMETHYST_CLUSTER, light, height, 2);
    }

    private static Block registerBundle(String name, MapColor color, SoundType sound, int light, float height, float inset)
    {
        return registerBlock(name, properties -> new AmethystClusterBlock(height, inset, properties), BlockBehaviour.Properties.of()
                .mapColor(color)
                .forceSolidOn()
                .noOcclusion()
                .requiresCorrectToolForDrops()
                .strength(1.5F)
                .lightLevel(state -> light)
                .pushReaction(PushReaction.POPPED)
                .sound(sound), true);
    }

    private static Block generateStairs(Block block, String name)
    {
        return registerBlock(name + "_stairs", properties -> new CustomStairsBlock(block.defaultBlockState(), properties), BlockBehaviour.Properties.ofFullCopy(block), true);
    }

    private static Block generateWall(Block block, String name)
    {
        return registerBlock(name + "_wall", WallBlock::new, BlockBehaviour.Properties.ofFullCopy(block), true);
    }

    private static Block generateSlab(Block block, String name)
    {
        return registerBlock(name + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(block), true);
    }

    public static Boolean never(BlockState state, BlockGetter world, BlockPos pos, EntityType<?> type)
    {
        return false;
    }
}
