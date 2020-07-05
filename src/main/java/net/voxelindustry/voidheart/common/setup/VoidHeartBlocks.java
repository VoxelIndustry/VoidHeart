package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.MaterialColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.CustomStairsBlock;
import net.voxelindustry.voidheart.common.block.PortalWallBlock;
import net.voxelindustry.voidheart.common.block.VoidAltarBlock;
import net.voxelindustry.voidheart.common.block.VoidPillarBlock;
import net.voxelindustry.voidheart.common.block.VoidPortalBlock;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    public static Block POCKET_PORTAL;
    public static Block PORTAL_WALL;

    public static Block POCKET_WALL;
    public static Block VOIDSTONE;
    public static Block VOIDSTONE_BRICKS;
    public static Block ELDRITCH_VOIDSTONE;

    public static Block VOID_ALTAR;
    public static Block VOID_PILLAR;

    public static void registerBlocks()
    {
        Settings itemGroup = new Item.Settings().group(VoidHeart.ITEMGROUP);

        registerBlock(POCKET_PORTAL = new VoidPortalBlock(), itemGroup, "portal_interior");
        registerBlock(PORTAL_WALL = new PortalWallBlock(), itemGroup, "portal_wall");

        registerBlock(VOID_ALTAR = new VoidAltarBlock(), itemGroup, "void_altar");
        registerBlock(VOID_PILLAR = new VoidPillarBlock(), itemGroup, "void_pillar");

        registerBlock(POCKET_WALL = new Block(FabricBlockSettings
                .of(Material.STONE)
                .strength(-1.0F, 3600000.0F)
                .dropsNothing()
                .allowsSpawning((state, world, pos, type) -> false)), itemGroup, "pocket_wall");

        registerBlock(VOIDSTONE = new Block(FabricBlockSettings
                .of(Material.STONE, MaterialColor.BLACK)
                .sounds(BlockSoundGroup.STONE)
                .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone");

        registerBlock(VOIDSTONE_BRICKS = new Block(FabricBlockSettings
                .of(Material.STONE, MaterialColor.BLACK)
                .sounds(BlockSoundGroup.STONE)
                .requiresTool().strength(1.5F, 6.0F)), itemGroup, "voidstone_bricks");

        registerBlock(ELDRITCH_VOIDSTONE = new Block(FabricBlockSettings
                .of(Material.STONE, MaterialColor.BLACK)
                .sounds(BlockSoundGroup.STONE)
                .requiresTool().strength(1.5F, 6.0F)), itemGroup, "eldritch_voidstone");

        generateStairs(VOIDSTONE, itemGroup, "voidstone");
        generateWall(VOIDSTONE, itemGroup, "voidstone");
        generateSlab(VOIDSTONE, itemGroup, "voidstone");

        generateStairs(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateWall(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
        generateSlab(VOIDSTONE_BRICKS, itemGroup, "voidstone_bricks");
    }

    private static void registerBlock(Block block, Item.Settings builder, String name)
    {
        Identifier identifier = new Identifier(MODID, name);

        Registry.register(Registry.BLOCK, identifier, block);
        BlockItem itemBlock = new BlockItem(block, builder);
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
}
