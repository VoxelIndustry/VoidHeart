package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen for the Void Armor, handed off through EnergyDatagen since its base charges from the energy grid. The base's plinth and the stand it grows when
 * a frame is docked are hand-written in assets/voidheart/models/block, until the frame gets its own model.
 */
public final class VoidArmorDatagen
{
    private VoidArmorDatagen()
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidArmorContent.VOID_ARMOR_BASE).with(
                PropertyDispatch.initial(VoidArmorBaseBlock.DOCKED)
                        .select(false, plainVariant(model("block/void_armor_base")))
                        .select(true, plainVariant(model("block/void_armor_base_docked")))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(VoidArmorContent.VOID_ARMOR_BASE.asItem(), ItemModelUtils.plainModel(model("block/void_armor_base")));
        generators.generateFlatItem(VoidArmorContent.VOID_ARMOR, ModelTemplates.FLAT_ITEM);
        for (var item : VoidArmorContent.MODULES.values())
            generators.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(VoidArmorContent.VOID_ARMOR_BASE);
    }
}
