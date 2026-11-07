package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.InsightMemory;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.oil.Brews;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;
import net.voxelindustry.voidheart.common.content.oil.OilFlaskItem;
import net.voxelindustry.voidheart.common.content.oil.OilType;
import net.voxelindustry.voidheart.common.content.oil.SinVessel;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

/**
 * Shards in spare crucibles and sin-metal flasks change what the athanor brews, once refinement is researched: a stronger, longer searing coat, a
 * Stillwater in gold that lasts far longer and leaves the drinker hungry, a Bulwark in iron that drives out other brews, two thinner oils from copper,
 * and an Ember Draught that takes one Rubrum as its reagent and a second as a shard. A vessel refuses what it can't hold.
 */
public class RefinementClientTest implements FabricClientGameTest
{
    private static final int GROUND_Y = -60;
    private static final BlockPos INTAKE = new BlockPos(0, GROUND_Y, 0);
    private static final BlockPos CORE = INTAKE.above();
    private static final BlockPos ALEMBIC = CORE.above();
    private static final List<BlockPos> CRUCIBLES = List.of(CORE.east(), CORE.west(), CORE.north(), CORE.south());
    private static final int BREW_WAIT = 600;
    private static final int MEMORY_X = 20;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode survival @a");
            server.runOnServer(RefinementClientTest::build);
            server.runCommand("execute as @a run voidheart research grant voidheart:sulfur_rite");
            server.runCommand("execute as @a run voidheart research grant voidheart:quicksilver_rite");
            context.waitTicks(20);

            // Before refinement, a shard beside the reagents is only one crucible too many
            setUp(server, new ItemStack(Items.GLASS_BOTTLE), Items.SULFUR, VoidHeartItems.QUICKSILVER, VoidHeartItems.AZURIUM_SHARD);
            context.waitTicks(4 * AthanorTile.CHECK_INTERVAL);
            expect(!server.computeOnServer(minecraft -> athanor(minecraft).isBrewing()), "Expected no brew with a shard before refinement");
            server.runCommand("execute as @a run voidheart research grant voidheart:refinement");

            // Searing with both shards: a stronger coat for half again as many hits
            var searing = brew(context, server, new ItemStack(Items.GLASS_BOTTLE), Items.SULFUR, VoidHeartItems.QUICKSILVER, VoidHeartItems.AZURIUM_SHARD,
                    VoidHeartItems.RUBRUM_SHARD);
            expect(searing.is(VoidHeartItems.SEARING_OIL) && searing.getCount() == 1, "Expected one searing oil, got " + searing);
            var searingBrew = Brews.brewOf(searing);
            expect(searingBrew.empowered() && searingBrew.prolonged() && searingBrew.vessel().isEmpty(), "Expected a stronger, longer brew, got " + searingBrew);
            server.runOnServer(minecraft ->
            {
                for (var crucible : CRUCIBLES)
                    expect(crucible(minecraft, crucible).getStack().isEmpty(), "Expected the brew to use the reagents and both shards, " + crucible + " holds "
                            + crucible(minecraft, crucible).getStack());
                var coat = coatSword(player(minecraft), searing);
                expect(coat.charges() == 30 && coat.capacity() == 30 && coat.strong(), "Expected a strong coat of 30 hits, got " + coat);
            });
            showTooltip(context, server, searing, "Azurium", "Rubrum");

            // Stillwater in gold, with Rubrum: three times as long as plain, and hungry for as long
            var stillwater = brew(context, server, new ItemStack(VoidHeartItems.RAVENOUS_GOLD_FLASK), VoidHeartItems.QUICKSILVER, VoidHeartBlocks.STILL_KELP,
                    VoidHeartBlocks.STILL_ANEMONE, VoidHeartItems.RUBRUM_SHARD);
            expect(stillwater.is(VoidHeartItems.STILLWATER), "Expected stillwater from a gold flask, got " + stillwater);
            showTooltip(context, server, stillwater, "Ravenous Gold", "Rubrum");
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                stillwater.copy().finishUsingItem(minecraft.overworld(), player);
                var still = player.getEffect(VoidHeartEffects.STILLWATER);
                int expected = Elixirs.STILLWATER_TICKS * 3;
                expect(still != null && Math.abs(still.getDuration() - expected) <= 2, "Expected Stillwater for " + expected + " ticks, got " + still);
                var hunger = player.getEffect(MobEffects.HUNGER);
                expect(hunger != null && Math.abs(hunger.getDuration() - expected) <= 2, "Expected hunger for as long, got " + hunger);
                player.removeAllEffects();
            });

            // Bulwark in iron with Azurium: two levels up, and it drives out the Insight drunk before it
            var bulwark = brew(context, server, new ItemStack(VoidHeartItems.ARROGANT_IRON_FLASK), VoidHeartItems.QUICKSILVER, VoidHeartItems.LISTLESS_LEAD_INGOT,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.AZURIUM_SHARD);
            expect(bulwark.is(VoidHeartItems.BULWARK_ELIXIR), "Expected a bulwark from an iron flask, got " + bulwark);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.addEffect(new MobEffectInstance(VoidHeartEffects.INSIGHT, 600, 0, false, false));
                bulwark.copy().finishUsingItem(minecraft.overworld(), player);
                var absorption = player.getEffect(MobEffects.ABSORPTION);
                expect(absorption != null && absorption.getAmplifier() == Elixirs.BULWARK_AMPLIFIER + 2, "Expected Absorption two levels up, got " + absorption);
                expect(!player.hasEffect(VoidHeartEffects.INSIGHT), "Expected an iron brew to drive out Insight");
                player.removeAllEffects();
            });

            // Copper: two arcane oils for one, each with fewer hits
            var arcane = brew(context, server, new ItemStack(VoidHeartItems.COVETOUS_COPPER_FLASK), Items.SULFUR, VoidHeartItems.QUICKSILVER, Items.LAPIS_LAZULI);
            server.runCommand("execute as @a run voidheart research grant voidheart:kindred_oils");
            expect(arcane.isEmpty(), "Expected no arcane oil before its research, got " + arcane);
            arcane = brew(context, server, new ItemStack(VoidHeartItems.COVETOUS_COPPER_FLASK), Items.SULFUR, VoidHeartItems.QUICKSILVER, Items.LAPIS_LAZULI);
            expect(arcane.is(VoidHeartItems.ARCANE_OIL) && arcane.getCount() == 2, "Expected two arcane oils from copper, got " + arcane);
            var copperOil = arcane;
            server.runOnServer(minecraft ->
            {
                var coat = coatSword(player(minecraft), copperOil);
                expect(coat.type() == OilType.ARCANE && coat.charges() == 12, "Expected a thinner coat of 12 hits, got " + coat);
            });

            // Ember Draught takes Rubrum as a reagent, and a second Rubrum lengthens it
            var ember = brew(context, server, new ItemStack(Items.GLASS_BOTTLE), VoidHeartItems.QUICKSILVER, VoidHeartItems.RUBRUM_SHARD, VoidHeartBlocks.SULFIDE_CRUST,
                    VoidHeartItems.RUBRUM_SHARD);
            expect(ember.is(VoidHeartItems.EMBER_DRAUGHT) && Brews.brewOf(ember).prolonged() && !Brews.brewOf(ember).empowered(),
                    "Expected a longer ember draught, got " + ember + " " + Brews.brewOf(ember));

            memoriam(context, server);

            // Vessels refuse what they can't hold
            var refusedElixir = brew(context, server, new ItemStack(VoidHeartItems.SEETHING_STEEL_FLASK), VoidHeartItems.QUICKSILVER, VoidHeartBlocks.STILL_KELP,
                    VoidHeartBlocks.STILL_ANEMONE);
            expect(refusedElixir.isEmpty(), "Expected seething steel to refuse a drink, got " + refusedElixir);
            var refusedOil = brew(context, server, new ItemStack(VoidHeartItems.LISTLESS_LEAD_FLASK), Items.SULFUR, VoidHeartItems.QUICKSILVER);
            expect(refusedOil.isEmpty(), "Expected listless lead to refuse an oil, got " + refusedOil);
            var steelOil = brew(context, server, new ItemStack(VoidHeartItems.SEETHING_STEEL_FLASK), Items.SULFUR, VoidHeartItems.QUICKSILVER);
            expect(steelOil.is(VoidHeartItems.SEARING_OIL) && Brews.brewOf(steelOil).in(SinVessel.SEETHING_STEEL), "Expected a seething steel searing oil, got " + steelOil);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var coat = coatSword(player, steelOil);
                expect(coat.charges() == 30 && coat.wearing(), "Expected a wearing coat of 30 hits, got " + coat);
            });

            // The flasks in the inventory
            server.runCommand("clear @a");
            for (var vessel : SinVessel.values())
                server.runCommand("give @a voidheart:" + vessel.flaskName());
            server.runOnServer(minecraft -> player(minecraft).getInventory().add(stillwater.copy()));
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("refinement-inventory").withSize(1280, 720));
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
            context.waitTicks(5);
        }
    }

    /**
     * Insight brewed with Memoriam remembers: a husk seen in plain view keeps a fading silhouette once a wall hides it, gone after the memory runs out,
     * and plain Insight shows nothing of it.
     */
    private static void memoriam(ClientGameTestContext context, TestServerContext server)
    {
        var insight = brew(context, server, new ItemStack(Items.GLASS_BOTTLE), VoidHeartItems.QUICKSILVER, VoidHeartItems.WRIGGLING_BRAIN, Items.SPIDER_EYE,
                VoidHeartItems.MEMORIAM_SHARD);
        expect(insight.is(VoidHeartItems.INSIGHT_ELIXIR) && Brews.brewOf(insight).remembering(), "Expected a remembering Insight, got " + insight + " " + Brews.brewOf(insight));
        showTooltip(context, server, insight, "Memoriam");
        var refused = brew(context, server, new ItemStack(Items.GLASS_BOTTLE), VoidHeartItems.QUICKSILVER, VoidHeartBlocks.STILL_KELP, VoidHeartBlocks.STILL_ANEMONE,
                VoidHeartItems.MEMORIAM_SHARD);
        expect(refused.isEmpty(), "Expected Memoriam to change nothing but Insight, got " + refused);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            insight.copy().finishUsingItem(minecraft.overworld(), player);
            expect(player.hasEffect(VoidHeartEffects.INSIGHT) && player.hasEffect(VoidHeartEffects.RECOLLECTION), "Expected Insight and Recollection, got "
                    + player.getActiveEffects());
            var husk = net.minecraft.world.entity.EntityTypes.HUSK.create(minecraft.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            husk.setNoAi(true);
            husk.snapTo(MEMORY_X + 0.5, GROUND_Y, 6.5, 180, 0);
            husk.setYBodyRot(180);
            minecraft.overworld().addFreshEntity(husk);
        });
        server.runCommand("tp @a " + (MEMORY_X + 0.5) + " " + GROUND_Y + " 0.5 0 0");
        context.waitTicks(20);
        expect(context.computeOnClient(minecraft -> InsightMemory.remembered()) == 1, "Expected the husk in plain view remembered");
        expect(context.computeOnClient(minecraft -> InsightMemory.alpha(husk(minecraft))) == 0, "Expected no silhouette while the husk is in plain view");

        server.runCommand("fill " + (MEMORY_X - 3) + " " + GROUND_Y + " 3 " + (MEMORY_X + 3) + " " + (GROUND_Y + 3) + " 3 stone");
        context.waitTicks(2 * InsightMemory.CHECK_TICKS);
        float early = context.computeOnClient(minecraft -> InsightMemory.alpha(husk(minecraft)));
        expect(early > 0 && early <= InsightMemory.MEMORY_ALPHA, "Expected the hidden husk outlined, alpha " + early);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        context.takeScreenshot("memoriam-remembered");
        context.waitTicks((int) (InsightMemory.MEMORY_MILLIS / 50 * 0.7));
        float late = context.computeOnClient(minecraft -> InsightMemory.alpha(husk(minecraft)));
        expect(late > 0 && late < early, "Expected the outline fading, " + late + " after " + early);
        context.takeScreenshot("memoriam-fading");
        context.waitTicks((int) (InsightMemory.MEMORY_MILLIS / 50 * 0.4));
        expect(context.computeOnClient(minecraft -> InsightMemory.alpha(husk(minecraft))) == 0, "Expected the memory gone after its time");
        context.takeScreenshot("memoriam-gone");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);

        // Plain Insight remembers nothing
        server.runCommand("fill " + (MEMORY_X - 3) + " " + GROUND_Y + " 3 " + (MEMORY_X + 3) + " " + (GROUND_Y + 3) + " 3 air");
        server.runCommand("effect clear @a voidheart:recollection");
        context.waitTicks(20);
        server.runCommand("fill " + (MEMORY_X - 3) + " " + GROUND_Y + " 3 " + (MEMORY_X + 3) + " " + (GROUND_Y + 3) + " 3 stone");
        context.waitTicks(2 * InsightMemory.CHECK_TICKS);
        expect(context.computeOnClient(minecraft -> InsightMemory.alpha(husk(minecraft))) == 0, "Expected plain Insight to show nothing of the husk");
        server.runCommand("kill @e[type=husk]");
        server.runCommand("fill " + (MEMORY_X - 3) + " " + GROUND_Y + " 3 " + (MEMORY_X + 3) + " " + (GROUND_Y + 3) + " 3 air");
        server.runOnServer(minecraft -> player(minecraft).removeAllEffects());
    }

    private static net.minecraft.world.entity.monster.zombie.Husk husk(net.minecraft.client.Minecraft minecraft)
    {
        for (var entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof net.minecraft.world.entity.monster.zombie.Husk husk)
                return husk;
        }
        throw new AssertionError("Expected a husk on the client");
    }

    private static void build(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(INTAKE, VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
        level.setBlockAndUpdate(CORE, VoidHeartBlocks.ATHANOR.defaultBlockState());
        level.setBlockAndUpdate(ALEMBIC, VoidHeartBlocks.ALEMBIC.defaultBlockState());
        for (var crucible : CRUCIBLES)
        {
            level.setBlockAndUpdate(crucible.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(crucible, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
        }
    }

    /**
     * Fills the crucibles in order, empties the rest, then sets the base as the player.
     */
    private static void setUp(TestServerContext server, ItemStack base, ItemLike... reagents)
    {
        server.runOnServer(minecraft ->
        {
            var athanor = athanor(minecraft);
            athanor.setStack(null, ItemStack.EMPTY);
            for (int i = 0; i < CRUCIBLES.size(); i++)
                crucible(minecraft, CRUCIBLES.get(i)).setStack(i < reagents.length ? new ItemStack(reagents[i]) : ItemStack.EMPTY);
            athanor.setStack(player(minecraft), base.copy());
            // The aspects come straight in, as an intake would bring them
            athanor.receiveAspect(EssenceRegistry.FIERY, 10_000);
            athanor.receiveAspect(EssenceRegistry.STILLNESS, 10_000);
        });
    }

    /**
     * @return what the athanor brewed from these, or empty when it never started
     */
    private static ItemStack brew(ClientGameTestContext context, TestServerContext server, ItemStack base, ItemLike... reagents)
    {
        setUp(server, base, reagents);
        for (int tick = 0; tick < 4 * AthanorTile.CHECK_INTERVAL; tick++)
        {
            if (server.computeOnServer(minecraft -> athanor(minecraft).isBrewing()))
            {
                for (int wait = 0; wait < BREW_WAIT && server.computeOnServer(minecraft -> athanor(minecraft).isBrewing()); wait++)
                    context.waitTick();
                return server.computeOnServer(minecraft -> athanor(minecraft).getStack().copy());
            }
            context.waitTick();
        }
        server.runOnServer(minecraft -> athanor(minecraft).setStack(null, ItemStack.EMPTY));
        return ItemStack.EMPTY;
    }

    /**
     * Uses the flask on a fresh sword in the first hotbar slot, the way a click in the inventory does.
     */
    private static net.voxelindustry.voidheart.common.content.oil.WeaponOil coatSword(ServerPlayer player, ItemStack flask)
    {
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        var slot = player.inventoryMenu.getSlot(36);
        var used = flask.copyWithCount(1);
        ((OilFlaskItem) used.getItem()).overrideStackedOnOther(used, slot, ClickAction.SECONDARY, player);
        var coat = OilCoating.coatingOf(player.getInventory().getItem(0));
        expect(coat != null, "Expected the flask to coat the sword");
        return coat;
    }

    private static void showTooltip(ClientGameTestContext context, TestServerContext server, ItemStack stack, String... expected)
    {
        context.computeOnClient(minecraft ->
        {
            var lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream()
                    .map(line -> line.getString()).toList();
            for (var text : expected)
                expect(lines.stream().anyMatch(line -> line.contains(text)), "Expected \"" + text + "\" in the tooltip of " + stack + ", got " + lines);
            return null;
        });
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static AthanorTile athanor(MinecraftServer minecraft)
    {
        return (AthanorTile) minecraft.overworld().getBlockEntity(CORE);
    }

    private static CrucibleTile crucible(MinecraftServer minecraft, BlockPos pos)
    {
        return (CrucibleTile) minecraft.overworld().getBlockEntity(pos);
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
