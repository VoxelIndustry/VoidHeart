package net.voxelindustry.voidheart.common.setup;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.item.VoidAmalgamItem;
import net.voxelindustry.voidheart.common.item.VoidHeartItem;
import net.voxelindustry.voidheart.common.item.VoidHeartPieceItem;

public class VoidHeartItems
{
    public static VoidHeartItem      VOID_HEART;
    public static VoidAmalgamItem    VOID_AMALGAM;
    public static VoidHeartPieceItem VOID_HEART_PIECE;

    public static void registerItems()
    {
        registerItem(VOID_HEART = new VoidHeartItem(), new Identifier(VoidHeart.MODID, "void_heart"));
        registerItem(VOID_HEART_PIECE = new VoidHeartPieceItem(), new Identifier(VoidHeart.MODID, "void_heart_piece"));
        registerItem(VOID_AMALGAM = new VoidAmalgamItem(), new Identifier(VoidHeart.MODID, "void_amalgam"));
    }

    public static void registerItem(Item item, Identifier name)
    {
        Registry.register(Registry.ITEM, name, item);
    }
}
