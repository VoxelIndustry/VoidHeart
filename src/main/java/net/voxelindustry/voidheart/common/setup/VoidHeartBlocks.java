package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.tag.TagFactory;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.Material;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockView;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.CustomStairsBlock;
import net.voxelindustry.voidheart.common.block.VoidLampBlock;
import net.voxelindustry.voidheart.common.block.VoidMonolithBlock;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarBlock;
import net.voxelindustry.voidheart.common.content.door.VoidDoorBlock;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartBlock;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import net.voxelindustry.voidheart.common.content.permeablebarrier.VoidBarrierEmitterBlock;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreBlock;
import net.voxelindustry.voidheart.common.content.portalframe.VoidStoneBricksBlock;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalImmersiveInteriorBlock;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorBlock;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullBlock;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullItemBlock;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarBlock;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    public static Tag<Block> PORTAL_INTERIOR_TAG = TagFactory.BLOCK.create(new Identifier(MODID, "portal_interior"));
    public static Tag<Block> PORTAL_FRAME_TAG    = TagFactory.BLOCK.create(new Identifier(MODID, "portal_frame"));

    public static Block PORTAL_INTERIOR;
    public static Block PORTAL_IMMERSIVE_INTERIOR;
    public static Block PORTAL_FRAME;
    public static Block PORTAL_FRAME_CORE;

    public static Block POCKET_WALL;
    public static Block VOIDSTONE;
    public static Block VOIDSTONE_POLISHED;
    public static Block VOIDSTONE_BRICKS;
    public static Block VOIDSTONE_BRICKS_VERTICAL;
    public static Block VOIDSTONE_BRICKS_CRACKED;
    public static Block VOIDSTONE_CHISELED;
    public static Block VOIDSTONE_TILE;
    public static Block VOIDSTONE_TILE_SMALL;
    public static Block VOIDSTONE_TILE_SMALL_CARVED;
    public static Block VOIDSTONE_TILE_SMALL_CHISELED;
    public static Block ELDRITCH_VOIDSTONE;
    public static Block VOIDSTONE_WRITHING;
    public static Block VOIDSTONE_WRITHING_BLOSSOM;
    public static Block VOIDSTONE_WRITHING_MAW;
    public static Block VOIDSTONE_PILLAR;
    public static Block VOIDSTONE_WEATHERED_PILLAR;

    public static Block VOID_LAMP;

    public static Block VOID_ALTAR;
    public static Block VOID_PILLAR;

    public static Block VOID_HEART;

    public static Block VOID_MONOLITH;
    public static Block VOID_MONOLITH_CAP;

    public static Block VOID_DOOR;
    public static Block PERMEABLE_BARRIER;

    public static Block VOID_BARRIER_EMITTER;
    public static Block VOID_BARRIER;

    public static Block EXPERIENCE_SKULL;
    public static Block MENDING_ALTAR;

    public static void registerBlocks()
    {
        Settings itemGroup = new Item.Settings().group(VoidHeart.ITEMGROUP);
        Settings noGroup = new Item.Settings();

        registerBlock(PORTAL_INTERIOR = new PortalInteriorBlock(), noGroup, "portal_interior");
        registerBlock(PORTAL_IMMERSIVE_INTERIOR = new PortalImmersiveInteriorBlock(), noGroup, "portal_immersive_interior");
        registerBlock(PORTAL_FRAME = new PortalFrameBlock(), noGroup, "portal_frame");
        registerBlock(PORTAL_FRAME_CORE = new PortalFrameCoreBlock(), noGroup, "portal_frame_core");

        registerBlock(VOID_ALTAR = new VoidAltarBlock(), itemGroup, "void_altar");
        registerBlock(VOID_PILLAR = new VoidPillarBlock(), itemGroup, "void_pillar");

        registerBlock(VOID_HEART = new VoidHeartBlock(), noGroup, "void_heart_block");

        registerBlock(POCKET_WALL = new Block(
                FabricBlockSettings
                        .of(Material.STONE)
                        .strength(-1.0F, 3600000.0F)
                        .dropsNothing()
                        .allowsSpawning((state, world, pos, type) -> false)), noGroup, "pocket_wall");

        registerBlock(VOIDSTONE = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(2F, 6.0F)), itemGroup, "voidstone");

        registerBlock(VOIDSTONE_POLISHED = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_polished");

        registerBlock(VOIDSTONE_BRICKS = new VoidStoneBricksBlock(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_bricks");
        registerBlock(VOIDSTONE_BRICKS_CRACKED = new VoidStoneBricksBlock(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_bricks_cracked");
        registerBlock(VOIDSTONE_CHISELED = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_chiseled");

        registerBlock(VOIDSTONE_BRICKS_VERTICAL = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_bricks_vertical");

        registerBlock(VOIDSTONE_TILE = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_tile");
        registerBlock(VOIDSTONE_TILE_SMALL = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_tile_small");
        registerBlock(VOIDSTONE_TILE_SMALL_CARVED = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_tile_small_carved");
        registerBlock(VOIDSTONE_TILE_SMALL_CHISELED = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_tile_small_chiseled");

        registerBlock(ELDRITCH_VOIDSTONE = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "eldritch_voidstone");

        registerBlock(VOIDSTONE_WRITHING = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_writhing");
        registerBlock(VOIDSTONE_WRITHING_BLOSSOM = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_writhing_blossom");
        registerBlock(VOIDSTONE_WRITHING_MAW = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_writhing_maw");

        registerBlock(VOIDSTONE_PILLAR = new PillarBlock(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_pillar");
        registerBlock(VOIDSTONE_WEATHERED_PILLAR = new PillarBlock(
                FabricBlockSettings
                        .of(Material.STONE, MapColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_pillar_weathered");

        registerBlock(VOID_MONOLITH = new VoidMonolithBlock(), itemGroup, "void_monolith");

        registerBlock(VOID_LAMP = new VoidLampBlock(), itemGroup, "void_lamp");

        registerBlock(VOID_DOOR = new VoidDoorBlock(), itemGroup, "void_door");

        registerBlock(PERMEABLE_BARRIER = new PermeableBarrierBlock(), itemGroup, "permeable_barrier");

        registerBlock(VOID_BARRIER_EMITTER = new VoidBarrierEmitterBlock(), itemGroup, "void_barrier_emitter");
        //  registerBlock(VOID_BARRIER = new VoidBarrierBlock(), itemGroup, "void_barrier");

        registerBlock(EXPERIENCE_SKULL = new ExperienceSkullBlock(FabricBlockSettings
                .of(Material.STONE)
                .strength(1.5F, 6.0F)
                .sounds(BlockSoundGroup.STONE)), new ExperienceSkullItemBlock(EXPERIENCE_SKULL), "experience_skull");
        registerBlock(MENDING_ALTAR = new MendingAltarBlock(FabricBlockSettings
                .of(Material.STONE)
                .strength(1.5F, 6.0F)
                .sounds(BlockSoundGroup.STONE)), itemGroup, "mending_altar");

        generateStairs(VOIDSTONE, itemGroup, "voidstone");
        generateWall(VOIDSTONE, itemGroup, "voidstone");
        generateSlab(VOIDSTONE, itemGroup, "voidstone");

        generateStairs(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateWall(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateSlab(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");

        generateStairs(VOIDSTONE_BRICKS_CRACKED, itemGroup, "voidstone_bricks_cracked");
        generateWall(VOIDSTONE_BRICKS_CRACKED, itemGroup, "voidstone_bricks_cracked");
        generateSlab(VOIDSTONE_BRICKS_CRACKED, itemGroup, "voidstone_bricks_cracked");

        generateStairs(VOIDSTONE_TILE_SMALL, itemGroup, "voidstone_tile_small");
        generateSlab(VOIDSTONE_TILE_SMALL, itemGroup, "voidstone_tile_small");
    }

    private static void registerBlock(Block block, Item.Settings settings, String name)
    {
        BlockItem itemBlock = new BlockItem(block, settings);
        registerBlock(block, itemBlock, name);
    }

    private static void registerBlock(Block block, BlockItem itemBlock, String name)
    {
        Identifier identifier = new Identifier(MODID, name);
        Registry.register(Registry.BLOCK, identifier, block);
        Registry.register(Registry.ITEM, identifier, itemBlock);
    }

    private static void generateStairs(Block block, Item.Settings builder, String name)
    {
        registerBlock(new CustomStairsBlock(block.getDefaultState(), AbstractBlock.Settings.copy(block)), builder, name + "_stairs");
    }

    private static void generateWall(Block block, Item.Settings builder, String name)
    {
        registerBlock(new WallBlock(AbstractBlock.Settings.copy(block)), builder, name + "_wall");
    }

    private static void generateSlab(Block block, Item.Settings builder, String name)
    {
        registerBlock(new SlabBlock(AbstractBlock.Settings.copy(block)), builder, name + "_slab");
    }

    public static Boolean never(BlockState state, BlockView world, BlockPos pos, EntityType<?> type)
    {
        return false;
    }
}
