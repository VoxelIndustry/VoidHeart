package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.item.LoreBlockItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Arrays;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Crystallography: the Crystal Cradle and the habits crystals carry, registered once at startup.
 */
public final class CrystalContent
{
    public static net.minecraft.world.level.block.Block CRYSTAL_CRADLE;

    public static BlockEntityType<CrystalCradleTile> CRYSTAL_CRADLE_TILE;

    private static final int REVIEW_RADIUS = 16;

    private CrystalContent()
    {
    }

    public static void register()
    {
        CRYSTAL_CRADLE = VoidHeartBlocks.registerBlock("crystal_cradle", CrystalCradleBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6F)
                .noOcclusion()
                .sound(SoundType.STONE), (block, properties) -> new LoreBlockItem(block, properties, MODID + ".crystal_cradle.lore"));

        VoidHeartTiles.registerTile(CRYSTAL_CRADLE_TILE = FabricBlockEntityTypeBuilder.create(CrystalCradleTile::new, CRYSTAL_CRADLE).build(), "crystal_cradle");

        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(MODID, "copy_crystal_habit"), CopyCrystalHabitFunction.MAP_CODEC);

        addHabitVariants();
        CrystalImprint.register();
    }

    /**
     * Every habit a crystal can take shows in the creative tab right after the plain shard or bundle it grows from.
     */
    private static void addHabitVariants()
    {
        for (var kind : CrystalKind.values())
        {
            for (var habit : CrystalHabit.values())
            {
                if (!habit.isHabit() || !habit.takenBy(kind))
                    continue;
                VoidHeartItems.addCreativeVariant(CrystalBundles.shard(kind), () -> CrystalBundles.shards(kind, 1, habit));
                for (var size : BundleSize.values())
                    VoidHeartItems.addCreativeVariant(CrystalBundles.bundle(kind, size).asItem(), () -> CrystalBundles.bundleItem(kind, size, habit));
            }
        }
        VoidHeartItems.addCreativeVariant(LeySpikeContent.AZURIUM_SPIKE.asItem(), () ->
        {
            var spike = new net.minecraft.world.item.ItemStack(LeySpikeContent.AZURIUM_SPIKE);
            spike.set(net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.CRYSTAL_HABIT, CrystalHabit.FOULED);
            return spike;
        });
    }

    /**
     * Adds /voidheart crystal grow [habit], which finishes every cradle near the caller at once, as if the feed given had paid for it all. Without one, each
     * cradle's own feed pays, or nothing for one no ley reaches.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("crystal").then(literal("grow")
                .executes(context -> grow(context, null))
                .then(argument("habit", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(CrystalHabit.values()).filter(habit -> habit != CrystalHabit.NONE).map(CrystalHabit::getSerializedName), suggestions))
                        .executes(context -> grow(context, StringArgumentType.getString(context, "habit"))))));
    }

    private static int grow(CommandContext<CommandSourceStack> context, String habitName)
    {
        var source = context.getSource();
        CrystalHabit paidBy = null;
        if (habitName != null)
        {
            paidBy = Arrays.stream(CrystalHabit.values()).filter(habit -> habit.getSerializedName().equals(habitName.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
            if (paidBy == null || paidBy == CrystalHabit.NONE)
            {
                source.sendFailure(Component.literal("No habit called " + habitName));
                return 0;
            }
        }

        var level = source.getLevel();
        var here = BlockPos.containing(source.getPosition());
        int grown = 0;
        for (var pos : BlockPos.betweenClosed(here.offset(-REVIEW_RADIUS, -REVIEW_RADIUS, -REVIEW_RADIUS), here.offset(REVIEW_RADIUS, REVIEW_RADIUS, REVIEW_RADIUS)))
        {
            if (!(level.getBlockEntity(pos) instanceof CrystalCradleTile cradle) || !cradle.growth().isGrowing())
                continue;
            // Without a habit given, what feeds it now pays: its line if ley reaches it, nothing otherwise
            cradle.finish(paidBy != null ? paidBy : cradle.supply() > 0 ? cradle.feed() : CrystalHabit.PLAIN);
            grown++;
        }
        int count = grown;
        source.sendSuccess(() -> Component.literal("Finished " + count + " cradle(s)"), false);
        return count;
    }
}
