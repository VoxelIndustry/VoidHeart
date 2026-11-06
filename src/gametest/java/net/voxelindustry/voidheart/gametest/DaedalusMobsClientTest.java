package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.BishopRetreat;
import net.voxelindustry.voidheart.common.content.mob.Centurion;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith.Phase;
import net.voxelindustry.voidheart.common.content.mob.VoidOrb;
import net.voxelindustry.voidheart.common.content.mob.WraithStalk;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The five Daedalus mobs against a netherite player: what each does, how hard it hits through that armor, and how it looks.
 */
public class DaedalusMobsClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int ROW_Z = 4;
    private static final int SPACING = 6;
    private static final int CLOSE_FOV = 45;
    private static final int DEFAULT_FOV = 70;
    // Full netherite with Protection IV everywhere leaves 0.64 * 0.36 of a 60 hit, and 0.36 of anything that ignores armor
    private static final float RUSH_ON_NETHERITE = 14.69F;
    private static final float CREEP_ON_NETHERITE = 2.59F;
    private static final float LANCE_ON_PROTECTION = 2.88F;

    private record Pose(String name, EntityType<? extends Mob> type, double height, Consumer<Mob> setup, boolean side)
    {
        Pose(String name, EntityType<? extends Mob> type, double height, Consumer<Mob> setup)
        {
            this(name, type, height, setup, false);
        }
    }

    // Ticks into the blow at which each pose is frozen, out of FallenBishop.BLOW_TICKS
    private static final int BLOW_WINDUP_TICK = 3;
    private static final int BLOW_STRIKE_TICK = 7;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("gamerule keep_inventory true");
            server.runCommand("difficulty normal");
            context.waitTicks(20);
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(DaedalusMobsClientTest::checkNumbers);
            checkLooks(context, server);

            server.runCommand("gamemode survival @a");
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(DEFAULT_FOV);
            });
            checkStalkerFollows(context, server);
            checkStalkerStare(context, server);
            checkStalkerShield(context, server);
            checkStalkerCreep(context, server);
            checkStalkerCrossing(context, server);
            checkBishopLance(context, server);
            checkBishopBlow(context, server);
            checkBishopRetreat(context, server);
            checkBoulderSlam(context, server);
            checkBoulderWard(context, server);
            checkBoulderBreach(context, server);
            checkLegionaries(context, server);
            checkCenturion(context, server);
            checkDrops(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }

        checkSavedState(context);
    }

    private static void checkNumbers(MinecraftServer minecraft)
    {
        record Stats(EntityType<? extends Mob> type, double health, double armor, boolean undead)
        {
        }
        var all = List.of(
                new Stats(VoidHeartEntities.STALKING_WRAITH, 70, 6, true),
                new Stats(VoidHeartEntities.FALLEN_BISHOP, 120, 10, true),
                new Stats(VoidHeartEntities.MALIGNANT_BOULDER, 240, 14, false),
                new Stats(VoidHeartEntities.LEGIONARY, 70, 14, false),
                new Stats(VoidHeartEntities.CENTURION, 150, 18, false));
        var level = minecraft.overworld();
        for (var stats : all)
        {
            var mob = stats.type().create(level, EntitySpawnReason.COMMAND);
            expect(mob.getAttributeValue(Attributes.MAX_HEALTH) == stats.health(), stats.type() + " health");
            expect(mob.getAttributeValue(Attributes.ARMOR) == stats.armor(), stats.type() + " armor");
            expect(mob.is(EntityTypeTags.UNDEAD) == stats.undead(), stats.type() + " undead tag");
            expect(mob.getBbHeight() < 3, stats.type() + " must fit a three block corridor, is " + mob.getBbHeight());
            mob.discard();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Looks
    // ---------------------------------------------------------------------------------------------

    private static void checkLooks(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.fov().set(CLOSE_FOV);
        });

        var poses = List.of(
                new Pose("stalking-wraith", VoidHeartEntities.STALKING_WRAITH, 1.4, mob -> ((StalkingWraith) mob).setRevealed(true)),
                new Pose("stalking-wraith-smile-half", VoidHeartEntities.STALKING_WRAITH, 1.4, mob -> stalker(mob, WraithStalk.CONTACT_TICKS / 2, true)),
                new Pose("stalking-wraith-smile-full", VoidHeartEntities.STALKING_WRAITH, 1.4, mob -> stalker(mob, WraithStalk.CONTACT_TICKS, true)),
                new Pose("stalking-wraith-faint", VoidHeartEntities.STALKING_WRAITH, 1.4, mob -> stalker(mob, 0, false)),
                new Pose("stalking-wraith-faint-smile", VoidHeartEntities.STALKING_WRAITH, 1.4, mob -> stalker(mob, WraithStalk.CONTACT_TICKS, false)),
                new Pose("fallen-bishop", VoidHeartEntities.FALLEN_BISHOP, 1.4, mob -> {}),
                new Pose("fallen-bishop-kneeling", VoidHeartEntities.FALLEN_BISHOP, 1, mob -> ((FallenBishop) mob).setKneeling(true)),
                new Pose("fallen-bishop-blow-windup", VoidHeartEntities.FALLEN_BISHOP, 1.4, mob -> {}, true),
                new Pose("fallen-bishop-blow-strike", VoidHeartEntities.FALLEN_BISHOP, 1.4, mob -> {}, true),
                new Pose("fallen-bishop-lance-locked", VoidHeartEntities.FALLEN_BISHOP, 1.4, mob -> showLance(mob, 15), true),
                new Pose("fallen-bishop-lance-fired", VoidHeartEntities.FALLEN_BISHOP, 1.4, mob -> showLance(mob, FallenBishop.CHARGE_TICKS + 2), true),
                new Pose("malignant-boulder", VoidHeartEntities.MALIGNANT_BOULDER, 1.5, mob -> {}),
                new Pose("legionary", VoidHeartEntities.LEGIONARY, 1.2, mob -> {}),
                new Pose("legionary-frenzied", VoidHeartEntities.LEGIONARY, 1.2, mob -> ((Legionary) mob).enterFrenzy()),
                new Pose("centurion", VoidHeartEntities.CENTURION, 1.4, mob -> {}));

        server.runCommand("fill -10 " + GROUND + " -6 " + (poses.size() * SPACING + 6) + " " + GROUND + " 14 voidheart:voidstone_tile");
        for (int i = 0; i < poses.size(); i++)
        {
            var pose = poses.get(i);
            int x = i * SPACING;
            server.runOnServer(minecraft -> posed(minecraft, pose, x));
        }
        context.waitTicks(30 - BLOW_STRIKE_TICK);
        swingPose(server, poses, "fallen-bishop-blow-strike");
        context.waitTicks(BLOW_STRIKE_TICK - BLOW_WINDUP_TICK);
        swingPose(server, poses, "fallen-bishop-blow-windup");
        context.waitTicks(BLOW_WINDUP_TICK);
        server.runCommand("tick freeze");

        for (int i = 0; i < poses.size(); i++)
        {
            var pose = poses.get(i);
            double x = i * SPACING + 0.5;
            double y = FEET + pose.height();
            double reach = 2.6 + pose.height() * 1.4;
            aimFrom(context, server, x + reach * 0.5, y - 0.6, ROW_Z + 0.5 + reach, x, y, ROW_Z + 0.5);
            context.takeScreenshot("daedalus-" + pose.name() + "-front");
            aimFrom(context, server, x - reach * 0.6, y, ROW_Z + 0.5 - reach, x, y, ROW_Z + 0.5);
            context.takeScreenshot("daedalus-" + pose.name() + "-back");
            if (pose.side())
            {
                aimFrom(context, server, x + reach * 1.1, y - 0.3, ROW_Z + 1.5, x, y, ROW_Z + 1.5);
                context.takeScreenshot("daedalus-" + pose.name() + "-side");
            }
        }

        // The smile close up, straight on, at its three stages, which are the first three poses
        for (int i = 0; i < 3; i++)
        {
            double x = i * SPACING + 0.5;
            aimFrom(context, server, x, FEET + 0.6, ROW_Z + 0.5 + 1.6, x, FEET + 2.2, ROW_Z);
            context.takeScreenshot("daedalus-" + poses.get(i).name() + "-face");
        }

        // The whole order side by side, lowest rank first
        server.runCommand("tick unfreeze");
        // Removed rather than killed, so that no death animation is left in the shot
        server.runOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(Mob.class, new AABB(-20, FEET - 2, -10, 120, FEET + 10, 20)).forEach(Entity::discard));
        server.runOnServer(minecraft ->
        {
            var ranks = List.<EntityType<? extends Mob>>of(VoidHeartEntities.FALLEN_NOVICE, VoidHeartEntities.FALLEN_ADEPT, VoidHeartEntities.FALLEN_ELDER, VoidHeartEntities.FALLEN_BISHOP);
            for (int i = 0; i < ranks.size(); i++)
                posed(minecraft, new Pose("rank", ranks.get(i), 1, mob -> {}), i * 2);
        });
        context.waitTicks(10);
        aimFrom(context, server, 3.5, FEET + 0.5, ROW_Z + 7.5, 3.5, FEET + 1.3, ROW_Z + 0.5);
        context.takeScreenshot("daedalus-fallen-order");

        // Far down a dark corridor, which is how a player would first see it
        server.runCommand("tick unfreeze");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("fill -2 " + FEET + " 20 2 " + (FEET + 3) + " 34 voidheart:voidstone_bricks hollow");
        server.runCommand("fill -1 " + FEET + " 20 1 " + (FEET + 2) + " 20 air");
        server.runOnServer(minecraft ->
        {
            var wraith = spawn(minecraft, VoidHeartEntities.STALKING_WRAITH, 0.5, FEET, 32.5);
            wraith.setNoAi(true);
            wraith.setYRot(180);
            wraith.setYHeadRot(180);
            wraith.setYBodyRot(180);
            wraith.setContact(WraithStalk.CONTACT_TICKS);
        });
        context.waitTicks(30);
        context.runOnClient(minecraft -> minecraft.options.fov().set(DEFAULT_FOV));
        aimFrom(context, server, 0.5, FEET, 22.5, 0.5, FEET + 2.1, 32.5);
        context.takeScreenshot("daedalus-stalking-wraith-corridor");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("fill -2 " + FEET + " 20 2 " + (FEET + 3) + " 34 air");
        server.runCommand("fill -10 " + GROUND + " -6 " + (poses.size() * SPACING + 6) + " " + GROUND + " grass_block");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }

    private static void stalker(Mob mob, int contact, boolean revealed)
    {
        var wraith = (StalkingWraith) mob;
        wraith.setContact(contact);
        wraith.setRevealed(revealed);
    }

    private static void showLance(Mob mob, int ticks)
    {
        var bishop = (FallenBishop) mob;
        // Forward and off to its right, so that the beam crosses the shot instead of coming at the camera
        var end = new Vec3(bishop.getX() - 3, bishop.getY() + 1.2, bishop.getZ() + 7);
        bishop.showLance(end, ticks);
    }

    private static void swingPose(TestServerContext server, List<Pose> poses, String name)
    {
        int index = poses.indexOf(poses.stream().filter(pose -> pose.name().equals(name)).findFirst().orElseThrow());
        double x = index * SPACING + 0.5;
        server.runOnServer(minecraft ->
        {
            var bishop = minecraft.overworld().getEntitiesOfClass(FallenBishop.class, new AABB(x - 1, FEET, ROW_Z - 1, x + 1, FEET + 3, ROW_Z + 2)).getFirst();
            bishop.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        });
    }

    private static void posed(MinecraftServer minecraft, Pose pose, int x)
    {
        var level = minecraft.overworld();
        var mob = pose.type().create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x + 0.5, FEET, ROW_Z + 0.5, 0, 0);
        mob.setYHeadRot(0);
        mob.setYBodyRot(0);
        mob.setNoAi(true);
        pose.setup().accept(mob);
        level.addFreshEntity(mob);
    }

    // ---------------------------------------------------------------------------------------------
    // Stalking Wraith
    // ---------------------------------------------------------------------------------------------

    private static final double STALK_X = 300;

    private static void checkStalkerFollows(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, STALK_X);
        clearArea(server, STALK_X);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, true);
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.STALKING_WRAITH, STALK_X + 14.5, FEET, 0.5);
        });
        context.waitTicks(10);
        server.runOnServer(minecraft -> expect(!first(minecraft, VoidHeartEntities.STALKING_WRAITH).isRevealed(), "Far from the player, it should stay faint"));
        // Facing west with the wraith far behind, so it has to come in to five or six blocks
        face(server, STALK_X + 0.5, FEET, 0.5, 90, 0);
        waitFor(context, server, 400, () -> "The stalker should settle behind the player, " + server.computeOnServer(DaedalusMobsClientTest::describeStalker), minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            var player = player(minecraft);
            double distance = wraith.distanceTo(player);
            return WraithStalk.isHolding(distance, WraithStalk.isBehind(player.position(), player.getViewVector(1), wraith.position()));
        });
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "Stalking must not hurt, health " + player.getHealth());
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            expect(wraith.contact() == 0, "Nobody looked at it, so it should not smile");
            expect(wraith.isRevealed(), "Close enough to stalk, it should show itself");
        });
        context.waitTicks(12);
        expectClient(context, "The client should see it faded in", minecraft ->
                minecraft.level.getEntitiesOfClass(StalkingWraith.class, minecraft.player.getBoundingBox().inflate(10)).stream().anyMatch(wraith -> wraith.presence > 0.99F));
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(2);
        context.takeScreenshot("daedalus-stalker-behind");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void checkStalkerStare(ClientGameTestContext context, TestServerContext server)
    {
        // A short look, then looking away: the smile comes and goes without a rush
        lookAtStalker(server);
        waitFor(context, server, 20, "The stalker should notice the stare", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.STARE);
        context.waitTicks(12);
        context.takeScreenshot("daedalus-stalker-stare-half");
        server.runOnServer(minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            expect(wraith.contact() > 5 && wraith.contact() < WraithStalk.CONTACT_TICKS, "The stare should be building, at " + wraith.contact());
        });
        server.runOnServer(minecraft -> lookAway(minecraft));
        waitFor(context, server, 40, "Looking away should let the smile fade", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).contact() == 0);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A short look must not set off the rush, health " + player.getHealth());
        });

        // A full stare: the rush lands for about fifteen through Protection IV netherite
        waitFor(context, server, 200, "The stalker should go back behind", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.STALK);
        lookAtStalker(server);
        waitFor(context, server, 40, "The stalker should notice the stare", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.STARE);
        context.waitTicks(WraithStalk.CONTACT_TICKS - 4);
        context.takeScreenshot("daedalus-stalker-stare-full");
        waitFor(context, server, 40, "The stalker should rush", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.RUSH);
        context.waitTicks(2);
        context.takeScreenshot("daedalus-stalker-rush");
        waitFor(context, server, 40, "The rush should end", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.RECOVER);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            expect(Math.abs(lost - RUSH_ON_NETHERITE) < 1, "The rush should take about " + RUSH_ON_NETHERITE + " through netherite, took " + lost);
        });
    }

    private static void checkStalkerShield(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var player = player(minecraft);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        });
        waitFor(context, server, 200, "The stalker should go back to stalking", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.STALK);
        lookAtStalker(server);
        raiseShield(context, server);
        var blockedAt = new String[1];
        waitFor(context, server, 80, "The stalker should rush the shield", minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            if (wraith.phase() == Phase.RUSH)
                blockedAt[0] = "blocking " + player(minecraft).isBlocking() + ", head yaw " + player(minecraft).getYHeadRot() + ", " + describeStalker(minecraft);
            return wraith.phase() == Phase.RECOVER;
        });
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A raised shield should stop the rush, health " + player.getHealth() + ", last seen " + blockedAt[0]);
            var shield = player.getItemBySlot(EquipmentSlot.OFFHAND);
            expect(player.getCooldowns().isOnCooldown(shield), "The rush should disable the shield");
        });
        lowerShield(context, server);
    }

    private static void checkStalkerCreep(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(GameTestKit::heal);
        waitFor(context, server, 200, "The stalker should go back to stalking", minecraft -> first(minecraft, VoidHeartEntities.STALKING_WRAITH).phase() == Phase.STALK);
        server.runOnServer(minecraft -> lookAway(minecraft));
        context.waitTicks(5);
        server.runOnServer(minecraft -> setIntField(first(minecraft, VoidHeartEntities.STALKING_WRAITH), StalkingWraith.class, "unnoticedTicks", WraithStalk.UNNOTICED_TICKS - 10));
        waitFor(context, server, 200, "Left unnoticed, the stalker should creep in and strike", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            expect(Math.abs(lost - CREEP_ON_NETHERITE) < 0.5, "The creeping strike should take about " + CREEP_ON_NETHERITE + " through netherite, took " + lost);
        });
        clearScene(server);
    }

    /**
     * Shut in a room facing one wall, the player is reached from the wall at their back.
     */
    private static void checkStalkerCrossing(ClientGameTestContext context, TestServerContext server)
    {
        double x = STALK_X + 40;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + ((int) x - 3) + " " + GROUND + " -3 " + ((int) x + 3) + " " + (FEET + 3) + " 3 voidheart:voidstone_bricks hollow");
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.STALKING_WRAITH, x + 0.5, FEET, 12.5);
        });
        face(server, x + 0.5, FEET, 0.5, 180, 0);
        context.waitTicks(80);
        context.takeScreenshot("daedalus-stalker-crossing");
        waitFor(context, server, 320, () -> "The stalker should cross into the room, " + server.computeOnServer(DaedalusMobsClientTest::describeStalker), minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            return !wraith.isCrossing() && Math.abs(wraith.getX() - (x + 0.5)) < 2.5 && Math.abs(wraith.getZ() - 0.5) < 2.5;
        });
        server.runOnServer(minecraft ->
        {
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            expect(wraith.getZ() > 0.5, "It should come out of the wall behind the player, at z " + wraith.getZ());
            var player = player(minecraft);
            wraith.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1);
            expect(wraith.isLockedSolid(), "A hit should take its crossing away");
        });
        clearScene(server);
        server.runCommand("fill " + ((int) x - 3) + " " + GROUND + " -3 " + ((int) x + 3) + " " + (FEET + 3) + " 3 air");
    }

    private static void lookAtStalker(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
            aimPlayer(player, wraith.getEyePosition());
        });
    }

    private static void lookAway(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var wraith = first(minecraft, VoidHeartEntities.STALKING_WRAITH);
        aimPlayer(player, player.getEyePosition().add(player.getEyePosition().subtract(wraith.getEyePosition()).multiply(1, 0, 1)));
    }

    // ---------------------------------------------------------------------------------------------
    // Fallen Bishop
    // ---------------------------------------------------------------------------------------------

    private static final double BISHOP_X = 400;

    private static void checkBishopLance(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, BISHOP_X);
        clearArea(server, BISHOP_X);
        // Armor without Protection: the lance should take all eight
        server.runOnServer(minecraft ->
        {
            armor(minecraft, false);
            heal(minecraft);
            // Rooted so that it keeps to lance range instead of walking in to club
            var bishop = spawn(minecraft, VoidHeartEntities.FALLEN_BISHOP, BISHOP_X + 10.5, FEET, 0.5);
            bishop.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 10));
        });
        face(server, BISHOP_X + 0.5, FEET, 0.5, -90, 0);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitFor(context, server, 120, "The bishop should start charging", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks() > 0);
        context.waitTicks(4);
        context.takeScreenshot("daedalus-bishop-aiming");
        waitFor(context, server, 20, "The bishop's aim should lock", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks() > FallenBishop.CHARGE_TICKS - FallenBishop.LOCK_TICKS);
        context.takeScreenshot("daedalus-bishop-locked");
        waitFor(context, server, 20, "The bishop should fire", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks() > FallenBishop.CHARGE_TICKS);
        context.waitTicks(1);
        context.takeScreenshot("daedalus-bishop-lance");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            expect(Math.abs(lost - FallenBishop.LANCE_DAMAGE) < 0.1, "Armor should do nothing against the lance, took " + lost);
            armor(minecraft, true);
            heal(minecraft);
        });

        lanceOnce(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            expect(Math.abs(lost - LANCE_ON_PROTECTION) < 0.2, "Protection should still count against the lance, took " + lost);
            heal(minecraft);
        });
        face(server, BISHOP_X + 0.5, FEET, 0.5, -90, 0);
        raiseShield(context, server);

        lanceOnce(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A shield should stop the lance, health " + player.getHealth());
        });
        lowerShield(context, server);

        // Stepping aside once the aim locks dodges it
        waitFor(context, server, 160, "The bishop should charge again", minecraft ->
        {
            int ticks = first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks();
            return ticks > FallenBishop.CHARGE_TICKS - FallenBishop.LOCK_TICKS && ticks <= FallenBishop.CHARGE_TICKS;
        });
        face(server, BISHOP_X + 0.5, FEET, 3.5, -90, 0);
        waitFor(context, server, 20, "The bishop should fire", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks() > FallenBishop.CHARGE_TICKS);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "Stepping aside after the lock should dodge the lance, health " + player.getHealth());
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        clearScene(server);
    }

    private static void lanceOnce(ClientGameTestContext context, TestServerContext server)
    {
        face(server, BISHOP_X + 0.5, FEET, 0.5, -90, 0);
        waitFor(context, server, 160, "The bishop should charge", minecraft ->
        {
            int ticks = first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks();
            return ticks > 0 && ticks <= FallenBishop.CHARGE_TICKS - FallenBishop.LOCK_TICKS;
        });
        waitFor(context, server, 30, "The bishop should fire", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).lanceTicks() > FallenBishop.CHARGE_TICKS);
        context.waitTicks(2);
    }

    private static void checkBishopBlow(ClientGameTestContext context, TestServerContext server)
    {
        double x = BISHOP_X + 20;
        arrive(context, server, x);
        clearArea(server, x);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, false);
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.FALLEN_BISHOP, x + 2.5, FEET, 0.5);
        });
        face(server, x + 0.5, FEET, 0.5, -90, 0);
        waitFor(context, server, 100, "The bishop should start a blow", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).blowTicks() >= 0);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "The blow should not hurt as it starts, health " + player.getHealth());
        });
        waitFor(context, server, 20, "The blow should land", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).blowTicks() > FallenBishop.BLOW_HIT_TICK);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() < player.getMaxHealth(), "The blow should hurt when the crozier comes down");
            heal(minecraft);
        });

        // Stepping back during the wind-up
        waitFor(context, server, 100, "The bishop should start another blow", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).blowTicks() == 1);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            var player = player(minecraft);
            var away = player.position().subtract(bishop.position()).multiply(1, 0, 1).normalize().scale(4);
            player.connection.teleport(player.getX() + away.x, player.getY(), player.getZ() + away.z, player.getYRot(), player.getXRot());
        });
        waitFor(context, server, 20, "The blow should come down", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).blowTicks() > FallenBishop.BLOW_HIT_TICK
                                                                                || first(minecraft, VoidHeartEntities.FALLEN_BISHOP).blowTicks() < 0);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "Stepping back during the wind-up should dodge the blow, health " + player.getHealth());
        });
        clearScene(server);
    }

    private static void checkBishopRetreat(ClientGameTestContext context, TestServerContext server)
    {
        double x = BISHOP_X + 40;
        arrive(context, server, x);
        clearArea(server, x);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var bishop = spawn(minecraft, VoidHeartEntities.FALLEN_BISHOP, x + 6.5, FEET, 0.5);
            bishop.setHealth(bishop.getMaxHealth() * 0.25F);
            bishop.setTarget(player(minecraft));
        });
        face(server, x + 0.5, FEET, 0.5, -90, 0);
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            expect(bishop.distanceTo(player(minecraft)) > 9, "A hurt bishop should run, still at " + bishop.distanceTo(player(minecraft))
                    + ", health " + bishop.getHealth() + ", target " + bishop.getTarget() + ", kneeling " + bishop.isKneeling() + ", withdrawals " + bishop.withdrawals()
                    + ", no ai " + bishop.isNoAi() + ", speed " + bishop.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", effects " + bishop.getActiveEffects());
            expect(bishop.withdrawals() == 1, "That should count as one withdrawal");
        });

        // Out of sight behind a wall, it kneels and heals four a second
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            var player = player(minecraft);
            var toward = bishop.position().subtract(player.position()).normalize();
            var side = new Vec3(-toward.z, 0, toward.x);
            var mid = player.position().add(toward.scale(3));
            for (int along = -6; along <= 6; along++)
                for (int up = 0; up < 5; up++)
                {
                    var at = mid.add(side.scale(along));
                    minecraft.overworld().setBlockAndUpdate(BlockPos.containing(at.x, FEET + up, at.z), Blocks.STONE.defaultBlockState());
                }
        });
        waitFor(context, server, 220, "Out of sight, the bishop should kneel", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).isKneeling());
        float before = server.computeOnServer(minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).getHealth());
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            float healed = bishop.getHealth() - before;
            expect(Math.abs(healed - BishopRetreat.HEAL_PER_TICK * 40) < 1, "Kneeling should heal four a second, healed " + healed + " in two seconds");

            // A hit breaks it off
            var player = player(minecraft);
            bishop.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1);
        });
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            expect(!bishop.isKneeling(), "A hit should break off the healing");
            bishop.setHealth(bishop.getMaxHealth() * 0.2F);
        });
        waitFor(context, server, 220, "A second withdrawal should be allowed", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).isKneeling());
        // Past the invulnerability left by the last hit, or this one would not count
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            expect(bishop.withdrawals() == 2, "That should be the second withdrawal, counted " + bishop.withdrawals());
            var player = player(minecraft);
            bishop.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1);
        });
        context.waitTicks(3);
        server.runOnServer(minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP).setHealth(10));
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var bishop = first(minecraft, VoidHeartEntities.FALLEN_BISHOP);
            expect(!bishop.isKneeling() && bishop.withdrawals() == 2, "There should be no third withdrawal, kneeling " + bishop.isKneeling() + ", withdrawals " + bishop.withdrawals());
        });
        clearScene(server);
        clearArea(server, x);
    }

    // ---------------------------------------------------------------------------------------------
    // Malignant Boulder
    // ---------------------------------------------------------------------------------------------

    private static final double BOULDER_X = 500;

    private static void checkBoulderSlam(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, BOULDER_X);
        clearArea(server, BOULDER_X);
        // Through Protection IV netherite and Resistance, only the absorption takes part of it
        server.runOnServer(minecraft ->
        {
            armor(minecraft, true);
            heal(minecraft);
            var player = player(minecraft);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            spawn(minecraft, VoidHeartEntities.MALIGNANT_BOULDER, BOULDER_X + 3.5, FEET, 0.5);
        });
        face(server, BOULDER_X + 0.5, FEET, 0.5, -90, 0);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        waitFor(context, server, 100, "The boulder should raise its fists", minecraft -> first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).slamTicks() > 0);
        context.waitTicks(MalignantBoulder.WINDUP_TICKS - 8);
        context.takeScreenshot("daedalus-boulder-raised");
        waitFor(context, server, 20, "The slam should land", minecraft -> first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).slamTicks() >= MalignantBoulder.WINDUP_TICKS);
        context.takeScreenshot("daedalus-boulder-slam");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getAbsorptionAmount() == 0, "The slam should use up the absorption, left " + player.getAbsorptionAmount());
            expect(Math.abs(player.getHealth() - 8) < 0.01, "The slam should take twenty through every defense, health " + player.getHealth() + ", deaths " + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS)));
            player.removeEffect(MobEffects.ABSORPTION);
        });

        // A raised shield doesn't help either. Each check starts as a wind-up begins, so that no other slam lands in between
        waitForSlamStart(context, server);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            player(minecraft).addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
        });
        raiseShield(context, server);
        waitForSlamLanding(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(Math.abs(player.getHealth() - 8) < 0.01, "A shield must not stop the slam, health " + player.getHealth()
                    + ", absorption " + player.getAbsorptionAmount() + ", blocking " + player.isBlocking()
                    + ", deaths " + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))
                    + ", boulder " + first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).distanceTo(player) + " away at " + first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).position()
                    + ", player at " + player.position());
            player.removeEffect(MobEffects.ABSORPTION);
        });
        lowerShield(context, server);

        // A totem still saves a full health bar
        waitForSlamStart(context, server);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            player(minecraft).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        });
        waitForSlamLanding(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.isAlive() && player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(), "The totem should save the player from a full health bar, health " + player.getHealth()
                    + ", offhand " + player.getOffhandItem() + ", deaths " + player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))
                    + ", boulder " + first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).distanceTo(player) + " away");
            player.removeAllEffects();
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        clearScene(server);
    }

    private static void checkBoulderWard(ClientGameTestContext context, TestServerContext server)
    {
        double x = BOULDER_X + 30;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var boulder = spawn(minecraft, VoidHeartEntities.MALIGNANT_BOULDER, x + 0.5, FEET, 0.5);
            boulder.setNoAi(true);
            boulder.setYRot(0);
            boulder.setYBodyRot(0);
            boulder.setYHeadRot(0);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var boulder = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER);
            float full = boulder.getHealth();

            var orb = new VoidOrb(level, player, boulder, 8);
            orb.setPos(x + 0.5, FEET + 1.5, 2.5);
            expect(!boulder.hurtServer(level, boulder.damageSources().source(VoidOrb.DAMAGE, orb, player), 8), "The boulder should shrug off an orb");
            expect(!boulder.hurtServer(level, boulder.damageSources().indirectMagic(player, player), 6), "The boulder should shrug off a potion of harming");
            expect(!boulder.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "The boulder should not take poison");
            expect(boulder.getHealth() == full, "Magic should leave its health alone");
            orb.discard();
        });
        // The totem from the slam check leaves particles around the camera for a while
        context.waitTicks(40);

        // The ward flash from the front left, frozen at a few moments
        server.runOnServer(minecraft -> ward(minecraft, new Vec3(x - 1.5, FEET + 2, -2.5)));
        context.waitTicks(2);
        server.runCommand("tick freeze");
        aimFrom(context, server, x - 2.5, FEET + 0.5, -4.5, x + 0.5, FEET + 1.5, 0.5);
        context.takeScreenshot("daedalus-boulder-ward-early");
        server.runCommand("tick step 4");
        context.waitTicks(8);
        context.takeScreenshot("daedalus-boulder-ward-spread");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, x - 2.5 + step * 0.06, FEET + 0.5, -4.5 + step * 0.05, x + 0.5, FEET + 1.5, 0.5);
            context.takeScreenshot("daedalus-boulder-ward-sweep-" + step);
        }
        server.runCommand("tick step 5");
        context.waitTicks(10);
        context.takeScreenshot("daedalus-boulder-ward-fading");
        server.runCommand("tick unfreeze");
        context.waitTicks(20);
        context.takeScreenshot("daedalus-boulder-ward-gone");

        // From behind, so the back faces of the hull show only through the front
        server.runOnServer(minecraft -> ward(minecraft, new Vec3(x + 0.5, FEET + 1.5, 3.5)));
        context.waitTicks(1);
        server.runCommand("tick freeze");
        aimFrom(context, server, x + 3.5, FEET + 1, 5, x + 0.5, FEET + 1.5, 0.5);
        server.runCommand("tick step 5");
        context.waitTicks(8);
        context.takeScreenshot("daedalus-boulder-ward-back");
        server.runCommand("tick unfreeze");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var boulder = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER);
            float full = boulder.getHealth();
            boulder.hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 10);
            expect(boulder.getHealth() < full, "A plain hit should still hurt it");
        });
        server.runCommand("gamemode survival @a");
        clearScene(server);
    }

    private static void waitForSlamStart(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 100, "The boulder should start another slam", minecraft ->
        {
            int ticks = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).slamTicks();
            return ticks > 0 && ticks <= 4;
        });
        // Each slam knocks the player back a little, so they are put back in reach for the next check
        server.runOnServer(minecraft ->
        {
            var boulder = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER);
            var player = player(minecraft);
            var toward = player.position().subtract(boulder.position()).multiply(1, 0, 1).normalize();
            var spot = boulder.position().add(toward.scale(boulder.getBbWidth() / 2 + 1.5));
            float yaw = (float) Math.toDegrees(Math.atan2(toward.x, -toward.z));
            player.connection.teleport(spot.x, spot.y, spot.z, yaw, 0);
        });
    }

    private static void waitForSlamLanding(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, MalignantBoulder.WINDUP_TICKS + 4, "The slam should land",
                minecraft -> first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).slamTicks() >= MalignantBoulder.WINDUP_TICKS);
    }

    private static void ward(MinecraftServer minecraft, Vec3 from)
    {
        var level = minecraft.overworld();
        var player = player(minecraft);
        var boulder = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER);
        var orb = new VoidOrb(level, player, boulder, 8);
        orb.setPos(from);
        boulder.hurtServer(level, boulder.damageSources().source(VoidOrb.DAMAGE, orb, player), 8);
        orb.discard();
    }

    /**
     * Walled into a box with a chest in its wall, the player is reached by breaking through, and the chest is left standing.
     */
    private static void checkBoulderBreach(ClientGameTestContext context, TestServerContext server)
    {
        double x = BOULDER_X + 60;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + ((int) x - 3) + " " + GROUND + " -3 " + ((int) x + 3) + " " + (FEET + 3) + " 3 voidheart:voidstone_bricks hollow");
        server.runCommand("setblock " + ((int) x - 3) + " " + FEET + " 0 chest");
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            // Enough to live through the slam that follows the breach
            player(minecraft).addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 9));
            var boulder = spawn(minecraft, VoidHeartEntities.MALIGNANT_BOULDER, x - 9.5, FEET, 0.5);
            boulder.setTarget(player(minecraft));
        });
        tp(server, x + 0.5, FEET, 0.5);
        waitFor(context, server, 200, "The boulder should start breaking the wall", minecraft -> first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER).isBreaking());
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        aimCamera(server, x + 0.5, FEET, 0.5, x - 4, FEET + 1, 0.5);
        context.waitTicks(10);
        context.takeScreenshot("daedalus-boulder-breaking");
        waitFor(context, server, 600, "The boulder should break into the box", minecraft ->
        {
            var boulder = first(minecraft, VoidHeartEntities.MALIGNANT_BOULDER);
            return boulder.getBoundingBox().maxX > x - 2;
        });
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(BlockPos.containing(x - 3, FEET, 0)).is(Blocks.CHEST), "The boulder should leave a chest alone");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(x - 12, FEET - 2, -8, x + 8, FEET + 6, 8));
            expect(drops.isEmpty(), "Broken walls should drop nothing, found " + drops.size());
        });
        context.takeScreenshot("daedalus-boulder-through");
        clearScene(server);
        server.runOnServer(minecraft -> player(minecraft).removeAllEffects());
        clearArea(server, x);

        // With mobGriefing off, walls hold
        server.runCommand("gamerule mob_griefing false");
        server.runCommand("fill " + ((int) x - 3) + " " + GROUND + " -3 " + ((int) x + 3) + " " + (FEET + 3) + " 3 voidheart:voidstone_bricks hollow");
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var boulder = spawn(minecraft, VoidHeartEntities.MALIGNANT_BOULDER, x - 9.5, FEET, 0.5);
            boulder.setTarget(player(minecraft));
        });
        tp(server, x + 0.5, FEET, 0.5);
        context.waitTicks(200);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (int y = FEET; y <= FEET + 2; y++)
                for (int z = -2; z <= 2; z++)
                    expect(!level.getBlockState(BlockPos.containing(x - 3, y, z)).isAir(), "With mobGriefing off the wall should hold");
        });
        server.runCommand("gamerule mob_griefing true");
        clearScene(server);
        clearArea(server, x);
    }

    // ---------------------------------------------------------------------------------------------
    // Legionaries and Centurion
    // ---------------------------------------------------------------------------------------------

    private static final double LEGION_X = 650;

    private static void checkLegionaries(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, LEGION_X);
        clearArea(server, LEGION_X);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, true);
            heal(minecraft);
        });
        face(server, LEGION_X + 0.5, FEET, 0.5, -90, 0);
        server.runCommand("summon voidheart:legionary " + (LEGION_X + 14.5) + " " + FEET + " 0.5");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var legionaries = all(minecraft, VoidHeartEntities.LEGIONARY);
            expect(legionaries.size() == 2, "A legionary should arrive with its partner, found " + legionaries.size());
            var one = legionaries.get(0);
            var other = legionaries.get(1);
            expect(other.getUUID().equals(one.partner()) && one.getUUID().equals(other.partner()), "The two should be paired");
        });

        // They spread to either side before closing in
        waitFor(context, server, 200, () -> "The pair should flank the player, " + server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            return "player looking " + player.getViewVector(1) + ", " + all(minecraft, VoidHeartEntities.LEGIONARY).stream()
                    .map(legionary -> legionary.position().subtract(player.position()) + " side " + get(legionary, Legionary.class, "flankSide")
                                      + " partner found " + (legionary.partner() != null && minecraft.overworld().getEntity(legionary.partner()) != null))
                    .toList();
        }), minecraft ->
        {
            var player = player(minecraft);
            var legionaries = all(minecraft, VoidHeartEntities.LEGIONARY);
            if (legionaries.size() < 2)
                return false;
            var a = legionaries.get(0).position().subtract(player.position());
            var b = legionaries.get(1).position().subtract(player.position());
            return a.length() < 5 && b.length() < 5 && a.normalize().dot(b.normalize()) < -0.2;
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(2);
        context.takeScreenshot("daedalus-legionaries-flanking");

        // A raised shield is knocked out of use by the axe
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            aimPlayer(player(minecraft), all(minecraft, VoidHeartEntities.LEGIONARY).getFirst().getEyePosition());
        });
        // An axe can land while the shield comes up, which is what this checks for, so it isn't asserted raised
        raiseShield(context, server, false);
        waitFor(context, server, 100, "An axe should disable the shield",
                minecraft -> player(minecraft).getCooldowns().isOnCooldown(player(minecraft).getItemBySlot(EquipmentSlot.OFFHAND)));
        lowerShield(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);

            var legionaries = all(minecraft, VoidHeartEntities.LEGIONARY);
            var survivor = legionaries.get(1);
            double before = survivor.getAttributeValue(Attributes.MOVEMENT_SPEED);
            legionaries.get(0).hurtServer(minecraft.overworld(), player.damageSources().playerAttack(player), 1000);
            expect(survivor.isFrenzied(), "The survivor should enter its frenzy");
            expect(survivor.attackInterval() == Legionary.FRENZY_ATTACK_INTERVAL, "The frenzy should shorten its attacks");
            double after = survivor.getAttributeValue(Attributes.MOVEMENT_SPEED);
            // Walking speed grows with the square of the attribute
            double ratio = (after * after) / (before * before);
            expect(Math.abs(ratio - 1.3) < 0.03, "The frenzy should make it about a third faster, ratio " + ratio);
        });
        context.waitTicks(4);
        context.takeScreenshot("daedalus-legionary-frenzy");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        clearScene(server);
    }

    private static void checkCenturion(ClientGameTestContext context, TestServerContext server)
    {
        double x = LEGION_X + 60;
        arrive(context, server, x);
        clearArea(server, x);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, true);
            heal(minecraft);
            spawn(minecraft, VoidHeartEntities.CENTURION, x + 2.5, FEET, 0.5);
        });
        face(server, x + 0.5, FEET, 0.5, -90, 0);
        waitFor(context, server, 100, "The centurion should raise its shield to bash", minecraft -> first(minecraft, VoidHeartEntities.CENTURION).bashTicks() > 0);
        var start = context.computeOnClient(minecraft -> minecraft.player.position());
        waitFor(context, server, 20, "The bash should land", minecraft -> first(minecraft, VoidHeartEntities.CENTURION).bashTicks() >= Centurion.BASH_WINDUP_TICKS);
        context.waitTicks(25);
        var landed = context.computeOnClient(minecraft -> minecraft.player.position());
        double thrown = landed.subtract(start).horizontalDistance();
        expect(thrown > 5, "The bash should throw the player far back, moved " + thrown);

        // Back in range, the scimitar sweeps
        server.runOnServer(GameTestKit::heal);
        server.runOnServer(minecraft ->
        {
            var centurion = first(minecraft, VoidHeartEntities.CENTURION);
            var player = player(minecraft);
            player.teleportTo(centurion.getX() - 2.5, FEET, centurion.getZ());
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        waitFor(context, server, 120, "The centurion should sweep", minecraft -> first(minecraft, VoidHeartEntities.CENTURION).sweepTicks() > 0);
        context.waitTicks(Centurion.SWEEP_WINDUP_TICKS - 3);
        context.takeScreenshot("daedalus-centurion-sweep");
        waitFor(context, server, 20, "The sweep should land", minecraft -> first(minecraft, VoidHeartEntities.CENTURION).sweepTicks() >= Centurion.SWEEP_WINDUP_TICKS);
        context.waitTicks(1);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() < player.getMaxHealth(), "The sweep should hit the player in front");
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        clearScene(server);
    }

    // ---------------------------------------------------------------------------------------------
    // Drops
    // ---------------------------------------------------------------------------------------------

    private static final double DROPS_X = 800;
    private static final int DROP_ROLLS = 60;

    /**
     * Hand-written tables can carry fields 26.3 quietly ignores, so the counts are checked by actually killing each mob.
     */
    private static void checkDrops(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, DROPS_X);
        server.runOnServer(minecraft ->
        {
            expectDrops(minecraft, VoidHeartEntities.STALKING_WRAITH, 3, 5);
            expectDrops(minecraft, VoidHeartEntities.FALLEN_BISHOP, 5, 8);
            expectDrops(minecraft, VoidHeartEntities.MALIGNANT_BOULDER, 8, 12);
            expectDrops(minecraft, VoidHeartEntities.LEGIONARY, 2, 4);
            expectDrops(minecraft, VoidHeartEntities.CENTURION, 5, 8);
        });
        clearScene(server);
    }

    private static void expectDrops(MinecraftServer minecraft, EntityType<? extends Mob> type, int min, int max)
    {
        var level = minecraft.overworld();
        var area = new AABB(DROPS_X - 10, FEET + 20, 20, DROPS_X + 10, FEET + 40, 40);
        int lowest = Integer.MAX_VALUE;
        int highest = 0;
        for (int roll = 0; roll < DROP_ROLLS; roll++)
        {
            var mob = spawn(minecraft, type, DROPS_X + 0.5, FEET + 30, 30.5);
            mob.setNoAi(true);
            mob.kill(level);
            int count = 0;
            for (var drop : level.getEntitiesOfClass(ItemEntity.class, area))
            {
                if (drop.getItem().is(net.voxelindustry.voidheart.common.setup.VoidHeartItems.ENDER_SHARD))
                    count += drop.getItem().getCount();
                drop.discard();
            }
            lowest = Math.min(lowest, count);
            highest = Math.max(highest, count);
        }
        expect(lowest == min && highest == max, type + " should drop " + min + "-" + max + " ender shards, dropped " + lowest + "-" + highest);
    }

    // ---------------------------------------------------------------------------------------------
    // Saved state
    // ---------------------------------------------------------------------------------------------

    private static void checkSavedState(ClientGameTestContext context)
    {
        TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            tp(server, 0.5, FEET, 0.5);
            server.runCommand("summon voidheart:legionary 6.5 " + FEET + " 6.5");
            context.waitTicks(2);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);
                var legionaries = all(minecraft, VoidHeartEntities.LEGIONARY);
                legionaries.forEach(legionary -> legionary.setNoAi(true));
                legionaries.getFirst().enterFrenzy();

                var bishop = spawn(minecraft, VoidHeartEntities.FALLEN_BISHOP, -6.5, FEET, 6.5);
                setIntField(bishop, FallenBishop.class, "withdrawals", 1);
                bishop.setNoAi(true);

                var wraith = spawn(minecraft, VoidHeartEntities.STALKING_WRAITH, 0.5, FEET, 8.5);
                wraith.hurtServer(level, player.damageSources().playerAttack(player), 1);
                wraith.setNoAi(true);
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
                var legionaries = all(minecraft, VoidHeartEntities.LEGIONARY);
                expect(legionaries.size() == 2, "Both legionaries should be back, found " + legionaries.size());
                expect(legionaries.stream().filter(Legionary::isFrenzied).count() == 1, "One legionary should still be frenzied");
                expect(legionaries.get(0).getUUID().equals(legionaries.get(1).partner()), "The pair should still be paired");
                var frenzied = legionaries.stream().filter(Legionary::isFrenzied).findFirst().orElseThrow();
                expect(frenzied.getAttributeValue(Attributes.MOVEMENT_SPEED) > frenzied.getAttributeBaseValue(Attributes.MOVEMENT_SPEED),
                        "The frenzy should keep its speed");
                expect(first(minecraft, VoidHeartEntities.FALLEN_BISHOP).withdrawals() == 1, "The bishop should remember its withdrawals");
                expect(first(minecraft, VoidHeartEntities.STALKING_WRAITH).isLockedSolid(), "The stalker should stay locked solid");
            });
            server.runCommand("gamerule spawn_mobs true");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Full netherite, with Protection IV on every piece when asked.
     */
    private static void armor(MinecraftServer minecraft, boolean protection)
    {
        var player = player(minecraft);
        var enchantments = minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var pieces = List.of(
                new Object[] { EquipmentSlot.HEAD, Items.NETHERITE_HELMET },
                new Object[] { EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE },
                new Object[] { EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS },
                new Object[] { EquipmentSlot.FEET, Items.NETHERITE_BOOTS });
        for (var piece : pieces)
        {
            var stack = new ItemStack((net.minecraft.world.item.Item) piece[1]);
            if (protection)
                stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
            player.setItemSlot((EquipmentSlot) piece[0], stack);
        }
    }

    /**
     * Chunks far from the player aren't loaded, so the player goes first, before any block is placed or mob spawned.
     */
    private static void arrive(ClientGameTestContext context, TestServerContext server, double x)
    {
        tp(server, x + 0.5, FEET, 0.5);
        context.waitTicks(20);
    }

    /**
     * Holds the real use key, since the client releases a use that only the server started.
     */
    private static void raiseShield(ClientGameTestContext context, TestServerContext server)
    {
        raiseShield(context, server, true);
    }

    private static void raiseShield(ClientGameTestContext context, TestServerContext server, boolean expectRaised)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        });
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(8);
        if (expectRaised)
            server.runOnServer(minecraft -> expect(player(minecraft).isBlocking(), "The player should be blocking, offhand " + player(minecraft).getOffhandItem()));
    }

    private static void lowerShield(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(2);
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY));
    }

    private static void face(TestServerContext server, double x, double y, double z, float yaw, float pitch)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
    }

    private static void aimPlayer(ServerPlayer player, Vec3 at)
    {
        var eye = player.getEyePosition();
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    private static String describeStalker(MinecraftServer minecraft)
    {
        var wraiths = all(minecraft, VoidHeartEntities.STALKING_WRAITH);
        if (wraiths.isEmpty())
            return "no stalker alive";
        var wraith = wraiths.getFirst();
        var player = player(minecraft);
        return "stalker at " + wraith.position() + " in " + wraith.phase() + (wraith.isCrossing() ? " crossing" : "") + (wraith.isLockedSolid() ? " locked" : "")
               + ", blind " + get(wraith, net.voxelindustry.voidheart.common.content.mob.Wraith.class, "blindTicks")
               + ", cooldown " + get(wraith, net.voxelindustry.voidheart.common.content.mob.Wraith.class, "cooldown")
               + ", targeting " + wraith.getTarget()
               + ", " + wraith.distanceTo(player) + " from the player at " + player.position() + " looking " + player.getViewVector(1);
    }

    private static <T extends Entity> List<T> all(MinecraftServer minecraft, EntityType<T> type)
    {
        return new ArrayList<>(minecraft.overworld().getEntities(type, Entity::isAlive));
    }

    private static int get(Object target, Class<?> owner, String name)
    {
        try
        {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }
}
