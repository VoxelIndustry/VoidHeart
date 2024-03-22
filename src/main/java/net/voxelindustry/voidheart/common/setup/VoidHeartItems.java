package net.voxelindustry.voidheart.common.setup;

import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.door.VoidKeyItem;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierItem;
import net.voxelindustry.voidheart.common.item.VoidAmalgamItem;
import net.voxelindustry.voidheart.common.item.VoidHeartItem;
import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;

public class VoidHeartItems
{
    public static VoidHeartItem VOID_HEART;
    public static VoidAmalgamItem VOID_AMALGAM;
    public static VoidPearlItem VOID_PEARL;
    public static VoidPearlItem LOCAL_PEARL;
    public static PortalCoreItem PORTAL_CORE;

    public static VoidKeyItem VOID_KEY;
    public static PermeableBarrierItem PERMEABLE_BARRIER;

    public static Item OBSIDIAN_SHARD;
    public static Item ENDER_SHARD;
    public static Item ARROGANT_IRON_INGOT;

    public static void registerItems()
    {
        registerItem(VOID_HEART = new VoidHeartItem(), "void_heart");
        registerItem(VOID_AMALGAM = new VoidAmalgamItem(), "void_amalgam");
        registerItem(VOID_PEARL = new VoidPearlItem(), "void_pearl");

        registerItem(PORTAL_CORE = new PortalCoreItem(), "portal_core");

        registerItem(LOCAL_PEARL = new VoidPearlItem(), "local_pearl");

        registerItem(VOID_KEY = new VoidKeyItem(), "void_key");
        registerItem(PERMEABLE_BARRIER = new PermeableBarrierItem(), "permeable_barrier_item");

        registerItem(OBSIDIAN_SHARD = new Item(new Settings()), "obsidian_shard");
        registerItem(ENDER_SHARD = new Item(new Settings()), "ender_shard");
        registerItem(ARROGANT_IRON_INGOT = new Item(new Settings()), "arrogant_iron_ingot");
    }

    public static void registerItem(Item item, String name)
    {
        Registry.register(Registries.ITEM, new Identifier(VoidHeart.MODID, name), item);
    }
}
