package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.MaterialColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockView;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.CustomStairsBlock;
import net.voxelindustry.voidheart.common.block.VoidMonolithBlock;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarBlock;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartBlock;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameBlock;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreBlock;
import net.voxelindustry.voidheart.common.content.portalframe.VoidStoneBricksBlock;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalImmersiveInteriorBlock;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorBlock;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    public static Block PORTAL_INTERIOR;
    public static Block PORTAL_IMMERSIVE_INTERIOR;
    public static Block PORTAL_FRAME;
    public static Block PORTAL_FRAME_CORE;

    public static Block POCKET_WALL;
    public static Block VOIDSTONE;
    public static Block VOIDSTONE_BRICKS;
    public static Block ELDRITCH_VOIDSTONE;

    public static Block VOID_ALTAR;
    public static Block VOID_PILLAR;

    public static Block VOID_HEART;

    public static Block VOID_MONOLITH;
    public static Block VOID_MONOLITH_CAP;

    public static Block PERMEABLE_BARRIER;

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
                        .of(Material.STONE, MaterialColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone");

        registerBlock(VOIDSTONE_BRICKS = new VoidStoneBricksBlock(
                FabricBlockSettings
                        .of(Material.STONE, MaterialColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_bricks");

        registerBlock(ELDRITCH_VOIDSTONE = new Block(
                FabricBlockSettings
                        .of(Material.STONE, MaterialColor.BLACK)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool().strength(1.5F, 6.0F)), itemGroup, "eldritch_voidstone");

        registerBlock(VOID_MONOLITH = new VoidMonolithBlock(), itemGroup, "void_monolith");
        registerBlock(VOID_MONOLITH_CAP = new Block(
                FabricBlockSettings
                        .of(Material.STONE)
                        .strength(1.5F, 6.0F)
                        .sounds(BlockSoundGroup.STONE)
                        .requiresTool()), itemGroup, "void_monolith_cap");

        registerBlock(PERMEABLE_BARRIER = new PermeableBarrierBlock(), itemGroup, "permeable_barrier");

        generateStairs(VOIDSTONE, itemGroup, "voidstone");
        generateWall(VOIDSTONE, itemGroup, "voidstone");
        generateSlab(VOIDSTONE, itemGroup, "voidstone");

        generateStairs(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateWall(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateSlab(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
    }

    private static void registerBlock(Block block, Item.Settings settings, String name)
    {
        Identifier identifier = new Identifier(MODID, name);

        Registry.register(Registry.BLOCK, identifier, block);
        BlockItem itemBlock = new BlockItem(block, settings);
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
