package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Kernacian Dragoon: what each hit teaches it and how that shows, its beam and kick against a netherite player, how it climbs after a player out of its
 * reach, its boss bar and what it drops.
 */
public class KernacianDragoonClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int ROW_Z = 4;
    private static final int SPACING = 7;
    private static final int CLOSE_FOV = 45;
    private static final int DEFAULT_FOV = 70;
    private static final float MAGIC_HIT = 10;

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

            server.runOnServer(minecraft ->
            {
                var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(minecraft.overworld(), EntitySpawnReason.COMMAND);
                expect(dragoon.getAttributeValue(Attributes.MAX_HEALTH) == 400, "The dragoon should have 400 health");
                expect(dragoon.getBbHeight() < 3, "The dragoon must fit a three block corridor");
                dragoon.discard();
            });

            checkLooks(context, server);
            server.runCommand("gamemode survival @a");
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(DEFAULT_FOV);
            });
            checkKickPoses(context, server);
            checkEffects(context, server);
            checkAdaptation(context, server);
            checkBeam(context, server);
            checkKick(context, server);
            checkClimbPoses(context, server);
            checkClimbPile(context, server);
            checkClimbLip(context, server);
            checkClimbLedge(context, server);
            checkDrops(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
        checkSavedState(context);
    }

    // ---------------------------------------------------------------------------------------------
    // Looks
    // ---------------------------------------------------------------------------------------------

    private record Pose(String name, int adaptation, Function<KernacianDragoon, Void> setup)
    {
    }

    private static int maxed(Kind... kinds)
    {
        int packed = 0;
        for (var kind : kinds)
            for (int i = 0; i < DragoonAdaptation.MAX_HITS; i++)
                packed = DragoonAdaptation.adapt(packed, kind);
        return packed;
    }

    private static int hits(Kind kind, int count)
    {
        int packed = 0;
        for (int i = 0; i < count; i++)
            packed = DragoonAdaptation.adapt(packed, kind);
        return packed;
    }

    private static void checkLooks(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.fov().set(CLOSE_FOV);
        });

        var poses = new ArrayList<Pose>();
        poses.add(new Pose("base", 0, dragoon -> null));
        for (var stage : List.of(new Object[] { "head-split", 4 }, new Object[] { "head-shutters", 10 }, new Object[] { "head-swelling", 22 }))
        {
            poses.add(new Pose((String) stage[0], 0, dragoon ->
            {
                dragoon.showBeam(dragoon.position(), (Integer) stage[1]);
                return null;
            }));
        }
        for (var kind : Kind.values())
        {
            poses.add(new Pose(kind.name().toLowerCase() + "-first", hits(kind, 1), dragoon -> null));
            poses.add(new Pose(kind.name().toLowerCase() + "-max", maxed(kind), dragoon -> null));
        }
        poses.add(new Pose("all-max", maxed(Kind.values()), dragoon -> null));
        poses.add(new Pose("head-open", 0, dragoon ->
        {
            dragoon.showBeam(dragoon.position(), KernacianDragoon.BEAM_CHARGE_TICKS);
            return null;
        }));
        poses.add(new Pose("beam", 0, dragoon ->
        {
            dragoon.showBeam(dragoon.position().add(-4, 1.2, 9), KernacianDragoon.BEAM_CHARGE_TICKS + 12);
            return null;
        }));

        server.runCommand("fill -10 " + GROUND + " -8 " + (poses.size() * SPACING + 8) + " " + GROUND + " 16 voidheart:voidstone_tile");
        for (int i = 0; i < poses.size(); i++)
        {
            var pose = poses.get(i);
            int x = i * SPACING;
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
                dragoon.snapTo(x + 0.5, FEET, ROW_Z + 0.5, 0, 0);
                dragoon.setYHeadRot(0);
                dragoon.setYBodyRot(0);
                dragoon.setNoAi(true);
                dragoon.setAdaptation(pose.adaptation());
                level.addFreshEntity(dragoon);
                pose.setup().apply(dragoon);
            });
        }
        // From the middle of the row the client tracks them all, and long enough for every adapted part to grow in
        double middle = poses.size() * SPACING / 2.0;
        aimFrom(context, server, middle, FEET + 4, ROW_Z + 12, middle, FEET, ROW_Z);
        context.waitTicks(60);
        server.runCommand("tick freeze");
        int tracked = context.computeOnClient(minecraft -> minecraft.level.getEntitiesOfClass(KernacianDragoon.class, new AABB(-20, FEET - 2, -20, 400, FEET + 10, 30)).size());
        expect(tracked == poses.size(), "The client should see every pose before they freeze, sees " + tracked);

        for (int i = 0; i < poses.size(); i++)
        {
            var pose = poses.get(i);
            double x = i * SPACING + 0.5;
            double y = FEET + 1.3;
            aimFrom(context, server, x + 2.5, y - 0.4, ROW_Z + 0.5 + 5, x, y, ROW_Z + 0.5);
            context.takeScreenshot("dragoon-" + pose.name() + "-front");
            aimFrom(context, server, x - 3, y + 0.2, ROW_Z + 0.5 - 4.5, x, y, ROW_Z + 0.5);
            context.takeScreenshot("dragoon-" + pose.name() + "-back");
            if (pose.name().equals("beam") || pose.name().equals("head-open"))
            {
                aimFrom(context, server, x + 5.5, y - 0.2, ROW_Z + 1, x, y, ROW_Z + 1);
                context.takeScreenshot("dragoon-" + pose.name() + "-side");
            }
            // The head close up, straight on, shut and open
            if (pose.name().equals("base") || pose.name().startsWith("head-"))
            {
                aimFrom(context, server, x, FEET + 0.8, ROW_Z + 0.5 + 2.2, x, FEET + 2.2, ROW_Z + 0.5);
                context.takeScreenshot("dragoon-" + pose.name() + "-face");
            }
        }

        server.runCommand("tick unfreeze");
        server.runOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(KernacianDragoon.class, new AABB(-20, FEET - 2, -20, 400, FEET + 10, 30)).forEach(Entity::discard));
        server.runCommand("fill -10 " + GROUND + " -8 " + (poses.size() * SPACING + 8) + " " + GROUND + " 16 grass_block");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }

    /**
     * The kick is shot on a dragoon of its own and with the game running, as the frozen row leaves a pose set on a far dragoon's legs undrawn.
     */
    private static void checkKickPoses(ClientGameTestContext context, TestServerContext server)
    {
        double x = 300;
        arrive(context, server, x);
        clearArea(server, x);
        server.runOnServer(minecraft ->
        {
            var dragoon = spawn(minecraft, x + 0.5, 4.5);
            dragoon.setNoAi(true);
            dragoon.setYRot(0);
            dragoon.setYBodyRot(0);
            dragoon.setYHeadRot(0);
        });
        for (var moment : List.of(new Object[] { "kick-windup", KernacianDragoon.KICK_WINDUP_TICKS - 3 }, new Object[] { "kick", KernacianDragoon.KICK_WINDUP_TICKS + 1 }))
        {
            server.runOnServer(minecraft -> first(minecraft).showKick((Integer) moment[1]));
            context.waitTicks(5);
            aimFrom(context, server, x + 6, FEET + 0.5, 4.5, x + 0.5, FEET + 1.2, 4.5);
            context.takeScreenshot("dragoon-" + moment[0] + "-side");
            aimFrom(context, server, x + 2.5, FEET + 0.5, 9.5, x + 0.5, FEET + 1.2, 4.5);
            context.takeScreenshot("dragoon-" + moment[0] + "-front");
        }
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        clearScene(server);
    }

    /**
     * Particles only spawn while the game runs, so these are shot unfrozen: embers while charging, void fire where the beam lands, sparks and steam when hit.
     */
    private static void checkEffects(ClientGameTestContext context, TestServerContext server)
    {
        double x = 340;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + ((int) x - 6) + " " + GROUND + " -2 " + ((int) x + 6) + " " + GROUND + " 10 voidheart:voidstone_tile");
        server.runOnServer(minecraft ->
        {
            var dragoon = spawn(minecraft, x + 0.5, 0.5);
            dragoon.setNoAi(true);
            dragoon.setYRot(0);
            dragoon.setYBodyRot(0);
            dragoon.setYHeadRot(0);
        });
        context.waitTicks(5);

        server.runOnServer(minecraft -> first(minecraft).showBeam(first(minecraft).position(), 24));
        aimFrom(context, server, x + 2.5, FEET + 0.8, 4, x + 0.5, FEET + 2, 0.5);
        context.waitTicks(6);
        context.takeScreenshot("dragoon-embers");

        // The beam held on a spot on the floor a few blocks ahead
        server.runOnServer(minecraft -> first(minecraft).showBeam(new Vec3(x + 0.5, FEET, 7.5), KernacianDragoon.BEAM_CHARGE_TICKS + 12));
        aimFrom(context, server, x + 4, FEET + 1, 4, x + 0.5, FEET, 7.5);
        context.waitTicks(10);
        context.takeScreenshot("dragoon-beam-impact");
        aimFrom(context, server, x + 5, FEET + 0.5, 2, x + 0.5, FEET + 1.2, 4);
        context.waitTicks(3);
        context.takeScreenshot("dragoon-beam-impact-wide");

        // Hits throw sparks off its joints and a gasp of steam from its back
        server.runOnServer(minecraft ->
        {
            var dragoon = first(minecraft);
            dragoon.showBeam(dragoon.position(), 0);
            var player = player(minecraft);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            hit(dragoon, player.damageSources().playerAttack(player), 6);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        });
        aimFrom(context, server, x - 2.5, FEET + 0.8, -3, x + 0.5, FEET + 1.4, 0.5);
        context.takeScreenshot("dragoon-hit-sparks");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        clearScene(server);
    }

    // ---------------------------------------------------------------------------------------------
    // Adaptation
    // ---------------------------------------------------------------------------------------------

    private static final double ADAPT_X = 400;

    private static void checkAdaptation(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, ADAPT_X);
        clearArea(server, ADAPT_X);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var dragoon = spawn(minecraft, ADAPT_X + 3.5, 0.5);
            dragoon.setNoAi(true);

            // Magic ignores its armor, so what it loses shows the adaptation alone
            var lost = new ArrayList<Float>();
            for (int i = 0; i < DragoonAdaptation.MAX_HITS + 2; i++)
                lost.add(hit(dragoon, player.damageSources().indirectMagic(player, player), MAGIC_HIT));
            for (int i = 0; i < lost.size(); i++)
            {
                float expected = MAGIC_HIT * (1 - DragoonAdaptation.PER_HIT * Math.min(i, DragoonAdaptation.MAX_HITS));
                expect(Math.abs(lost.get(i) - expected) < 0.01, "Magic hit " + (i + 1) + " should take " + expected + ", took " + lost.get(i));
            }
            expect(DragoonAdaptation.count(dragoon.adaptation(), Kind.MAGIC) == DragoonAdaptation.MAX_HITS, "Magic should stop adapting at the cap");

            // Each source counts toward its own kind
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            hit(dragoon, player.damageSources().playerAttack(player), 6);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
            hit(dragoon, player.damageSources().playerAttack(player), 6);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hit(dragoon, player.damageSources().source(DamageTypes.ARROW, player, player), 6);
            hit(dragoon, player.damageSources().onFire(), 2);
            hit(dragoon, player.damageSources().explosion(null, null), 6);
            hit(dragoon, player.damageSources().fellOutOfWorld(), 1);

            int packed = dragoon.adaptation();
            for (var kind : List.of(Kind.BLADE, Kind.BLUNT, Kind.PROJECTILE, Kind.FIRE, Kind.EXPLOSION))
                expect(DragoonAdaptation.count(packed, kind) == 1, kind + " should have adapted once, got " + DragoonAdaptation.count(packed, kind));
            expect(DragoonAdaptation.count(packed, Kind.MAGIC) == DragoonAdaptation.MAX_HITS, "The void must not count as any kind");
            dragoon.setYRot(-90);
            dragoon.setYBodyRot(-90);
            dragoon.setYHeadRot(-90);
        });

        // The parts grown from those hits, seen from in front
        context.waitTicks(30);
        aimFrom(context, server, ADAPT_X + 0.5, FEET, 0.5, ADAPT_X + 3.5, FEET + 1.5, 0.5);
        context.runOnClient(minecraft -> minecraft.options.fov().set(CLOSE_FOV));
        context.takeScreenshot("dragoon-adapted-by-a-fight");
        context.runOnClient(minecraft -> minecraft.options.fov().set(DEFAULT_FOV));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        clearScene(server);
    }

    /**
     * @return the health this hit took
     */
    private static float hit(KernacianDragoon dragoon, DamageSource source, float damage)
    {
        setIntField(dragoon, LivingEntity.class, "damageCooldownTime", 0);
        setIntField(dragoon, Entity.class, "invulnerableTime", 0);
        float before = dragoon.getHealth();
        dragoon.hurtServer((net.minecraft.server.level.ServerLevel) dragoon.level(), source, damage);
        float lost = before - dragoon.getHealth();
        dragoon.setHealth(dragoon.getMaxHealth());
        return lost;
    }

    // ---------------------------------------------------------------------------------------------
    // Beam
    // ---------------------------------------------------------------------------------------------

    private static final double BEAM_X = 500;

    private static void checkBeam(ClientGameTestContext context, TestServerContext server)
    {
        arrive(context, server, BEAM_X);
        clearArea(server, BEAM_X);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, Enchantments.PROTECTION);
            heal(minecraft);
            var player = player(minecraft);
            // Rooted so the beam is all it does from range
            var dragoon = spawn(minecraft, BEAM_X + 10.5, 0.5);
            dragoon.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 10));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0));
        });
        // Turned a little away, so that the camera behind the player sees the dragoon beside them rather than hidden behind them
        face(server, BEAM_X + 0.5, FEET, 0.5, -65, 5);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));

        waitFor(context, server, 140, "The dragoon should open its head", minecraft -> first(minecraft).beamTicks() > 0);
        expectBossBar(server);
        context.waitTicks(KernacianDragoon.BEAM_CHARGE_TICKS - 6);
        context.takeScreenshot("dragoon-charging");
        waitFor(context, server, 20, "The dragoon should fire", minecraft -> first(minecraft).isFiring());
        context.waitTicks(10);
        context.takeScreenshot("dragoon-firing");
        waitFor(context, server, 60, "The beam should end", minecraft -> first(minecraft).beamTicks() == 0);
        float protectedLoss = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.hasEffect(MobEffects.FIRE_RESISTANCE), "The player should still have Fire Resistance");
            expect(player.getRemainingFireTicks() > 0 || player.isOnFire(), "Void fire should set the player alight despite Fire Resistance");
            return player.getMaxHealth() - player.getHealth();
        });
        expect(protectedLoss > 4, "Fire Resistance must not stop the beam, Protection IV netherite lost " + protectedLoss);

        // Fire Protection does better
        server.runOnServer(minecraft ->
        {
            armor(minecraft, Enchantments.FIRE_PROTECTION);
            heal(minecraft);
            player(minecraft).clearFire();
            setIntField(first(minecraft), KernacianDragoon.class, "beamCooldown", 0);
        });
        waitFor(context, server, 40, "The dragoon should fire again", minecraft -> first(minecraft).isFiring());
        waitFor(context, server, 60, "The beam should end", minecraft -> first(minecraft).beamTicks() == 0);
        float fireProtectedLoss = server.computeOnServer(minecraft -> player(minecraft).getMaxHealth() - player(minecraft).getHealth());
        expect(fireProtectedLoss < protectedLoss, "Fire Protection should do better than Protection, lost " + fireProtectedLoss + " against " + protectedLoss);

        // Cover blocks it
        server.runCommand("fill " + ((int) BEAM_X + 3) + " " + FEET + " -2 " + ((int) BEAM_X + 3) + " " + (FEET + 3) + " 2 voidheart:voidstone_bricks");
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            player(minecraft).clearFire();
            var dragoon = first(minecraft);
            setIntField(dragoon, KernacianDragoon.class, "beamCooldown", 0);
            // It only opens up on what it can see, so it is set going as the wall rises
            dragoon.showBeam(dragoon.position(), KernacianDragoon.BEAM_CHARGE_TICKS);
        });
        context.waitTicks(KernacianDragoon.BEAM_FIRE_TICKS + 20);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A wall should keep the beam off, health " + player.getHealth());
            expect(minecraft.overworld().getBlockState(net.minecraft.core.BlockPos.containing(BEAM_X + 3, FEET + 1, 0)).isSolid(), "The beam must not harm the wall");
            player.removeAllEffects();
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        clearScene(server);
    }

    private static void expectBossBar(TestServerContext server)
    {
        server.runOnServer(minecraft -> expect(first(minecraft).isBossBarShownTo(player(minecraft)), "The boss bar should show while it fights the player"));
    }

    // ---------------------------------------------------------------------------------------------
    // Kick
    // ---------------------------------------------------------------------------------------------

    private static void checkKick(ClientGameTestContext context, TestServerContext server)
    {
        double x = BEAM_X + 60;
        arrive(context, server, x);
        clearArea(server, x);
        server.runOnServer(minecraft ->
        {
            armor(minecraft, Enchantments.PROTECTION);
            heal(minecraft);
            spawn(minecraft, x + 2.2, 0.5);
        });
        face(server, x + 0.5, FEET, 0.5, -90, 0);
        waitFor(context, server, 100, "The dragoon should wind up a kick", minecraft -> first(minecraft).kickTicks() > 0);
        var start = context.computeOnClient(minecraft -> minecraft.player.position());
        waitFor(context, server, 20, "The kick should land", minecraft -> first(minecraft).kickTicks() >= KernacianDragoon.KICK_WINDUP_TICKS);
        context.waitTicks(25);
        var landed = context.computeOnClient(minecraft -> minecraft.player.position());
        double thrown = landed.subtract(start).horizontalDistance();
        expect(thrown > 6, "The kick should throw the player far, moved " + thrown);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() < player.getMaxHealth(), "The kick should hurt");
        });
        clearScene(server);
    }

    // ---------------------------------------------------------------------------------------------
    // Climbing
    // ---------------------------------------------------------------------------------------------

    private static final double CLIMB_X = 700;

    /**
     * Posed on a wall with the game running, as the climbing pose blends in over a few ticks: hand over hand from the side and from below, then the strike over
     * the edge.
     */
    private static void checkClimbPoses(ClientGameTestContext context, TestServerContext server)
    {
        double x = CLIMB_X;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + ((int) x - 2) + " " + FEET + " 0 " + ((int) x + 2) + " " + (FEET + 5) + " 0 voidheart:voidstone_bricks");
        server.runOnServer(minecraft ->
        {
            var dragoon = spawn(minecraft, x + 0.5, 1.5);
            dragoon.setNoAi(true);
            dragoon.setPos(x + 0.5, FEET + 1.5, 1.5);
            dragoon.setYRot(180);
            dragoon.setYBodyRot(180);
            dragoon.setYHeadRot(180);
            dragoon.showClimb(true);
        });
        context.waitTicks(10);
        // Midway through a reach, so that one hand grips as the other lifts
        context.runOnClient(minecraft -> minecraft.level.getEntitiesOfClass(KernacianDragoon.class, new AABB(x - 3, FEET - 2, -3, x + 3, FEET + 8, 5))
                .forEach(dragoon -> dragoon.climbCycle = dragoon.climbCycleO = (float) (Math.PI / 2)));
        aimFrom(context, server, x + 5, FEET + 2, 3.5, x + 0.5, FEET + 2.8, 1.5);
        context.takeScreenshot("dragoon-climb-side");
        aimFrom(context, server, x - 1.5, FEET, 6, x + 0.5, FEET + 3, 1.5);
        context.takeScreenshot("dragoon-climb-below");
        for (var moment : List.of(new Object[] { "climb-swipe-windup", KernacianDragoon.KICK_WINDUP_TICKS - 3 }, new Object[] { "climb-swipe", KernacianDragoon.KICK_WINDUP_TICKS + 1 }))
        {
            server.runOnServer(minecraft -> first(minecraft).showKick((Integer) moment[1]));
            aimFrom(context, server, x + 5, FEET + 2, 3.5, x + 0.5, FEET + 2.8, 1.5);
            context.takeScreenshot("dragoon-" + moment[0] + "-side");
        }
        clearScene(server);
    }

    /**
     * A pile three high keeps a player out of its kick, so it climbs onto the pile's face and strikes them off the top.
     */
    private static void checkClimbPile(ClientGameTestContext context, TestServerContext server)
    {
        double x = CLIMB_X + 60;
        int column = (int) x;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + column + " " + FEET + " 0 " + column + " " + (FEET + 2) + " 0 voidheart:voidstone_bricks");
        stand(server, x + 0.5, FEET + 3, 0.5, 0, 45);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            calm(spawn(minecraft, x + 0.5, 5.5));
        });

        waitFor(context, server, 200, "The dragoon should climb the pile", minecraft -> first(minecraft).isClimbing());
        context.waitTicks(4);
        context.takeScreenshot("dragoon-climbing-pile");
        waitFor(context, server, 100, "The dragoon should strike from the pile's face", minecraft -> first(minecraft).isClimbing() && first(minecraft).kickTicks() > 0);
        waitFor(context, server, 60, "The strike should throw the player off the pile", minecraft -> player(minecraft).getY() < FEET + 2);
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() < player(minecraft).getMaxHealth(), "The strike should hurt"));
        clearScene(server);
    }

    /**
     * A pillar five high whose top juts out toward it: it edges out from under the lip, climbs its outer face and strikes over it. With the player thrown down
     * below it, it lets go and drops unhurt.
     */
    private static void checkClimbLip(ClientGameTestContext context, TestServerContext server)
    {
        double x = CLIMB_X + 120;
        int column = (int) x;
        arrive(context, server, x);
        clearArea(server, x);
        server.runCommand("fill " + column + " " + FEET + " 0 " + column + " " + (FEET + 4) + " 0 voidheart:voidstone_bricks");
        server.runCommand("setblock " + column + " " + (FEET + 4) + " 1 voidheart:voidstone_bricks");
        stand(server, x + 0.5, FEET + 5, 0.5, 0, 50);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            calm(spawn(minecraft, x + 0.5, 6.5));
        });

        waitFor(context, server, 200, "The dragoon should climb the pillar", minecraft -> first(minecraft).isClimbing());
        waitFor(context, server, 160, "The dragoon should get out from under the lip and strike over it", minecraft -> first(minecraft).isClimbing() && first(minecraft).kickTicks() > 0);
        double height = server.computeOnServer(minecraft -> first(minecraft).getY());
        expect(height > FEET + 3, "The dragoon should strike from above the lip's underside, at " + (height - FEET));
        context.takeScreenshot("dragoon-climb-over-lip");
        waitFor(context, server, 60, "The strike should throw the player off the pillar", minecraft -> player(minecraft).getY() < FEET + 4);

        waitFor(context, server, 100, "With the player below it, the dragoon should let go and land", minecraft -> !first(minecraft).isClimbing() && first(minecraft).onGround());
        server.runOnServer(minecraft -> expect(first(minecraft).getHealth() == first(minecraft).getMaxHealth(), "The drop must not hurt it"));
        clearScene(server);
    }

    /**
     * A player well back from the edge of a wall is out of reach from its face, so it hauls itself over the top and walks on to kick them.
     */
    private static void checkClimbLedge(ClientGameTestContext context, TestServerContext server)
    {
        double x = CLIMB_X + 180;
        int column = (int) x;
        arrive(context, server, x);
        clearArea(server, x);
        // Deep enough that the face it comes up is the nearest way to the player
        server.runCommand("fill " + (column - 3) + " " + FEET + " -8 " + (column + 3) + " " + (FEET + 3) + " 0 voidheart:voidstone_bricks");
        stand(server, x + 0.5, FEET + 4, -2.5, 0, 25);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            calm(spawn(minecraft, x + 0.5, 6.5));
        });

        waitFor(context, server, 200, "The dragoon should climb the wall", minecraft -> first(minecraft).isClimbing());
        waitFor(context, server, 100, "The dragoon's head should come over the edge", minecraft -> first(minecraft).getY() > FEET + 2);
        context.takeScreenshot("dragoon-climb-over-ledge");
        waitFor(context, server, 100, "The dragoon should haul itself onto the top", minecraft ->
        {
            var dragoon = first(minecraft);
            return !dragoon.isClimbing() && dragoon.onGround() && dragoon.getY() > FEET + 3.9;
        });
        waitFor(context, server, 120, "The dragoon should kick the player on the top", minecraft -> !first(minecraft).isClimbing() && first(minecraft).kickTicks() >= KernacianDragoon.KICK_WINDUP_TICKS);
        clearScene(server);
    }

    /**
     * Keeps its beam shut, so that it has to come to the player.
     */
    private static void calm(KernacianDragoon dragoon)
    {
        setIntField(dragoon, KernacianDragoon.class, "beamCooldown", 100000);
    }

    private static void stand(TestServerContext server, double x, double y, double z, float yaw, float pitch)
    {
        face(server, x, y, z, yaw, pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Drops and saved state
    // ---------------------------------------------------------------------------------------------

    private static void checkDrops(ClientGameTestContext context, TestServerContext server)
    {
        double x = BEAM_X + 120;
        arrive(context, server, x);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var area = new AABB(x - 10, FEET + 20, 20, x + 10, FEET + 40, 40);
            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            for (int roll = 0; roll < 30; roll++)
            {
                var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
                dragoon.snapTo(x + 0.5, FEET + 30, 30.5, 0, 0);
                dragoon.setNoAi(true);
                level.addFreshEntity(dragoon);
                dragoon.kill(level);
                int shards = 0;
                int cores = 0;
                for (var drop : level.getEntitiesOfClass(ItemEntity.class, area))
                {
                    if (drop.getItem().is(VoidHeartItems.ENDER_SHARD))
                        shards += drop.getItem().getCount();
                    if (drop.getItem().is(VoidHeartItems.KERNACIAN_CORE))
                        cores += drop.getItem().getCount();
                    drop.discard();
                }
                expect(cores == 1, "Each dragoon should drop exactly one core, dropped " + cores);
                lowest = Math.min(lowest, shards);
                highest = Math.max(highest, shards);
            }
            expect(lowest >= 16 && highest <= 24 && highest > lowest, "Dragoons should drop 16-24 ender shards, dropped " + lowest + "-" + highest);
        });
        clearScene(server);
    }

    private static void checkSavedState(ClientGameTestContext context)
    {
        TestWorldSave save;
        int packed = maxed(Kind.BLADE) | hits(Kind.FIRE, 5);
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            server.runOnServer(minecraft ->
            {
                var dragoon = spawn(minecraft, 6.5, 6.5);
                dragoon.setNoAi(true);
                dragoon.setAdaptation(packed);
            });
            context.waitTicks(10);
        }
        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            server.runOnServer(minecraft -> expect(first(minecraft).adaptation() == packed, "The dragoon should keep what it adapted to"));
            server.runCommand("gamerule spawn_mobs true");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static void armor(MinecraftServer minecraft, ResourceKey<Enchantment> enchantment)
    {
        var player = player(minecraft);
        var enchantments = minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var pieces = List.<Object[]>of(
                new Object[] { EquipmentSlot.HEAD, Items.NETHERITE_HELMET },
                new Object[] { EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE },
                new Object[] { EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS },
                new Object[] { EquipmentSlot.FEET, Items.NETHERITE_BOOTS });
        for (var piece : pieces)
        {
            var stack = new ItemStack((Item) piece[1]);
            stack.enchant(enchantments.getOrThrow(enchantment), 4);
            player.setItemSlot((EquipmentSlot) piece[0], stack);
        }
    }

    private static KernacianDragoon spawn(MinecraftServer minecraft, double x, double z)
    {
        var level = minecraft.overworld();
        var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
        dragoon.snapTo(x, FEET, z, 90, 0);
        level.addFreshEntity(dragoon);
        return dragoon;
    }

    private static KernacianDragoon first(MinecraftServer minecraft)
    {
        var found = minecraft.overworld().getEntities(VoidHeartEntities.KERNACIAN_DRAGOON, Entity::isAlive);
        if (found.isEmpty())
            throw new AssertionError("Expected a living dragoon");
        return found.getFirst();
    }

    private static void arrive(ClientGameTestContext context, TestServerContext server, double x)
    {
        server.runCommand("tp @a " + (x + 0.5) + " " + FEET + " 0.5");
        context.waitTicks(20);
    }

    private static void face(TestServerContext server, double x, double y, double z, float yaw, float pitch)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, String message, Predicate<MinecraftServer> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (server.computeOnServer(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError(message + " within " + maxTicks + " ticks (" + server.computeOnServer(KernacianDragoonClientTest::describe) + ")");
    }

    private static String describe(MinecraftServer minecraft)
    {
        var found = minecraft.overworld().getEntities(VoidHeartEntities.KERNACIAN_DRAGOON, Entity::isAlive);
        if (found.isEmpty())
            return "no dragoon";
        var dragoon = found.getFirst();
        var path = dragoon.getNavigation().getPath();
        var target = dragoon.getTarget();
        return String.format("dragoon at %.2f %.2f %.2f, target %s, navigation done %s, path %s, climbing %s, on ground %s, against a wall %s",
                dragoon.getX(), dragoon.getY() - FEET, dragoon.getZ(), target == null ? "none" : target.position(), dragoon.getNavigation().isDone(),
                path == null ? "none" : (path.canReach() ? "reaching " : "short of target at ") + path.getEndNode(), dragoon.isClimbing(), dragoon.onGround(), dragoon.horizontalCollision);
    }
}
