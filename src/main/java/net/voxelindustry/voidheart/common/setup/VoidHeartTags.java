package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTags
{
    public static TagKey<Block> PORTAL_INTERIOR_TAG = TagKey.of(Registry.BLOCK_KEY, new Identifier(MODID, "portal_interior"));

    public static TagKey<Block> PORTAL_FRAME_TAG = TagKey.of(Registry.BLOCK_KEY, new Identifier(MODID, "portal_frame"));

    public static final TagKey<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagKey.of(Registry.ITEM_KEY, new Identifier(MODID, "pillar_place_instead_of_store"));
}
