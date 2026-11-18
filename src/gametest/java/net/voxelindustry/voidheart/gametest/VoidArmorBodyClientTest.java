package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.LoadShedding;
import net.voxelindustry.voidheart.common.content.armor.ParkedArmor;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

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
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * Inside the Void Armor, with every module fitted. The frame spends its charge standing, faster with its sights on, by the block walked and by the powered
 * jump, and its wearer doesn't tire from moving. Sneak neither crouches nor holds the wearer at an edge. The frame sinks in water and walks the bottom,
 * rides nothing, lies in no bed and won't glide, and its legs take a fall out of its battery. Run low, it cuts its spectral lens, night vision and
 * exoskeleton power in turn, then goes limp: very slow, no jump, no sprint. Holding sneak still lets the wearer out.
 */
public class VoidArmorBodyClientTest implements FabricClientGameTest
{
    private static final long HUM = ArmorBalance.STANDING_DRAIN + ArmorBalance.EXOSKELETON_DRAIN;

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

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var frame = everything();
                expect(VoidArmorItem.stats(frame).exoskeleton() && VoidArmorItem.stats(frame).spectralLens(), "Expected the test frame to have every module working");
                ArmorWear.stepIn(player, frame, player.position());
            });
            context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
            expect(phase(server) == WornArmor.Phase.WORN, "Expected the frame worn, got " + phase(server));
            double speed = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.MOVEMENT_SPEED));
            double jump = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.JUMP_STRENGTH));
            double expectedSpeed = 0.1 * (1 + ArmorBalance.FRAME_SPEED + ArmorBalance.EXOSKELETON_SPEED);
            expect(Math.abs(speed - expectedSpeed) < 1.0E-6 && jump > 0.42, "Expected the wearer free to move once stepped in, speed " + speed + ", jump " + jump);

            drains(context, server);
            sneakNeitherCrouchesNorHolds(context, server);
            movingFeedsOnCharge(context, server);
            sinksInWater(context, server);
            ridesAndSleepsNowhere(context, server);
            fallsComeOutOfTheBattery(context, server);
            shedsThenGoesLimp(context, server);
            stepsOutLimp(context, server);
        }
    }

    private static ItemStack everything()
    {
        var grids = ArmorGrids.EMPTY
                .with(HELM, new Placement(NIGHT_VISION, 0, 0, 0))
                .with(HELM, new Placement(SPECTRAL_LENS, 0, 1, 0))
                .with(LIMBS, new Placement(EXOSKELETON_POWER, 0, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 0, 0, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity());
        return frame;
    }

    private static void drains(ClientGameTestContext context, TestServerContext server)
    {
        long standing = drainedOver(context, server, 40);
        expect(Math.abs(standing - 40 * HUM) <= 3 * HUM, "Expected the frame to hum " + HUM + " a tick standing, drained " + standing + " in 40 ticks");

        server.runCommand("execute as @a run voidheart armor toggle night_vision");
        server.runCommand("execute as @a run voidheart armor toggle spectral_lens");
        context.waitTicks(2);
        var load = server.computeOnServer(minecraft -> ArmorWear.load(player(minecraft)));
        expect(load.nightVision() && load.spectral(), "Expected both sights switched on, got " + load);
        long sights = HUM + ArmorBalance.NIGHT_VISION_DRAIN + ArmorBalance.SPECTRAL_DRAIN;
        long lit = drainedOver(context, server, 40);
        expect(Math.abs(lit - 40 * sights) <= 3 * sights, "Expected the sights to add to the hum, drained " + lit + " in 40 ticks");

        long client = context.computeOnClient(minecraft -> ArmorWear.charge(minecraft.player));
        long full = server.computeOnServer(minecraft -> VoidArmorItem.stats(ArmorWear.worn(player(minecraft)).frame()).capacity());
        long now = server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        expect(client < full && client >= now && client - now <= (ArmorBalance.CHARGE_SYNC + 2) * sights,
                "Expected the client told of the charge every second, client " + client + ", server " + now);
    }

    /**
     * Sneak held for less than it takes to step out walks the wearer over the edge of a pit, standing.
     */
    private static void sneakNeitherCrouchesNorHolds(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill 3 " + (FLAT_FEET - 3) + " -1 5 " + (FLAT_FEET - 1) + " 1 air");
        server.runCommand("tp @a 2.5 " + FLAT_FEET + " 0.5 -90 0");
        context.waitTicks(5);
        context.getInput().holdKey(options -> options.keyShift);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(6);
        boolean crouching = context.computeOnClient(minecraft -> minecraft.player.isCrouching());
        var pose = server.computeOnServer(minecraft -> player(minecraft).getPose());
        context.waitTicks(4);
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(10);
        expect(!crouching && pose == Pose.STANDING, "Expected sneak not to crouch the wearer, crouching " + crouching + ", pose " + pose);
        double y = server.computeOnServer(minecraft -> player(minecraft).getY());
        expect(y < FLAT_FEET - 1, "Expected sneak not to hold the wearer at the edge, feet at " + y);
        expect(phase(server) == WornArmor.Phase.WORN, "Expected a sneak shorter than the hold to keep the frame on, got " + phase(server));
    }

    private static void movingFeedsOnCharge(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 10.5 0 0");
        context.waitTicks(5);
        server.runOnServer(minecraft -> setExhaustion(player(minecraft).getFoodData(), 0));
        double startZ = server.computeOnServer(minecraft -> player(minecraft).getZ());
        long sprinted = drainedWhile(context, server, 20, options -> options.keyUp, options -> options.keySprint);
        double walked = server.computeOnServer(minecraft -> player(minecraft).getZ()) - startZ;
        expect(walked > 3, "Expected the wearer to sprint ahead, moved " + walked);
        expect(sprinted > 20 * HUM + 2 * ArmorBalance.SPRINT_DRAIN_PER_BLOCK, "Expected sprinting to cost by the block, drained " + sprinted + " over " + walked);

        long jumped = drainedWhile(context, server, 30, options -> options.keyJump);
        expect(jumped >= 30 * HUM + ArmorBalance.POWERED_JUMP_DRAIN, "Expected a powered jump to cost its charge, drained " + jumped + " in 30 ticks");
        float exhaustion = server.computeOnServer(minecraft -> exhaustion(player(minecraft).getFoodData()));
        expect(exhaustion == 0, "Expected a charged frame to spare its wearer's hunger, exhaustion " + exhaustion);
    }

    /**
     * Held jump doesn't lift the frame, and it walks the bottom of a pool three deep without swimming.
     */
    private static void sinksInWater(ClientGameTestContext context, TestServerContext server)
    {
        int floor = FLAT_FEET - 3;
        server.runCommand("fill 10 " + floor + " -3 14 " + (FLAT_FEET - 1) + " 3 water");
        server.runCommand("tp @a 12.5 " + (FLAT_FEET - 1.5) + " 0.5 -90 0");
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(30);
        context.getInput().releaseKey(options -> options.keyJump);
        double y = server.computeOnServer(minecraft -> player(minecraft).getY());
        expect(y < floor + 0.2, "Expected the frame to sink to the bottom with jump held, feet at " + y);

        context.getInput().holdKey(options -> options.keySprint);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        boolean swimming = server.computeOnServer(minecraft -> player(minecraft).isSwimming() || player(minecraft).isSprinting());
        boolean clientSwimming = context.computeOnClient(minecraft -> minecraft.player.isSwimming() || minecraft.player.isSprinting());
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keySprint);
        expect(!swimming && !clientSwimming, "Expected the wearer neither to swim nor sprint in water");
        server.runCommand("fill 10 " + floor + " -3 14 " + (FLAT_FEET - 1) + " 3 air");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 20.5 0 0");
        context.waitTicks(5);
    }

    private static void ridesAndSleepsNowhere(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("summon minecraft:oak_boat 2.5 " + FLAT_FEET + " 20.5");
        context.waitTicks(3);
        server.runCommand("execute as @a run ride @s mount @e[type=minecraft:oak_boat,limit=1]");
        context.waitTicks(3);
        boolean riding = server.computeOnServer(minecraft -> player(minecraft).isPassenger());
        expect(!riding, "Expected the wearer to ride nothing");
        server.runCommand("kill @e[type=minecraft:oak_boat]");

        var head = new BlockPos(4, FLAT_FEET, 23);
        server.runCommand("setblock 4 " + FLAT_FEET + " 23 minecraft:red_bed[part=head,facing=north]");
        server.runCommand("setblock 4 " + FLAT_FEET + " 24 minecraft:red_bed[part=foot,facing=north]");
        server.runCommand("time set midnight");
        server.runCommand("tp @a 2.5 " + FLAT_FEET + " 23.5 -90 0");
        context.waitTicks(3);
        server.runOnServer(minecraft -> GameTestKit.useOn(player(minecraft), head));
        context.waitTicks(3);
        boolean sleeping = server.computeOnServer(minecraft -> player(minecraft).isSleeping());
        server.runCommand("time set noon");
        expect(!sleeping, "Expected the wearer unable to lie down in a bed");
    }

    private static void fallsComeOutOfTheBattery(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
        server.runCommand("tp @a 0.5 " + (FLAT_FEET + 20) + " 30.5 0 0");
        context.waitTicks(4);
        long before = server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        boolean glided = server.computeOnServer(minecraft -> player(minecraft).tryToStartFallFlying());
        context.getInput().pressKey(options -> options.keyJump);
        context.waitTicks(3);
        boolean flying = context.computeOnClient(minecraft -> minecraft.player.isFallFlying());
        expect(!glided && !flying, "Expected the frame unable to glide with an elytra on");
        waitFor(context, server, 80, "Expected the wearer to land", minecraft -> player(minecraft).onGround());
        context.waitTicks(3);
        float health = server.computeOnServer(minecraft -> player(minecraft).getHealth());
        long drained = before - server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        expect(health == 20, "Expected the frame's legs to take a 20 block fall, health " + health);
        expect(drained >= 10 * ArmorBalance.FALL_DRAIN_PER_POINT, "Expected the fall paid from the battery, drained " + drained);
        server.runCommand("item replace entity @a armor.chest with minecraft:air");
    }

    private static void shedsThenGoesLimp(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 40.5 0 0");
        charge(context, server, 0.12F);
        var load = server.computeOnServer(minecraft -> ArmorWear.load(player(minecraft)));
        expect(!load.spectral() && load.nightVision() && load.exoskeleton(), "Expected the spectral lens cut first, got " + load);
        charge(context, server, 0.07F);
        load = server.computeOnServer(minecraft -> ArmorWear.load(player(minecraft)));
        double hit = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.ATTACK_DAMAGE));
        expect(!load.nightVision() && load.exoskeleton() && hit == 1 + ArmorBalance.EXOSKELETON_HIT, "Expected night vision cut next, got " + load + ", hit " + hit);
        charge(context, server, 0.03F);
        load = server.computeOnServer(minecraft -> ArmorWear.load(player(minecraft)));
        hit = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.ATTACK_DAMAGE));
        double speed = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.MOVEMENT_SPEED));
        expect(!load.exoskeleton() && hit == 1 && Math.abs(speed - 0.1 * (1 + ArmorBalance.FRAME_SPEED)) < 1.0E-6,
                "Expected exoskeleton power cut last, got " + load + ", hit " + hit + ", speed " + speed);

        charge(context, server, 0);
        var stage = server.computeOnServer(minecraft -> ArmorWear.stage(player(minecraft)));
        boolean clientLimp = context.computeOnClient(minecraft -> ArmorWear.limp(minecraft.player));
        speed = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.MOVEMENT_SPEED));
        double jump = attribute(server, minecraft -> player(minecraft).getAttributeValue(Attributes.JUMP_STRENGTH));
        expect(stage == LoadShedding.Stage.LIMP && clientLimp, "Expected an empty frame limp on both sides, server " + stage + ", client " + clientLimp);
        expect(Math.abs(speed - 0.1 * (1 - ArmorBalance.LIMP_SLOW)) < 1.0E-6 && jump == 0, "Expected a limp frame very slow and unable to jump, speed " + speed + ", jump " + jump);

        context.getInput().holdKey(options -> options.keySprint);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        boolean sprinting = context.computeOnClient(minecraft -> minecraft.player.isSprinting());
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keySprint);
        expect(!sprinting, "Expected a limp frame unable to sprint");
    }

    private static void stepsOutLimp(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_HOLD_TICKS + 2);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_TICKS + 3);
        int parked = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ParkedArmor.class,
                new AABB(player(minecraft).blockPosition()).inflate(4)).size());
        expect(phase(server) == null && parked == 1, "Expected holding sneak to let the wearer out of a limp frame, parked " + parked);
    }

    // Helpers

    private static void charge(ClientGameTestContext context, TestServerContext server, float fraction)
    {
        server.runCommand("execute as @a run voidheart armor charge " + fraction);
        context.waitTicks(3);
    }

    private static long drainedOver(ClientGameTestContext context, TestServerContext server, int ticks)
    {
        long before = server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        context.waitTicks(ticks);
        return before - server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
    }

    @SafeVarargs
    private static long drainedWhile(ClientGameTestContext context, TestServerContext server, int ticks, Function<Options, KeyMapping>... keys)
    {
        long before = server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
        for (var key : keys)
            context.getInput().holdKey(key);
        context.waitTicks(ticks);
        for (var key : keys)
            context.getInput().releaseKey(key);
        context.waitTicks(4);
        return before - server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
    }

    private static double attribute(TestServerContext server, Function<MinecraftServer, Double> read)
    {
        return server.computeOnServer(read::apply);
    }

    private static float exhaustion(FoodData food)
    {
        try
        {
            var field = FoodData.class.getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            return field.getFloat(food);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("No exhaustion level on FoodData", e);
        }
    }

    private static void setExhaustion(FoodData food, float value)
    {
        try
        {
            var field = FoodData.class.getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            field.setFloat(food, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("No exhaustion level on FoodData", e);
        }
    }

    private static WornArmor.@Nullable Phase phase(TestServerContext server)
    {
        return server.computeOnServer(minecraft ->
        {
            var worn = player(minecraft).getAttached(ArmorWear.WORN);
            return worn == null ? null : worn.phase();
        });
    }
}
