package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.tag.TagFactory;
import net.minecraft.item.Item;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTags
{
    public static final Tag<Item> PILLAR_PLACE_INSTEAD_OF_STORE = TagFactory.ITEM.create(new Identifier(MODID, "pillar_place_instead_of_store"));
}
