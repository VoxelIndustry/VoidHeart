package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.ArcaneBolt;
import net.voxelindustry.voidheart.common.content.mob.ArcaneEye;
import net.voxelindustry.voidheart.common.content.mob.ArcaneStalk;
import net.voxelindustry.voidheart.common.content.mob.EyeMaw;
import net.voxelindustry.voidheart.common.content.mob.LargeEyeMaw;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the eyes of the Still Depths do against a real player: flocking, lunging, spitting, stalking and the bolts they fire.
 */
public class VoidEyeBehaviourClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int FLOCK = 6;
    private static final int LOOT_ROLLS = 200;
    private static final float HARD_SCALING = 1.5F;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("difficulty hard");
            server.runCommand("gamemode survival @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(VoidEyeBehaviourClientTest::checkNumbers);
            checkLoot(context, server);
            checkPeaceful(context, server);
            checkFlockInABox(context, server);
            checkLunge(context, server);
            checkAroundAWall(context, server);
            checkLargeMaw(context, server);
            checkStalking(context, server);
            checkFireRange(context, server);
            checkBolt(context, server);
            prepareSavedState(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            server.runOnServer(minecraft ->
            {
                var spat = minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive);
                expect(spat.size() == 2, "Both maws should be saved, found " + spat.size());
                expect(spat.stream().filter(EyeMaw::wasSpat).count() == 1, "Being spat should be saved");
                var large = first(minecraft, VoidHeartEntities.LARGE_EYE_MAW);
                expect(large.aliveSpat(minecraft.overworld()) == 1, "A large maw should remember its brood");
            });
        }
    }

    private static void checkNumbers(MinecraftServer minecraft)
    {
        record Stats(EntityType<? extends Mob> type, double health, double damage)
        {
        }
        var level = minecraft.overworld();
        for (var stats : List.of(
                new Stats(VoidHeartEntities.EYE_MAW, 10, 5),
                new Stats(VoidHeartEntities.LARGE_EYE_MAW, 60, 0),
                new Stats(VoidHeartEntities.ARCANE_EYE, 24, 6)))
        {
            var mob = stats.type().create(level, EntitySpawnReason.COMMAND);
            expect(mob.getAttributeValue(Attributes.MAX_HEALTH) == stats.health(), stats.type() + " health");
            expect(mob.getAttributeValue(Attributes.ATTACK_DAMAGE) == stats.damage(), stats.type() + " damage");
            expect(!stats.type().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE), stats.type() + " is no undead");
            expect(!stats.type().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS), stats.type() + " is no arthropod");
            expect(SpawnEggItem.byId(stats.type()).isPresent(), stats.type() + " spawn egg");
            expect(minecraft.reloadableRegistries().getLootTable(stats.type().getDefaultLootTable().orElseThrow()) != LootTable.EMPTY, stats.type() + " loot table");
        }

        var maw = VoidHeartEntities.EYE_MAW.create(level, EntitySpawnReason.COMMAND);
        var arcane = VoidHeartEntities.ARCANE_EYE.create(level, EntitySpawnReason.COMMAND);
        expect(arcane.getAttributeValue(Attributes.MOVEMENT_SPEED) < maw.getAttributeValue(Attributes.MOVEMENT_SPEED) / 2, "Arcane Eyes should be slower than maws");
    }

    /**
     * Rolled over and over, so a drop rate can be told apart without failing by bad luck. A spat maw must never pay out.
     */
    private static void checkLoot(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, -300.5, FEET, 0.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);

            int dropped = 0;
            for (int roll = 0; roll < LOOT_ROLLS; roll++)
                dropped += shardsFrom(minecraft, VoidHeartEntities.EYE_MAW, false);
            double share = dropped / (double) LOOT_ROLLS;
            expect(share > 0.15 && share < 0.35, "A small maw should drop a shard about a quarter of the time, got " + share);

            for (int roll = 0; roll < 40; roll++)
                expect(shardsFrom(minecraft, VoidHeartEntities.EYE_MAW, true) == 0, "A spat maw should drop nothing");

            var maw = VoidHeartEntities.EYE_MAW.create(level, EntitySpawnReason.COMMAND);
            expect(maw.getExperienceReward(level, player) > 0, "A placed maw should give experience");
            maw.setSpat();
            expect(maw.getExperienceReward(level, player) == 0, "A spat maw should give no experience");

            for (int roll = 0; roll < 12; roll++)
            {
                int large = shardsFrom(minecraft, VoidHeartEntities.LARGE_EYE_MAW, false);
                expect(large >= 3 && large <= 5, "A large maw should drop 3-5 shards, got " + large);
                int arcane = shardsFrom(minecraft, VoidHeartEntities.ARCANE_EYE, false);
                expect(arcane >= 1 && arcane <= 2, "An Arcane Eye should drop 1-2 shards, got " + arcane);
            }
        });
        clearScene(server);
    }

    private static int shardsFrom(MinecraftServer minecraft, EntityType<? extends Mob> type, boolean spat)
    {
        var level = minecraft.overworld();
        var mob = spawn(minecraft, type, -300.5, FEET + 40, 20.5);
        mob.setNoAi(true);
        if (spat && mob instanceof EyeMaw maw)
            maw.setSpat();
        mob.kill(level);

        int shards = 0;
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(-310, FEET + 30, 10, -290, FEET + 50, 30)))
        {
            expect(item.getItem().is(VoidHeartItems.ENDER_SHARD), "Only ender shards should drop, got " + item.getItem());
            shards += item.getItem().getCount();
            item.discard();
        }
        mob.discard();
        return shards;
    }

    private static void checkPeaceful(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, -200.5, FEET, 0.5);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.EYE_MAW, -200.5, FEET + 4, 8.5).setDeltaMovement(0.3, 0, 0));
        context.waitTicks(5);
        server.runCommand("difficulty peaceful");
        context.waitTicks(5);
        var at = server.computeOnServer(minecraft -> first(minecraft, VoidHeartEntities.EYE_MAW).position());
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var maw = first(minecraft, VoidHeartEntities.EYE_MAW);
            expect(maw.isDormant(), "A maw should lie dormant in Peaceful");
            expect(maw.position().distanceTo(at) < 0.01, "A dormant maw should hang still, moved " + maw.position().distanceTo(at));
        });
        server.runCommand("difficulty hard");
        context.waitTicks(5);
        server.runOnServer(minecraft -> expect(!first(minecraft, VoidHeartEntities.EYE_MAW).isDormant(), "A maw should wake with the difficulty"));
        clearScene(server);
    }

    /**
     * With nothing to hunt, a flock drifts about a small sealed room: airborne, together, moving and on the inside.
     */
    private static void checkFlockInABox(ClientGameTestContext context, TestServerContext server)
    {
        int x = -100;
        int top = FEET + 8;
        server.runCommand("gamemode creative @a");
        tp(server, x + 0.5, top + 3, 0.5);
        context.waitTicks(20);
        server.runCommand("fill " + (x - 8) + " " + GROUND + " -8 " + (x + 8) + " " + top + " 8 voidheart:voidstone_bricks hollow");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            for (int i = 0; i < FLOCK; i++)
                spawn(minecraft, VoidHeartEntities.EYE_MAW, x + 0.5 + i * 0.4, FEET + 3 + i % 3, 0.5 + i * 0.3);
        });
        context.waitTicks(10);
        var startAt = server.computeOnServer(minecraft -> first(minecraft, VoidHeartEntities.EYE_MAW).position());

        for (int step = 0; step < 16; step++)
        {
            context.waitTicks(20);
            server.runOnServer(minecraft ->
            {
                var maws = minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive);
                expect(maws.size() == FLOCK, "The flock should stay whole, found " + maws.size());
                var center = Vec3.ZERO;
                for (var maw : maws)
                {
                    center = center.add(maw.position());
                    expect(maw.getY() >= FEET && maw.getY() < top, "A maw should stay between floor and ceiling, at " + maw.position());
                    expect(Math.abs(maw.getX() - (x + 0.5)) < 8 && Math.abs(maw.getZ() - 0.5) < 8, "A maw should stay inside, at " + maw.position());
                    expect(maw.getTarget() == null, "Creative players should go unnoticed");
                }
                center = center.scale(1.0 / maws.size());
                for (var maw : maws)
                    expect(maw.position().distanceTo(center) < 7, "The flock should stay together, one is " + maw.position().distanceTo(center) + " off");
            });
        }
        server.runOnServer(minecraft -> expect(first(minecraft, VoidHeartEntities.EYE_MAW).position().distanceTo(startAt) > 0.5, "The flock should be moving"));

        server.runCommand("effect give @a night_vision 30 0 true");
        tp(server, x + 0.5, FEET + 1, 6.5);
        context.waitTicks(10);
        aimCamera(server, x + 0.5, FEET + 1, 6.5, x + 0.5, FEET + 3, 0.5);
        context.waitTicks(5);
        context.takeScreenshot("eye-flock-idle");
        clearScene(server);
        server.runCommand("effect clear @a night_vision");
        server.runCommand("fill " + (x - 8) + " " + GROUND + " -8 " + (x + 8) + " " + top + " 8 air");
        server.runCommand("fill " + (x - 8) + " " + GROUND + " -8 " + (x + 8) + " " + GROUND + " 8 grass_block");
        server.runCommand("gamemode survival @a");
    }

    private static void checkLunge(ClientGameTestContext context, TestServerContext server)
    {
        int x = 0;
        tp(server, x + 0.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            for (int i = 0; i < FLOCK; i++)
                spawn(minecraft, VoidHeartEntities.EYE_MAW, x + 6.5 + i * 0.5, FEET + 3, 0.5 + i * 0.5);
        });

        waitFor(context, server, 120, "The flock should notice the player", minecraft ->
                minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).stream().allMatch(maw -> maw.getTarget() != null));
        waitFor(context, server, 200, "A maw should open up", minecraft ->
                minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).stream().anyMatch(EyeMaw::jawsOpen));
        var lunger = server.computeOnServer(minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, EyeMaw::jawsOpen).stream().findFirst().map(Entity::position).orElse(Vec3.ZERO));
        aimCamera(server, x + 0.5, FEET, 0.5, lunger.x, lunger.y + 0.3, lunger.z);
        context.waitTicks(3);
        context.takeScreenshot("eye-maw-lunging");

        // Never more than a few at once, and each opens before it dashes
        int[] mostLunging = { 0 };
        var dashed = new HashSet<UUID>();
        boolean bitten = false;
        var opened = new HashSet<UUID>();
        for (int tick = 0; tick < 300 && !bitten; tick++)
        {
            bitten = server.computeOnServer(minecraft ->
            {
                var maws = minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive);
                int lunging = 0;
                for (var maw : maws)
                {
                    if (maw.isLunging())
                        lunging++;
                    if (maw.phase() == EyeMaw.Phase.OPENING)
                        opened.add(maw.getUUID());
                    if (maw.phase() == EyeMaw.Phase.DASH)
                    {
                        dashed.add(maw.getUUID());
                        expect(opened.contains(maw.getUUID()) || maw.jawsOpen(), "A maw should open before it dashes");
                        expect(maw.jawsOpen(), "A dashing maw should have its jaws open");
                    }
                }
                mostLunging[0] = Math.max(mostLunging[0], lunging);
                return player(minecraft).getHealth() < player(minecraft).getMaxHealth();
            });
            if (tick % 40 == 20)
            {
                var near = server.computeOnServer(minecraft -> nearest(minecraft).position());
                aimCamera(server, x + 0.5, FEET, 0.5, near.x, near.y + 0.3, near.z);
                context.takeScreenshot("eye-flock-hunting-" + tick);
            }
            context.waitTick();
        }
        expect(bitten, "A lunge should reach a player standing still");
        expect(!dashed.isEmpty(), "A maw should have dashed");
        System.out.println("[VoidEyes] most lunging at once: " + mostLunging[0]);
        expect(mostLunging[0] <= EyeMaw.MAX_LUNGING + 1, "Lunges should come in small waves, saw " + mostLunging[0]);
        server.runOnServer(minecraft ->
        {
            float lost = player(minecraft).getMaxHealth() - player(minecraft).getHealth();
            expect(lost == 5 * HARD_SCALING, "A bite should deal 5 damage, scaled by Hard, lost " + lost);
        });
        clearScene(server);
        server.runOnServer(VoidEyeBehaviourClientTest::heal);
    }

    /**
     * A wall with one gap at its far end, and the player on the other side: the flock has to find the way.
     */
    private static void checkAroundAWall(ClientGameTestContext context, TestServerContext server)
    {
        int x = 100;
        tp(server, x + 0.5, FEET, 6.5);
        context.waitTicks(20);
        server.runCommand("fill " + (x - 12) + " " + FEET + " 0 " + (x + 3) + " " + (FEET + 8) + " 0 stone");
        server.runCommand("fill " + (x - 12) + " " + (FEET + 9) + " -12 " + (x + 12) + " " + (FEET + 9) + " 12 glass");
        server.runCommand("fill " + (x - 12) + " " + FEET + " -12 " + (x - 12) + " " + (FEET + 8) + " 12 stone");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            for (int i = 0; i < 3; i++)
            {
                var maw = spawn(minecraft, VoidHeartEntities.EYE_MAW, x - 2.5 + i, FEET + 3, -5.5);
                maw.setTarget(player(minecraft));
            }
        });
        waitFor(context, server, 500, "A maw should find its way around the wall", minecraft ->
                minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).stream().anyMatch(maw -> maw.getZ() > 1.5));
        clearScene(server);
        server.runOnServer(VoidEyeBehaviourClientTest::heal);
    }

    private static void checkLargeMaw(ClientGameTestContext context, TestServerContext server)
    {
        int x = 200;
        tp(server, x + 0.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.LARGE_EYE_MAW, x + 11.5, FEET + 2, 0.5);
        });
        aimCamera(server, x + 0.5, FEET, 0.5, x + 11.5, FEET + 3, 0.5);
        waitFor(context, server, 200, "The large maw should open up", minecraft -> first(minecraft, VoidHeartEntities.LARGE_EYE_MAW).jawsOpen());
        waitFor(context, server, 100, "The first of its brood should come out", minecraft -> !minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).isEmpty());
        context.takeScreenshot("eye-large-spitting");
        waitFor(context, server, 60, "It should spit four", minecraft ->
        {
            freezeBrood(minecraft);
            return minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).size() == LargeEyeMaw.SPIT_COUNT;
        });
        server.runOnServer(minecraft -> expect(minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, EyeMaw::wasSpat).size() == LargeEyeMaw.SPIT_COUNT, "The brood should know it was spat"));
        waitFor(context, server, 80, "It should close again", minecraft -> !first(minecraft, VoidHeartEntities.LARGE_EYE_MAW).jawsOpen());

        // Two more rounds at most, as the brood is limited even when nothing thins it out
        for (int round = 0; round < 3; round++)
        {
            server.runOnServer(minecraft -> set(first(minecraft, VoidHeartEntities.LARGE_EYE_MAW), "spitCooldown", 1));
            context.waitTicks(LargeEyeMaw.OPEN_TICKS + 40);
            server.runOnServer(VoidEyeBehaviourClientTest::freezeBrood);
        }
        server.runOnServer(minecraft ->
        {
            int brood = minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).size();
            expect(brood == LargeEyeMaw.MAX_ALIVE, "The brood should stop at " + LargeEyeMaw.MAX_ALIVE + ", found " + brood);
            expect(first(minecraft, VoidHeartEntities.LARGE_EYE_MAW).aliveSpat(minecraft.overworld()) == brood, "It should count its brood");
        });

        // Killing it stops the flow, and its brood lives on
        server.runOnServer(minecraft -> first(minecraft, VoidHeartEntities.LARGE_EYE_MAW).kill(minecraft.overworld()));
        context.waitTicks(30);
        server.runOnServer(minecraft -> expect(minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive).size() == LargeEyeMaw.MAX_ALIVE, "The brood should outlive it"));
        clearScene(server);

        // It never bites and backs away from anyone coming close
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var large = spawn(minecraft, VoidHeartEntities.LARGE_EYE_MAW, x + 3.5, FEET + 2, 0.5);
            set(large, "spitCooldown", 100000);
            large.setTarget(player(minecraft));
        });
        waitFor(context, server, 300, "It should back away to about 8 blocks", minecraft -> first(minecraft, VoidHeartEntities.LARGE_EYE_MAW).distanceTo(player(minecraft)) >= 7.5);
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "A large maw should never hurt anyone itself"));
        clearScene(server);
    }

    private static void freezeBrood(MinecraftServer minecraft)
    {
        for (var maw : minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive))
            maw.setNoAi(true);
    }

    private static void checkStalking(ClientGameTestContext context, TestServerContext server)
    {
        int x = 300;
        tp(server, x + 0.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var eye = spawn(minecraft, VoidHeartEntities.ARCANE_EYE, x + 7.5, FEET + 1, 0.5);
            eye.setTarget(player(minecraft));
        });

        // Under a gaze it holds back and never comes within reach
        aimCamera(server, x + 0.5, FEET, 0.5, x + 7.5, FEET + 1.5, 0.5);
        waitFor(context, server, 40, "It should notice being watched", minecraft -> first(minecraft, VoidHeartEntities.ARCANE_EYE).watched());
        context.waitTicks(10);
        aimAt(server, x + 0.5, FEET, 0.5, VoidHeartEntities.ARCANE_EYE);
        context.waitTicks(3);
        context.takeScreenshot("eye-arcane-watched");
        double watchedFrom = server.computeOnServer(minecraft -> reach(first(minecraft, VoidHeartEntities.ARCANE_EYE), player(minecraft)));
        for (int step = 0; step < 6; step++)
        {
            context.waitTicks(10);
            aimAt(server, x + 0.5, FEET, 0.5, VoidHeartEntities.ARCANE_EYE);
            server.runOnServer(minecraft ->
            {
                double reach = reach(first(minecraft, VoidHeartEntities.ARCANE_EYE), player(minecraft));
                expect(reach >= watchedFrom - 0.3, "A watched eye should not come closer, from " + watchedFrom + " to " + reach);
                expect(minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty(), "A watched eye out of range should not fire");
            });
        }
        double afterGaze = server.computeOnServer(minecraft -> reach(first(minecraft, VoidHeartEntities.ARCANE_EYE), player(minecraft)));
        expect(afterGaze > watchedFrom + 0.2, "It should drift back when watched up close, from " + watchedFrom + " to " + afterGaze);

        // Left alone it closes in, and fires only once within reach
        aimCamera(server, x + 0.5, FEET, 0.5, x - 10, FEET + 1.5, 0.5);
        waitFor(context, server, 40, "It should notice being left alone", minecraft -> !first(minecraft, VoidHeartEntities.ARCANE_EYE).watched());
        double[] closest = { 100 };
        boolean fired = false;
        for (int tick = 0; tick < 200 && !fired; tick++)
        {
            fired = server.computeOnServer(minecraft ->
            {
                var eye = first(minecraft, VoidHeartEntities.ARCANE_EYE);
                closest[0] = Math.min(closest[0], reach(eye, player(minecraft)));
                var bolts = minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive);
                if (bolts.isEmpty())
                    return false;
                double at = reach(eye, player(minecraft));
                expect(at <= ArcaneStalk.FIRE_RANGE + 0.3, "It should only fire within reach, fired from " + at);
                return true;
            });
            context.waitTick();
        }
        expect(fired, "An unwatched eye should close in and fire, got as close as " + closest[0]);
        aimAt(server, x + 0.5, FEET, 0.5, VoidHeartEntities.ARCANE_EYE);
        context.waitTick();
        context.takeScreenshot("eye-arcane-fired");

        // Then it backs away
        waitFor(context, server, 80, "It should back off after firing", minecraft -> reach(first(minecraft, VoidHeartEntities.ARCANE_EYE), player(minecraft)) > 5.5);
        clearScene(server);
        server.runOnServer(VoidEyeBehaviourClientTest::heal);
    }

    /**
     * An eye that cannot move, a block and a half out of reach, then just inside it: the limit is all that changes.
     */
    private static void checkFireRange(ClientGameTestContext context, TestServerContext server)
    {
        int x = 400;
        tp(server, x + 0.5, FEET, 0.5);
        aimCamera(server, x + 0.5, FEET, 0.5, x - 10, FEET + 1, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var eye = spawn(minecraft, VoidHeartEntities.ARCANE_EYE, x + 6.5, FEET + 0.5, 0.5);
            eye.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            eye.setTarget(player(minecraft));
        });
        for (int tick = 0; tick < 100; tick++)
        {
            context.waitTick();
            server.runOnServer(minecraft ->
            {
                expect(minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty(), "No bolt beyond reach");
                expect(!first(minecraft, VoidHeartEntities.ARCANE_EYE).charging(), "No charging beyond reach");
            });
        }

        tp(server, x + 3, FEET, 0.5);
        aimCamera(server, x + 3, FEET, 0.5, x - 10, FEET + 1, 0.5);
        waitFor(context, server, 60, "It should charge as soon as the player is in reach", minecraft -> first(minecraft, VoidHeartEntities.ARCANE_EYE).charging());
        aimAt(server, x + 3, FEET, 0.5, VoidHeartEntities.ARCANE_EYE);
        context.waitTicks(4);
        context.takeScreenshot("eye-arcane-charging");
        waitFor(context, server, 30, "A bolt should follow", minecraft -> !minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty());
        context.waitTick();
        context.takeScreenshot("eye-arcane-bolt");
        clearScene(server);
        server.runOnServer(VoidEyeBehaviourClientTest::heal);
    }

    private static void checkBolt(ClientGameTestContext context, TestServerContext server)
    {
        int x = 500;

        // Straight, and gone after 8 blocks
        tp(server, x + 0.5, FEET, 40.5);
        var from = new Vec3(x + 0.5, FEET + 2, 0.5);
        server.runOnServer(minecraft -> launch(minecraft, from, new Vec3(1, 0, 0)));
        double[] farthest = { 0 };
        for (int tick = 0; tick < 30; tick++)
        {
            context.waitTick();
            server.runOnServer(minecraft ->
            {
                for (var bolt : minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive))
                {
                    farthest[0] = Math.max(farthest[0], bolt.getX() - from.x);
                    expect(Math.abs(bolt.getZ() - from.z) < 0.01, "A bolt should fly straight, at " + bolt.position());
                }
            });
        }
        server.runOnServer(minecraft -> expect(minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty(), "A bolt should die out"));
        expect(farthest[0] > ArcaneBolt.MAX_TRAVEL - 1 && farthest[0] < ArcaneBolt.MAX_TRAVEL + 1, "A bolt should fly about 8 blocks, flew " + farthest[0]);

        // Armor takes the edge off
        float bare = boltDamage(context, server, x, false);
        float armored = boltDamage(context, server, x, true);
        expect(bare > 0 && armored > 0 && armored < bare, "Armor should reduce a bolt, bare " + bare + ", armored " + armored);

        // A raised shield stops it
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);
        });
        aimCamera(server, x + 0.5, FEET, 60.5, x + 6, FEET + 1, 60.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            expect(player(minecraft).isBlocking(), "The player should be blocking");
            launch(minecraft, new Vec3(x + 3, FEET + 1, 60.5), new Vec3(-1, 0, 0));
        });
        waitFor(context, server, 30, "The bolt should meet the shield", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A shield should stop a bolt, health " + player.getHealth());
            player.stopUsingItem();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        });

        // Walls stop it too
        server.runCommand("fill " + (x + 2) + " " + FEET + " 78 " + (x + 2) + " " + (FEET + 3) + " 82 stone");
        tp(server, x + 0.5, FEET, 80.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            launch(minecraft, new Vec3(x + 5, FEET + 1, 80.5), new Vec3(-1, 0, 0));
        });
        waitFor(context, server, 30, "The bolt should break on the wall", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.ARCANE_BOLT, Entity::isAlive).isEmpty());
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "A wall should have stopped the bolt"));
        clearScene(server);
    }

    private static float boltDamage(ClientGameTestContext context, TestServerContext server, int x, boolean armored)
    {
        tp(server, x + 0.5, FEET, 60.5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            player.setItemSlot(EquipmentSlot.FEET, armored ? new ItemStack(Items.NETHERITE_BOOTS) : ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.LEGS, armored ? new ItemStack(Items.NETHERITE_LEGGINGS) : ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.CHEST, armored ? new ItemStack(Items.NETHERITE_CHESTPLATE) : ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, armored ? new ItemStack(Items.NETHERITE_HELMET) : ItemStack.EMPTY);
            launch(minecraft, new Vec3(x + 3, FEET + 1, 60.5), new Vec3(-1, 0, 0));
        });
        waitFor(context, server, 30, "The bolt should hit the player", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        float damage = server.computeOnServer(minecraft -> player(minecraft).getMaxHealth() - player(minecraft).getHealth());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD))
                player.setItemSlot(slot, ItemStack.EMPTY);
            heal(minecraft);
        });
        context.waitTicks(15);
        return damage;
    }

    private static void launch(MinecraftServer minecraft, Vec3 from, Vec3 direction)
    {
        var level = minecraft.overworld();
        var shooter = VoidHeartEntities.ARCANE_EYE.create(level, EntitySpawnReason.COMMAND);
        shooter.setPos(from.x, from.y, from.z);
        level.addFreshEntity(new ArcaneBolt(level, shooter, from, direction, ArcaneEye.BOLT_DAMAGE));
    }

    private static void prepareSavedState(ClientGameTestContext context, TestServerContext server)
    {
        int x = 600;
        tp(server, x + 0.5, FEET, 0.5);
        server.runCommand("gamemode creative @a");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            spawn(minecraft, VoidHeartEntities.EYE_MAW, x + 4.5, FEET + 2, 0.5).setNoAi(true);
            var spat = spawn(minecraft, VoidHeartEntities.EYE_MAW, x + 6.5, FEET + 2, 0.5);
            spat.setNoAi(true);
            spat.setSpat();
            var large = spawn(minecraft, VoidHeartEntities.LARGE_EYE_MAW, x + 12.5, FEET + 2, 0.5);
            large.setNoAi(true);
            list(large).add(spat.getUUID());
        });
        context.waitTicks(10);
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> list(LargeEyeMaw large)
    {
        try
        {
            Field field = LargeEyeMaw.class.getDeclaredField("spat");
            field.setAccessible(true);
            return (List<UUID>) field.get(large);
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
    }

    private static void set(LargeEyeMaw large, String name, int value)
    {
        try
        {
            Field field = LargeEyeMaw.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(large, value);
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError(exception);
        }
    }

    private static double reach(Entity eye, ServerPlayer player)
    {
        return eye.getBoundingBox().getCenter().distanceTo(player.getBoundingBox().getCenter());
    }

    private static Entity nearest(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        Entity closest = player;
        double best = Double.MAX_VALUE;
        for (var maw : minecraft.overworld().getEntities(VoidHeartEntities.EYE_MAW, Entity::isAlive))
        {
            if (maw.distanceToSqr(player) < best)
            {
                best = maw.distanceToSqr(player);
                closest = maw;
            }
        }
        return closest;
    }

    private static void aimAt(TestServerContext server, double feetX, double feetY, double feetZ, EntityType<? extends Entity> type)
    {
        var at = server.computeOnServer(minecraft -> minecraft.overworld().getEntities(type, Entity::isAlive).getFirst().getBoundingBox().getCenter());
        aimCamera(server, feetX, feetY, feetZ, at.x, at.y, at.z);
    }

    private static <T extends Mob> T spawn(MinecraftServer minecraft, EntityType<T> type, double x, double y, double z)
    {
        var level = minecraft.overworld();
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x, y, z, 90, 0);
        level.addFreshEntity(mob);
        return mob;
    }

    private static <T extends Entity> T first(MinecraftServer minecraft, EntityType<T> type)
    {
        return minecraft.overworld().getEntities(type, Entity::isAlive).getFirst();
    }

    private static void heal(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        player.setHealth(player.getMaxHealth());
    }

    private static void clearScene(TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
    }

    private static void tp(TestServerContext server, double x, double y, double z)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z);
    }

    private static void aimCamera(TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, String message, Predicate<MinecraftServer> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (server.computeOnServer(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError(message + " within " + maxTicks + " ticks");
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }
}
