package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.BarnacleInfection;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestTile;
import net.voxelindustry.voidheart.common.content.mob.CorruptedBarnacle;
import net.voxelindustry.voidheart.common.content.mob.FallenElder;
import net.voxelindustry.voidheart.common.content.mob.InfectionRules;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.content.mob.VoidOrb;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMob;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What the mobs do when left to their own devices against a real player, and what the numbers behind them are.
 */
public class VoidMobBehaviourClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int CLEARED_RADIUS = 32;
    private static final int MOB_CLOSE_UPS = 3;
    private static final int CHANCE_ROLLS = 400;

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
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("difficulty hard");
            server.runCommand("gamemode survival @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(VoidMobBehaviourClientTest::checkNumbers);
            checkPeaceful(context, server);
            checkGhoulLunge(context, server);
            checkCrabClimbing(context, server);
            checkCrabCeiling(context, server);
            checkLargeCrabDeath(context, server);
            checkWraith(context, server);
            checkBarnacleGrowth(context, server);
            checkInfection(context, server);
            checkNest(context, server);
            checkElder(context, server);
            checkChances(context, server);
            checkDungeons(context, server, world);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }

        checkSavedState(context);
    }

    /**
     * Every chance in the numbers, rolled often enough to tell them apart without ever failing by bad luck.
     */
    private static void checkChances(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 600.5, FEET, 0.5);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var cow = spawn(minecraft, EntityTypes.COW, 603.5, FEET, 0.5);
            cow.setNoAi(true);
            var wraith = spawn(minecraft, VoidHeartEntities.WRAITH, 601.5, FEET, 0.5);
            wraith.setNoAi(true);
            int withered = 0;
            for (int roll = 0; roll < CHANCE_ROLLS; roll++)
            {
                set(cow, net.minecraft.world.entity.LivingEntity.class, "damageCooldownTime", 0);
                cow.setHealth(cow.getMaxHealth());
                cow.removeAllEffects();
                wraith.doHurtTarget(level, cow);
                if (cow.hasEffect(MobEffects.WITHER))
                    withered++;
            }
            expectShare(withered, 0.1, "wither");

            var player = player(minecraft);
            var barnacle = spawn(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, 601.5, FEET, 0.5);
            barnacle.setNoAi(true);
            double[] chances = { 0.1, 0.2, 0.35 };
            for (int stage = 0; stage <= CorruptedBarnacle.GROWN; stage++)
            {
                barnacle.setStage(stage);
                int infected = 0;
                for (int roll = 0; roll < CHANCE_ROLLS; roll++)
                {
                    BarnacleInfection.clear(player);
                    set(player, net.minecraft.world.entity.LivingEntity.class, "damageCooldownTime", 0);
                    player.setHealth(player.getMaxHealth());
                    barnacle.doHurtTarget(level, player);
                    if (BarnacleInfection.amount(player) > 0)
                        infected++;
                }
                expectShare(infected, chances[stage], "infection at stage " + stage);
            }
            BarnacleInfection.clear(player);
            heal(minecraft);
        });
        clearScene(server);
    }

    private static void expectShare(int count, double chance, String name)
    {
        double expected = CHANCE_ROLLS * chance;
        double spread = 4.5 * Math.sqrt(CHANCE_ROLLS * chance * (1 - chance));
        expect(Math.abs(count - expected) < spread, "Expected about " + expected + " rolls of " + name + ", got " + count);
    }

    /**
     * What must outlive a world being closed and opened again.
     */
    private static void checkSavedState(ClientGameTestContext context)
    {
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        var nestPos = new BlockPos(5, FEET + 2, 12);
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty hard");
            server.runCommand("gamemode survival @a");
            tp(server, 0.5, FEET, 0.5);
            server.runCommand("fill 0 " + FEET + " 13 10 " + (FEET + 5) + " 13 stone");
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);

                var crab = spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, 2.5, FEET, 12.5);
                crab.settleOn(new BlockPos(2, FEET + 3, 12), Direction.NORTH);
                crab.setNoAi(true);

                var barnacle = spawn(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, 8.5, FEET, 8.5);
                barnacle.setStage(1);
                barnacle.setNoAi(true);

                var wraith = spawn(minecraft, VoidHeartEntities.WRAITH, 8.5, FEET, 4.5);
                wraith.hurtServer(level, player.damageSources().playerAttack(player), 1);
                expect(wraith.isLockedSolid(), "The wraith should lock before saving");
                wraith.setNoAi(true);

                level.setBlockAndUpdate(nestPos, VoidHeartBlocks.BARNACLE_NEST.defaultBlockState().setValue(BarnacleNestBlock.FACING, Direction.NORTH));
                set(level.getBlockEntity(nestPos), BarnacleNestTile.class, "ticksUntilRelease", 123);

                BarnacleInfection.infect(player);
                BarnacleInfection.infect(player);
                setInfectionTicks(player, 1500);
            });
            context.waitTicks(10);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);

                var crab = first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM);
                expect(crab.surface() == Direction.NORTH, "The crab should still hold the wall, holds " + crab.surface());
                expect(first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE).stage() == 1, "The barnacle should keep its stage");
                expect(first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE).getBbWidth() == 0.75F, "The barnacle should keep its size");
                expect(first(minecraft, VoidHeartEntities.WRAITH).isLockedSolid(), "The wraith should stay locked");
                expect(level.getBlockState(nestPos).is(VoidHeartBlocks.BARNACLE_NEST), "The nest should remain");
                expect(BarnacleInfection.amount(player) == 2, "The infection amount should be saved, got " + BarnacleInfection.amount(player));
                int ticks = BarnacleInfection.ticks(player);
                expect(ticks > 1400 && ticks <= 1500, "The infection timer should pause while away, got " + ticks);
            });
            server.runCommand("gamerule spawn_mobs true");
        }
    }

    private static void checkNumbers(MinecraftServer minecraft)
    {
        record Stats(EntityType<? extends Mob> type, double health, double damage, boolean undead)
        {
        }
        var all = List.of(
                new Stats(VoidHeartEntities.VOID_SKELETON, 26, 6, true),
                new Stats(VoidHeartEntities.VOID_GHOUL, 34, 7, true),
                new Stats(VoidHeartEntities.WRAITH, 30, 6, true),
                new Stats(VoidHeartEntities.CORRUPTED_CRAB_SMALL, 8, 3, false),
                new Stats(VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, 22, 5, false),
                new Stats(VoidHeartEntities.CORRUPTED_CRAB_LARGE, 50, 9, false),
                new Stats(VoidHeartEntities.CORRUPTED_BARNACLE, 6, 2, false),
                new Stats(VoidHeartEntities.FALLEN_NOVICE, 20, 5, true),
                new Stats(VoidHeartEntities.FALLEN_ADEPT, 44, 9, true),
                new Stats(VoidHeartEntities.FALLEN_ELDER, 80, 8, true));

        var level = minecraft.overworld();
        for (var stats : all)
        {
            var mob = stats.type().create(level, EntitySpawnReason.COMMAND);
            expect(mob.getAttributeValue(Attributes.MAX_HEALTH) == stats.health(), stats.type() + " health");
            expect(mob.getAttributeValue(Attributes.ATTACK_DAMAGE) == stats.damage(), stats.type() + " damage");
            expect(stats.type().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE) == stats.undead(), stats.type() + " smite");
            expect(stats.type().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS) != stats.undead(), stats.type() + " bane of arthropods");
            expect(SpawnEggItem.byId(stats.type()).isPresent(), stats.type() + " spawn egg");
            var table = stats.type().getDefaultLootTable().orElseThrow();
            if (stats.type() != VoidHeartEntities.CORRUPTED_BARNACLE)
                expect(minecraft.reloadableRegistries().getLootTable(table) != LootTable.EMPTY, stats.type() + " loot table");
        }

        var adept = VoidHeartEntities.FALLEN_ADEPT.create(level, EntitySpawnReason.COMMAND);
        expect(adept.getAttributeValue(Attributes.ARMOR) == 12, "adept armor");
        expect(adept.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) > 0.5, "adept resists knockback");
    }

    private static void checkPeaceful(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 0.5, FEET, 0.5);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.VOID_SKELETON, 5.5, FEET, 0.5));
        context.waitTicks(10);
        server.runCommand("difficulty peaceful");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var mob = first(minecraft, VoidHeartEntities.VOID_SKELETON);
            expect(mob.isDormant() && mob.isInvisible() && mob.isNoAi(), "Peaceful should leave the mob dormant");
        });
        context.takeScreenshot("mob-peaceful-dormant");
        server.runCommand("difficulty hard");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var mob = first(minecraft, VoidHeartEntities.VOID_SKELETON);
            expect(!mob.isDormant() && !mob.isInvisible() && !mob.isNoAi(), "Leaving Peaceful should wake the mob");
        });
        clearScene(server);
    }

    private static void checkGhoulLunge(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 0.5, FEET, 20.5);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.VOID_GHOUL, 9.5, FEET, 20.5));
        waitFor(context, server, 200, "The ghoul should lunge", minecraft -> first(minecraft, VoidHeartEntities.VOID_GHOUL).isLunging());
        context.takeScreenshot("mob-ghoul-lunge");
        clearScene(server);
    }

    private static void checkCrabClimbing(ClientGameTestContext context, TestServerContext server)
    {
        int x = 30;
        int z = 30;
        server.runCommand("fill " + x + " " + FEET + " " + z + " " + (x + 2) + " " + (FEET + 5) + " " + (z + 2) + " stone");
        tp(server, x + 1.5, FEET + 6, z + 1.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, x + 15.5, FEET, z + 1.5);
        });

        waitFor(context, server, 400, "The crab should climb the pillar", minecraft -> first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).isClinging());
        context.waitTicks(8);
        aimCamera(server, x + 1.5, FEET + 6, z + 1.5, x + 3.5, FEET + 3, z + 1.5);
        context.takeScreenshot("mob-crab-climbing");
        waitFor(context, server, 240, "The crab should reach the player on the pillar", minecraft ->
                first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM).distanceTo(player(minecraft)) < 2.5);
        waitFor(context, server, 80, "The crab should bite the player", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        clearScene(server);
    }

    private static void checkCrabCeiling(ClientGameTestContext context, TestServerContext server)
    {
        int x = 60;
        int z = 30;
        server.runCommand("fill " + x + " " + GROUND + " " + z + " " + (x + 12) + " " + (GROUND + 6) + " " + (z + 12) + " stone hollow");
        server.runCommand("setblock " + (x + 6) + " " + (GROUND + 6) + " " + (z + 6) + " sea_lantern");
        tp(server, x + 9.5, FEET, z + 6.5);
        double[] start = new double[1];
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var crab = spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, x + 3.5, FEET + 4, z + 6.5);
            crab.settleOn(new BlockPos(x + 3, FEET + 4, z + 6), Direction.DOWN);
            start[0] = crab.getX();
        });
        waitFor(context, server, 60, "The crab should crawl along the ceiling", minecraft ->
        {
            var crab = first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM);
            expect(crab.surface() == Direction.DOWN, "The crab should hold on to the ceiling, holds " + crab.surface());
            return crab.getX() > start[0] + 0.5;
        });
        aimCamera(server, x + 9.5, FEET, z + 6.5, x + 6.5, FEET + 4.5, z + 6.5);
        context.waitTick();
        context.takeScreenshot("mob-crab-ceiling");
        waitFor(context, server, 200, "The crab should drop once over the player", minecraft ->
        {
            var crab = first(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM);
            return crab.surface() == Direction.UP && Math.abs(crab.getX() - player(minecraft).getX()) < 3;
        });
        clearScene(server);
    }

    private static void checkLargeCrabDeath(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 0.5, FEET, 40.5);
        server.runOnServer(minecraft ->
        {
            var large = spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_LARGE, 8.5, FEET, 40.5);
            var medium = spawn(minecraft, VoidHeartEntities.CORRUPTED_CRAB_MEDIUM, 4.5, FEET, 45.5);
            var player = player(minecraft);
            large.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1000);
            medium.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1000);
        });
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            int small = minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_CRAB_SMALL, Entity::isAlive).size();
            expect(small == 3, "A large crab should release 3 small ones, got " + small);
            int shards = 0;
            for (var item : minecraft.overworld().getEntities(net.minecraft.world.entity.EntityTypes.ITEM, Entity::isAlive))
            {
                if (item.getItem().is(net.voxelindustry.voidheart.common.setup.VoidHeartItems.OBSIDIAN_SHARD))
                    shards += item.getItem().getCount();
            }
            expect(shards >= 3, "A large and a medium crab should drop at least 3 shards, got " + shards);
        });
        clearScene(server);
    }

    private static void checkWraith(ClientGameTestContext context, TestServerContext server)
    {
        int x = 100;
        int z = 0;
        server.runCommand("fill " + x + " " + GROUND + " " + z + " " + (x + 8) + " " + (GROUND + 6) + " " + (z + 8) + " stone hollow");
        server.runCommand("fill " + (x + 8) + " " + FEET + " " + z + " " + (x + 10) + " " + (GROUND + 6) + " " + (z + 8) + " stone");
        server.runCommand("setblock " + (x + 4) + " " + (GROUND + 6) + " " + (z + 4) + " sea_lantern");
        tp(server, x + 2.5, FEET, z + 4.5);
        aimCamera(server, x + 2.5, FEET, z + 4.5, x + 8, FEET + 1.5, z + 4.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.WRAITH, x + 14.5, FEET, z + 4.5);
        });

        waitFor(context, server, 200, "The wraith should mark the wall", minecraft -> !minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty());
        server.runOnServer(minecraft ->
        {
            var stain = minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, entity -> true).getFirst();
            expect(stain.getX() < x + 8.1 && stain.face() == Direction.WEST, "The stain should be on the inner face of the wall, at " + stain.position() + " facing " + stain.face());
            var wraith = first(minecraft, VoidHeartEntities.WRAITH);
            expect(wraith.isCrossing(), "The wraith should be crossing");
            float health = wraith.getHealth();
            boolean hurt = wraith.hurtServer(minecraft.overworld(), player(minecraft).damageSources().playerAttack(player(minecraft)), 5);
            expect(!hurt && wraith.getHealth() == health, "A crossing wraith should not be hit");
            expect(!wraith.isPickable(), "A crossing wraith should not catch the crosshair");
            expect(!wraith.isLockedSolid(), "A hit that did nothing should not lock it");
        });
        context.waitTicks(20);
        context.takeScreenshot("mob-wraith-stain-growing");
        context.waitTicks(30);
        context.takeScreenshot("mob-wraith-stain-full");
        waitFor(context, server, 50, "The wraith should have come through", minecraft -> !first(minecraft, VoidHeartEntities.WRAITH).isCrossing());
        context.takeScreenshot("mob-wraith-emerged");
        server.runOnServer(minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.WRAITH);
            expect(wraith.getX() < x + 8, "The wraith should be on the player's side, at " + wraith.position());
            expect(minecraft.overworld().noCollision(wraith), "The wraith should stand free of the wall");
            expect(wraith.hurtServer(minecraft.overworld(), player(minecraft).damageSources().playerAttack(player(minecraft)), 1), "A solid wraith should be hit");
            expect(wraith.isLockedSolid(), "Player damage should lock it solid");
        });

        // Locked, it stays behind when the player leaves through the wall
        tp(server, x + 14.5, FEET, z + 4.5);
        context.waitTicks(220);
        server.runOnServer(minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.WRAITH);
            expect(!wraith.isCrossing(), "A locked wraith should not cross");
            expect(minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty(), "A locked wraith should not mark anything");
            expect(wraith.getX() < x + 8, "A locked wraith should stay inside, at " + wraith.position());
        });
        clearScene(server);

        // Pocket wall stops it for good
        int pocketX = 130;
        server.runCommand("fill " + pocketX + " " + GROUND + " " + z + " " + (pocketX + 8) + " " + (GROUND + 6) + " " + (z + 8) + " voidheart:pocket_wall hollow");
        tp(server, pocketX + 4.5, FEET, z + 4.5);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.WRAITH, pocketX + 14.5, FEET, z + 4.5));
        context.waitTicks(120);
        server.runOnServer(minecraft ->
        {
            expect(minecraft.overworld().getEntities(VoidHeartEntities.WRAITH_STAIN, stain -> true).isEmpty(), "A wraith should never mark across pocket wall");
            expect(!first(minecraft, VoidHeartEntities.WRAITH).isCrossing(), "A wraith should never cross pocket wall");
        });
        clearScene(server);
    }

    private static void checkBarnacleGrowth(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, 200.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            var barnacle = spawn(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, 215.5, FEET, 0.5);
            set(barnacle, CorruptedBarnacle.class, "growthTicks", CorruptedBarnacle.GROWTH_TICKS - 4);
        });
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var barnacle = first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE);
            expect(barnacle.stage() == 1, "The barnacle should have grown, stage " + barnacle.stage());
            expect(barnacle.getMaxHealth() == 16 && barnacle.getBbWidth() == 0.75F, "Its stats should have followed, health " + barnacle.getMaxHealth());
            barnacle.setStage(CorruptedBarnacle.GROWN);
            expect(barnacle.isRooted() && barnacle.getMaxHealth() == 30, "It should now be rooted");
            player(minecraft).teleportTo(barnacle.getX() + 3, FEET, barnacle.getZ());
            heal(minecraft);
        });
        context.waitTicks(20);
        double[] rootedAt = new double[1];
        server.runOnServer(minecraft -> rootedAt[0] = first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE).getX());
        aimCamera(server, 218.5, FEET, 0.5, 215.5, FEET + 0.3, 0.5);
        waitFor(context, server, 100, "The grown barnacle should strike from afar", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        context.takeScreenshot("mob-barnacle-strike");
        server.runOnServer(minecraft ->
        {
            var barnacle = first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE);
            expect(Math.abs(barnacle.getX() - rootedAt[0]) < 0.01, "A rooted barnacle should not move");
        });
        clearScene(server);
    }

    private static void checkInfection(ClientGameTestContext context, TestServerContext server)
    {
        int x = 300;
        tp(server, x + 0.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            BarnacleInfection.clear(player);
            BarnacleInfection.infect(player);
            expect(BarnacleInfection.amount(player) == 1, "First hit should infect");
            expect(BarnacleInfection.ticks(player) == InfectionRules.DURATION_TICKS, "First hit should start the timer");
            setInfectionTicks(player, 1000);
            for (int i = 0; i < 6; i++)
                BarnacleInfection.infect(player);
            expect(BarnacleInfection.amount(player) == InfectionRules.MAX_AMOUNT, "Infection should stop at its limit, got " + BarnacleInfection.amount(player));
            expect(BarnacleInfection.ticks(player) == 1000, "Later hits should leave the timer alone, got " + BarnacleInfection.ticks(player));
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var effect = player.getEffect(VoidHeartEffects.BARNACLE_INFECTION);
            expect(effect != null && effect.getAmplifier() == InfectionRules.MAX_AMOUNT - 1, "The effect should show the amount, got " + effect);
            expect(Math.abs(effect.getDuration() - BarnacleInfection.ticks(player)) <= 25, "The effect should show the time left");
            player.removeAllEffects();
        });
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.hasEffect(VoidHeartEffects.BARNACLE_INFECTION), "Clearing effects should not cure anything");
            expect(BarnacleInfection.amount(player) == InfectionRules.MAX_AMOUNT, "Clearing effects should leave the infection");
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(5);
        context.takeScreenshot("mob-infection-hud");
        context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("mob-infection-inventory");
        context.setScreen(() -> null);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        expectClient(context, "Other clients should learn the amount", minecraft -> BarnacleInfection.amount(minecraft.player) == InfectionRules.MAX_AMOUNT);

        // Creative players are left alone while the timer waits
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft -> setInfectionTicks(player(minecraft), 40));
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(BarnacleInfection.ticks(player) == 40, "Creative should hold the timer, got " + BarnacleInfection.ticks(player));
            expect(minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive).isEmpty(), "Creative should hold the burst");
        });
        server.runCommand("gamemode survival @a");

        // Bursts: barnacles scatter and become nests
        Vec3[] burstAt = new Vec3[1];
        waitFor(context, server, 80, "The infection should burst", minecraft -> BarnacleInfection.amount(player(minecraft)) == 0);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            burstAt[0] = player.position();
            expect(!player.hasEffect(VoidHeartEffects.BARNACLE_INFECTION), "The burst should clear the effect");
            var barnacles = minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive);
            expect(barnacles.size() == InfectionRules.MAX_AMOUNT, "One barnacle should burst per point, got " + barnacles.size());
            for (var barnacle : barnacles)
                expect(barnacle.isScattering() && barnacle.getTarget() == null, "A burst barnacle should run rather than fight");
        });
        context.waitTicks(20);
        context.takeScreenshot("mob-infection-burst");
        for (int wait = 0; wait < 6; wait++)
        {
            context.waitTicks(100);
            server.runOnServer(minecraft ->
            {
                for (var barnacle : minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive))
                    System.out.println("[VoidMobs] barnacle at " + barnacle.position() + " from " + burstAt[0] + " scattering " + barnacle.isScattering() + " done " + barnacle.getNavigation().isDone());
            });
        }
        waitFor(context, server, 2, "The barnacles should turn into nests", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive).isEmpty());
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            int nests = BarnacleNestBlock.countNestsAround(level, BlockPos.containing(burstAt[0]), 64);
            expect(nests == InfectionRules.MAX_AMOUNT, "Each barnacle should become a nest, got " + nests);
            var nearest = nestsAround(level, BlockPos.containing(burstAt[0]), 64);
            for (var nest : nearest)
                expect(nest.distSqr(BlockPos.containing(burstAt[0])) >= 14 * 14, "A nest should form away from the player, at " + nest);
        });
        context.takeScreenshot("mob-infection-nests");
        server.runCommand("fill " + (x - 40) + " " + FEET + " -40 " + (x + 40) + " " + (FEET + 3) + " 40 air");

        // With griefing off they give up and fight
        server.runCommand("gamerule mob_griefing false");
        server.runOnServer(minecraft ->
        {
            var barnacle = spawn(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE, x + 20.5, FEET, 0.5);
            barnacle.scatterFrom(new Vec3(x + 20.5, FEET, 0.5), 0);
            set(barnacle, CorruptedBarnacle.class, "scatterTicks", 120);
        });
        context.waitTicks(160);
        server.runOnServer(minecraft ->
        {
            expect(BarnacleNestBlock.countNestsAround(minecraft.overworld(), new BlockPos(x, FEET, 0), 64) == 0, "No nest should form without griefing");
            var barnacle = first(minecraft, VoidHeartEntities.CORRUPTED_BARNACLE);
            expect(!barnacle.isScattering(), "With nowhere to nest it should become a normal barnacle");
        });
        server.runCommand("gamerule mob_griefing true");
        clearScene(server);

        // Death clears it
        server.runOnServer(minecraft -> BarnacleInfection.infect(player(minecraft)));
        server.runCommand("kill @a");
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.isAlive(), "The player should have respawned");
            expect(BarnacleInfection.amount(player) == 0, "Death should clear the infection");
        });
    }

    private static void checkNest(ClientGameTestContext context, TestServerContext server)
    {
        int x = 400;
        var nestPos = new BlockPos(x, FEET, 0);
        tp(server, x + 6.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(nestPos, VoidHeartBlocks.BARNACLE_NEST.defaultBlockState());
            var player = player(minecraft);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            float progress = level.getBlockState(nestPos).getDestroyProgress(player, level, nestPos);
            expect(progress > 0 && 1 / progress > 25 && 1 / progress < 55, "A diamond pickaxe should take about 2 seconds, ticks " + 1 / progress);
        });

        for (int released = 1; released <= BarnacleNestTile.MAX_ALIVE + 1; released++)
        {
            server.runOnServer(minecraft -> set(minecraft.overworld().getBlockEntity(nestPos), BarnacleNestTile.class, "ticksUntilRelease", 1));
            context.waitTicks(25);
            int expected = Math.min(released, BarnacleNestTile.MAX_ALIVE);
            int count = server.computeOnServer(minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive).size());
            if (count != expected)
                throw new AssertionError("Expected " + expected + " barnacles alive from the nest, got " + count);
        }
        server.runCommand("kill @e[type=voidheart:corrupted_barnacle]");
        tp(server, x + 30.5, FEET, 0.5);
        server.runOnServer(minecraft -> set(minecraft.overworld().getBlockEntity(nestPos), BarnacleNestTile.class, "ticksUntilRelease", 1));
        context.waitTicks(45);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getEntities(VoidHeartEntities.CORRUPTED_BARNACLE, Entity::isAlive).isEmpty(), "A nest should stay quiet with nobody near");
            level.destroyBlock(nestPos, true, player(minecraft));
            expect(level.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, item -> item.distanceToSqr(Vec3.atCenterOf(nestPos)) < 9).isEmpty(), "A nest should drop nothing");
        });
        clearScene(server);
    }

    private static void checkElder(ClientGameTestContext context, TestServerContext server)
    {
        int x = 500;
        tp(server, x + 0.5, FEET, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            player(minecraft).setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            spawn(minecraft, VoidHeartEntities.FALLEN_ELDER, x + 11.5, FEET, 0.5);
        });
        aimCamera(server, x + 0.5, FEET, 0.5, x + 11.5, FEET + 2, 0.5);
        waitFor(context, server, 100, "The elder should throw an orb", minecraft -> !minecraft.overworld().getEntities(VoidHeartEntities.VOID_ORB, Entity::isAlive).isEmpty());
        context.waitTicks(4);
        context.takeScreenshot("mob-elder-casting");
        context.waitTicks(10);
        context.takeScreenshot("mob-elder-orb-flying");
        waitFor(context, server, 140, "The orb should reach the player", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        waitFor(context, server, 200, "The elder should raise novices", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.FALLEN_NOVICE, Entity::isAlive).size() == FallenElder.RAISED_AT_ONCE);
        context.takeScreenshot("mob-elder-raised");

        server.runOnServer(minecraft -> set(first(minecraft, VoidHeartEntities.FALLEN_ELDER), FallenElder.class, "raiseCooldown", 1));
        context.waitTicks(5);
        server.runOnServer(minecraft -> set(first(minecraft, VoidHeartEntities.FALLEN_ELDER), FallenElder.class, "raiseCooldown", 1));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            int novices = minecraft.overworld().getEntities(VoidHeartEntities.FALLEN_NOVICE, Entity::isAlive).size();
            expect(novices == FallenElder.MAX_RAISED, "Raised novices should stop at " + FallenElder.MAX_RAISED + ", got " + novices);
        });
        server.runCommand("kill @e[type=voidheart:fallen_novice]");
        server.runCommand("kill @e[type=voidheart:void_orb]");

        // Closing in makes it back away
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var elder = first(minecraft, VoidHeartEntities.FALLEN_ELDER);
            player(minecraft).teleportTo(elder.getX() - 4, FEET, elder.getZ());
        });
        waitFor(context, server, 200, "The elder should back away", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_ELDER).distanceTo(player(minecraft)) >= 7.5);
        clearScene(server);

        checkOrb(context, server, x);
    }

    private static void checkOrb(ClientGameTestContext context, TestServerContext server, int x)
    {
        float bare = orbDamage(context, server, x, false);
        float armored = orbDamage(context, server, x, true);
        if (bare <= 0 || armored != bare)
            throw new AssertionError("Armor should not change orb damage, bare " + bare + ", armored " + armored);

        // A raised shield stops it
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);
        });
        aimCamera(server, x + 0.5, FEET, 20.5, x + 6, FEET + 1, 20.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            expect(player(minecraft).isBlocking(), "The player should be blocking");
            launchOrb(minecraft, new Vec3(x + 3, FEET + 1, 20.5));
        });
        waitFor(context, server, 60, "The orb should meet the shield", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.VOID_ORB, Entity::isAlive).isEmpty());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A shield should stop the orb, health " + player.getHealth());
            player.stopUsingItem();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        });

        // A hit sends it where the player looks, into the elder
        tp(server, x + 0.5, FEET, 20.5);
        server.runOnServer(minecraft -> spawn(minecraft, VoidHeartEntities.FALLEN_ELDER, x + 9.5, FEET, 20.5).setNoAi(true));
        aimCamera(server, x + 0.5, FEET, 20.5, x + 9.5, FEET + 1.6, 20.5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var orb = launchOrb(minecraft, new Vec3(x + 2.5, FEET + 1.5, 20.5));
            expect(orb.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1), "A hit should deflect the orb");
            expect(orb.getOwner() == player, "A deflected orb should now be the player's");
            expect(orb.getDeltaMovement().x > 0.5, "A deflected orb should fly where the player looks, moving " + orb.getDeltaMovement());
        });
        waitFor(context, server, 60, "The deflected orb should hit the elder", minecraft ->
        {
            var elder = first(minecraft, VoidHeartEntities.FALLEN_ELDER);
            return elder.getHealth() < elder.getMaxHealth();
        });
        clearScene(server);

        // It cannot follow around a corner
        server.runCommand("fill " + (x + 3) + " " + FEET + " 38 " + (x + 3) + " " + (FEET + 3) + " 43 stone");
        tp(server, x + 0.5, FEET, 40.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            launchOrb(minecraft, new Vec3(x + 6, FEET + 1, 40.5));
        });
        waitFor(context, server, 60, "The orb should break on the wall", minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.VOID_ORB, Entity::isAlive).isEmpty());
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "A wall should have stopped the orb"));
        clearScene(server);
    }

    private static float orbDamage(ClientGameTestContext context, TestServerContext server, int x, boolean armored)
    {
        tp(server, x + 0.5, FEET, 60.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            var boots = armored ? new ItemStack(Items.NETHERITE_BOOTS) : ItemStack.EMPTY;
            var legs = armored ? new ItemStack(Items.NETHERITE_LEGGINGS) : ItemStack.EMPTY;
            var chest = armored ? new ItemStack(Items.NETHERITE_CHESTPLATE) : ItemStack.EMPTY;
            var head = armored ? new ItemStack(Items.NETHERITE_HELMET) : ItemStack.EMPTY;
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(EquipmentSlot.LEGS, legs);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.HEAD, head);
            launchOrb(minecraft, new Vec3(x + 3, FEET + 1, 60.5));
        });
        waitFor(context, server, 80, "The orb should hit the player", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
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

    private static VoidOrb launchOrb(MinecraftServer minecraft, Vec3 from)
    {
        var level = minecraft.overworld();
        var player = player(minecraft);
        var shooter = VoidHeartEntities.FALLEN_NOVICE.create(level, EntitySpawnReason.COMMAND);
        shooter.setPos(from.x, from.y, from.z);
        var orb = new VoidOrb(level, shooter, player, 8);
        orb.setPos(from.x, from.y, from.z);
        level.addFreshEntity(orb);
        return orb;
    }

    private static void checkDungeons(ClientGameTestContext context, TestServerContext server, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world)
    {
        server.runCommand("gamemode spectator @a");
        context.waitTicks(10);
        checkDungeon(context, server, world, DungeonSize.MEDIUM, DungeonTheme.FOREST, "medium_forest", 3, DungeonMob.Family.PEST);
        checkDungeon(context, server, world, DungeonSize.MEDIUM, DungeonTheme.TEMPLE, "medium_temple", 4, DungeonMob.Family.FALLEN);
        checkDungeon(context, server, world, DungeonSize.MEDIUM, DungeonTheme.FOUNDRY, "medium_foundry", 2, DungeonMob.Family.GENERIC);
        server.runCommand("gamemode survival @a");
    }

    /**
     * Opens a dungeon and compares what its generated chunks hold to its plan, then checks that once killed nothing comes back.
     */
    private static void checkDungeon(ClientGameTestContext context,
                                     TestServerContext server,
                                     net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world,
                                     DungeonSize size,
                                     DungeonTheme theme,
                                     String command,
                                     int difficulty,
                                     DungeonMob.Family family)
    {
        server.runCommand("execute as @a run voidheart dungeon open " + command + " " + difficulty);
        context.waitTicks(100);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        context.waitTicks(40);
        context.takeScreenshot("mob-dungeon-" + command);

        var seen = new ArrayList<String>();
        int count = server.computeOnServer(minecraft ->
        {
            var level = dungeonLevel(minecraft);
            var address = DungeonAddress.at(player(minecraft).getBlockX(), player(minecraft).getBlockZ());
            int found = 0;
            for (var entity : level.getAllEntities())
            {
                if (!(entity instanceof VoidMonster monster) || !sameDungeon(address, entity))
                    continue;

                found++;
                seen.add(entity.getType().toShortString());
                expect(monster.isPersistenceRequired(), "A placed mob should be persistent: " + entity);
            }
            return found;
        });
        if (count == 0)
            throw new AssertionError("Expected mobs in " + command);
        for (var type : seen)
        {
            boolean pest = type.contains("crab") || type.contains("barnacle");
            boolean fallen = type.contains("fallen");
            if (pest && family != DungeonMob.Family.PEST)
                throw new AssertionError("Pests should not live in " + theme + ", found " + type);
            if (fallen && family != DungeonMob.Family.FALLEN)
                throw new AssertionError("The Fallen should not live in " + theme + ", found " + type);
        }
        System.out.println("[VoidMobs] " + command + " holds " + count + " loaded mobs: " + seen);

        boolean hasNests = server.computeOnServer(minecraft -> BarnacleNestBlock.countNestsAround(dungeonLevel(minecraft), player(minecraft).blockPosition(), 128) > 0);
        if (hasNests != (family == DungeonMob.Family.PEST))
            throw new AssertionError("Nests should only grow where pests live, found some: " + hasNests + " in " + theme);

        lookAtNearestMobs(context, server, command);

        // A cleared dungeon stays cleared
        var at = server.computeOnServer(minecraft -> player(minecraft).position());
        server.runCommand("execute as @a at @s run kill @e[type=!player]");
        // Large crabs leave young behind them
        context.waitTicks(5);
        server.runCommand("execute as @a at @s run kill @e[type=!player]");
        server.runCommand("execute as @a run voidheart dungeon open small_foundry 1");
        context.waitTicks(100);
        server.runCommand("execute as @a at @s run tp @s " + at.x + " " + at.y + " " + at.z);
        context.waitTicks(80);
        int left = server.computeOnServer(minecraft ->
        {
            var level = dungeonLevel(minecraft);
            var player = player(minecraft);
            int found = 0;
            for (var entity : level.getAllEntities())
            {
                if (entity instanceof VoidMonster && entity.distanceTo(player) < CLEARED_RADIUS)
                    found++;
            }
            return found;
        });
        if (left != 0)
            throw new AssertionError("A cleared dungeon should stay cleared, found " + left + " in " + command);
    }

    /**
     * Spectating from a little off each of the closest few, as they are rarely in view of where players arrive.
     */
    private static void lookAtNearestMobs(ClientGameTestContext context, TestServerContext server, String command)
    {
        var home = server.computeOnServer(minecraft -> player(minecraft).position());
        var spots = server.computeOnServer(minecraft ->
        {
            var address = DungeonAddress.at(player(minecraft).getBlockX(), player(minecraft).getBlockZ());
            var mobs = new ArrayList<Entity>();
            for (var entity : dungeonLevel(minecraft).getAllEntities())
            {
                if (entity instanceof VoidMonster && sameDungeon(address, entity))
                    mobs.add(entity);
            }
            mobs.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(home)));
            return mobs.stream().limit(MOB_CLOSE_UPS).map(Entity::getId).toList();
        });
        for (int i = 0; i < spots.size(); i++)
        {
            int id = spots.get(i);
            server.runCommand("gamemode spectator @a");
            var at = server.computeOnServer(minecraft ->
            {
                var entity = dungeonLevel(minecraft).getEntity(id);
                return entity == null ? null : entity.getBoundingBox().getCenter();
            });
            if (at == null)
                continue;
            double feetX = at.x + 2.2;
            double feetY = at.y + 0.4;
            double feetZ = at.z + 2.2;
            double dx = at.x - feetX;
            double dy = at.y - (feetY + 1.62);
            double dz = at.z - feetZ;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            server.runCommand("execute as @a at @s run tp @s " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
            context.waitTicks(25);
            context.takeScreenshot("mob-dungeon-" + command + "-near-" + i);
        }
        server.runCommand("execute as @a at @s run tp @s " + home.x + " " + home.y + " " + home.z);
        context.waitTicks(10);
    }

    private static boolean sameDungeon(DungeonAddress address, Entity entity)
    {
        var other = DungeonAddress.at(entity.getBlockX(), entity.getBlockZ());
        return address != null && address.equals(other);
    }

    private static ServerLevel dungeonLevel(MinecraftServer minecraft)
    {
        return minecraft.getLevel(VoidDungeons.WORLD_KEY);
    }

    private static List<BlockPos> nestsAround(ServerLevel level, BlockPos center, int range)
    {
        var found = new ArrayList<BlockPos>();
        for (int cx = (center.getX() - range) >> 4; cx <= (center.getX() + range) >> 4; cx++)
        {
            for (int cz = (center.getZ() - range) >> 4; cz <= (center.getZ() + range) >> 4; cz++)
            {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                    continue;
                for (var tile : chunk.getBlockEntities().values())
                {
                    if (tile instanceof BarnacleNestTile)
                        found.add(tile.getBlockPos());
                }
            }
        }
        return found;
    }

    private static void setInfectionTicks(ServerPlayer player, int ticks)
    {
        try
        {
            Field field = BarnacleInfection.class.getDeclaredField("TICKS");
            field.setAccessible(true);
            player.setAttached((net.fabricmc.fabric.api.attachment.v1.AttachmentType<Integer>) field.get(null), ticks);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static void set(Object target, Class<?> owner, String name, int value)
    {
        try
        {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
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

    private static void expectClient(ClientGameTestContext context, String message, Predicate<net.minecraft.client.Minecraft> condition)
    {
        if (!context.computeOnClient(condition::test))
            throw new AssertionError(message);
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
