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
 * Datagen for the Void Armor, handed off through EnergyDatagen since its base charges from the energy grid. The base's plinth is hand-written in
 * assets/voidheart/models/block, and a docked frame is drawn standing on it by VoidArmorBaseRender.
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
        // The plinth is round about, the frame's space is drawn by VoidArmorBaseRender, and the arch's parts turn with the way it faces, the right
        // pillar half round again so it faces the middle
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidArmorContent.VOID_ARMOR_BASE).with(
                PropertyDispatch.initial(VoidArmorBaseBlock.PART, VoidArmorBaseBlock.FACING, VoidArmorBaseBlock.RIGHT).generate((part, facing, right) ->
                {
                    var variant = plainVariant(model(switch (part)
                    {
                        case BASE -> "block/void_armor_base";
                        case FRAME -> "block/void_armor_arch_space";
                        default -> "block/void_armor_arch_" + part.getSerializedName();
                    }));
                    if (part == VoidArmorBaseBlock.Part.BASE || part == VoidArmorBaseBlock.Part.FRAME)
                        return variant;
                    int quarters = (switch (facing)
                    {
                        case EAST -> 1;
                        case SOUTH -> 2;
                        case WEST -> 3;
                        default -> 0;
                    } + (right ? 2 : 0)) % 4;
                    return switch (quarters)
                    {
                        case 1 -> variant.with(BlockModelGenerators.Y_ROT_90);
                        case 2 -> variant.with(BlockModelGenerators.Y_ROT_180);
                        case 3 -> variant.with(BlockModelGenerators.Y_ROT_270);
                        default -> variant;
                    };
                })));
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
