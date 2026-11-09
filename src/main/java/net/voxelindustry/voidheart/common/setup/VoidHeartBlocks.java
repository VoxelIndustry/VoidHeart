package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.registry.LandPathTypeRegistry;
import net.minecraft.world.level.pathfinder.PathType;
import net.voxelindustry.voidheart.common.content.salt.SaltWardBlock;
import net.voxelindustry.voidheart.common.content.mob.VoidSkeletonHeadBlock;
import net.voxelindustry.voidheart.common.content.athanor.AlembicBlock;
import net.voxelindustry.voidheart.common.content.athanor.AspectIntakeBlock;
import net.voxelindustry.voidheart.common.content.athanor.AthanorBlock;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleBlock;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.fabricmc.fabric.impl.datagen.FabricDataGenHelper;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.research.CogitantTile;
import net.voxelindustry.voidheart.common.content.research.MeatProxyBlock;
import net.voxelindustry.voidheart.common.content.research.MeatProxyBlockItem;
import net.voxelindustry.voidheart.common.content.research.RepositoryUpgradeBlock;
import net.voxelindustry.voidheart.common.content.research.VoidLoomBlock;
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
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.block.CustomStairsBlock;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchorBlock;
import net.voxelindustry.voidheart.common.block.VoidLampBlock;
import net.voxelindustry.voidheart.common.block.VoidMonolithBlock;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.content.flask.PlacedFlasksBlock;
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
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlockItem;
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
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryBaseBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryCoreBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryHubBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeBlock;
import net.voxelindustry.voidheart.common.content.shatterforge.VoidConduitBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlock;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterBlockItem;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterPartBlock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.voxelindustry.voidheart.common.content.daedalus.DaedalusPropBlock;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    private static final int RIFT_LIGHT = 10;

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

    public static Block BRONZE_INLAID_VOIDSTONE;
    public static Block IRON_INLAID_VOIDSTONE;
    public static Block VOIDSTONE_ENGRAVED_GEAR;
    public static Block VOIDSTONE_ENGRAVED_EYE;
    public static Block CAGED_LAMP_AMBER;
    public static Block CAGED_LAMP_TEAL;
    public static Block CAGED_LAMP_PALE;
    public static Block UMBRAL_RUG;
    public static Block VIOLET_RUG;
    public static Block TEAL_RUG;
    public static Block VOIDSTONE_BOOKSHELF;
    public static Block RUINED_VOIDSTONE_BOOKSHELF;

    public static Block VOID_LAMP;

    public static Block VOID_ALTAR;
    public static Block VOID_PILLAR;
    public static Block KNOWLEDGE_REPOSITORY;
    public static Block KNOWLEDGE_COLUMN;
    public static Block COGITANT;
    public static Block VOID_LOOM;
    public static Block MEAT_PROXY;

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

    public static Block ATHANOR;
    public static Block ALEMBIC;
    public static Block CRUCIBLE;
    public static Block ASPECT_INTAKE;
    public static Block SALT_WARD;
    public static Block PLACED_FLASKS;
    public static Block ARMILLARY_CORE;
    public static Block ARMILLARY_BASE_SIDE;
    public static Block ARMILLARY_BASE_CORNER;
    public static Block ARMILLARY_HUB;
    public static Block ARMILLARY_RIFT;
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

    private static final Map<DaedalusProp, Block> DAEDALUS_PROPS = new EnumMap<>(DaedalusProp.class);

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
        COGITANT = registerBlock("cogitant", properties -> new RepositoryUpgradeBlock<>(properties, Block.box(2, 0, 2, 14, 13, 14),
                () -> VoidHeartTiles.COGITANT, CogitantTile::new, CogitantTile::clientTick), BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .sound(SoundType.STONE), true);
        VOID_LOOM = registerBlock("void_loom", VoidLoomBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .sound(SoundType.STONE), true);
        MEAT_PROXY = registerBlock("meat_proxy", properties -> new MeatProxyBlock(properties, Block.box(1, 0, 1, 15, 15, 15)), BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(1F)
                .noOcclusion()
                .sound(SoundType.SLIME_BLOCK), MeatProxyBlockItem::new);

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

        registerDaedalusDecor();

        VOID_MONOLITH =registerBlock("void_monolith", VoidMonolithBlock::new, BlockBehaviour.Properties.of(), true);
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
        ARMILLARY_CORE = registerBlock("armillary_core", ArmillaryCoreBlock::new, BlockBehaviour.Properties.of(), true);
        ARMILLARY_HUB = registerArmillaryPart("armillary_hub", ArmillaryHubBlock::new, ArmillaryHubBlock::lightLevel);
        ARMILLARY_RIFT = registerArmillaryPart("armillary_rift", ArmillaryRiftBlock::new, state -> RIFT_LIGHT);
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
        ARMILLARY_BASE_SIDE = registerArmillaryBase("armillary_base_side", CUT_RAVENOUS_GOLD, false, Shapes.or(
                Block.box(0, 0, 0, 16, 3, 16),
                Block.box(0, 3, 2, 16, 9, 16),
                Block.box(0, 9, 4, 16, 13, 16)));
        ARMILLARY_BASE_CORNER = registerArmillaryBase("armillary_base_corner", VOIDSTONE_CHISELED, true, Shapes.or(
                Block.box(0, 0, 12, 16, 3, 16),
                Block.box(0, 0, 0, 4, 3, 16),
                Block.box(0, 0, 6, 10, 3, 16),
                Block.box(0, 3, 8, 8, 13, 16),
                Block.box(0, 3, 12, 12, 13, 16),
                Block.box(0, 3, 4, 4, 13, 16),
                Block.box(6, 0, 6, 12, 10, 12),
                Block.box(7, 10, 7, 11, 21, 11)));
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
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), RavenousStorageBlockItem::new);
        RAVENOUS_STORAGE_MATRIX = registerBlock("ravenous_storage_matrix", RavenousStorageMatrixBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), RavenousStorageMatrixBlockItem::new);
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
        registerDaedalusProps();
        registerAthanorBlocks();

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

    public static Block daedalusProp(DaedalusProp prop)
    {
        return DAEDALUS_PROPS.get(prop);
    }

    private static void registerDaedalusProps()
    {
        for (var prop : DaedalusProp.values())
        {
            var shape = switch (prop)
            {
                case BENCH -> Block.box(0, 0, 4, 16, 8, 12);
                case PLINTH -> Block.box(2, 0, 2, 14, 16, 14);
                case BUST -> Block.box(3, 0, 4, 13, 15, 12);
                case URN -> Block.box(3, 0, 3, 13, 15, 13);
                case CANDELABRUM -> Block.box(3, 0, 6, 13, 15, 10);
                case CHANDELIER -> Block.box(2, 3, 2, 14, 16, 14);
                case FRAME -> Block.box(1, 2, 15, 15, 15, 16);
                case DESK -> Block.box(0, 0, 2, 16, 15, 14);
                case LEDGERS -> Block.box(2, 0, 2, 13, 8, 12);
                case GRATE -> Block.box(0, 0, 0, 16, 16, 16);
                case VALVE -> Block.box(3, 3, 10, 13, 13, 16);
                case GROWTH -> Block.box(3, 0, 4, 13, 8, 13);
                case COT -> Block.box(0, 0, 1, 16, 5, 15);
                case ORRERY -> Block.box(2, 0, 2, 14, 15, 14);
                case LECTERN -> Block.box(2, 0, 2, 14, 15, 14);
                case CHAIR -> Block.box(2, 0, 2, 14, 16, 14);
                case HOURGLASS -> Block.box(4, 0, 4, 12, 14, 12);
                case CLOCK -> Block.box(2, 2, 13, 14, 14, 16);
                case MIRROR -> Block.box(2, 0, 14, 14, 16, 16);
                case PIPES -> Block.box(2, 0, 11, 12, 16, 16);
                case BASIN -> Block.box(2, 0, 2, 14, 9, 14);
                case CRATES -> Block.box(0, 0, 1, 16, 14, 13);
                case BRAZIER -> Block.box(2, 0, 2, 14, 10, 14);
                case CAGE -> Block.box(3, 1, 3, 13, 16, 13);
                case CANDLES -> Block.box(3, 0, 3, 13, 8, 12);
                case REMAINS -> Block.box(2, 0, 2, 14, 5, 14);
                case TABLE -> Block.box(0, 0, 1, 16, 16, 15);
                case BOOKS -> Block.box(2, 0, 2, 15, 5, 14);
                case SCONCE -> Block.box(5, 4, 9, 11, 14, 16);
                case TAPESTRY -> Block.box(1, 0, 14, 15, 15, 16);
                case CABINET -> Block.box(2, 0, 2, 14, 16, 14);
                case BARREL -> Block.box(3, 0, 3, 13, 14, 13);
                case TRUNK -> Block.box(1, 0, 2, 15, 11, 14);
                case PLANTER -> Block.box(3.5, 0, 3.5, 12.5, 15, 12.5);
                case CENSER -> Block.box(5, 3, 5, 11, 16, 11);
                case STATUE -> Block.box(3, 0, 3, 13, 16, 13);
                case SCROLLS -> Block.box(1, 4, 11, 15, 16, 16);
                case GEARS -> Block.box(2, 2, 13, 15, 15, 16);
                case GAUGE -> Block.box(3.5, 0, 12, 12.5, 13, 16);
                case CONSOLE -> Block.box(1, 0, 4, 15, 14, 15);
                case COIL -> Block.box(4, 0, 4, 12, 16, 12);
                case CONDENSER -> Block.box(2, 0, 2, 14, 16, 14);
                case FLYWHEEL -> Block.box(0.5, 0, 2, 15.5, 15, 14);
                case PISTON -> Block.box(3, 0, 3, 13, 16, 13);
                case AUTOMATON -> Block.box(2.5, 0, 1, 13.5, 12, 12);
                case LANTERN -> Block.box(4.5, 0, 4.5, 11.5, 16, 11.5);
                case WINCH -> Block.box(2, 0, 9, 16, 13, 16);
                case CELLS -> Block.box(3, 0, 3, 13, 11, 13);
                case SARCOPHAGUS -> Block.box(1, 0, 0, 15, 11, 16);
                case SARCOPHAGUS_FOOT -> Block.box(1, 0, 0.5, 15, 9, 16);
                case SARCOPHAGUS_OPEN -> Block.box(1, 0, 0, 15, 11, 16);
            };
            var sound = switch (prop)
            {
                case CANDELABRUM, CHANDELIER, GRATE, VALVE, ORRERY, CLOCK, BRAZIER, CAGE, SCONCE, CENSER -> SoundType.CHAIN;
                case CABINET -> SoundType.METAL;
                case GEARS, GAUGE, CONSOLE, COIL, CONDENSER, FLYWHEEL, PISTON, AUTOMATON, LANTERN, WINCH, CELLS -> SoundType.COPPER;
                case PIPES -> SoundType.COPPER;
                case GROWTH -> SoundType.SLIME_BLOCK;
                case LEDGERS, COT, CANDLES, BOOKS, TAPESTRY -> SoundType.WOOL;
                case LECTERN, CHAIR, CRATES, TABLE, BARREL, TRUNK, SCROLLS -> SoundType.WOOD;
                case HOURGLASS, MIRROR -> SoundType.GLASS;
                case REMAINS -> SoundType.BONE_BLOCK;
                default -> SoundType.STONE;
            };
            int light = switch (prop)
            {
                case CANDELABRUM -> 12;
                case CHANDELIER -> 15;
                case BRAZIER -> 14;
                case SCONCE -> 11;
                case LANTERN -> 4;
                case CONDENSER -> 3;
                case CELLS -> 2;
                case CANDLES -> 9;
                default -> 0;
            };
            var properties = BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(1.5F, 6.0F)
                    .sound(sound)
                    .noOcclusion()
                    .lightLevel(state -> light)
                    .pushReaction(PushReaction.POPPED);
            DAEDALUS_PROPS.put(prop, registerBlock(prop.id(), blockProperties -> new DaedalusPropBlock(blockProperties, prop, shape), properties, true));
        }
    }

    private static void registerAthanorBlocks()
    {
        ATHANOR = registerBlock("athanor", AthanorBlock::new, athanorPart()
                .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 13 : 4),
                (block, properties) -> new LoreBlockItem(block, properties, MODID + ".athanor.lore"));
        ALEMBIC = registerBlock("alembic", AlembicBlock::new, athanorPart(), true);
        CRUCIBLE = registerBlock("crucible", CrucibleBlock::new, athanorPart(), true);
        ASPECT_INTAKE = registerBlock("aspect_intake", AspectIntakeBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE), true);

        // Laid from salt rather than placed, so it has no item and drops nothing
        var saltId = Identifier.fromNamespaceAndPath(MODID, "salt_ward");
        SALT_WARD = Registry.register(BuiltInRegistries.BLOCK, saltId, new SaltWardBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, saltId))
                .mapColor(MapColor.SNOW)
                .noCollision()
                .noOcclusion()
                .instabreak()
                .noLootTable()
                .sound(SoundType.SAND)
                .pushReaction(PushReaction.POPPED)));
        LandPathTypeRegistry.register(SALT_WARD, PathType.DAMAGE_CAUTIOUS, null);

        // Set down from a flask rather than placed, so it has no item and drops the flasks it holds
        var flasksId = Identifier.fromNamespaceAndPath(MODID, "placed_flasks");
        PLACED_FLASKS = Registry.register(BuiltInRegistries.BLOCK, flasksId, new PlacedFlasksBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, flasksId))
                .mapColor(MapColor.NONE)
                .noOcclusion()
                .strength(0.2F)
                .noLootTable()
                .sound(SoundType.GLASS)
                .pushReaction(PushReaction.POPPED)));
    }

    /**
     * Voidstone banded with seething steel, so it sounds and maps like stone.
     */
    private static BlockBehaviour.Properties athanorPart()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE)
                .noOcclusion()
                .requiresCorrectToolForDrops()
                .strength(4F, 10.0F);
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

    /**
     * Mined and sounding like the block it stands in for, which is also what it drops. It has no item of its own, and part of its cell is empty.
     */
    private static Block registerArmillaryBase(String name, Block original, boolean corner, VoxelShape northShape)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, name);
        var block = Registry.register(BuiltInRegistries.BLOCK, id, new ArmillaryBaseBlock(BlockBehaviour.Properties.ofFullCopy(original)
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .noLootTable()
                .randomTicks()
                .pushReaction(PushReaction.IMMOVEABLE)
                .isValidSpawn(VoidHeartBlocks::never)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false), original, corner, northShape));
        if (FabricDataGenHelper.ENABLED)
            MINEABLE_BLOCKS.add(block);
        return block;
    }

    /**
     * Nothing a player can stand on, push, or take home.
     */
    private static Block registerArmillaryPart(String name, Function<BlockBehaviour.Properties, Block> factory, ToIntFunction<BlockState> light)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, name);
        return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1, 3600000)
                .noCollision()
                .noOcclusion()
                .noLootTable()
                .lightLevel(light)
                .pushReaction(PushReaction.IMMOVEABLE)
                .isValidSpawn(VoidHeartBlocks::never)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false)));
    }

    /**
     * Trim, lamps, panels, rugs and shelves the Daedalus is furnished with, so its rooms need no vanilla blocks that look out of place.
     */
    private static void registerDaedalusDecor()
    {
        BRONZE_INLAID_VOIDSTONE = registerBlock("bronze_inlaid_voidstone", Block::new, voidstoneProperties(), true);
        IRON_INLAID_VOIDSTONE = registerBlock("iron_inlaid_voidstone", Block::new, voidstoneProperties(), true);
        VOIDSTONE_ENGRAVED_GEAR = registerBlock("voidstone_engraved_gear", Block::new, voidstoneProperties(), true);
        VOIDSTONE_ENGRAVED_EYE = registerBlock("voidstone_engraved_eye", Block::new, voidstoneProperties(), true);

        CAGED_LAMP_AMBER = registerBlock("caged_lamp_amber", Block::new, lampProperties(MapColor.COLOR_ORANGE), true);
        CAGED_LAMP_TEAL = registerBlock("caged_lamp_teal", Block::new, lampProperties(MapColor.COLOR_CYAN), true);
        CAGED_LAMP_PALE = registerBlock("caged_lamp_pale", Block::new, lampProperties(MapColor.COLOR_LIGHT_BLUE), true);

        UMBRAL_RUG = registerBlock("umbral_rug", CarpetBlock::new, rugProperties(MapColor.COLOR_BLACK), true);
        VIOLET_RUG = registerBlock("violet_rug", CarpetBlock::new, rugProperties(MapColor.COLOR_PURPLE), true);
        TEAL_RUG = registerBlock("teal_rug", CarpetBlock::new, rugProperties(MapColor.COLOR_CYAN), true);

        VOIDSTONE_BOOKSHELF = registerBlock("voidstone_bookshelf", Block::new, voidstoneProperties(), true);
        RUINED_VOIDSTONE_BOOKSHELF = registerBlock("ruined_voidstone_bookshelf", Block::new, voidstoneProperties(), true);
    }

    private static BlockBehaviour.Properties voidstoneProperties()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .strength(1.5F, 6.0F);
    }

    private static BlockBehaviour.Properties lampProperties(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.COPPER)
                .strength(1.5F, 6.0F)
                .lightLevel(state -> 15);
    }

    private static BlockBehaviour.Properties rugProperties(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.1F)
                .sound(SoundType.WOOL)
                .ignitedByLava();
    }

    public static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean inCreativeTab)
    {
        return registerBlock(name, factory, properties, BlockItem::new, inCreativeTab);
    }

    public static Block registerBlock(String name,
                                       Function<BlockBehaviour.Properties, Block> factory,
                                       BlockBehaviour.Properties properties,
                                       BiFunction<Block, Item.Properties, Item> itemFactory)
    {
        return registerBlock(name, factory, properties, itemFactory, true);
    }

    public static Block registerBlock(String name,
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
