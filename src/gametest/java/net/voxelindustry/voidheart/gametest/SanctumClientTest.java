package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.temple.MosaicDoorHint;
import net.voxelindustry.voidheart.common.content.sanctum.BileGlob;
import net.voxelindustry.voidheart.common.content.sanctum.BilePool;
import net.voxelindustry.voidheart.common.content.sanctum.BlessedCradleTile;
import net.voxelindustry.voidheart.common.content.sanctum.FleshTentacle;
import net.voxelindustry.voidheart.common.content.sanctum.LesserShoggoth;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance;
import net.voxelindustry.voidheart.common.content.sanctum.Sanctums;
import net.voxelindustry.voidheart.common.content.sanctum.VeilGate;
import net.voxelindustry.voidheart.common.content.sanctum.VileFleshMass;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumAddress;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumPlacer;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumPlan;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The way through a temple's mosaic and what lies behind it. In a temple: the door's hint while a Wraith Veil is in hand, the veil opening the door
 * by a real click, and walking in. In the sanctum: its porch, nave, columns, flesh and tentacles, the mass and its shoggoths; then the mass's
 * eruptions, sweep, grab, bile and a shoggoth feeding it, a shoggoth splitting, the mass dying and its pillar rising, the cradle taken by hand, and
 * the gate back out to the temple's dais.
 */
public class SanctumClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int MOVE_TIMEOUT_TICKS = 200;
    private static final int ACTION_TIMEOUT_TICKS = 400;
    /** A row of the picture inside the door, under the cracked world. */
    private static final int DOOR_ROW = 6;
    private static final int WINDOW_SWEEP_STEPS = 6;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            var cameraBefore = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            try
            {
                server.runCommand("difficulty normal");
                server.runCommand("gamemode spectator @a");
                var fort = visit(context, world);
                expect(fort != null, "No dry temple was found near the start");
                server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
                var door = temple(context, world, fort);
                sanctum(context, world, door);
                server.runCommand("effect clear @a");
            } finally
            {
                server.runCommand("gamemode creative @a");
                context.runOnClient(minecraft -> minecraft.options.setCameraType(cameraBefore));
                if (!hudWasHidden)
                    context.getInput().pressKey(InputConstants.KEY_F1);
            }
        }
    }

    private static MosaicDoor temple(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort)
    {
        var server = world.getServer();
        var temple = fort.temple();
        var tile = blockAt(fort, UnderwayTemple.AISLE, temple.rowY(DOOR_ROW), UnderwayTemple.DEPTH);
        var front = blockAt(fort, UnderwayTemple.AISLE, temple.rowY(DOOR_ROW), UnderwayTemple.DEPTH - 1);
        var facing = Direction.getApproximateNearest(front.getX() - tile.getX(), 0, front.getZ() - tile.getZ());
        var door = server.computeOnServer(minecraft -> MosaicDoor.at(VoidDaedalus.level(minecraft), tile, facing));
        expect(door != null, "No door found in the mosaic at " + tile + " facing " + facing);

        // Without a veil in hand nothing shows; with one the door's outline breathes on the mosaic
        server.runCommand("gamemode creative @a");
        server.runCommand("clear @a");
        standIn(context, world, fort, UnderwayTemple.AISLE, 4, door.center());
        context.waitTicks(15);
        shot(context, "temple-no-veil");
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:wraith_veil");
        context.waitTicks(15);
        expectClient(context, "The hint found no door while a veil was in hand", minecraft -> MosaicDoorHint.found() > 0);
        shot(context, "temple-hint");
        standIn(context, world, fort, UnderwayTemple.AISLE - 3, 6, door.center());
        shot(context, "temple-hint-aside");

        // Pressed to the door by a real click from the dais, it opens
        standIn(context, world, fort, UnderwayTemple.AISLE, UnderwayTemple.DEPTH - 1, Vec3.atCenterOf(tile));
        rightClick(context);
        waitFor(context, server, 40, "The veil opened no door in the mosaic", minecraft -> !gates(minecraft, door).isEmpty());
        context.waitTicks(8);
        standIn(context, world, fort, UnderwayTemple.AISLE, 5, door.center());
        shot(context, "temple-door-opening");
        context.waitTicks(20);
        shot(context, "temple-door-open");
        // The hall beyond, held still and seen from either side and close, then swept a little for flicker and to show it moves as a room would
        server.runCommand("tick freeze");
        server.runCommand("gamemode spectator @a");
        try
        {
            standIn(context, world, fort, UnderwayTemple.AISLE - 4, 6, door.center());
            shot(context, "temple-window-left");
            standIn(context, world, fort, UnderwayTemple.AISLE + 4, 6, door.center());
            shot(context, "temple-window-right");
            standIn(context, world, fort, UnderwayTemple.AISLE, UnderwayTemple.DEPTH - 1, door.center());
            shot(context, "temple-window-close");
            var from = blockAt(fort, UnderwayTemple.AISLE - 2, fort.temple().room().floorY() + 2, UnderwayTemple.DEPTH - 2);
            var across = blockAt(fort, UnderwayTemple.AISLE + 2, fort.temple().room().floorY() + 2, UnderwayTemple.DEPTH - 2).subtract(from);
            for (int step = 0; step < WINDOW_SWEEP_STEPS; step++)
            {
                double t = step / (double) (WINDOW_SWEEP_STEPS - 1);
                var center = door.center();
                server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(from.getX() + 0.5 + across.getX() * t, from.getY() + 0.3,
                        from.getZ() + 0.5 + across.getZ() * t, center.x, center.y, center.z));
                context.waitTicks(3);
                shot(context, "temple-window-sweep-" + step);
            }
        } finally
        {
            server.runCommand("gamemode creative @a");
            server.runCommand("tick unfreeze");
        }

        // Walking into it leads to the sanctum
        // A step short of the threshold, body against the wall where the door is
        var mouth = door.threshold().subtract(door.facing().getUnitVec3().scale(0.18));
        server.runCommand("execute in voidheart:daedalus run tp @a " + mouth.x + " " + mouth.y + " " + mouth.z);
        waitFor(context, server, MOVE_TIMEOUT_TICKS, () -> "Walking into the open door did not lead to the sanctum: the player is at "
                + server.computeOnServer(minecraft -> player(minecraft).level().dimension() + " " + player(minecraft).position()),
                minecraft -> player(minecraft).level().dimension() == VoidDungeons.WORLD_KEY);
        return door;
    }

    private static void sanctum(ClientGameTestContext context, TestSingleplayerContext world, MosaicDoor door)
    {
        var server = world.getServer();
        int index = server.computeOnServer(minecraft -> Sanctums.indexAt(player(minecraft).blockPosition()));
        expect(index >= 0, "The player did not arrive in a sanctum's cell");
        var origin = SanctumAddress.origin(index);
        var arrival = server.computeOnServer(minecraft -> player(minecraft).position());
        expect(arrival.distanceTo(Vec3.atBottomCenterOf(origin.offset(SanctumPlan.ARRIVAL_X, SanctumPlan.FLOOR_Y, SanctumPlan.ARRIVAL_Z))) < 1,
                "The player arrived at " + arrival + ", not in the porch");
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == VoidDungeons.WORLD_KEY, MOVE_TIMEOUT_TICKS);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        var box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(SanctumPlan.WIDTH, SanctumPlan.HEIGHT, SanctumPlan.LENGTH)));
        waitFor(context, server, ACTION_TIMEOUT_TICKS, () -> "The sanctum's dwellers never showed: " + server.computeOnServer(minecraft -> count(minecraft, LesserShoggoth.class, box))
                + " shoggoths", minecraft -> count(minecraft, VileFleshMass.class, box) == 1 && count(minecraft, LesserShoggoth.class, box) == 8);
        expect(server.computeOnServer(minecraft -> count(minecraft, VeilGate.class, box) == 1), "The sanctum has no gate back");
        expect(server.computeOnServer(minecraft -> dungeons(minecraft).getBlockState(origin.offset(SanctumPlan.GATE_X0 - 1, SanctumPlan.FLOOR_Y, SanctumPlan.GATE_Z))
                .is(VoidHeartBlocks.ELDRITCH_VOIDSTONE)), "The gate back has no frame");

        // The scene, watched from where nothing notices
        server.runCommand("gamemode spectator @a");
        look(context, server, origin, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 1.2, 4, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 3, 40);
        shot(context, "porch");
        look(context, server, origin, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 1.2, 6, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 1.5, 1.5);
        shot(context, "gate-back");
        look(context, server, origin, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 2, 14, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 3, 52);
        shot(context, "nave");
        look(context, server, origin, 6.5, SanctumPlan.FLOOR_Y + 3, 31, 10.5, SanctumPlan.FLOOR_Y + 6, 35.5);
        shot(context, "column");
        look(context, server, origin, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 2, 36, SanctumPlan.AXIS + 0.5, SanctumPlan.FLOOR_Y + 17, 44);
        shot(context, "ceiling");
        look(context, server, origin, SanctumPlan.AXIS + 0.5, SanctumPlan.MASS_Y + 2, SanctumPlan.MASS_Z - 8, SanctumPlan.AXIS + 0.5, SanctumPlan.MASS_Y + 1, SanctumPlan.MASS_Z);
        shot(context, "mass");
        look(context, server, origin, SanctumPlan.AXIS + 5.5, SanctumPlan.MASS_Y + 2.5, SanctumPlan.MASS_Z - 5, SanctumPlan.AXIS + 0.5, SanctumPlan.MASS_Y + 1, SanctumPlan.MASS_Z);
        shot(context, "mass-aside");
        var shoggoth = server.computeOnServer(minecraft -> first(minecraft, LesserShoggoth.class, box).position());
        look(context, server, shoggoth.add(0, 1.4, -3.5), shoggoth.add(0, 0.6, 0));
        shot(context, "shoggoth");
        // Each kind of growth on the floor, close up and from a little above
        var plan = server.computeOnServer(minecraft -> SanctumPlacer.plan(dungeons(minecraft).getSeed(), index));
        for (var kind : List.of(UnderwayProp.FLESH_MOUND, UnderwayProp.FLESH_MOUND_LARGE, UnderwayProp.FLESH_EYES))
        {
            var prop = plan.props().stream().filter(candidate -> candidate.prop() == kind).findFirst();
            if (prop.isEmpty())
                continue;
            var at = Vec3.atBottomCenterOf(origin.offset(prop.get().x(), prop.get().y(), prop.get().z()));
            look(context, server, at.add(1.5, 1.4, -1.5), at.add(0, 0.15, 0));
            shot(context, "growth-" + kind.name().toLowerCase(java.util.Locale.ROOT));
        }

        fight(context, world, origin, box);
        leave(context, world, origin, door);
    }

    private static void fight(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin, AABB box)
    {
        var server = world.getServer();
        var altar = origin.offset(SanctumPlan.MASS_X, SanctumPlan.MASS_Y, SanctumPlan.MASS_Z);
        // Shoggoths out of the way, so the mass's own moves can be told apart; one kept to split, one to feed it
        server.runOnServer(minecraft ->
        {
            var shoggoths = all(minecraft, LesserShoggoth.class, box);
            for (int i = 2; i < shoggoths.size(); i++)
                shoggoths.get(i).discard();
            for (var shoggoth : shoggoths.subList(0, 2))
                shoggoth.setNoAi(true);
        });

        // A shoggoth cut open falls in two smaller ones
        server.runOnServer(minecraft -> all(minecraft, LesserShoggoth.class, box).getFirst().kill(dungeons(minecraft)));
        waitFor(context, server, 40, "A slain shoggoth did not fall in two", minecraft -> all(minecraft, LesserShoggoth.class, box).stream().filter(LesserShoggoth::isSmall).count() == 2);
        server.runOnServer(minecraft -> all(minecraft, LesserShoggoth.class, box).stream().filter(LesserShoggoth::isSmall).forEach(Entity::discard));

        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 3 true");
        server.runCommand("effect give @a minecraft:regeneration infinite 4 true");
        server.runCommand("effect give @a minecraft:night_vision infinite 0 true");

        // Near it: it wakes, raises tentacles from the floor under the player and sweeps
        stand(context, server, altar.getX() + 0.5, altar.getY(), altar.getZ() - 5, altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "The mass did not wake with a player close by", minecraft -> mass(minecraft, box).isAwake());
        expect(server.computeOnServer(minecraft -> mass(minecraft, box).isBossBarShownTo(player(minecraft))), "The mass's boss bar did not show");
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "No tentacle rose from the floor", minecraft -> !tentacles(minecraft, box, false).isEmpty());
        // Watched from aside while it swells and bursts, frozen at each moment so the camera can be moved to it
        var eruption = server.computeOnServer(minecraft -> tentacles(minecraft, box, false).getFirst().position());
        server.runCommand("tick freeze");
        server.runCommand("gamemode spectator @a");
        look(context, server, eruption.add(3.2, 2.2, -2.4), eruption.add(0, 0.6, 0));
        shot(context, "eruption-warning");
        server.runCommand("gamemode survival @a");
        server.runCommand("tick unfreeze");
        waitFor(context, server, FleshTentacle.ERUPT_TICKS, "The tentacle never burst up",
                minecraft -> tentacles(minecraft, box, false).stream().anyMatch(tentacle -> tentacle.tickCount > SanctumBalance.ERUPT_WARN_TICKS + FleshTentacle.RISE_TICKS + 2));
        var risen = server.computeOnServer(minecraft -> tentacles(minecraft, box, false).stream()
                .filter(tentacle -> tentacle.tickCount > SanctumBalance.ERUPT_WARN_TICKS + FleshTentacle.RISE_TICKS).findFirst().orElseThrow().position());
        server.runCommand("tick freeze");
        server.runCommand("gamemode spectator @a");
        look(context, server, risen.add(3.6, 2.4, -3), risen.add(0, 1.6, 0));
        shot(context, "eruption");
        server.runCommand("gamemode survival @a");
        server.runCommand("tick unfreeze");
        stand(context, server, altar.getX() + 0.5, altar.getY(), altar.getZ() - 4.5, altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "The mass never swept round itself",
                minecraft -> player(minecraft).getLastHurtByMob() instanceof VileFleshMass && player(minecraft).getLastDamageSource() != null
                        && player(minecraft).getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK));

        // Further off: it grabs and drags to its gash, which bites
        server.runOnServer(minecraft -> setIntField(mass(minecraft, box), VileFleshMass.class, "grabCooldown", 0));
        int nave = origin.getY() + SanctumPlan.FLOOR_Y;
        stand(context, server, altar.getX() + 0.5, nave, altar.getZ() - 11.5, altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
        double far = server.computeOnServer(minecraft -> mass(minecraft, box).edgeDistance(player(minecraft)));
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "The mass never grabbed the player", minecraft -> !tentacles(minecraft, box, true).isEmpty());
        context.waitTicks(14);
        shot(context, "grab");
        double dragged = server.computeOnServer(minecraft -> mass(minecraft, box).edgeDistance(player(minecraft)));
        expect(dragged < far - 0.5, "The grab did not drag the player in: " + far + " then " + dragged);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "The grab ended without a bite",
                minecraft -> player(minecraft).getLastDamageSource() != null && player(minecraft).getLastDamageSource().is(VileFleshMass.BITE));

        // Far off, past its reach: it spits bile, which pools where it lands
        server.runOnServer(minecraft -> setIntField(mass(minecraft, box), VileFleshMass.class, "grabCooldown", 10_000));
        stand(context, server, altar.getX() + 0.5, nave, altar.getZ() - 20.5, altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
        waitFor(context, server, ACTION_TIMEOUT_TICKS, "The mass spat no bile", minecraft -> count(minecraft, BileGlob.class, box) > 0 || count(minecraft, BilePool.class, box) > 0);
        waitFor(context, server, 80, "The bile left no pool", minecraft -> count(minecraft, BilePool.class, box) > 0);
        var pool = server.computeOnServer(minecraft -> first(minecraft, BilePool.class, box).position());
        server.runCommand("gamemode spectator @a");
        look(context, server, pool.add(1.5, 2.2, -2.5), pool);
        shot(context, "bile-pool");

        // A shoggoth badly hurt crawls in to be eaten, and the mass heals
        server.runOnServer(minecraft ->
        {
            var mass = mass(minecraft, box);
            mass.setHealth(mass.getMaxHealth() / 2);
            var shoggoth = all(minecraft, LesserShoggoth.class, box).getFirst();
            shoggoth.setNoAi(false);
            shoggoth.snapTo(altar.getX() + 0.5, altar.getY(), altar.getZ() - 6.5, 0, 0);
            shoggoth.setHealth(4);
        });
        float halved = server.computeOnServer(minecraft -> mass(minecraft, box).getHealth());
        look(context, server, Vec3.atBottomCenterOf(altar).add(6, 2.5, -7), Vec3.atBottomCenterOf(altar).add(0, 0.5, -4));
        waitFor(context, server, ACTION_TIMEOUT_TICKS, () -> "The hurt shoggoth did not feed the mass: " + server.computeOnServer(minecraft ->
                all(minecraft, LesserShoggoth.class, box).stream().map(shoggoth -> shoggoth.position() + " health " + shoggoth.getHealth() + " feeding "
                        + shoggoth.isFeeding() + " " + mass(minecraft, box).distanceTo(shoggoth) + " from the mass").toList()),
                minecraft -> count(minecraft, LesserShoggoth.class, box) == 0);
        float fed = server.computeOnServer(minecraft -> mass(minecraft, box).getHealth());
        expect(fed > halved + 20, "Feeding did not heal the mass: " + halved + " then " + fed);

        // Dead, it sinks while the pillar rises where it sat
        server.runOnServer(minecraft -> mass(minecraft, box).kill(dungeons(minecraft)));
        look(context, server, Vec3.atBottomCenterOf(altar).add(0, 2, -7), Vec3.atBottomCenterOf(altar).add(0, 1.2, 0));
        context.waitTicks(VileFleshMass.DEATH_TICKS / 2);
        shot(context, "mass-dying");
        waitFor(context, server, VileFleshMass.DEATH_TICKS + 40, "The pillar did not rise",
                minecraft -> dungeons(minecraft).getBlockState(altar.above(2)).is(VoidHeartBlocks.BLESSED_CRADLE));
        int index = server.computeOnServer(minecraft -> Sanctums.indexAt(altar));
        expect(server.computeOnServer(minecraft -> Sanctums.state(minecraft).get(index).slain() && Sanctums.state(minecraft).get(index).raised()),
                "The sanctum does not remember its guardian slain and the pillar risen");
        expect(server.computeOnServer(minecraft -> dungeons(minecraft).getBlockState(altar).is(VoidHeartBlocks.VOIDSTONE_POLISHED)
                && dungeons(minecraft).getBlockState(altar.above()).is(VoidHeartBlocks.VOIDSTONE_PILLAR)), "The pillar's stones are not where the mass sat");

        // No contraption is known yet, so the cradle is laid with a stand-in to check it is taken by hand
        server.runOnServer(minecraft ->
        {
            if (dungeons(minecraft).getBlockEntity(altar.above(2)) instanceof BlessedCradleTile cradle)
                cradle.hold(new ItemStack(Items.NETHER_STAR));
        });
        context.waitTicks(10);
        shot(context, "pillar");
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        stand(context, server, altar.getX() + 0.5, altar.getY(), altar.getZ() - 1.8, altar.getX() + 0.5, altar.getY() + 2.3, altar.getZ() + 0.5);
        context.waitTicks(6);
        rightClick(context);
        waitFor(context, server, 20, "Using the cradle did not hand over what it held",
                minecraft -> player(minecraft).getInventory().countItem(Items.NETHER_STAR) == 1);
    }

    private static void leave(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin, MosaicDoor door)
    {
        var server = world.getServer();
        server.runCommand("gamemode creative @a");
        var mouth = Vec3.atBottomCenterOf(origin.offset(SanctumPlan.ARRIVAL_X, SanctumPlan.FLOOR_Y, SanctumPlan.GATE_Z + 1)).add(0, 0, -0.2);
        context.waitTicks(80);
        server.runCommand("execute in voidheart:dungeons run tp @a " + mouth.x + " " + mouth.y + " " + mouth.z);
        waitFor(context, server, MOVE_TIMEOUT_TICKS, "The gate back did not lead out of the sanctum",
                minecraft -> player(minecraft).level().dimension() == VoidDaedalus.WORLD_KEY);
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == VoidDaedalus.WORLD_KEY, MOVE_TIMEOUT_TICKS);
        var back = server.computeOnServer(minecraft -> player(minecraft).position());
        expect(back.distanceTo(door.threshold()) < 1.5, "Came back out at " + back + ", not on the temple's dais at " + door.threshold());
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        shot(context, "back-in-temple");
    }

    private static UnderwayFort visit(ClientGameTestContext context, TestSingleplayerContext world)
    {
        var server = world.getServer();
        var before = server.computeOnServer(minecraft -> player(minecraft).blockPosition());
        server.runCommand("execute as @a run voidheart underway temple dry");
        for (int tick = 0; tick < MOVE_TIMEOUT_TICKS && server.computeOnServer(minecraft -> player(minecraft).blockPosition()).equals(before); tick++)
            context.waitTick();
        var fort = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level().dimension() != VoidDaedalus.WORLD_KEY || player.blockPosition().equals(before))
                return null;
            return UnderwayPlans.fort(VoidDaedalus.level(minecraft).getSeed(), DistrictPos.at(player.getBlockX(), player.getBlockZ()));
        });
        if (fort != null)
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        return fort;
    }

    /** Stands at a place in the temple, {@code w} across it and {@code d} in from its doorway, on the floor or the dais, looking at a point. */
    private static void standIn(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, int w, int d, Vec3 at)
    {
        int floor = fort.temple().room().floorY();
        var from = blockAt(fort, w, floor + 1 + (d >= UnderwayTemple.DEPTH - 3 ? 1 : 0), d);
        world.getServer().runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, at.x, at.y, at.z));
        context.waitTicks(4);
    }

    private static void stand(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, double x, double y, double z,
                              double lookX, double lookY, double lookZ)
    {
        server.runCommand("execute in voidheart:dungeons run tp @a " + aimedPose(x, y, z, lookX, lookY, lookZ));
        context.waitTicks(4);
    }

    private static void look(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos origin,
                             double x, double y, double z, double lookX, double lookY, double lookZ)
    {
        look(context, server, Vec3.atLowerCornerOf(origin).add(x, y, z), Vec3.atLowerCornerOf(origin).add(lookX, lookY, lookZ));
    }

    private static void look(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, Vec3 from, Vec3 to)
    {
        server.runCommand("execute in voidheart:dungeons run tp @a " + aimedPose(from.x, from.y, from.z, to.x, to.y, to.z));
        context.waitTicks(6);
    }

    private static net.minecraft.server.level.ServerLevel dungeons(MinecraftServer minecraft)
    {
        return VoidDungeons.level(minecraft);
    }

    private static <T extends Entity> List<T> all(MinecraftServer minecraft, Class<T> type, AABB box)
    {
        return dungeons(minecraft).getEntitiesOfClass(type, box, Entity::isAlive);
    }

    private static <T extends Entity> int count(MinecraftServer minecraft, Class<T> type, AABB box)
    {
        return all(minecraft, type, box).size();
    }

    private static <T extends Entity> T first(MinecraftServer minecraft, Class<T> type, AABB box)
    {
        var found = all(minecraft, type, box);
        if (found.isEmpty())
            throw new AssertionError("Expected a living " + type.getSimpleName() + " in the sanctum");
        return found.getFirst();
    }

    private static VileFleshMass mass(MinecraftServer minecraft, AABB box)
    {
        return first(minecraft, VileFleshMass.class, box);
    }

    private static List<FleshTentacle> tentacles(MinecraftServer minecraft, AABB box, boolean grab)
    {
        return dungeons(minecraft).getEntitiesOfClass(FleshTentacle.class, box, tentacle -> tentacle.isGrab() == grab);
    }

    private static List<VeilGate> gates(MinecraftServer minecraft, MosaicDoor door)
    {
        return VoidDaedalus.level(minecraft).getEntitiesOfClass(VeilGate.class, door.mouth(0.2).inflate(0.5), gate -> !gate.stays());
    }

    private static void shot(ClientGameTestContext context, String name)
    {
        context.takeScreenshot(TestScreenshotOptions.of("sanctum-" + name).withSize(1280, 720));
    }

    private static BlockPos blockAt(UnderwayFort fort, int w, int y, int d)
    {
        var temple = fort.temple();
        int along = temple.along(w, d);
        int across = temple.across(w, d);
        return new BlockPos(fort.frame().x(along, across), y, fort.frame().z(along, across));
    }
}
