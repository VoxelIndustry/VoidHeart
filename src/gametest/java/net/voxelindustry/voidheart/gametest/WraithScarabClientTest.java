package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.ScarabHops;
import net.voxelindustry.voidheart.common.content.mob.WraithScarab;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.EnumSet;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Wraith Scarab in a glass-roofed corridor: it never walks, only hops, about half a second apart, onto the floor, walls and ceiling by turns, and clients
 * see it jump from spot to spot with an afterimage left behind rather than glide. It bites what it lands beside, a line of salt stops it, and salt under it
 * holds it in place.
 */
public class WraithScarabClientTest implements FabricClientGameTest
{
    private static final int X0 = 300;
    private static final int FLOOR = FLAT_GROUND;
    private static final int FEET = FLAT_FEET;
    private static final int CEILING = FEET + 3;
    private static final int LENGTH = 12;
    private static final int WATCH_TICKS = 160;
    private static final double STILL = 0.05;
    private static final int SALT_X = X0 + 6;
    private static final int DROP_ROLLS = 40;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            tp(server, X0 + 6.5, FEET, 8.5);
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            buildRoom(server);
            hops(context, server);
            bitesPlayer(context, server);
            stopsAtSalt(context, server);
            heldBySalt(context, server);
            drops(context, server);

            clearScene(server);
            server.runCommand("fill " + (X0 - 8) + " " + FEET + " -8 " + (X0 + LENGTH + 2) + " " + (CEILING + 1) + " 8 air");
            server.runCommand("gamemode creative @a");
        }
    }

    /**
     * Hollow voidstone, with a glass wall toward the camera and a glass ceiling to let the light in. Inside it is three blocks high and three wide.
     */
    private static void buildRoom(TestServerContext server)
    {
        server.runCommand("fill " + X0 + " " + FLOOR + " -2 " + (X0 + LENGTH) + " " + CEILING + " 2 voidheart:voidstone_tile hollow");
        server.runCommand("fill " + (X0 + 1) + " " + FEET + " 2 " + (X0 + LENGTH - 1) + " " + (CEILING - 1) + " 2 glass");
        server.runCommand("fill " + (X0 + 1) + " " + CEILING + " -1 " + (X0 + LENGTH - 1) + " " + CEILING + " 1 glass");
    }

    private static WraithScarab spawnScarab(MinecraftServer minecraft, double x)
    {
        var level = minecraft.overworld();
        var scarab = VoidHeartEntities.WRAITH_SCARAB.create(level, EntitySpawnReason.COMMAND);
        scarab.snapTo(x, FEET, 0.5, 90, 0);
        scarab.settleOn(BlockPos.containing(x, FEET, 0.5), Direction.UP);
        level.addFreshEntity(scarab);
        return scarab;
    }

    /**
     * A target that stands still and lasts: the scarab hunts players, but takes whatever it is set on.
     */
    private static Husk spawnTarget(MinecraftServer minecraft, double x)
    {
        var level = minecraft.overworld();
        var husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.snapTo(x, FEET, 0.5, -90, 0);
        husk.setNoAi(true);
        husk.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100_000, 2));
        level.addFreshEntity(husk);
        return husk;
    }

    private record Sample(Vec3 server, Direction surface, Vec3 client, int afterimages)
    {
    }

    private static void hops(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, X0 + 6.5, FEET - 0.3, 7.5, X0 + 6.5, FEET + 1, 0.5);
        int[] ids = new int[1];
        server.runOnServer(minecraft ->
        {
            var scarab = spawnScarab(minecraft, X0 + 1.5);
            scarab.setTarget(spawnTarget(minecraft, X0 + 10.5));
            ids[0] = scarab.getId();
        });
        context.waitTicks(2);
        context.getInput().pressKey(InputConstants.KEY_F1);

        var samples = new ArrayList<Sample>();
        int shots = 0;
        int lastShot = -100;
        for (int tick = 0; tick < WATCH_TICKS; tick++)
        {
            context.waitTick();
            var onServer = server.computeOnServer(minecraft ->
            {
                var scarab = first(minecraft, VoidHeartEntities.WRAITH_SCARAB);
                return new Object[] { scarab.position(), scarab.surface() };
            });
            var onClient = context.computeOnClient(minecraft ->
            {
                var entity = (WraithScarab) minecraft.level.getEntity(ids[0]);
                return entity == null ? null : new Object[] { entity.position(), entity.afterimages.size() };
            });
            expect(onClient != null, "The client should track the scarab");
            var sample = new Sample((Vec3) onServer[0], (Direction) onServer[1], (Vec3) onClient[0], (Integer) onClient[1]);
            samples.add(sample);

            if (shots < 4 && sample.afterimages() > 0 && tick - lastShot > 15)
            {
                // Up to the glass and onto the scarab, with the spot it left in frame
                server.runCommand("tick freeze");
                var at = sample.server();
                aimFrom(context, server, at.x - 0.8, FEET - 0.6, 4.5, at.x - 0.8, at.y + 0.2, 0.5, 2);
                context.takeScreenshot("wraith-scarab-hop-" + shots);
                aimFrom(context, server, X0 + 6.5, FEET - 0.3, 7.5, X0 + 6.5, FEET + 1, 0.5, 1);
                server.runCommand("tick unfreeze");
                shots++;
                lastShot = tick;
            }
        }
        context.getInput().pressKey(InputConstants.KEY_F1);

        var hopTicks = new ArrayList<Integer>();
        var kinds = EnumSet.noneOf(ScarabHops.Kind.class);
        int clientGlides = 0;
        int afterimageTicks = 0;
        for (int i = 1; i < samples.size(); i++)
        {
            var before = samples.get(i - 1);
            var now = samples.get(i);
            double moved = now.server().distanceTo(before.server());
            expect(moved < STILL || moved >= ScarabHops.MIN_HOP * 0.7, "It should never walk, but moved " + moved + " in one tick at sample " + i);
            if (moved >= STILL)
                hopTicks.add(i);
            kinds.add(ScarabHops.Kind.of(now.surface()));

            double clientMoved = now.client().distanceTo(before.client());
            if (clientMoved >= STILL && clientMoved < ScarabHops.MIN_HOP * 0.7)
                clientGlides++;
            if (now.afterimages() > 0)
                afterimageTicks++;
        }
        var gaps = new ArrayList<Integer>();
        for (int i = 1; i < hopTicks.size(); i++)
            gaps.add(hopTicks.get(i) - hopTicks.get(i - 1));
        System.out.println("[Scarab] hops at " + hopTicks + ", gaps " + gaps + ", faces " + kinds + ", client glides " + clientGlides + ", ticks with afterimages "
                + afterimageTicks);

        expect(hopTicks.size() >= 6, "It should hop at least 6 times in " + WATCH_TICKS + " ticks, hopped " + hopTicks.size());
        expect(gaps.stream().allMatch(gap -> gap >= 5), "Hops should be spaced, gaps " + gaps);
        expect(kinds.contains(ScarabHops.Kind.FLOOR) && kinds.size() >= 2, "It should hop onto walls or the ceiling as well as the floor, held " + kinds);
        expect(clientGlides == 0, "The client should snap it from spot to spot, but it glided on " + clientGlides + " ticks");
        expect(afterimageTicks > 0, "The client should keep afterimages of where it hopped from");

        server.runOnServer(minecraft ->
        {
            var husk = first(minecraft, EntityTypes.HUSK);
            var scarab = first(minecraft, VoidHeartEntities.WRAITH_SCARAB);
            expect(husk.getHealth() < husk.getMaxHealth(), "It should have bitten the target it reached");
            expect(scarab.getBoundingBox().getCenter().distanceTo(husk.getBoundingBox().getCenter()) < 3, "It should have reached its target");
        });
        clearScene(server);
    }

    private static void bitesPlayer(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, X0 + 10.5, FEET, 0.5);
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance 30 2 true");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            spawnScarab(minecraft, X0 + 4.5);
        });
        waitFor(context, server, 200, "The scarab should bite the player", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        clearScene(server);
        server.runOnServer(GameTestKit::heal);
        server.runCommand("effect clear @a");
        server.runCommand("gamemode creative @a");
    }

    private static void stopsAtSalt(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + SALT_X + " " + FEET + " -6 " + SALT_X + " " + FEET + " 6 voidheart:salt_ward keep");
        aimFrom(context, server, X0 + 6.5, FEET - 0.3, 7.5, X0 + 6.5, FEET + 1, 0.5);
        server.runOnServer(minecraft -> spawnScarab(minecraft, X0 + 1.5).setTarget(spawnTarget(minecraft, X0 + 10.5)));

        var positions = new ArrayList<Vec3>();
        for (int tick = 0; tick < WATCH_TICKS; tick++)
        {
            context.waitTick();
            var at = server.computeOnServer(minecraft ->
            {
                var scarab = first(minecraft, VoidHeartEntities.WRAITH_SCARAB);
                expect(!SaltWard.isWardedBelow(minecraft.overworld(), BlockPos.containing(scarab.getBoundingBox().getCenter())),
                        "The scarab should never land in a ward, but stands at " + scarab.position());
                return scarab.position();
            });
            expect(at.x < SALT_X - 1, "The scarab should not get past the salt line, but reached " + at);
            if (positions.isEmpty() || !positions.getLast().equals(at))
                positions.add(at);
        }
        System.out.println("[Scarab] by the salt line it held " + positions);
        expect(positions.getLast().x > X0 + 3, "It should hop up to the salt, but ended at " + positions.getLast());
        expect(positions.size() >= 4, "Held back by the salt it should keep skittering along it, but held " + positions.size() + " spots");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("wraith-scarab-salt-line");
        context.getInput().pressKey(InputConstants.KEY_F1);
        clearScene(server);
        server.runCommand("fill " + SALT_X + " " + FEET + " -6 " + SALT_X + " " + FEET + " 6 air replace voidheart:salt_ward");
    }

    private static void heldBySalt(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + (X0 + 3) + " " + FEET + " 0 voidheart:salt_ward");
        Vec3[] start = new Vec3[1];
        server.runOnServer(minecraft ->
        {
            var scarab = spawnScarab(minecraft, X0 + 3.5);
            scarab.setTarget(spawnTarget(minecraft, X0 + 9.5));
            start[0] = scarab.position();
        });
        context.waitTicks(60);
        server.runOnServer(minecraft -> expect(first(minecraft, VoidHeartEntities.WRAITH_SCARAB).position().equals(start[0]),
                "A scarab on salt should not hop away from it"));
        clearScene(server);
        server.runCommand("setblock " + (X0 + 3) + " " + FEET + " 0 air");
    }

    private static void drops(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var area = new AABB(X0 - 6, FEET - 1, -6, X0 - 2, FEET + 4, 6);
            int fewestShards = Integer.MAX_VALUE;
            int mostShards = 0;
            int inks = 0;
            for (int roll = 0; roll < DROP_ROLLS; roll++)
            {
                var scarab = spawn(minecraft, VoidHeartEntities.WRAITH_SCARAB, X0 - 3.5, FEET, 0.5);
                scarab.setNoAi(true);
                scarab.kill(level);
                int shards = 0;
                for (var drop : level.getEntitiesOfClass(ItemEntity.class, area))
                {
                    if (drop.getItem().is(VoidHeartItems.ENDER_SHARD))
                        shards += drop.getItem().getCount();
                    if (drop.getItem().is(VoidHeartItems.WRAITH_INK))
                        inks += drop.getItem().getCount();
                    drop.discard();
                }
                fewestShards = Math.min(fewestShards, shards);
                mostShards = Math.max(mostShards, shards);
            }
            System.out.println("[Scarab] drops over " + DROP_ROLLS + ": shards " + fewestShards + "-" + mostShards + ", inks " + inks);
            expect(fewestShards == 0 && mostShards == 1, "It should drop 0-1 ender shards, dropped " + fewestShards + "-" + mostShards);
            expect(inks > 0 && inks < DROP_ROLLS / 2, "It should sometimes drop wraith ink, dropped " + inks + " in " + DROP_ROLLS);
        });
        clearScene(server);
    }
}
