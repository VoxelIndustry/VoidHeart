package net.voxelindustry.voidheart.compat.soularcana;

import net.fabricmc.fabric.api.tag.TagFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;

public class SoulArcanaCompat
{
    public static final String SOULARCANA = "soularcana";

    public static final Tag<Item> TAG_SOUL_GEMS = TagFactory.ITEM.create(new Identifier(SOULARCANA, "soul_gems"));

    public static boolean useSoulArcana()
    {
        return FabricLoader.getInstance().isModLoaded(SOULARCANA);
    }
}
