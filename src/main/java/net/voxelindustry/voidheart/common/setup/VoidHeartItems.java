package net.voxelindustry.voidheart.common.setup;

import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.item.VoidAmalgamItem;
import net.voxelindustry.voidheart.common.item.VoidHeartItem;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;

public class VoidHeartItems
{
    public static VoidHeartItem   VOID_HEART;
    public static VoidAmalgamItem VOID_AMALGAM;
    public static VoidPearlItem   VOID_PEARL;

    public static Item OBSIDIAN_SHARD;
    public static Item ENDER_SHARD;

    public static void registerItems()
    {
        registerItem(VOID_HEART = new VoidHeartItem(), new Identifier(VoidHeart.MODID, "void_heart"));
        registerItem(VOID_PEARL = new VoidPearlItem(), new Identifier(VoidHeart.MODID, "void_pearl"));
        registerItem(VOID_AMALGAM = new VoidAmalgamItem(), new Identifier(VoidHeart.MODID, "void_amalgam"));

        registerItem(OBSIDIAN_SHARD = new Item(new Settings().group(VoidHeart.ITEMGROUP)), new Identifier(VoidHeart.MODID, "obsidian_shard"));
        registerItem(ENDER_SHARD = new Item(new Settings().group(VoidHeart.ITEMGROUP)), new Identifier(VoidHeart.MODID, "ender_shard"));
    }

    public static void registerItem(Item item, Identifier name)
    {
        Registry.register(Registry.ITEM, name, item);
    }
}
