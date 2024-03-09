package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTags
{
    public static TagKey<Block> PORTAL_INTERIOR_TAG = TagKey.of(RegistryKeys.BLOCK, new Identifier(MODID, "portal_interior"));

    public static TagKey<Block> PORTAL_FRAME_TAG = TagKey.of(RegistryKeys.BLOCK, new Identifier(MODID, "portal_frame"));

    public static final TagKey<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagKey.of(RegistryKeys.ITEM, new Identifier(MODID, "pillar_place_instead_of_store"));
}
