package net.voxelindustry.voidheart.common.setup;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.door.VoidKeyItem;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierItem;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeItem;
import net.voxelindustry.voidheart.common.item.VoidAmalgamItem;
import net.voxelindustry.voidheart.common.item.VoidHeartItem;
import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class VoidHeartItems
{
    public static VoidHeartItem VOID_HEART;
    public static VoidAmalgamItem VOID_AMALGAM;
    public static VoidPearlItem VOID_PEARL;
    public static VoidPearlItem LOCAL_PEARL;
    public static PortalCoreItem PORTAL_CORE;

    public static VoidKeyItem VOID_KEY;
    public static PermeableBarrierItem PERMEABLE_BARRIER;
    public static ShoulderEyeItem SHOULDER_EYE;

    public static Item OBSIDIAN_SHARD;
    public static Item ENDER_SHARD;
    public static Item ARROGANT_IRON_INGOT;
    public static Item RAVENOUS_GOLD_INGOT;

    public static final List<Item> CREATIVE_TAB_ITEMS = new ArrayList<>();

    public static void registerItems()
    {
        VOID_HEART = registerItem("void_heart", VoidHeartItem::new);
        VOID_AMALGAM = registerItem("void_amalgam", VoidAmalgamItem::new);
        VOID_PEARL = registerItem("void_pearl", VoidPearlItem::new);

        PORTAL_CORE = registerItem("portal_core", PortalCoreItem::new);

        LOCAL_PEARL = registerItem("local_pearl", VoidPearlItem::new);

        VOID_KEY = registerItem("void_key", VoidKeyItem::new);
        PERMEABLE_BARRIER = registerItem("permeable_barrier_item", PermeableBarrierItem::new);
        SHOULDER_EYE = registerItem("shoulder_eye", ShoulderEyeItem::new);

        OBSIDIAN_SHARD = registerItem("obsidian_shard", Item::new);
        ENDER_SHARD = registerItem("ender_shard", Item::new);
        ARROGANT_IRON_INGOT = registerItem("arrogant_iron_ingot", Item::new);
        RAVENOUS_GOLD_INGOT = registerItem("ravenous_gold_ingot", Item::new);
    }

    public static <T extends Item> T registerItem(String name, Function<Properties, T> factory)
    {
        var identifier = Identifier.fromNamespaceAndPath(VoidHeart.MODID, name);
        var item = Registry.register(BuiltInRegistries.ITEM, identifier, factory.apply(new Properties().setId(ResourceKey.create(Registries.ITEM, identifier))));
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }
}
