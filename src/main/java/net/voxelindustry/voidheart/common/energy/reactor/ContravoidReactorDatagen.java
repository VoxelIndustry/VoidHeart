package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for W10 Contravoid reactor, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * The core's cage and the containment's four slit models are hand-written in assets/voidheart/models/block, since they are open.
 */
public final class ContravoidReactorDatagen
{
    private ContravoidReactorDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE,
                plainVariant(model("block/contravoid_reactor_core"))));

        var top = texture("reactor_containment_top");
        var containment = ModelTemplates.CUBE_BOTTOM_TOP.create(ContravoidReactorContent.REACTOR_CONTAINMENT, new TextureMapping()
                .put(TextureSlot.SIDE, texture("reactor_containment"))
                .put(TextureSlot.TOP, top)
                .put(TextureSlot.BOTTOM, top), generators.modelOutput);
        var slits = PropertyDispatch.initial(ReactorContainmentBlock.SLIT);
        for (var slit : Slit.values())
            slits = slits.select(slit, plainVariant(slit == Slit.NONE ? containment : model("block/reactor_containment_slit_" + slit.getSerializedName())));
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(ContravoidReactorContent.REACTOR_CONTAINMENT).with(slits));

        var intake = ModelTemplates.CUBE_BOTTOM_TOP.create(ContravoidReactorContent.REACTOR_INTAKE, new TextureMapping()
                .put(TextureSlot.SIDE, texture("reactor_intake"))
                .put(TextureSlot.TOP, top)
                .put(TextureSlot.BOTTOM, top), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(ContravoidReactorContent.REACTOR_INTAKE, plainVariant(intake)));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE.asItem(), ItemModelUtils.plainModel(model("block/contravoid_reactor_core")));
        generators.itemModelOutput.accept(ContravoidReactorContent.REACTOR_CONTAINMENT.asItem(), ItemModelUtils.plainModel(model("block/reactor_containment")));
        generators.itemModelOutput.accept(ContravoidReactorContent.REACTOR_INTAKE.asItem(), ItemModelUtils.plainModel(model("block/reactor_intake")));
    }

    /**
     * The core is an altar recipe around a large azurium bundle, so a reactor costs more than a row of engines. The containment comes eight at a time
     * from voidstone and gold, the intakes from a relay's parts around an ender shard.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        var output = recipes.output();
        var voidstone = VoidHeartBlocks.VOIDSTONE_POLISHED;
        var gold = VoidHeartItems.RAVENOUS_GOLD_INGOT;

        provider.shaped(RecipeCategory.MISC, ContravoidReactorContent.REACTOR_CONTAINMENT, 8)
                .pattern("VVV").pattern("GOG").pattern("VVV")
                .define('V', voidstone)
                .define('G', gold)
                .define('O', net.minecraft.world.level.block.Blocks.OBSIDIAN)
                .unlockedBy(provider.getHasName(gold), provider.has(gold))
                .save(output);

        provider.shaped(RecipeCategory.MISC, ContravoidReactorContent.REACTOR_INTAKE)
                .pattern("VGV").pattern("GEG").pattern("VGV")
                .define('V', voidstone)
                .define('G', gold)
                .define('E', VoidHeartItems.ENDER_SHARD)
                .unlockedBy(provider.getHasName(gold), provider.has(gold))
                .save(output);

        recipes.altar("contravoid_reactor_core", ContravoidReactorContent.CONTRAVOID_REACTOR_CORE, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE,
                gold, gold, gold, gold,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.AZURIUM_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE);
        loot.dropSelf(ContravoidReactorContent.REACTOR_CONTAINMENT);
        // The intake's getDrops keeps its fuel on the item, this table stands for the plain drop
        loot.dropSelf(ContravoidReactorContent.REACTOR_INTAKE);
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Material texture(String name)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + name));
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
