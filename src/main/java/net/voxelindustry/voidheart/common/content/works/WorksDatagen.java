package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Officiant's model, recipe and loot, called from the datagen providers through EnergyDatagen.
 */
public final class WorksDatagen
{
    private WorksDatagen()
    {
    }

    private static Material blockTexture(String texture)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + texture));
    }

    /**
     * An engraved eye on each side, since it watches the station it serves; chiseled on top, where the station stands.
     */
    public static void blockModels(BlockModelGenerators generators)
    {
        var model = ModelTemplates.CUBE_BOTTOM_TOP.create(WorksContent.OFFICIANT, new TextureMapping()
                .put(TextureSlot.SIDE, blockTexture("voidstone_engraved_eye"))
                .put(TextureSlot.TOP, blockTexture("voidstone_chiseled"))
                .put(TextureSlot.BOTTOM, blockTexture("voidstone_polished")), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(WorksContent.OFFICIANT, plainVariant(model)));
    }

    public static void itemModels(net.minecraft.client.data.models.ItemModelGenerators generators)
    {
        generators.generateFlatItem(WorksContent.MEMORIAM_LENS, ModelTemplates.FLAT_ITEM);
    }

    /**
     * Raised around a Void Pearl, since it reaches into the pocket, with memoriam to remember the rites it lays out.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("officiant", WorksContent.OFFICIANT, VoidHeartItems.VOID_PEARL,
                VoidHeartBlocks.VOIDSTONE_CHISELED, VoidHeartBlocks.VOIDSTONE_CHISELED, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, CrystalBundles.shard(CrystalKind.MEMORIAM), CrystalBundles.shard(CrystalKind.AZURIUM));
        // Memoriam remembers what it saw and works things out, framed in greed's copper
        recipes.altar("memoriam_lens", WorksContent.MEMORIAM_LENS, net.minecraft.world.item.Items.GLASS_PANE,
                CrystalBundles.shard(CrystalKind.MEMORIAM), CrystalBundles.shard(CrystalKind.MEMORIAM),
                VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(WorksContent.OFFICIANT);
    }
}
