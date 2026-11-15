package net.voxelindustry.voidheart.data.provider;

import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.client.render.sin.SinClientModels;
import net.voxelindustry.voidheart.client.render.sin.SinFlareProperty;
import net.voxelindustry.voidheart.client.render.sin.SinTridentRender;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Sin weapons are drawn the way vanilla draws trimmed armour: the item definition selects on the {@code voidheart:sin} component, and each case is the
 * vanilla model with a second layer, an inlay mask recoloured per sin by the paletted atlas source in {@code assets/minecraft/atlases/items.json}.
 * <p>
 * The definitions replace vanilla's for these items, with the vanilla tree kept as the fallback.
 */
public final class SinInlayModels
{
    public static final String FLARE = "flare";

    private static final List<Item> SWORDS = List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
    private static final List<Item> AXES   = List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
    private static final List<Item> SPEARS = List.of(Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.GOLDEN_SPEAR, Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR);

    private static final List<ItemDisplayContext> FLAT_CONTEXTS = List.of(ItemDisplayContext.GUI, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED, ItemDisplayContext.ON_SHELF);

    private final ItemModelGenerators generator;
    private final Set<Identifier>     written = new HashSet<>();

    private SinInlayModels(ItemModelGenerators generator)
    {
        this.generator = generator;
    }

    /**
     * Picks, for one look (a sin, the flare, or none), the model standing for a vanilla model inlaid with a mask.
     */
    private interface Look
    {
        ItemModel.Unbaked model(Identifier vanilla, String mask);

        /**
         * The trident's 3D model in hand, which carries its own inlay.
         */
        SpecialModelRenderer.Unbaked<?> trident();
    }

    private interface Tree
    {
        ItemModel.Unbaked build(Look look);
    }

    public static void generate(ItemModelGenerators generator)
    {
        SinClientModels.register();
        new SinInlayModels(generator).generateAll();
    }

    private void generateAll()
    {
        for (var item : SWORDS)
            accept(item, look -> look.model(model(item), "sword"));
        for (var item : AXES)
            accept(item, look -> look.model(model(item), "axe"));
        accept(Items.MACE, look -> look.model(model(Items.MACE), "mace"));

        for (var item : SPEARS)
            generator.itemModelOutput.accept(item, sinned(look -> ItemModelUtils.select(new DisplayContext(),
                    look.model(model(item, "_in_hand"), "spear_in_hand"),
                    ItemModelUtils.when(FLAT_CONTEXTS, look.model(model(item), "spear")))), new ClientItem.Properties(true, false, 1.95F));

        accept(Items.TRIDENT, look -> ItemModelUtils.select(new DisplayContext(),
                ItemModelUtils.conditional(TridentSpecialRenderer.DEFAULT_TRANSFORMATION, ItemModelUtils.isUsingItem(),
                        ItemModelUtils.specialModel(model(Items.TRIDENT, "_throwing"), look.trident()),
                        ItemModelUtils.specialModel(model(Items.TRIDENT, "_in_hand"), look.trident())),
                ItemModelUtils.when(FLAT_CONTEXTS, look.model(model(Items.TRIDENT), "trident"))));

        accept(Items.BOW, look -> ItemModelUtils.conditional(ItemModelUtils.isUsingItem(),
                ItemModelUtils.rangeSelect(new UseDuration(false), 0.05F,
                        look.model(model(Items.BOW, "_pulling_0"), "bow_pulling_0"),
                        ItemModelUtils.override(look.model(model(Items.BOW, "_pulling_1"), "bow_pulling_1"), 0.65F),
                        ItemModelUtils.override(look.model(model(Items.BOW, "_pulling_2"), "bow_pulling_2"), 0.9F)),
                look.model(model(Items.BOW), "bow")));

        accept(Items.CROSSBOW, look -> ItemModelUtils.select(new Charge(),
                ItemModelUtils.conditional(ItemModelUtils.isUsingItem(),
                        ItemModelUtils.rangeSelect(new CrossbowPull(),
                                look.model(model(Items.CROSSBOW, "_pulling_0"), "crossbow"),
                                ItemModelUtils.override(look.model(model(Items.CROSSBOW, "_pulling_1"), "crossbow"), 0.58F),
                                ItemModelUtils.override(look.model(model(Items.CROSSBOW, "_pulling_2"), "crossbow"), 1.0F)),
                        look.model(model(Items.CROSSBOW), "crossbow")),
                ItemModelUtils.when(CrossbowItem.ChargeType.ARROW, look.model(model(Items.CROSSBOW, "_arrow"), "crossbow")),
                ItemModelUtils.when(CrossbowItem.ChargeType.ROCKET, look.model(model(Items.CROSSBOW, "_firework"), "crossbow"))));
    }

    private static Identifier model(Item item)
    {
        return ModelLocationUtils.getModelLocation(item);
    }

    private static Identifier model(Item item, String suffix)
    {
        return ModelLocationUtils.getModelLocation(item, suffix);
    }

    private void accept(Item item, Tree tree)
    {
        generator.itemModelOutput.accept(item, sinned(tree));
    }

    /**
     * The vanilla tree when the weapon carries no sin, and for each sin the same tree inlaid, white-hot while it flares.
     */
    private ItemModel.Unbaked sinned(Tree tree)
    {
        var plain = tree.build(new Look()
        {
            @Override
            public ItemModel.Unbaked model(Identifier vanilla, String mask)
            {
                return ItemModelUtils.plainModel(vanilla);
            }

            @Override
            public SpecialModelRenderer.Unbaked<?> trident()
            {
                return new TridentSpecialRenderer.Unbaked();
            }
        });

        var cases = new ArrayList<SelectItemModel.SwitchCase<Sin>>();
        for (var sin : Sin.values())
        {
            var inlaid = tree.build(inlaid(sin.getSerializedName()));
            var flaring = tree.build(inlaid(FLARE));
            cases.add(ItemModelUtils.when(sin, ItemModelUtils.conditional(new SinFlareProperty(), flaring, inlaid)));
        }
        return ItemModelUtils.select(new ComponentContents<>(VoidHeartDataComponents.SIN), plain, cases);
    }

    private Look inlaid(String palette)
    {
        return new Look()
        {
            @Override
            public ItemModel.Unbaked model(Identifier vanilla, String mask)
            {
                return ItemModelUtils.plainModel(variant(vanilla, mask, palette));
            }

            @Override
            public SpecialModelRenderer.Unbaked<?> trident()
            {
                return new SinTridentRender.Unbaked();
            }
        };
    }

    /**
     * The vanilla model as parent keeps its texture and display, so a resource pack retexturing the weapon keeps working, and the inlay is its second layer.
     */
    private Identifier variant(Identifier vanilla, String mask, String palette)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, "item/sin/" + vanilla.getPath().substring("item/".length()) + "_" + palette);
        if (written.add(id))
        {
            var template = new ModelTemplate(Optional.of(vanilla), Optional.empty(), TextureSlot.LAYER1);
            var inlay = new Material(Identifier.fromNamespaceAndPath(MODID, "sin/" + mask + "_" + palette));
            template.create(id, new TextureMapping().put(TextureSlot.LAYER1, inlay), generator.modelOutput);
        }
        return id;
    }
}
