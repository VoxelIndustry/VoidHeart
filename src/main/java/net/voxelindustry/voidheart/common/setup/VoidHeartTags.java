package net.voxelindustry.voidheart.common.setup;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

public class VoidHeartTags
{
    public static TagKey<Block> PORTAL_INTERIOR_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "portal_interior"));

    public static TagKey<Block> PORTAL_FRAME_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "portal_frame"));

    public static TagKey<Block> ASPECT_STILLNESS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "aspect_stillness"));
    public static TagKey<Block> ASPECT_FIERY_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "aspect_fiery"));

    /**
     * Flasks and bottles that can be set down on a block by sneaking with one in hand.
     */
    public static final TagKey<Item> PLACEABLE_FLASKS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "placeable_flasks"));

    public static final TagKey<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "pillar_place_instead_of_store"));

    /**
     * Items a player can come across in the world, so the research that also makes them never hides them.
     */
    public static final TagKey<Item> FOUND_IN_WORLD = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "found_in_world"));

    public static final TagKey<Item> KNOWLEDGE_FAINT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/faint"));
    public static final TagKey<Item> KNOWLEDGE_LUCID = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/lucid"));
    public static final TagKey<Item> KNOWLEDGE_PROFOUND = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/profound"));
    public static final TagKey<Item> KNOWLEDGE_TRANSCENDENT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/transcendent"));

    /**
     * Mobs that salt burns as they cross it.
     */
    public static final TagKey<EntityType<?>> SALT_BURNED = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "salt_burned"));
    public static final TagKey<EntityType<?>> TORMENTER_REFUSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "tormenter_refuses"));

    /**
     * Every hostile mob of the mod, without projectiles, stains or the shoulder eye.
     */
    public static final TagKey<EntityType<?>> MOBS = mobTag("mobs");

    public static final TagKey<EntityType<?>> MOBS_VOID_DUNGEON = mobTag("mobs/void_dungeon");
    public static final TagKey<EntityType<?>> MOBS_DAEDALUS = mobTag("mobs/daedalus");

    public static final TagKey<EntityType<?>> MOBS_FALLEN = mobTag("mobs/fallen");
    public static final TagKey<EntityType<?>> MOBS_WRAITHS = mobTag("mobs/wraiths");
    public static final TagKey<EntityType<?>> MOBS_PESTS = mobTag("mobs/pests");
    public static final TagKey<EntityType<?>> MOBS_CRABS = mobTag("mobs/crabs");
    public static final TagKey<EntityType<?>> MOBS_EYES = mobTag("mobs/eyes");
    public static final TagKey<EntityType<?>> MOBS_CONSTRUCTS = mobTag("mobs/constructs");
    public static final TagKey<EntityType<?>> MOBS_UNDEAD = mobTag("mobs/undead");
    public static final TagKey<EntityType<?>> MOBS_BOSSES = mobTag("mobs/bosses");

    public static final TagKey<Structure> LEY_MINOR_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_minor_anchor"));
    public static final TagKey<Structure> LEY_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_anchor"));
    public static final TagKey<Structure> LEY_MAJOR_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_major_anchor"));

    private static TagKey<EntityType<?>> mobTag(String path)
    {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, path));
    }
}
