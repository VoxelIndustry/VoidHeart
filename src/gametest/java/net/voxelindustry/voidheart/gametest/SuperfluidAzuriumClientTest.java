package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import net.voxelindustry.voidheart.common.energy.pearl.PearlContent;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidContent;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidPour;
import net.voxelindustry.voidheart.common.energy.superfluid.TesseractItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.SHARD_UNITS;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearArea;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearScene;
import static net.voxelindustry.voidheart.gametest.GameTestKit.essenceForAltar;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.useOn;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * Superfluid azurium. Cradles of azurium near a mature pearl on its chain pour into it once their owner knows how, at a unit a tick each, and the
 * azurium they lose is exactly what the pearl gains; a stranger's cradle, Fouled azurium, rubrum and a pearl still growing pour nothing. A cradle
 * emptied mid-pour keeps back the shard it had started. A ringed pearl won't be picked by hand; a Tesseract draws its rings off, tops up from a second
 * pearl, and refuses more once brimming. A brimming Tesseract makes the levitation module on the altar, a partly filled one doesn't. Captures show the
 * stream and a forming ring by day and night, four closed rings, a camera sweep round them, the rings drawn off, and the tesseract held, dropped and
 * in the inventory.
 */
public class SuperfluidAzuriumClientTest implements FabricClientGameTest
{
    private static final int P = FLAT_FEET + 2;

    private static final BlockPos PEARL  = new BlockPos(0, P, 0);
    // Two cradles pouring into it, and three near it that mustn't: a stranger's, a Fouled one and one of rubrum
    private static final BlockPos POUR_A   = new BlockPos(2, FLAT_FEET, 0);
    private static final BlockPos POUR_B   = new BlockPos(-2, FLAT_FEET, 1);
    private static final BlockPos STRANGER = new BlockPos(0, FLAT_FEET, 3);
    private static final BlockPos FOULED   = new BlockPos(-2, FLAT_FEET, -2);
    private static final BlockPos RUBRUM   = new BlockPos(2, FLAT_FEET, -2);
    // A pearl still growing, far from the mature one, with a cradle beside it
    private static final BlockPos GROWING        = new BlockPos(20, P, 0);
    private static final BlockPos GROWING_CRADLE = new BlockPos(21, FLAT_FEET, 0);
    // A second mature pearl, far from the first's cradles, to top a tesseract up from
    private static final BlockPos SECOND = new BlockPos(0, P, 14);

    private static final BlockPos       ALTAR   = new BlockPos(40, FLAT_FEET, 0);
    private static final BlockPos       SKULL   = ALTAR.offset(2, 0, 2);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            clearArea(server, 0);
            clearScene(server);
            server.runCommand("execute as @a run voidheart research grant voidheart:superfluid_azurium");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " -5.5");
            context.waitTicks(5);

            poursOnlyWhereItShould(context, server);
            pourKeepsEveryShard(context, server);
            showPour(context, server);
            emptiedCradleKeepsBackItsStartedShard(context, server);
            drawnOffIntoATesseract(context, server);
            buildsTheLevitationModule(context, server);
            showItems(context, server);
            clearScene(server);
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
        }
    }

    // Pouring

    private static void poursOnlyWhereItShould(ClientGameTestContext context, TestServerContext server)
    {
        hangMature(server, PEARL);
        hang(server, GROWING);
        for (var pos : List.of(POUR_A, POUR_B, STRANGER, FOULED, RUBRUM, GROWING_CRADLE))
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " voidheart:crystal_cradle");
        context.waitTicks(3);

        server.runOnServer(minecraft ->
        {
            var owner = player(minecraft).getUUID();
            pearl(minecraft, PEARL).setShare(1);
            pearl(minecraft, GROWING).setShare(0.5);
            seed(minecraft, POUR_A, owner, CrystalKind.AZURIUM, CrystalHabit.NONE);
            seed(minecraft, POUR_B, owner, CrystalKind.AZURIUM, CrystalHabit.CLEAR);
            seed(minecraft, STRANGER, UUID.randomUUID(), CrystalKind.AZURIUM, CrystalHabit.NONE);
            seed(minecraft, FOULED, owner, CrystalKind.AZURIUM, CrystalHabit.FOULED);
            seed(minecraft, RUBRUM, owner, CrystalKind.RUBRUM, CrystalHabit.NONE);
            seed(minecraft, GROWING_CRADLE, owner, CrystalKind.AZURIUM, CrystalHabit.NONE);
        });

        waitFor(context, server, 60, "Both cradles of the owner who knows it should start pouring into the mature pearl",
                minecraft -> PEARL.equals(cradle(minecraft, POUR_A).pourTarget()) && PEARL.equals(cradle(minecraft, POUR_B).pourTarget()));
        context.waitTicks(25);
        server.runOnServer(minecraft ->
        {
            expect(cradle(minecraft, POUR_A).source() == CrystalCradleTile.Source.POURING, "A pouring cradle should say so, says " + cradle(minecraft, POUR_A).source());
            for (var pos : List.of(STRANGER, FOULED, RUBRUM))
                expect(cradle(minecraft, pos).pourTarget() == null, "The cradle at " + pos.toShortString() + " should pour nothing");
            expect(cradle(minecraft, GROWING_CRADLE).pourTarget() == null, "A pearl still growing should pull nothing");
            expect(pearl(minecraft, GROWING).superfluid() == 0, "A pearl still growing should hold no superfluid");
        });

        var before = server.computeOnServer(minecraft -> pearl(minecraft, PEARL).superfluid());
        context.waitTicks(100);
        var gained = server.computeOnServer(minecraft -> pearl(minecraft, PEARL).superfluid()) - before;
        expect(gained >= 2 * 95 && gained <= 2 * 105, "Two cradles should pour about 200 units in 100 ticks, poured " + gained);
    }

    private static void pourKeepsEveryShard(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            expect(conserved(minecraft) == 8L * SHARD_UNITS, "What the two cradles hold and the pearl holds should add to 8 shards, adds to " + conserved(minecraft));
            var poured = cradle(minecraft, POUR_A).pourNow(2);
            expect(poured == 2 * SHARD_UNITS, "Pouring two shards at once should move two shards' worth, moved " + poured);
            expect(conserved(minecraft) == 8L * SHARD_UNITS, "After a pour at once, the total should still be 8 shards, is " + conserved(minecraft));
            expect(cradle(minecraft, POUR_A).growth().worth() <= 2, "Cradle A should have given up two shards, holds " + cradle(minecraft, POUR_A).growth().worth());
        });
    }

    private static long conserved(MinecraftServer minecraft)
    {
        var a = cradle(minecraft, POUR_A);
        var b = cradle(minecraft, POUR_B);
        return (long) a.growth().worth() * SHARD_UNITS - a.pourCredit() + (long) b.growth().worth() * SHARD_UNITS - b.pourCredit()
                + pearl(minecraft, PEARL).superfluid();
    }

    private static void showPour(ClientGameTestContext context, TestServerContext server)
    {
        // A ring and a half wound, the second still forming as the cradles pour into it
        server.runOnServer(minecraft ->
        {
            pearl(minecraft, PEARL).setSuperfluid(6 * SHARD_UNITS);
            cradle(minecraft, POUR_A).seed(CrystalKind.AZURIUM, 2, CrystalHabit.NONE);
        });
        waitFor(context, server, 60, "Cradle A should still pour", minecraft -> PEARL.equals(cradle(minecraft, POUR_A).pourTarget()));
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(POUR_A) instanceof CrystalCradleTile cradle && cradle.pourShown(1) >= 1, 100);
        context.waitTicks(20);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, FLAT_FEET + 0.4, -4.5, 0.7, P + 0.2, 0.5, 3);
        context.takeScreenshot("superfluid-pour-day");
        aimFrom(context, server, 0.9, FLAT_FEET + 1.6, -1.6, 0.5, P + 0.65, 0.5, 3);
        context.takeScreenshot("superfluid-ring-forming-close");
        aimFrom(context, server, 4.6, FLAT_FEET + 0.9, -1.8, 1.2, FLAT_FEET + 1.2, 0.3, 3);
        context.takeScreenshot("superfluid-pour-cradle-close");
        aimFrom(context, server, -3.5, FLAT_FEET + 3.2, -2.5, 0.5, P + 0.6, 0.5, 3);
        context.takeScreenshot("superfluid-pour-above");
        freezeTime(server, "midnight");
        context.waitTicks(3);
        aimFrom(context, server, 0.5, FLAT_FEET + 0.4, -4.5, 0.7, P + 0.2, 0.5, 3);
        context.takeScreenshot("superfluid-pour-midnight");
        freezeTime(server, "noon");

        // Wound full: four rings closed, and the pour stops
        server.runOnServer(minecraft -> pearl(minecraft, PEARL).setSuperfluid(SuperfluidBalance.PEARL_CAPACITY));
        waitFor(context, server, 60, "A full pearl should take no more", minecraft -> cradle(minecraft, POUR_A).pourTarget() == null
                && cradle(minecraft, POUR_B).pourTarget() == null);
        context.waitTicks(30);
        aimFrom(context, server, 0.5, FLAT_FEET + 0.4, -4.5, 0.5, P + 0.6, 0.5, 3);
        context.takeScreenshot("superfluid-rings-full-day");
        freezeTime(server, "midnight");
        context.waitTicks(3);
        context.takeScreenshot("superfluid-rings-full-midnight");
        freezeTime(server, "noon");

        // The rings ease toward what the pearl holds, so wait until the last one has closed and shows no head
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(PEARL) instanceof HangingPearlTile pearl
                && pearl.shownSuperfluid(1) >= SuperfluidBalance.PEARL_CAPACITY, 200);
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
            aimSweep(context, server, step);
        server.runCommand("tick unfreeze");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void aimSweep(ClientGameTestContext context, TestServerContext server, int step)
    {
        aimFrom(context, server, -1.2 + step * 0.06, FLAT_FEET + 0.7, -2.2 + step * 0.04, 0.5, P + 0.6, 0.5, 2);
        context.takeScreenshot("superfluid-rings-sweep-" + step);
    }

    private static void emptiedCradleKeepsBackItsStartedShard(ClientGameTestContext context, TestServerContext server)
    {
        // Pour B part way into a shard on the second pearl, then break it
        hangMature(server, SECOND);
        var cradle = SECOND.offset(2, -2, 0);
        server.runCommand("setblock " + cradle.getX() + " " + cradle.getY() + " " + cradle.getZ() + " voidheart:crystal_cradle");
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            pearl(minecraft, SECOND).setShare(1);
            seed(minecraft, cradle, player(minecraft).getUUID(), CrystalKind.AZURIUM, CrystalHabit.NONE);
        });
        waitFor(context, server, 60, "The cradle by the second pearl should pour", minecraft -> SECOND.equals(cradle(minecraft, cradle).pourTarget()));
        context.waitTicks(10);
        var kept = server.computeOnServer(minecraft -> SuperfluidPour.kept(cradle(minecraft, cradle).growth().worth(), cradle(minecraft, cradle).pourCredit()));
        expect(kept == 3, "A cradle of four with a shard started should keep three, keeps " + kept);

        server.runCommand("setblock " + cradle.getX() + " " + cradle.getY() + " " + cradle.getZ() + " air destroy");
        context.waitTicks(5);
        var dropped = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cradle).inflate(2)).stream()
                .filter(item -> item.getItem().is(VoidHeartItems.AZURIUM_SHARD)).mapToInt(item -> item.getItem().getCount()).sum());
        expect(dropped == 3, "Breaking it should drop the three shards not yet started, dropped " + dropped);
        server.runCommand("kill @e[type=item]");
    }

    // The tesseract

    private static void drawnOffIntoATesseract(ClientGameTestContext context, TestServerContext server)
    {
        // Its cradles would pour straight back into a drawn pearl, so they go first
        for (var pos : List.of(POUR_A, POUR_B))
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " air");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " -2.5");
        context.waitTicks(3);

        // A ringed pearl isn't picked by hand
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player, PEARL);
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(PEARL).is(PearlContent.HANGING_RUBRUM_PEARL)),
                "A ringed pearl should stay on its chain when used with an empty hand");

        // The tesseract takes all four rings at once, and the rings unspool to the hand
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, FLAT_FEET, -2.5, 0.5, P + 0.6, 0.5, 3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperfluidContent.TESSERACT));
            useOn(player, PEARL);
        });
        context.waitTicks(7);
        context.takeScreenshot("superfluid-draw-off");
        context.waitTicks(20);
        context.takeScreenshot("superfluid-drawn");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft ->
        {
            var held = TesseractItem.held(player(minecraft).getMainHandItem());
            expect(held == SuperfluidBalance.TESSERACT_CAPACITY, "The tesseract should hold the whole pearl, holds " + held);
            expect(pearl(minecraft, PEARL).superfluid() == 0, "The pearl should be left bare");
            expect(TesseractItem.fill(player(minecraft).getMainHandItem()) == net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidRings.Fill.FULL,
                    "A tesseract holding a whole pearl should read as full");
        });

        // Bare, the pearl is picked by hand again
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().setItem(8, player.getMainHandItem().copy());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player, PEARL);
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(PEARL).isAir()), "A bare mature pearl should be picked by hand");

        // A tesseract tops up from another pearl, then refuses more once brimming
        server.runOnServer(minecraft ->
        {
            pearl(minecraft, SECOND).setSuperfluid(10 * SHARD_UNITS);
            var partial = new ItemStack(SuperfluidContent.TESSERACT);
            TesseractItem.setHeld(partial, 10 * SHARD_UNITS);
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, partial);
            useOn(player, SECOND);
            expect(TesseractItem.held(player.getMainHandItem()) == SuperfluidBalance.TESSERACT_CAPACITY, "Topped up, the tesseract should be full");
            expect(pearl(minecraft, SECOND).superfluid() == 4 * SHARD_UNITS, "The pearl should keep what didn't fit, keeps " + pearl(minecraft, SECOND).superfluid());
            useOn(player, SECOND);
            expect(pearl(minecraft, SECOND).superfluid() == 4 * SHARD_UNITS, "A full tesseract should take nothing more");
        });
    }

    private static void buildsTheLevitationModule(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart research grant voidheart:armor_levitation");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            for (var offset : PILLARS)
                level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
            level.setBlockAndUpdate(SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState());
            ((ExperienceSkullTile) level.getBlockEntity(SKULL)).setExperience(1000);
            essenceForAltar(level, ALTAR, EssenceRegistry.STILLNESS);
        });
        context.waitTicks(3);

        // Partly filled, the tesseract makes nothing
        server.runOnServer(minecraft ->
        {
            lay(minecraft);
            var partial = new ItemStack(SuperfluidContent.TESSERACT);
            TesseractItem.setHeld(partial, SuperfluidBalance.TESSERACT_CAPACITY - 1);
            altar(minecraft).setStack(player(minecraft), partial);
        });
        context.waitTicks(10);
        expect(server.computeOnServer(minecraft -> !altar(minecraft).isCrafting()), "A tesseract not quite full should not start the levitation module");

        server.runOnServer(minecraft ->
        {
            altar(minecraft).setStack(player(minecraft), ItemStack.EMPTY);
            altar(minecraft).setStack(player(minecraft), TesseractItem.full());
        });
        context.waitTicks(5);
        expect(server.computeOnServer(minecraft -> altar(minecraft).isCrafting()), "A brimming tesseract should start the levitation module");
        var module = VoidArmorContent.MODULES.get(ArmorModule.LEVITATION);
        waitFor(context, server, 2000, "The altar should give the levitation module", minecraft ->
                !altar(minecraft).isCrafting() && altar(minecraft).getStack().is(module));
    }

    private static void lay(MinecraftServer minecraft)
    {
        Item[] items = {VoidHeartItems.LEVITATION_BELT, Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE, VoidHeartItems.SEETHING_STEEL_INGOT,
                VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.VOID_PEARL};
        var level = minecraft.overworld();
        for (int index = 0; index < items.length; index++)
            ((VoidPillarTile) level.getBlockEntity(ALTAR.offset(PILLARS.get(index)))).setStack(new ItemStack(items[index]));
    }

    private static void showItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            var thread = new ItemStack(SuperfluidContent.TESSERACT);
            TesseractItem.setHeld(thread, SHARD_UNITS);
            var part = new ItemStack(SuperfluidContent.TESSERACT);
            TesseractItem.setHeld(part, 8 * SHARD_UNITS);
            player.getInventory().setItem(0, TesseractItem.full());
            player.getInventory().setItem(1, new ItemStack(SuperfluidContent.TESSERACT));
            player.getInventory().setItem(2, thread);
            player.getInventory().setItem(3, part);
            player.getInventory().setSelectedSlot(0);
            var level = minecraft.overworld();
            var dropped = List.of(new ItemStack(SuperfluidContent.TESSERACT), TesseractItem.full());
            for (int i = 0; i < dropped.size(); i++)
            {
                var item = new ItemEntity(level, 0.2 + i * 0.6, FLAT_FEET, -1.5, dropped.get(i), 0, 0, 0);
                item.setNoGravity(true);
                item.setPickUpDelay(32767);
                level.addFreshEntity(item);
            }
        });
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
        aimFrom(context, server, 0.5, FLAT_FEET, -4.5, 0.5, FLAT_FEET + 0.3, -1.5, 5);
        context.takeScreenshot("tesseract-in-hand-full");
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(1));
        context.waitTicks(10);
        context.takeScreenshot("tesseract-in-hand-empty");
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        context.takeScreenshot("tesseract-inventory");
        context.setScreen(() -> null);
        server.runCommand("kill @e[type=item]");
    }

    // Scene

    private static void hang(TestServerContext server, BlockPos pearl)
    {
        server.runCommand("setblock " + pearl.getX() + " " + (pearl.getY() + 2) + " " + pearl.getZ() + " voidheart:voidstone_bricks");
        server.runCommand("setblock " + pearl.getX() + " " + (pearl.getY() + 1) + " " + pearl.getZ() + " iron_chain[axis=y]");
        server.runCommand("setblock " + pearl.getX() + " " + pearl.getY() + " " + pearl.getZ() + " voidheart:hanging_rubrum_pearl");
    }

    private static void hangMature(TestServerContext server, BlockPos pearl)
    {
        hang(server, pearl);
    }

    private static void seed(MinecraftServer minecraft, BlockPos pos, UUID owner, CrystalKind kind, CrystalHabit habit)
    {
        var cradle = cradle(minecraft, pos);
        cradle.claim(owner);
        cradle.seed(kind, 4, habit);
    }

    private static HangingPearlTile pearl(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof HangingPearlTile pearl)
            return pearl;
        throw new AssertionError("Expected a hanging pearl at " + pos.toShortString());
    }

    private static CrystalCradleTile cradle(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof CrystalCradleTile cradle)
            return cradle;
        throw new AssertionError("Expected a crystal cradle at " + pos.toShortString());
    }

    private static VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }
}
