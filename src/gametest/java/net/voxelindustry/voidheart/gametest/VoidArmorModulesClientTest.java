package net.voxelindustry.voidheart.gametest;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.voxelindustry.voidheart.client.armor.ArmorSight;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldState;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.LIMBS;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.BATTERY_CELL;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.EXOSKELETON_POWER;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.NIGHT_VISION;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.SPECTRAL_LENS;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.spawn;

/**
 * What the Void Armor's modules do while worn. A Shielding Apparatus in the frame's socket holds the wearer's shield, one worn on the body going dormant;
 * the frame refills it faster and pays by the point, stops once it has cut its regen, and the shield still takes hits. Exoskeleton power adds knockback.
 * Night vision lifts a dark room without the vanilla effect, and the spectral lens finds living things behind a wall, not those in plain view, and a Treant
 * asleep in its trunk.
 */
public class VoidArmorModulesClientTest implements FabricClientGameTest
{
    private static final String SLOT = "chest/shielding_apparatus";
    private static final long   HUM  = ArmorBalance.STANDING_DRAIN + ArmorBalance.EXOSKELETON_DRAIN;
    private static final int    ROOM = FLAT_FEET + 1;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            socketHoldsTheShield(context, server);
            sightsShowWhatTheyShould(context, server);
        }
    }

    private static ItemStack apparatus(ApparatusGrid.Placement... placements)
    {
        var grid = ApparatusGrid.EMPTY;
        for (var placement : placements)
            grid = grid.with(placement);
        var stack = new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS);
        stack.set(VoidHeartDataComponents.APPARATUS_GRID, grid);
        return stack;
    }

    private static ItemStack frame(ItemStack socketed)
    {
        var grids = ArmorGrids.EMPTY
                .with(HELM, new Placement(NIGHT_VISION, 0, 0, 0))
                .with(HELM, new Placement(SPECTRAL_LENS, 0, 1, 0))
                .with(LIMBS, new Placement(EXOSKELETON_POWER, 0, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 0, 0, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity());
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        slots.set(ArmorBalance.SOCKET_SLOT, socketed);
        frame.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots));
        return frame;
    }

    private static void stepIn(ClientGameTestContext context, TestServerContext server, ItemStack frame)
    {
        server.runOnServer(minecraft -> ArmorWear.stepIn(player(minecraft), frame, player(minecraft).position()));
        context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
        var phase = server.computeOnServer(minecraft ->
        {
            var worn = player(minecraft).getAttached(ArmorWear.WORN);
            return worn == null ? null : worn.phase();
        });
        expect(phase == WornArmor.Phase.WORN, "Expected the frame worn, got " + phase);
    }

    // The apparatus socket

    private static void socketHoldsTheShield(ClientGameTestContext context, TestServerContext server)
    {
        var worn = apparatus(new ApparatusGrid.Placement(ApparatusComponent.CELL, 0, 0, 0));
        var socketed = apparatus(new ApparatusGrid.Placement(ApparatusComponent.RESERVOIR, 0, 0, 0), new ApparatusGrid.Placement(ApparatusComponent.CELL, 2, 0, 0));
        float wornCapacity = ShieldingApparatus.stats(worn).capacity();
        var socketStats = ShieldingApparatus.stats(socketed);
        float socketCapacity = socketStats.capacity();
        expect(wornCapacity != socketCapacity, "Expected the two apparatus to hold different shields");

        server.runOnServer(minecraft -> expect(TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).set(worn.copy()),
                "Expected the worn apparatus to go into its slot"));
        context.waitTicks(3);
        float capacity = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).capacity());
        expect(capacity == wornCapacity, "Expected the body's apparatus to hold the shield before stepping in, capacity " + capacity);

        stepIn(context, server, frame(socketed));
        var state = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)));
        expect(state.capacity() == socketCapacity, "Expected the socketed apparatus to hold the shield inside the frame, capacity " + state.capacity());
        expect(state.points() < socketCapacity, "Expected the socketed shield to start empty, " + state.points() + " points");

        // Refilling from empty, with the wait over: the frame runs it faster and pays for each point
        int ticks = 60;
        server.runOnServer(minecraft -> player(minecraft).setAttached(ShieldingApparatus.SHIELD, new ShieldState(0, socketCapacity, 0)));
        long before = server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        context.waitTicks(ticks);
        float refilled = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).points());
        long drained = before - server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        float rate = socketStats.regenPerTick() * ArmorBalance.REGEN_BOOST;
        expect(rate * (ticks - 1) <= refilled && refilled <= rate * (ticks + 2) && refilled < socketCapacity,
                "Expected the frame to refill " + rate + " a tick, refilled " + refilled + " in " + ticks + " ticks");
        double paid = drained - HUM * ticks;
        double owed = refilled * ArmorBalance.REGEN_DRAIN_PER_POINT;
        expect(Math.abs(paid - owed) <= ArmorBalance.REGEN_DRAIN_PER_POINT + 2 * HUM,
                "Expected the frame to pay " + owed + " for the shield it refilled, paid " + paid + " above its hum");

        // With its regen cut the frame no longer refills it
        server.runCommand("execute as @a run voidheart armor charge 0.015");
        context.waitTicks(3);
        server.runOnServer(minecraft -> player(minecraft).setAttached(ShieldingApparatus.SHIELD, new ShieldState(0, socketCapacity, 0)));
        context.waitTicks(20);
        float cut = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).points());
        expect(cut == 0, "Expected no refill once the frame cut its regen, refilled " + cut);
        server.runCommand("execute as @a run voidheart armor charge 1");
        context.waitTicks(3);

        // The socketed shield takes a hit before the wearer does
        float left = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = minecraft.overworld();
            player.setAttached(ShieldingApparatus.SHIELD, new ShieldState(socketCapacity, socketCapacity, 0));
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            zombie.snapTo(player.getX() + 1, player.getY(), player.getZ() + 2, 0, 0);
            level.addFreshEntity(zombie);
            player.damageCooldownTime = 0;
            player.hurtServer(level, player.damageSources().mobAttack(zombie), 2);
            zombie.discard();
            return ShieldingApparatus.state(player).points();
        });
        float health = server.computeOnServer(minecraft -> player(minecraft).getHealth());
        expect(Math.abs(left - (socketCapacity - 2)) < 0.01F && health == 20, "Expected the socketed shield to take the hit, " + left + " left, health " + health);

        double knockback = server.computeOnServer(minecraft -> player(minecraft).getAttributeValue(Attributes.ATTACK_KNOCKBACK));
        expect(Math.abs(knockback - ArmorBalance.EXOSKELETON_KNOCKBACK) < 1.0E-6, "Expected exoskeleton power to add knockback, got " + knockback);

        // Out of the frame the body's apparatus takes over again, starting empty
        server.runCommand("execute as @a run voidheart armor leave");
        context.waitTicks(3);
        state = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)));
        expect(state.capacity() == wornCapacity && state.points() == 0,
                "Expected the body's apparatus back and empty once out of the frame, capacity " + state.capacity() + ", points " + state.points());

        server.runOnServer(minecraft -> TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).set(ItemStack.EMPTY));
        server.runCommand("kill @e[type=!player]");
        context.waitTicks(3);
    }

    // The sights

    private static void sightsShowWhatTheyShould(ClientGameTestContext context, TestServerContext server)
    {
        // Creative, so the dormant Treant doesn't wake
        server.runCommand("gamemode creative @a");
        server.runCommand("time set midnight");
        // A shut room of dark stone with a few things in it
        server.runCommand("fill -7 " + FLAT_FEET + " -4 7 " + (FLAT_FEET + 6) + " 13 deepslate_bricks hollow");
        server.runCommand("fill -3 " + ROOM + " 9 -1 " + (ROOM + 1) + " 9 bookshelf");
        server.runCommand("setblock 2 " + ROOM + " 7 crafting_table");
        server.runCommand("fill 4 " + ROOM + " 10 4 " + (ROOM + 2) + " 10 oak_log");
        server.runCommand("tp @a 0.5 " + ROOM + " 0.5 0 10");
        context.waitTicks(5);
        stepIn(context, server, frame(ItemStack.EMPTY));
        context.waitTicks(10);
        context.takeScreenshot("void-armor-night-vision-off");

        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.waitTicks(ArmorSight.FADE_TICKS + 4);
        float sight = context.computeOnClient(minecraft -> ArmorSight.nightVision(1));
        boolean effect = context.computeOnClient(minecraft -> minecraft.player.hasEffect(MobEffects.NIGHT_VISION));
        expect(sight == 1 && !effect, "Expected the frame's night vision on without the vanilla effect, sight " + sight + ", effect " + effect);
        context.takeScreenshot("void-armor-night-vision-on");
        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.waitTicks(ArmorSight.FADE_TICKS + 4);
        sight = context.computeOnClient(minecraft -> ArmorSight.nightVision(1));
        expect(sight == 0, "Expected night vision to fade out once switched off, sight " + sight);

        // Out in the open: a wall with a zombie and a cow behind it, a zombie beside it in plain view and a Treant asleep in a trunk in front
        server.runCommand("fill -7 " + FLAT_FEET + " -4 7 " + (FLAT_FEET + 6) + " 13 air");
        server.runCommand("fill -4 " + FLAT_FEET + " 6 4 " + (FLAT_FEET + 3) + " 6 stone_bricks");
        server.runCommand("setblock -3 " + FLAT_FEET + " 4 voidheart:dormant_treant");
        server.runCommand("fill -3 " + (FLAT_FEET + 1) + " 4 -3 " + (FLAT_FEET + 2) + " 4 dark_oak_log");
        server.runCommand("time set noon");
        server.runOnServer(minecraft ->
        {
            spawn(minecraft, EntityTypes.ZOMBIE, -1.5, FLAT_FEET, 9.5).setNoAi(true);
            spawn(minecraft, EntityTypes.COW, 2.5, FLAT_FEET, 9.5).setNoAi(true);
            spawn(minecraft, EntityTypes.ZOMBIE, 6.5, FLAT_FEET, 7.5).setNoAi(true);
        });
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 5");
        context.waitTicks(10);

        context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
        context.waitTicks(ArmorSight.FADE_TICKS + ArmorSight.CHECK_TICKS + 4);
        int hidden = context.computeOnClient(minecraft -> ArmorSight.hidden());
        int plain = context.computeOnClient(minecraft ->
        {
            int shown = 0;
            for (var entity : minecraft.level.entitiesForRendering())
                if (entity instanceof net.minecraft.world.entity.LivingEntity living && entity.getX() > 5 && ArmorSight.silhouette(living, 1) != 0)
                    shown++;
            return shown;
        });
        expect(hidden == 2, "Expected the lens to find the zombie and the cow behind the wall, found " + hidden);
        expect(plain == 0, "Expected no silhouette on the zombie in plain view");
        context.takeScreenshot("void-armor-spectral-noon");

        server.runCommand("time set midnight");
        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.waitTicks(ArmorSight.FADE_TICKS + 4);
        context.takeScreenshot("void-armor-spectral-night");

        context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.waitTicks(ArmorSight.FADE_TICKS + 4);
        hidden = context.computeOnClient(minecraft -> ArmorSight.hidden());
        expect(hidden == 0, "Expected the lens to show nothing once switched off, still " + hidden);

        server.runCommand("execute as @a run voidheart armor leave");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("fill -7 " + FLAT_FEET + " -4 7 " + (FLAT_FEET + 6) + " 13 air");
        server.runCommand("gamemode survival @a");
        context.waitTicks(3);
    }
}
