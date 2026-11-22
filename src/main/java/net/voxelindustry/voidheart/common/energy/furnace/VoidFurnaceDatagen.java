package net.voxelindustry.voidheart.common.energy.furnace;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for the Void Furnace, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class VoidFurnaceDatagen
{
    private VoidFurnaceDatagen()
    {
    }

    private static Material blockTexture(String texture)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + texture));
    }

    /**
     * An orientable cube whose mouth changes with its flame: cold, violet on void energy, embers on Fiery.
     */
    public static void blockModels(BlockModelGenerators generators)
    {
        var block = VoidFurnaceContent.VOID_FURNACE;
        var models = new java.util.EnumMap<VoidFurnaceBlock.Flame, Identifier>(VoidFurnaceBlock.Flame.class);
        for (var flame : VoidFurnaceBlock.Flame.values())
        {
            var suffix = flame == VoidFurnaceBlock.Flame.NONE ? "" : "_" + flame.getSerializedName();
            var mapping = new TextureMapping()
                    .put(TextureSlot.FRONT, blockTexture("void_furnace_front" + suffix))
                    .put(TextureSlot.SIDE, blockTexture("void_furnace_side"))
                    .put(TextureSlot.TOP, blockTexture("void_furnace_top"));
            models.put(flame, ModelTemplates.CUBE_ORIENTABLE.createWithSuffix(block, suffix, mapping, generators.modelOutput));
        }
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(VoidFurnaceBlock.FLAME).generate(flame -> plainVariant(models.get(flame))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(VoidFurnaceContent.VOID_FURNACE.asItem(),
                ItemModelUtils.plainModel(Identifier.fromNamespaceAndPath(MODID, "block/void_furnace")));
    }

    /**
     * Raised around a furnace: voidstone to hold the heat, azurium to carry void energy into it, and blaze rods for the fire it no longer needs.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("void_furnace", VoidFurnaceContent.VOID_FURNACE, Items.FURNACE,
                VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED,
                CrystalBundles.shard(CrystalKind.AZURIUM), CrystalBundles.shard(CrystalKind.AZURIUM), Items.BLAZE_ROD, Items.BLAZE_ROD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        // What lies inside drops from the tile, the table stands for the block itself
        loot.dropSelf(VoidFurnaceContent.VOID_FURNACE);
    }
}
