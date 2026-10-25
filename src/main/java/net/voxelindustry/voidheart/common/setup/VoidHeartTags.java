package net.voxelindustry.voidheart.common.setup;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class VoidHeartTags
{
    public static TagKey<Block> PORTAL_INTERIOR_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "portal_interior"));

    public static TagKey<Block> PORTAL_FRAME_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "portal_frame"));

    public static TagKey<Block> ESSENCE_WATER_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "essence_water"));
    public static TagKey<Block> ESSENCE_LAVA_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "essence_lava"));

    public static final TagKey<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "pillar_place_instead_of_store"));
}
