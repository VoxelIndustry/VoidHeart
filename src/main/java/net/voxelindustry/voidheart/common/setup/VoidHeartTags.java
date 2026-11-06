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

    public static final TagKey<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "pillar_place_instead_of_store"));

    public static final TagKey<Item> KNOWLEDGE_FAINT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/faint"));
    public static final TagKey<Item> KNOWLEDGE_LUCID = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/lucid"));
    public static final TagKey<Item> KNOWLEDGE_PROFOUND = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/profound"));
    public static final TagKey<Item> KNOWLEDGE_TRANSCENDENT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "knowledge/transcendent"));

    /**
     * Mobs that salt burns as they cross it.
     */
    public static final TagKey<EntityType<?>> SALT_BURNED = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "salt_burned"));
    public static final TagKey<EntityType<?>> TORMENTER_REFUSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "tormenter_refuses"));

    public static final TagKey<Structure> LEY_MINOR_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_minor_anchor"));
    public static final TagKey<Structure> LEY_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_anchor"));
    public static final TagKey<Structure> LEY_MAJOR_ANCHOR = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MODID, "ley_major_anchor"));
}
