package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.EnumMap;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Void Armor's base, frame, modules and the data the frame carries, registered once at startup.
 */
public final class VoidArmorContent
{
    public static Block         VOID_ARMOR_BASE;
    public static VoidArmorItem VOID_ARMOR;
    public static final Map<ArmorModule, ArmorModuleItem> MODULES = new EnumMap<>(ArmorModule.class);

    public static BlockEntityType<VoidArmorBaseTile> VOID_ARMOR_BASE_TILE;

    public static ExtendedMenuType<VoidArmorMenu, BlockPos> VOID_ARMOR_MENU;

    /** The modules set in the frame's grids. */
    public static DataComponentType<ArmorGrids>            ARMOR_GRIDS;
    /** The frame's four racks, then its apparatus socket. */
    public static DataComponentType<ItemContainerContents> ARMOR_SLOTS;

    public static final TagKey<Item> RACK_WEAPONS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "armor_rack_weapons"));

    private VoidArmorContent()
    {
    }

    public static void register()
    {
        ARMOR_GRIDS = component("armor_grids", ArmorGrids.CODEC);
        ARMOR_SLOTS = component("armor_slots", ItemContainerContents.CODEC);

        VOID_ARMOR_BASE = VoidHeartBlocks.registerBlock("void_armor_base", VoidArmorBaseBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 1200F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.NETHERITE_BLOCK), true);
        VoidHeartTiles.registerTile(VOID_ARMOR_BASE_TILE = FabricBlockEntityTypeBuilder.create(VoidArmorBaseTile::new, VOID_ARMOR_BASE).build(),
                "void_armor_base");

        VOID_ARMOR = VoidHeartItems.registerItem("void_armor", VoidArmorItem::new);
        VoidHeartItems.addCreativeVariant(VOID_ARMOR, () -> VoidArmorItem.full(VOID_ARMOR));
        for (var module : ArmorModule.values())
            MODULES.put(module, VoidHeartItems.registerItem(module.itemName(), properties -> new ArmorModuleItem(properties, module)));

        VOID_ARMOR_MENU = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MODID, "void_armor"),
                new ExtendedMenuType<>(VoidArmorMenu::new, BlockPos.STREAM_CODEC));
    }

    private static <T> DataComponentType<T> component(String name, Codec<T> codec)
    {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MODID, name),
                DataComponentType.<T>builder()
                        .persistent(codec)
                        .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec))
                        .build());
    }
}
