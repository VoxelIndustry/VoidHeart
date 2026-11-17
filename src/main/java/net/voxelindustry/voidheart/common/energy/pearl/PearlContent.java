package net.voxelindustry.voidheart.common.energy.pearl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.item.LoreItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for the Collapsing Rubrum Pearls, called once by EnergySetup since they grow on void energy. A burning reactor throws off a
 * fractal seed, the Shatter Forge kindles it with Fiery, and the kindled seed hangs from a chain as a pearl that grows until it can be picked.
 * <p>
 * The hanging pearl has no item of its own: the kindled seed places it, and it drops as the seed again until it has matured.
 */
public final class PearlContent
{
    /**
     * Light a hanging pearl gives at each stage of its growth.
     */
    private static final int[] LIGHT = {1, 2, 3, 4, 5};

    public static Item  FRACTAL_RUBRUM_SEED;
    public static Item  KINDLED_FRACTAL_SEED;
    public static Item  COLLAPSING_RUBRUM_PEARL;
    public static Block HANGING_RUBRUM_PEARL;

    public static BlockEntityType<HangingPearlTile> HANGING_PEARL_TILE;

    /**
     * Ticks of growth a kindled seed carries once its pearl was taken down unfinished.
     */
    public static DataComponentType<Double> PEARL_GROWTH;

    private PearlContent()
    {
    }

    public static void register()
    {
        PEARL_GROWTH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MODID, "pearl_growth"),
                DataComponentType.<Double>builder()
                        .persistent(Codec.DOUBLE)
                        .networkSynchronized(ByteBufCodecs.DOUBLE)
                        .build());

        var blockId = Identifier.fromNamespaceAndPath(MODID, "hanging_rubrum_pearl");
        HANGING_RUBRUM_PEARL = Registry.register(BuiltInRegistries.BLOCK, blockId, new HangingPearlBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, blockId))
                .mapColor(MapColor.COLOR_RED)
                .strength(1.5F)
                .noOcclusion()
                .lightLevel(state -> LIGHT[state.getValue(HangingPearlBlock.STAGE)])
                .pushReaction(PushReaction.POPPED)
                .sound(SoundType.AMETHYST_CLUSTER)));

        FRACTAL_RUBRUM_SEED = VoidHeartItems.registerItem("fractal_rubrum_seed",
                properties -> new LoreItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON), "voidheart.fractal_rubrum_seed.lore"));
        KINDLED_FRACTAL_SEED = VoidHeartItems.registerItem("kindled_fractal_seed",
                properties -> new KindledSeedItem(HANGING_RUBRUM_PEARL, properties.stacksTo(16).rarity(Rarity.RARE)));
        COLLAPSING_RUBRUM_PEARL = VoidHeartItems.registerItem("collapsing_rubrum_pearl",
                properties -> new LoreItem(properties.stacksTo(16).rarity(Rarity.EPIC), "voidheart.collapsing_rubrum_pearl.lore"));

        VoidHeartTiles.registerTile(HANGING_PEARL_TILE = FabricBlockEntityTypeBuilder.create(HangingPearlTile::new, HANGING_RUBRUM_PEARL).build(),
                "hanging_rubrum_pearl");
    }

    /**
     * Adds /voidheart energy pearl under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        PearlCommands.register(energy);
    }
}
