package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CradleResearch;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundleBlock;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalContent;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Crystal Cradle and Clear azurium in a real world.
 * <ul>
 *     <li>Shards used on a cradle fill it up to a large bundle's worth, and another crystal or habit is refused.</li>
 *     <li>A cradle near a creative ley stone draws ley and grows Clear for an owner who studied it, and plain for one who didn't.</li>
 *     <li>The review command finishes it, and an empty hand takes out the bundle with its habit, or the bundle so far and the shards not yet grown.</li>
 *     <li>A grown bundle breaks into exactly the shards that grew it, whatever the fortune, and the drops keep the habit. Silk touch keeps it whole.</li>
 *     <li>A Clear bundle stays Clear when placed and when a pylon lets it go.</li>
 *     <li>Two Clear pylons arc through a wall that stops two plain ones, and a Clear pylon goes dark in a wound's seep while a plain one stays.</li>
 *     <li>Tooltips name the habit and its changes plainly.</li>
 * </ul>
 * Screenshots show the cradles at each stage, Clear crystal beside plain by day and night, and the items in hand and in the inventory.
 */
public class CrystalCradleClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    private static final BlockPos STONE = new BlockPos(1, Y, 4);

    // A row of cradles within the creative stone's reach, each held at another stage for the screenshots
    private static final BlockPos FED         = new BlockPos(-4, Y, 0);
    private static final BlockPos BED         = new BlockPos(-2, Y, 0);
    private static final BlockPos RISING      = new BlockPos(0, Y, 0);
    private static final BlockPos LARGER      = new BlockPos(2, Y, 0);
    private static final BlockPos STRANGER    = new BlockPos(4, Y, 0);
    private static final BlockPos GROWN_CLEAR = new BlockPos(6, Y, 0);

    // A lone cradle for the review command and the harvests, with its own stone
    private static final BlockPos LONE       = new BlockPos(40, Y, -10);
    private static final BlockPos LONE_STONE = new BlockPos(40, Y, -7);

    // Bundles placed on the ground, plain beside Clear
    private static final int      PLACED_Z  = -6;
    private static final BlockPos DROP_TEST = new BlockPos(-8, Y, -10);
    private static final BlockPos RELEASE   = new BlockPos(-10, Y, -10);

    // Two Clear pylons walled apart, and two plain ones the same
    private static final BlockPos CLEAR_A = new BlockPos(20, Y, 0);
    private static final BlockPos CLEAR_B = new BlockPos(28, Y, 0);
    private static final BlockPos PLAIN_A = new BlockPos(20, Y, 10);
    private static final BlockPos PLAIN_B = new BlockPos(28, Y, 10);
    private static final int      WALL_X  = 24;

    // A wound and two pylons in its seep
    // Each crystal's large bundle by habit, a column per habit and a row per crystal
    private static final List<CrystalHabit> GALLERY = List.of(CrystalHabit.NONE, CrystalHabit.CLEAR, CrystalHabit.DEEP, CrystalHabit.FOULED, CrystalHabit.SCOURED,
            CrystalHabit.SEARING, CrystalHabit.STILL, CrystalHabit.WRUNG);
    private static final int GALLERY_X = -10;
    private static final int GALLERY_Z = 8;

    private static final BlockPos WOUND      = new BlockPos(60, Y, 0);
    private static final BlockPos SEEP_CLEAR = new BlockPos(64, Y, 0);
    private static final BlockPos SEEP_PLAIN = new BlockPos(64, Y, 4);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty peaceful");
            server.runCommand("gamemode survival @a");
            server.runCommand("fill -12 " + (Y - 1) + " -12 70 " + (Y - 1) + " 14 voidheart:voidstone_polished");
            server.runCommand("setblock " + at(STONE) + " voidheart:creative_ley_stone");
            server.runCommand("setblock " + at(LONE_STONE) + " voidheart:creative_ley_stone");
            for (var cradle : List.of(FED, BED, RISING, LARGER, STRANGER, GROWN_CLEAR, LONE))
                server.runCommand("setblock " + at(cradle) + " voidheart:crystal_cradle");
            tp(server, 1, Y, -3);
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(minecraft -> Knowledge.of(minecraft, player(minecraft).getUUID()).grant(CradleResearch.CRYSTAL_CRADLE));

            checkTranslations(context);
            checkSeeding(server);
            checkGrowsOnLey(context, server);
            checkCommandAndHarvest(context, server);
            checkPartialHarvest(server);
            checkDrops(server);
            checkPlacement(server);
            checkClearArcs(context, server);
            checkDarkInSeep(context, server);
            checkTooltips(context);

            stageCradles(server);
            placeComparison(server);
            placeGallery(server);
            screenshots(context, server);
            habitScreenshots(context, server);
        }
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static CrystalCradleTile cradle(MinecraftServer minecraft, BlockPos pos)
    {
        if (!(minecraft.overworld().getBlockEntity(pos) instanceof CrystalCradleTile cradle))
            throw new AssertionError("No cradle at " + pos);
        return cradle;
    }

    private static void checkTranslations(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var language = Language.getInstance();
            for (var key : List.of("block.voidheart.crystal_cradle", "voidheart.crystal_cradle.lore", "research.voidheart.crystal_cradle",
                    "research.voidheart.crystal_cradle.desc", "voidheart.page.crystal_cradle", "config.jade.plugin_voidheart.crystal_cradle",
                    "voidheart.habit.clear", "voidheart.habit.plain", "voidheart.habit.clear.arcs", "voidheart.habit.clear.arcs.block",
                    "voidheart.habit.clear.dark", "voidheart.habit.clear.dark.block", "voidheart.jade.pylon.dark"))
                expect(language.has(key), "Untranslated " + key);
            for (var habit : CrystalHabit.values())
                expect(habit == CrystalHabit.NONE || language.has(habit.translationKey()), "Untranslated " + habit.translationKey());
            expect(VoidHeartItems.CREATIVE_TAB_ITEMS.contains(CrystalContent.CRYSTAL_CRADLE.asItem()), "The cradle is missing from the creative tab");
        });
    }

    private static void checkSeeding(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD, 6));
            useOn(player, FED);
            var fed = cradle(minecraft, FED);
            expect(fed.growth().worth() == 4, "Six shards on an empty cradle should fill it to a large bundle's 4, found " + fed.growth().worth());
            expect(player.getMainHandItem().getCount() == 2, "Two shards should be left in hand, found " + player.getMainHandItem().getCount());
            expect(player.getUUID().equals(fed.owner()), "Feeding an unowned cradle should claim it");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.RUBRUM_SHARD, 3));
            useOn(player, FED);
            expect(fed.growth().worth() == 4 && player.getMainHandItem().getCount() == 3, "A cradle of azurium should refuse rubrum");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD, 2));
            useOn(player, BED);
            player.setItemInHand(InteractionHand.MAIN_HAND, CrystalBundles.shards(CrystalKind.AZURIUM, 1, CrystalHabit.CLEAR));
            useOn(player, BED);
            expect(cradle(minecraft, BED).growth().worth() == 2 && player.getMainHandItem().getCount() == 1,
                    "A cradle of plain shards should refuse a Clear one");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    private static void checkGrowsOnLey(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var fed = cradle(minecraft, FED);
            expect(fed.supply() > 0.99F, "A cradle by a creative ley stone should be fully fed, got " + fed.supply());
            expect(fed.feed() == CrystalHabit.CLEAR, "A clean line should feed Clear, got " + fed.feed());
            double grown = fed.growth().grown();
            expect(grown > 0 && grown < 0.01, "Forty ticks of full ley should grow a sliver, grew " + grown);
            expect(fed.preview() == CrystalHabit.CLEAR, "It should come out Clear for an owner who studied it, shows " + fed.preview());
        });
    }

    private static void checkCommandAndHarvest(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, LONE.getX() + 0.5, Y, LONE.getZ() - 2.5);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.AZURIUM_SHARD, 4));
            useOn(player, LONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().clearContent();
        });
        context.waitTicks(5);
        server.runCommand("execute as @a at @s run voidheart crystal grow");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var lone = cradle(minecraft, LONE);
            expect(!lone.growth().isGrowing() && lone.growth().grownSize() == BundleSize.LARGE, "The review command should finish the large bundle");

            var player = player(minecraft);
            useOn(player, LONE);
            var bundle = findInInventory(minecraft, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.asItem());
            expect(bundle != null, "An empty hand should take out the large bundle");
            expect(CrystalBundles.habitOf(bundle) == CrystalHabit.CLEAR, "Grown on a clean line it should be Clear, is " + CrystalBundles.habitOf(bundle));
            expect(lone.growth().worth() == 0, "The cradle should be empty after the harvest");
            player.getInventory().clearContent();
        });
    }

    private static void checkPartialHarvest(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var lone = cradle(minecraft, LONE);
            lone.seed(CrystalKind.AZURIUM, 4, CrystalHabit.NONE);
            lone.advance(CrystalHabit.PLAIN, 2.5);
            var items = lone.harvest();
            var bundle = items.stream().filter(stack -> stack.is(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE.asItem())).findFirst().orElse(null);
            var shards = items.stream().filter(stack -> stack.is(VoidHeartItems.AZURIUM_SHARD)).findFirst().orElse(null);
            expect(bundle != null && shards != null && shards.getCount() == 2,
                    "Two and a half of four grown should give a medium bundle and two shards, got " + items);
            expect(CrystalBundles.habitOf(bundle) == CrystalHabit.PLAIN, "Grown on no habit's feed it should be plain, is " + CrystalBundles.habitOf(bundle));
            expect(!shards.has(net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.CRYSTAL_HABIT),
                    "Plain shards should carry nothing, so they stack with mined ones");

            var stranger = cradle(minecraft, STRANGER);
            stranger.claim(UUID.randomUUID());
            stranger.seed(CrystalKind.AZURIUM, 4, CrystalHabit.NONE);
            stranger.finish(CrystalHabit.CLEAR);
            expect(stranger.preview() == CrystalHabit.PLAIN, "An owner who never studied it should grow plain, shows " + stranger.preview());
        });
    }

    private static void checkDrops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var clear = habit(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE, CrystalHabit.CLEAR);
            level.setBlockAndUpdate(DROP_TEST, clear);

            for (var tool : List.of(new ItemStack(Items.IRON_PICKAXE), enchanted(level, Enchantments.FORTUNE, 3)))
            {
                for (int roll = 0; roll < 20; roll++)
                {
                    var drops = Block.getDrops(clear, level, DROP_TEST, null, null, tool);
                    expect(drops.size() == 1 && drops.getFirst().is(VoidHeartItems.AZURIUM_SHARD) && drops.getFirst().getCount() == 4,
                            "A grown large bundle should break into exactly 4 shards, got " + drops);
                    expect(CrystalBundles.habitOf(drops.getFirst()) == CrystalHabit.CLEAR, "Its shards should stay Clear");
                }
            }

            var whole = Block.getDrops(clear, level, DROP_TEST, null, null, enchanted(level, Enchantments.SILK_TOUCH, 1));
            expect(whole.size() == 1 && whole.getFirst().is(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.asItem())
                    && CrystalBundles.habitOf(whole.getFirst()) == CrystalHabit.CLEAR, "Silk touch should keep it whole and Clear, got " + whole);

            var plain = habit(VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE, CrystalHabit.PLAIN);
            var plainDrops = Block.getDrops(plain, level, DROP_TEST, null, null, enchanted(level, Enchantments.FORTUNE, 3));
            expect(plainDrops.size() == 1 && plainDrops.getFirst().getCount() == 2
                    && !plainDrops.getFirst().has(net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.CRYSTAL_HABIT),
                    "A plain grown medium bundle should break into 2 bare shards, got " + plainDrops);
            level.removeBlock(DROP_TEST, false);
        });
    }

    private static void checkPlacement(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var ground = DROP_TEST.below();
            player.setItemInHand(InteractionHand.MAIN_HAND, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.SMALL, CrystalHabit.CLEAR));
            useOn(player, ground);
            var placed = level.getBlockState(DROP_TEST);
            expect(placed.is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE) && CrystalBundles.habitOf(placed) == CrystalHabit.CLEAR,
                    "A Clear bundle should be placed Clear, placed " + placed);
            level.removeBlock(DROP_TEST, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            level.setBlockAndUpdate(RELEASE, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
            level.setBlockAndUpdate(RELEASE.above(), habit(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE, CrystalHabit.CLEAR));
            level.destroyBlock(RELEASE, true);
            var released = level.getEntitiesOfClass(ItemEntity.class, new AABB(RELEASE).inflate(2)).stream()
                    .map(ItemEntity::getItem).filter(stack -> stack.is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem())).findFirst().orElse(null);
            expect(released != null && CrystalBundles.habitOf(released) == CrystalHabit.CLEAR, "A broken pylon should let its bundle go still Clear, got " + released);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(RELEASE).inflate(3)).forEach(ItemEntity::discard);
        });
    }

    private static void pylon(TestServerContext server, BlockPos pos, CrystalHabit habit)
    {
        server.runCommand("setblock " + at(pos) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(pos.above()) + " voidheart:small_azurium_bundle[facing=up,habit=" + habit.getSerializedName() + "]");
    }

    private static boolean arced(GridLayout layout, BlockPos a, BlockPos b)
    {
        int first = -1;
        int second = -1;
        for (int i = 0; i < layout.pylons().size(); i++)
        {
            if (layout.pylons().get(i).pos().equals(a))
                first = i;
            if (layout.pylons().get(i).pos().equals(b))
                second = i;
        }
        for (var arc : layout.arcs())
        {
            if ((arc[0] == first && arc[1] == second) || (arc[0] == second && arc[1] == first))
                return true;
        }
        return false;
    }

    private static boolean inGrid(GridLayout layout, BlockPos pos)
    {
        return layout.pylons().stream().anyMatch(pylon -> pylon.pos().equals(pos));
    }

    private static void checkClearArcs(ClientGameTestContext context, TestServerContext server)
    {
        pylon(server, CLEAR_A, CrystalHabit.CLEAR);
        pylon(server, CLEAR_B, CrystalHabit.CLEAR);
        pylon(server, PLAIN_A, CrystalHabit.NONE);
        pylon(server, PLAIN_B, CrystalHabit.NONE);
        server.runCommand("fill " + WALL_X + " " + Y + " -3 " + WALL_X + " " + (Y + 4) + " 13 minecraft:stone");
        context.waitTicks(45);
        server.runOnServer(minecraft ->
        {
            var layout = VoidGrid.get(minecraft.overworld()).layout();
            expect(arced(layout, CLEAR_A, CLEAR_B), "Two Clear pylons should arc through the wall");
            expect(!arced(layout, PLAIN_A, PLAIN_B), "Two plain pylons should not arc through the wall");
        });

        pylon(server, CLEAR_B, CrystalHabit.NONE);
        context.waitTicks(45);
        server.runOnServer(minecraft -> expect(!arced(VoidGrid.get(minecraft.overworld()).layout(), CLEAR_A, CLEAR_B),
                "A Clear pylon should not arc through a wall to a plain one"));
        pylon(server, CLEAR_B, CrystalHabit.CLEAR);
        context.waitTicks(45);
    }

    private static void checkDarkInSeep(ClientGameTestContext context, TestServerContext server)
    {
        pylon(server, SEEP_CLEAR, CrystalHabit.CLEAR);
        pylon(server, SEEP_PLAIN, CrystalHabit.NONE);
        server.runCommand("voidheart energy fissure wound " + at(WOUND));
        server.runCommand("voidheart energy fissure " + at(WOUND) + " seep 10");
        context.waitTicks(45);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(VoidSeep.strengthAt(level, SEEP_CLEAR.above()) > 0, "The Clear pylon's bundle should stand in the seep");
            expect(PylonBundle.isDarkInSeep(level, SEEP_CLEAR.above()), "A Clear bundle in a seep should be dark");
            var layout = VoidGrid.get(level).layout();
            expect(!inGrid(layout, SEEP_CLEAR), "A dark Clear pylon should hold no field");
            expect(inGrid(layout, SEEP_PLAIN), "A plain pylon in the same seep should still hold its field");
        });
    }

    private static void checkTooltips(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var clear = lines(minecraft, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.LARGE, CrystalHabit.CLEAR));
            for (var expected : List.of("Clear", "Pylon: arcs through walls to Clear pylons", "Pylon: goes dark in a seep"))
                expect(clear.contains(expected), "A Clear bundle's tooltip should read \"" + expected + "\": " + clear);

            var shard = lines(minecraft, CrystalBundles.shards(CrystalKind.AZURIUM, 1, CrystalHabit.CLEAR));
            expect(shard.contains("Regrows as a Clear bundle"), "A Clear shard should say what it regrows as: " + shard);

            var grown = lines(minecraft, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.MEDIUM, CrystalHabit.PLAIN));
            expect(grown.contains("Cradle-grown"), "A plain grown bundle should say it grew in a cradle: " + grown);

            var world = lines(minecraft, new ItemStack(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE));
            expect(world.stream().noneMatch(line -> line.equals("Clear") || line.equals("Cradle-grown")), "World crystal should show no habit: " + world);
        });
    }

    private static List<String> lines(Minecraft minecraft, ItemStack stack)
    {
        return stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
    }

    /**
     * Holds each cradle at its own stage: a fresh bed, a small bundle half risen, a medium and a large on their way, a large grown plain for a stranger,
     * and a large grown Clear.
     */
    private static void stageCradles(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var uuid = player(minecraft).getUUID();
            cradle(minecraft, BED).advance(CrystalHabit.PLAIN, 0.5);
            for (var pos : List.of(RISING, LARGER, GROWN_CLEAR))
            {
                cradle(minecraft, pos).claim(uuid);
                cradle(minecraft, pos).seed(CrystalKind.AZURIUM, 4, CrystalHabit.NONE);
            }
            cradle(minecraft, RISING).advance(CrystalHabit.CLEAR, 1.5);
            cradle(minecraft, LARGER).advance(CrystalHabit.CLEAR, 3);
            cradle(minecraft, GROWN_CLEAR).finish(CrystalHabit.CLEAR);
        });
    }

    private static void placeComparison(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            int x = -3;
            for (var size : BundleSize.values())
            {
                level.setBlockAndUpdate(new BlockPos(x, Y, PLACED_Z), CrystalBundles.bundle(CrystalKind.AZURIUM, size).defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x + 1, Y, PLACED_Z), habit(CrystalBundles.bundle(CrystalKind.AZURIUM, size), CrystalHabit.CLEAR));
                x += 3;
            }
        });
    }

    private static void screenshots(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode spectator @a");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(40);

        aimFrom(context, server, -2.5, Y + 1.2, -2.2, -2.5, Y + 0.2, 0.5, 15);
        context.takeScreenshot("cradle-bed-and-rising");
        aimFrom(context, server, 1.5, Y + 1.2, -2.2, 1.5, Y + 0.3, 0.5, 5);
        context.takeScreenshot("cradle-medium-and-large-rising");
        aimFrom(context, server, 5.5, Y + 1.3, -2.2, 5.5, Y + 0.4, 0.5, 5);
        context.takeScreenshot("cradle-grown-plain-and-clear");
        aimFrom(context, server, 1.5, Y + 3.5, -5.5, 1.5, Y, 0.5, 5);
        context.takeScreenshot("cradle-row");
        aimFrom(context, server, 0.5, Y + 2.2, PLACED_Z - 3.2, 0.5, Y + 0.3, PLACED_Z + 0.5, 5);
        context.takeScreenshot("crystal-clear-beside-plain");
        aimFrom(context, server, 24.5, Y + 6, -6, 24.5, Y + 1, 2, 5);
        context.takeScreenshot("clear-pylons-through-wall");

        server.runCommand("time set midnight");
        context.waitTicks(10);
        aimFrom(context, server, 1.5, Y + 3.5, -5.5, 1.5, Y, 0.5, 5);
        context.takeScreenshot("cradle-row-night");
        aimFrom(context, server, 0.5, Y + 2.2, PLACED_Z - 3.2, 0.5, Y + 0.3, PLACED_Z + 0.5, 5);
        context.takeScreenshot("crystal-clear-beside-plain-night");
        server.runCommand("time set noon");

        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(CrystalContent.CRYSTAL_CRADLE));
            inventory.setItem(1, CrystalBundles.shards(CrystalKind.AZURIUM, 3, CrystalHabit.CLEAR));
            inventory.setItem(2, new ItemStack(VoidHeartItems.AZURIUM_SHARD, 3));
            inventory.setItem(3, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.LARGE, CrystalHabit.CLEAR));
            inventory.setItem(4, new ItemStack(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE));
        });
        aimFrom(context, server, 1.5, Y, -4, 1.5, Y, 2, 5);
        for (int slot = 0; slot < 4; slot++)
        {
            int selected = slot;
            context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(selected));
            context.waitTicks(5);
            context.takeScreenshot("cradle-item-in-hand-" + slot);
        }
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("cradle-items-inventory");
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /**
     * Every crystal's large bundle in a row, plain then each habit it takes in the habit's column, one row per crystal.
     */
    private static void placeGallery(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (int row = 0; row < CrystalKind.values().length; row++)
            {
                var kind = CrystalKind.values()[row];
                var bundle = CrystalBundles.bundle(kind, BundleSize.LARGE);
                for (int column = 0; column < GALLERY.size(); column++)
                {
                    var habit = GALLERY.get(column);
                    if (habit == CrystalHabit.NONE || habit.takenBy(kind))
                        level.setBlockAndUpdate(new BlockPos(GALLERY_X + 2 * column, Y, GALLERY_Z + 2 * row), habit(bundle, habit));
                }
            }
        });
    }

    /**
     * The gallery by day and night, and every habit's shard and large bundle in the inventory.
     */
    private static void habitScreenshots(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode spectator @a");
        context.getInput().pressKey(InputConstants.KEY_F1);
        double middle = GALLERY_X + GALLERY.size() - 1;
        aimFrom(context, server, middle + 0.5, Y + 4, GALLERY_Z - 5, middle + 0.5, Y, GALLERY_Z + 2.5, 15);
        context.takeScreenshot("crystal-habits-gallery");
        aimFrom(context, server, GALLERY_X + 4.5, Y + 1.6, GALLERY_Z - 2.5, GALLERY_X + 5.5, Y + 0.3, GALLERY_Z + 2.5, 5);
        context.takeScreenshot("crystal-habits-gallery-close");
        server.runCommand("time set midnight");
        context.waitTicks(10);
        aimFrom(context, server, middle + 0.5, Y + 4, GALLERY_Z - 5, middle + 0.5, Y, GALLERY_Z + 2.5, 5);
        context.takeScreenshot("crystal-habits-gallery-night");
        server.runCommand("time set noon");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Survival, so the inventory screen shows all 36 slots rather than the creative tabs
        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            int slot = 0;
            for (var kind : CrystalKind.values())
            {
                for (var habit : GALLERY)
                {
                    if (habit != CrystalHabit.NONE && !habit.takenBy(kind))
                        continue;
                    inventory.setItem(slot++, CrystalBundles.shards(kind, 1, habit));
                    inventory.setItem(slot++, CrystalBundles.bundleItem(kind, BundleSize.LARGE, habit));
                }
            }
        });
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("crystal-habits-inventory");
        context.waitTicks(16);
        context.takeScreenshot("crystal-habits-inventory-later");
        context.setScreen(() -> null);
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
        server.runCommand("gamemode creative @a");
        context.waitTicks(2);
    }

    private static BlockState habit(Block bundle, CrystalHabit habit)
    {
        return bundle.defaultBlockState().setValue(CrystalBundleBlock.HABIT, habit);
    }

    private static ItemStack findInInventory(MinecraftServer minecraft, Item item)
    {
        var inventory = player(minecraft).getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            if (inventory.getItem(slot).is(item))
                return inventory.getItem(slot);
        }
        return null;
    }

    private static ItemStack enchanted(ServerLevel level, ResourceKey<Enchantment> enchantment, int enchantLevel)
    {
        var stack = new ItemStack(Items.IRON_PICKAXE);
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), enchantLevel);
        return stack;
    }
}
