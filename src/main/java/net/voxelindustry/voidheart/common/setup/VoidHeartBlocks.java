package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.PortalWallBlock;
import net.voxelindustry.voidheart.common.block.VoidPortalBlock;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartBlocks
{
    public static Block POCKET_PORTAL;
    public static Block PORTAL_WALL;

    public static void registerBlocks()
    {
        Settings itemGroup = new Item.Settings().group(VoidHeart.ITEMGROUP);

        registerBlock(POCKET_PORTAL = new VoidPortalBlock(), itemGroup, new Identifier(MODID, "pocket_portal"));
        registerBlock(PORTAL_WALL = new PortalWallBlock(), itemGroup, new Identifier(MODID, "portal_wall"));
    }

    private static void registerBlock(Block block, Item.Settings builder, Identifier name)
    {
        Registry.register(Registry.BLOCK, name, block);
        BlockItem itemBlock = new BlockItem(block, builder);
        Registry.register(Registry.ITEM, name, itemBlock);
    }
}
