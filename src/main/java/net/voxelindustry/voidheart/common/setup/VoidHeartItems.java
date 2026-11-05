package net.voxelindustry.voidheart.common.setup;

import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.EntityType;
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
import net.voxelindustry.voidheart.common.content.research.KnowledgeTabletItem;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeItem;
import net.voxelindustry.voidheart.common.item.LoreItem;
import net.voxelindustry.voidheart.common.item.VoidAmalgamItem;
import net.voxelindustry.voidheart.common.item.VoidHeartItem;
import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class VoidHeartItems
{
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();

    public static VoidHeartItem VOID_HEART;
    public static VoidAmalgamItem VOID_AMALGAM;
    public static VoidPearlItem VOID_PEARL;
    public static VoidPearlItem LOCAL_PEARL;
    public static PortalCoreItem PORTAL_CORE;

    public static VoidKeyItem VOID_KEY;
    public static PermeableBarrierItem PERMEABLE_BARRIER;
    public static ShoulderEyeItem SHOULDER_EYE;
    public static KnowledgeTabletItem KNOWLEDGE_TABLET;

    public static Item WRIGGLING_BRAIN;

    public static Item OBSIDIAN_SHARD;
    public static Item ENDER_SHARD;
    public static Item ARROGANT_IRON_INGOT;
    public static Item RAVENOUS_GOLD_INGOT;
    public static Item SEETHING_STEEL_INGOT;
    public static Item COVETOUS_COPPER_INGOT;
    public static Item LISTLESS_LEAD_INGOT;
    public static Item AZURIUM_SHARD;
    public static Item RUBRUM_SHARD;
    public static Item MEMORIAM_SHARD;

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
        KNOWLEDGE_TABLET = registerItem("knowledge_tablet", KnowledgeTabletItem::new);

        OBSIDIAN_SHARD = registerItem("obsidian_shard", Item::new);
        ENDER_SHARD = registerItem("ender_shard", Item::new);
        ARROGANT_IRON_INGOT = registerItem("arrogant_iron_ingot", Item::new);
        RAVENOUS_GOLD_INGOT = registerItem("ravenous_gold_ingot", Item::new);
        SEETHING_STEEL_INGOT = registerItem("seething_steel_ingot", Item::new);
        COVETOUS_COPPER_INGOT = registerItem("covetous_copper_ingot", Item::new);
        LISTLESS_LEAD_INGOT = registerItem("listless_lead_ingot", Item::new);
        WRIGGLING_BRAIN = registerItem("wriggling_brain", Item::new);
        AZURIUM_SHARD = registerItem("azurium_shard", properties -> new LoreItem(properties, "voidheart.azurium_shard.lore"));
        RUBRUM_SHARD = registerItem("rubrum_shard", properties -> new LoreItem(properties, "voidheart.rubrum_shard.lore"));
        MEMORIAM_SHARD = registerItem("memoriam_shard", properties -> new LoreItem(properties, "voidheart.memoriam_shard.lore"));
    }

    public static void registerSpawnEggs()
    {
        registerSpawnEgg("void_skeleton", VoidHeartEntities.VOID_SKELETON);
        registerSpawnEgg("void_ghoul", VoidHeartEntities.VOID_GHOUL);
        registerSpawnEgg("wraith", VoidHeartEntities.WRAITH);
        registerSpawnEgg("corrupted_crab_small", VoidHeartEntities.CORRUPTED_CRAB_SMALL);
        registerSpawnEgg("corrupted_crab_medium", VoidHeartEntities.CORRUPTED_CRAB_MEDIUM);
        registerSpawnEgg("corrupted_crab_large", VoidHeartEntities.CORRUPTED_CRAB_LARGE);
        registerSpawnEgg("corrupted_barnacle", VoidHeartEntities.CORRUPTED_BARNACLE);
        registerSpawnEgg("fallen_novice", VoidHeartEntities.FALLEN_NOVICE);
        registerSpawnEgg("fallen_adept", VoidHeartEntities.FALLEN_ADEPT);
        registerSpawnEgg("fallen_elder", VoidHeartEntities.FALLEN_ELDER);
        registerSpawnEgg("eye_maw", VoidHeartEntities.EYE_MAW);
        registerSpawnEgg("large_eye_maw", VoidHeartEntities.LARGE_EYE_MAW);
        registerSpawnEgg("arcane_eye", VoidHeartEntities.ARCANE_EYE);
    }

    private static void registerSpawnEgg(String mob, EntityType<?> type)
    {
        SPAWN_EGGS.add(registerItem(mob + "_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(type))));
    }

    public static <T extends Item> T registerItem(String name, Function<Properties, T> factory)
    {
        var identifier = Identifier.fromNamespaceAndPath(VoidHeart.MODID, name);
        var item = Registry.register(BuiltInRegistries.ITEM, identifier, factory.apply(new Properties().setId(ResourceKey.create(Registries.ITEM, identifier))));
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }
}
