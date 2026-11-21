package net.voxelindustry.voidheart.gametest;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.client.screen.ApparatusScreen;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldState;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CradleResearch;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Crystallography's third step, on a flat world: a relay feeding a cradle Fiery, Stillness or Suffering grows Searing, Still or Wrung, a Searing cell let go
 * empties in a burst, a Still cell charges unpowered, a Wrung cell drinks the harm done beside it, the cell recipe carries the bundle's habit, and a Grudge
 * Ward wards the kind of hit that last broke its shield.
 */
public class CrystalFedClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    /**
     * A creative stone, a cradle beside it, and a relay fed by a storage of one essence, far enough apart that no relay reaches another station.
     */
    private record Station(EssenceType essence, CrystalKind kind, CrystalHabit habit, int x)
    {
        BlockPos stone()
        {
            return new BlockPos(x, Y, 0);
        }

        BlockPos cradle()
        {
            return new BlockPos(x + 2, Y, 0);
        }

        BlockPos relay()
        {
            return new BlockPos(x + 4, Y + 1, 2);
        }

        BlockPos storage()
        {
            return new BlockPos(x + 6, Y, 2);
        }
    }

    private static final int STORED_ESSENCE = 2_000;

    private static final BlockPos UNOWNED = new BlockPos(96, Y, 0);

    private static final BlockPos WELL           = new BlockPos(0, Y, 40);
    private static final BlockPos SEARING_SOCKET = new BlockPos(3, Y, 40);
    private static final BlockPos STILL_SOCKET   = new BlockPos(6, Y, 40);
    // Far from the well, so what it drinks stays in it
    private static final BlockPos WRUNG_SOCKET   = new BlockPos(60, Y, 40);

    private static final String APPARATUS_SLOT = "chest/shielding_apparatus";

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -10 -50 110 50");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(minecraft ->
            {
                var knowledge = Knowledge.of(minecraft, player(minecraft).getUUID());
                knowledge.grant(CradleResearch.CRYSTAL_CRADLE);
                knowledge.grant(CradleResearch.researchFor(CrystalHabit.SEARING));
                knowledge.grant(CradleResearch.researchFor(CrystalHabit.WRUNG));
            });
            context.waitTicks(10);

            checkTranslations(context);
            checkTooltips(context);
            checkCellRecipe(server);
            checkFedGrowth(context, server);
            checkCells(context, server);
            checkGrudgeWard(context, server);
        }
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void checkTranslations(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var language = Language.getInstance();
            for (var key : List.of("research." + MODID + ".fed_growth", "research." + MODID + ".wrung_growth", MODID + ".jade.cradle.fed.searing",
                    MODID + ".jade.rubrum_construct.rates", "item." + MODID + ".apparatus_grudge_ward", MODID + ".apparatus_screen.stat.grudge",
                    MODID + ".grudge.kind.fire", MODID + ".apparatus_screen.refusal.second_grudge"))
                expect(language.has(key), "Missing translation " + key);
        });
    }

    private static void checkTooltips(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.SMALL, CrystalHabit.SEARING), "Searing",
                    "Construct: gives out twice as fast", "Construct: takes nothing in");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.SMALL, CrystalHabit.STILL), "Construct: takes in twice as fast",
                    "Construct: gives nothing out");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.SMALL, CrystalHabit.WRUNG), "Cell: charges when things nearby are hurt");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.WRUNG), "Altar: becomes a Grudge Ward");
            expectLines(minecraft, cell(CrystalHabit.SEARING), "Searing", "Empties all at once when let go");
            expectLines(minecraft, cell(CrystalHabit.STILL), "Still", "Charges even without redstone", "Never gives to a field");
            expectLines(minecraft, new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(ApparatusComponent.GRUDGE_WARD)),
                    "The last kind of hit to break the shield costs it half", "Only one Grudge Ward fits an apparatus");
        });
    }

    private static ItemStack cell(CrystalHabit habit)
    {
        return CrystalBundles.withHabit(new ItemStack(RubrumContent.RUBRUM_CELL), habit);
    }

    private static void expectLines(Minecraft minecraft, ItemStack stack, String... expected)
    {
        var lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        for (var line : expected)
            expect(lines.contains(line), stack.getItem() + "'s tooltip should read \"" + line + "\": " + lines);
    }

    /**
     * The cell's own recipe takes the habit of the bundle sealed in it, plain bundles make plain cells, and a bundle holding taint it drew in is refused.
     */
    private static void checkCellRecipe(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(craftCell(level, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.SMALL, CrystalHabit.SEARING)).map(CrystalBundles::habitOf)
                    .orElse(null) == CrystalHabit.SEARING, "A cell crafted around a Searing bundle should be Searing");
            expect(craftCell(level, new ItemStack(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE)).map(CrystalBundles::habitOf).orElse(null) == CrystalHabit.NONE,
                    "A cell crafted around a world bundle should be plain");
            expect(craftCell(level, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.SMALL, CrystalHabit.FOULED)).map(CrystalBundles::habitOf)
                    .orElse(null) == CrystalHabit.NONE, "Only the habits that change a cell should carry into it");
            var soaked = CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.SMALL, CrystalHabit.SCOURED);
            soaked.set(VoidHeartDataComponents.CRYSTAL_SOAK, 2);
            expect(craftCell(level, soaked).isEmpty(), "A bundle holding taint it drew in should not make a cell");
        });
    }

    private static java.util.Optional<ItemStack> craftCell(ServerLevel level, ItemStack bundle)
    {
        var gold = new ItemStack(VoidHeartItems.RAVENOUS_GOLD_INGOT);
        var stone = new ItemStack(VoidHeartBlocks.VOIDSTONE_POLISHED);
        var input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, gold, ItemStack.EMPTY, stone, bundle, stone.copy(), ItemStack.EMPTY, gold.copy(), ItemStack.EMPTY));
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).map(recipe -> recipe.value().assemble(input));
    }

    /**
     * Each station's relay feeds its essence into the cradle, which is drunk while the ley grows the crystal and settles the essence's habit. A cradle
     * nobody owns and an essence a crystal won't take are refused before any is sent.
     */
    private static void checkFedGrowth(ClientGameTestContext context, TestServerContext server)
    {
        var stations = List.of(
                new Station(EssenceRegistry.FIERY, CrystalKind.AZURIUM, CrystalHabit.SEARING, 0),
                new Station(EssenceRegistry.STILLNESS, CrystalKind.RUBRUM, CrystalHabit.STILL, 30),
                new Station(EssenceRegistry.SUFFERING, CrystalKind.MEMORIAM, CrystalHabit.WRUNG, 60));
        for (var station : stations)
        {
            server.runCommand("setblock " + at(station.stone()) + " voidheart:creative_ley_stone");
            server.runCommand("setblock " + at(station.cradle()) + " voidheart:crystal_cradle");
            server.runCommand("setblock " + at(station.relay().below()) + " voidheart:voidstone_polished");
        }
        server.runCommand("setblock " + at(UNOWNED) + " voidheart:crystal_cradle");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            for (var station : stations)
            {
                var cradle = cradle(minecraft, station.cradle());
                cradle.claim(player.getUUID());
                expect(cradle.seed(station.kind(), 1, CrystalHabit.NONE) == net.voxelindustry.voidheart.common.content.crystal.CradleGrowth.Refusal.NONE,
                        "The cradle should take a shard");
                placeStorage(level, station.storage(), station.essence(), STORED_ESSENCE);
                level.setBlockAndUpdate(station.relay(), VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
            }
            cradle(minecraft, UNOWNED).seed(CrystalKind.AZURIUM, 1, CrystalHabit.NONE);
        });

        waitFor(context, server, 600, () -> "Each cradle should be fed its relay's essence", minecraft -> stations.stream()
                .allMatch(station -> cradle(minecraft, station.cradle()).source() == CrystalCradleTile.Source.FED));
        server.runOnServer(minecraft ->
        {
            for (var station : stations)
            {
                var cradle = cradle(minecraft, station.cradle());
                expect(cradle.feed() == station.habit(), station.essence().identifier() + " should teach " + station.habit() + ", got " + cradle.feed());
                expect(cradle.supply() > 0.99F, "The creative stone should feed the cradle fully, got " + cradle.supply());
                expect(cradle.essence().amount(station.habit()) > 0, "The relay should have filled the cradle's " + station.habit() + " tank");
            }
            var azurium = cradle(minecraft, stations.getFirst().cradle());
            expect(azurium.spaceFor(EssenceRegistry.SUFFERING) == 0, "Azurium is never wrung, so its cradle should take no Suffering");
            expect(cradle(minecraft, UNOWNED).spaceFor(EssenceRegistry.FIERY) == 0, "A cradle nobody owns should take no essence it can't settle");
        });

        // The pools in each essence's colour with motes climbing the bundle, and the relay's beam sinking into the basin
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        for (var station : stations)
        {
            var cradle = station.cradle();
            server.runOnServer(minecraft -> cradle(minecraft, cradle).advance(station.habit(), 0.5));
            aimFrom(context, server, cradle.getX() + 0.5, Y + 0.6, cradle.getZ() - 1.6, cradle.getX() + 0.5, Y + 0.25, cradle.getZ() + 0.5, 20);
            context.takeScreenshot("crystal-fed-" + station.habit().getSerializedName());
        }
        var first = stations.getFirst();
        aimFrom(context, server, first.cradle().getX() + 0.5, Y + 1.5, first.cradle().getZ() - 4.5, first.relay().getX() + 0.5, Y + 0.5, 1.5, 10);
        context.takeScreenshot("crystal-fed-relay");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);

        server.runOnServer(minecraft ->
        {
            for (var station : stations)
            {
                var cradle = cradle(minecraft, station.cradle());
                cradle.finish(station.habit());
                var items = cradle.harvest();
                expect(items.size() == 1 && CrystalBundles.habitOf(items.getFirst()) == station.habit(),
                        "A " + station.kind() + " fed " + station.essence().identifier() + " should come out " + station.habit() + ", got " + items);
            }
        });
    }

    private static CrystalCradleTile cradle(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof CrystalCradleTile cradle)
            return cradle;
        throw new AssertionError("Expected a cradle at " + pos);
    }

    private static void placeStorage(ServerLevel level, BlockPos pos, EssenceType aspect, int amount)
    {
        var stack = new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE);
        var stored = new EssenceStorage(EssenceRegistry.EMPTY, net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile.CAPACITY);
        stored.attune(aspect);
        stored.fill(aspect, amount);
        stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, stored);
        level.setBlockAndUpdate(pos, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
        VoidHeartBlocks.RAVENOUS_STORAGE.setPlacedBy(level, pos, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState(), null, stack);
    }

    /**
     * A Searing cell let go empties in a burst, a Still cell charges with no redstone and gives nothing back, and a Wrung cell drinks the harm done to a pig
     * beside it.
     */
    private static void checkCells(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
            for (var pos : List.of(SEARING_SOCKET, STILL_SOCKET, WRUNG_SOCKET))
                level.setBlock(pos, RubrumContent.CELL_SOCKET.defaultBlockState(), Block.UPDATE_ALL);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var searing = RubrumCellItem.full();
            CrystalBundles.withHabit(searing, CrystalHabit.SEARING);
            socket(minecraft, SEARING_SOCKET).setCell(searing);
            socket(minecraft, STILL_SOCKET).setCell(cell(CrystalHabit.STILL));
            socket(minecraft, WRUNG_SOCKET).setCell(cell(CrystalHabit.WRUNG));
        });

        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        aimFrom(context, server, SEARING_SOCKET.getX() + 0.5, Y + 0.2, SEARING_SOCKET.getZ() - 1.6, SEARING_SOCKET.getX() + 0.5, Y + 0.45,
                SEARING_SOCKET.getZ() + 0.5, 8);
        context.takeScreenshot("crystal-cell-searing-burst");
        long burst = server.computeOnServer(minecraft -> RubrumCellItem.charge(socket(minecraft, SEARING_SOCKET).getCell()));
        expect(burst < RubrumBalance.CELL_CAPACITY - 20 * RubrumBalance.CELL_SOCKET_RATE * 4,
                "A Searing cell let go should empty far faster than a socket's rate, holds " + burst);
        waitFor(context, server, 160, "A Searing cell should empty its whole charge in about five seconds",
                minecraft -> RubrumCellItem.charge(socket(minecraft, SEARING_SOCKET).getCell()) == 0);

        waitFor(context, server, 80, "A Still cell should charge from the field with no redstone",
                minecraft -> RubrumCellItem.charge(socket(minecraft, STILL_SOCKET).getCell()) >= 1_000
                             && socket(minecraft, STILL_SOCKET).activity() == CellSocketTile.Activity.CHARGING);

        var pigAt = WRUNG_SOCKET.offset(3, 0, 0);
        server.runOnServer(minecraft ->
        {
            var pig = spawn(minecraft, EntityTypes.PIG, pigAt.getX() + 0.5, Y, pigAt.getZ() + 0.5);
            pig.setNoAi(true);
        });
        context.waitTicks(5);
        aimFrom(context, server, WRUNG_SOCKET.getX() + 2, Y + 0.6, WRUNG_SOCKET.getZ() - 3.5, WRUNG_SOCKET.getX() + 1.5, Y + 0.5, WRUNG_SOCKET.getZ() + 0.5, 10);
        server.runOnServer(minecraft ->
        {
            var pig = first(minecraft, EntityTypes.PIG);
            pig.hurtServer(minecraft.overworld(), pig.damageSources().generic(), 5);
            expect(socket(minecraft, WRUNG_SOCKET).wrungBank() > 0, "A Wrung cell should bank the harm done to the pig beside it");
        });
        context.waitTicks(8);
        context.takeScreenshot("crystal-cell-wrung-wisps");
        context.waitTicks(10);
        context.takeScreenshot("crystal-cell-wrung-wisps-later");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        waitFor(context, server, 120, "A Wrung cell should take in what it banked",
                minecraft -> RubrumCellItem.charge(socket(minecraft, WRUNG_SOCKET).getCell()) >= 5 * RubrumBalance.WRUNG_PER_DAMAGE * 9 / 10);

        // A killing blow is harm too, counted no deeper than the creature could be hurt
        server.runOnServer(minecraft ->
        {
            var banked = socket(minecraft, WRUNG_SOCKET).wrungBank();
            var pig = first(minecraft, EntityTypes.PIG);
            pig.hurtServer(minecraft.overworld(), pig.damageSources().generic(), 1_000);
            expect(pig.isDeadOrDying(), "The blow should kill the pig");
            long gained = socket(minecraft, WRUNG_SOCKET).wrungBank() - banked;
            expect(gained > 0 && gained <= (long) (pig.getMaxHealth() * RubrumBalance.WRUNG_PER_DAMAGE),
                    "A Wrung cell should bank a killing blow, up to the pig's health, banked " + gained);
        });
        server.runCommand("kill @e[type=minecraft:pig]");
    }

    private static CellSocketTile socket(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof CellSocketTile socket)
            return socket;
        throw new AssertionError("Expected a cell socket at " + pos);
    }

    /**
     * A Grudge Ward takes up the kind of hit that broke the shield, wards that kind at half cost and no other, and turns to a new kind that breaks it.
     */
    private static void checkGrudgeWard(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        server.runCommand("tp @a 0.5 " + Y + " -40.5 0 0");
        context.waitTicks(5);
        float capacity = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            var apparatus = new ItemStack(VoidHeartItems.GREATER_SHIELDING_APPARATUS);
            apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, new ApparatusGrid(List.of(new ApparatusGrid.Placement(ApparatusComponent.GRUDGE_WARD, 0, 0, 0))));
            expect(TrinketsApi.getAttachment(player).getInventory(APPARATUS_SLOT).getOrCreateSlotAccess(0).set(apparatus), "The apparatus should go on");
            var stats = ShieldingApparatus.stats(ShieldingApparatus.equipped(player));
            expect(stats != null && stats.grudge(), "A worn Grudge Ward should show in the shield's stats");
            return stats.capacity();
        });

        var pig = server.computeOnServer(minecraft -> spawn(minecraft, EntityTypes.PIG, 3.5, Y, -40.5).getId());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = minecraft.overworld();
            var attacker = (net.minecraft.world.entity.LivingEntity) level.getEntity(pig);

            fullShield(player, capacity);
            player.hurtServer(level, player.damageSources().inFire(), capacity + 1);
            expect(ShieldingApparatus.grudge(ShieldingApparatus.equipped(player)) == DragoonAdaptation.Kind.FIRE,
                    "Fire that broke the shield should be held against, got " + ShieldingApparatus.grudge(ShieldingApparatus.equipped(player)));

            fullShield(player, capacity);
            player.hurtServer(level, player.damageSources().inFire(), 4);
            float afterFire = ShieldingApparatus.state(player).points();
            expect(Math.abs(afterFire - (capacity - 2)) < 0.01F, "Fire should cost a grudging shield half, " + afterFire + " of " + capacity + " left");

            fullShield(player, capacity);
            player.hurtServer(level, player.damageSources().mobAttack(attacker), 4);
            float afterBlow = ShieldingApparatus.state(player).points();
            expect(Math.abs(afterBlow - (capacity - 4)) < 0.01F, "A blow should cost it in full, " + afterBlow + " of " + capacity + " left");

            fullShield(player, capacity);
            player.hurtServer(level, player.damageSources().mobAttack(attacker), capacity + 1);
            expect(ShieldingApparatus.grudge(ShieldingApparatus.equipped(player)) == DragoonAdaptation.Kind.BLUNT,
                    "A blow that broke the shield should turn the grudge to blows");

            // Into the hand, to see what it holds against in its grid
            var access = TrinketsApi.getAttachment(player).getInventory(APPARATUS_SLOT).getOrCreateSlotAccess(0);
            var worn = access.get().copy();
            access.set(ItemStack.EMPTY);
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), worn);
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        expect(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof ApparatusScreen), "Using the apparatus in hand should open its grid");
        context.takeScreenshot("crystal-grudge-ward-grid");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runCommand("kill @e[type=minecraft:pig]");
        server.runCommand("clear @a");
        server.runCommand("gamemode creative @a");
    }

    private static void fullShield(net.minecraft.server.level.ServerPlayer player, float capacity)
    {
        player.setHealth(player.getMaxHealth());
        player.setAttached(ShieldingApparatus.SHIELD, new ShieldState(capacity, capacity, 0));
        player.damageCooldownTime = 0;
        player.setInvulnerableTime(0);
    }
}
