package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.energy.siphon.SiphonArcRender;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryConnections;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.DaedalusConnections;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonBalance;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonChunkHold;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonContent;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonTile;
import net.voxelindustry.voidheart.common.energy.siphon.SiphonBridge;
import net.voxelindustry.voidheart.common.energy.siphon.SiphonStatus;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridge;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Deep Siphon in a real world.
 * <ul>
 *     <li>An armillary opens and holds a way into the Daedalus. A siphon can't be set down in the overworld, but can on the way's bridge, where it takes up
 *     the way of the player who placed it.</li>
 *     <li>With the armillary's well taken away, the siphon alone pays the hold and powers a machine beside the armillary through a pylon, while the player is
 *     back in the overworld, and arcs run from the rings to the pylon.</li>
 *     <li>What it draws adds to its notice. Banked notice brings nothing while the bridge is empty, then comes as waves of four, twenty seconds apart, once a
 *     player arrives: district mobs climbing out onto the bridge, never the Dragoon.</li>
 *     <li>Severing the way stops it at once: nothing more reaches the machine, the arcs fade and its chunk is let go.</li>
 * </ul>
 * The machine is a stand-in drawing from a server tick hook, as in VoidGridClientTest, since nothing beside an armillary draws steadily on its own.
 */
public class DeepSiphonClientTest implements FabricClientGameTest
{
    private static final int FEET   = FLAT_FEET;
    private static final int BURIED = FLAT_GROUND - 2;

    private static final BlockPos ARMILLARY = new BlockPos(0, FEET, 0);
    private static final BlockPos WELL      = new BlockPos(0, BURIED, 0);
    private static final BlockPos PYLON     = new BlockPos(6, FEET, 0);
    private static final BlockPos MACHINE   = new BlockPos(5, FEET, 4);

    /** What the stand-in machine asks every tick, less than the siphon's 32 VE/t left after the hold. */
    private static final long MACHINE_DRAW = 16;
    /** Along the bridge from the district it starts from, short of the rift at 18 and where players arrive at 15. */
    private static final int  SIPHON_SPAN  = 10;
    private static final int  SETTLE       = 45;

    private static final AtomicLong DRAWN       = new AtomicLong();
    private static volatile boolean machineOn;
    private static boolean          hooked;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("deep_siphon");
        hook();

        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -16 -16 16 16");
            // Jade only shows with the HUD, which an earlier test may have left hidden
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
            context.runOnClient(minecraft ->
            {
                if (hudWasHidden)
                    minecraft.gui.hud.toggle();
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.waitTicks(10);

            int id = openTheWay(context, server);
            checkRefusedInTheOverworld(context, server);
            var siphon = placeOnTheBridge(context, server, id);
            checkPowersTheBase(context, server, siphon);
            checkWaves(context, server, id, siphon);
            checkSevering(context, server, siphon);

            machineOn = false;
            context.runOnClient(minecraft ->
            {
                if (hudWasHidden)
                    minecraft.gui.hud.toggle();
                minecraft.options.setCameraType(camera);
            });
        }
    }

    private static void hook()
    {
        if (hooked)
            return;
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            if (machineOn && level.dimension() == Level.OVERWORLD)
                DRAWN.addAndGet(VoidEnergy.draw(level, MACHINE, MACHINE_DRAW));
        });
    }

    /**
     * The armillary's search, charge and opening, paid by a creative well under it, as in MachineRetrofitClientTest.
     *
     * @return the way's id
     */
    private static int openTheWay(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ARMILLARY, VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState());
            for (var part : ArmillaryLayout.base(ARMILLARY))
                level.setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
        });
        server.runCommand("setblock " + at(WELL) + " voidheart:creative_void_well");
        server.runCommand("setblock " + at(PYLON) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(PYLON.above()) + " voidheart:medium_azurium_bundle[facing=up]");
        server.runCommand("setblock " + at(MACHINE) + " voidheart:voidstone_engraved_gear");
        waitFor(context, server, 100, "The armillary should form", minecraft -> armillary(minecraft).isFormed());

        server.runCommand("execute as @a at @a run voidheart research grant voidheart:daedalus");
        server.runOnServer(minecraft -> armillary(minecraft).getChart().target(ArmillaryChart.STRATA, ArmillaryTile.rules(), RandomSource.create(11)));
        context.waitTicks(5);
        server.runOnServer(minecraft -> armillary(minecraft).advanceSearch(1));
        waitFor(context, server, 1500, "The way into the Daedalus should gather its charge", minecraft -> armillary(minecraft).getCharge() >= armillary(minecraft).openingCost());

        aimFrom(context, server, 3.5, FEET, 1.5, 0.5, FEET + 0.9, 0.5, 5);
        server.runOnServer(minecraft -> armillary(minecraft).use(player(minecraft), false));
        context.waitTicks(30);
        context.runOnClient(minecraft -> ClientPlayNetworking.send(new ArmillaryPayloads.Action(ArmillaryPayloads.ActionKind.OPEN, -1)));
        context.waitTicks(10);
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);

        int id = onServer(server, minecraft -> armillary(minecraft).getDaedalusConnection());
        expect(id != ArmillaryRiftTile.NONE, "A charged armillary should open the way into the Daedalus");
        return id;
    }

    private static void checkRefusedInTheOverworld(ClientGameTestContext context, TestServerContext server)
    {
        var floor = new BlockPos(-4, FLAT_GROUND, 4);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(DeepSiphonContent.DEEP_SIPHON));
            useOn(player, floor);
        });
        context.waitTicks(3);
        expect(onServer(server, minecraft -> !minecraft.overworld().getBlockState(floor.above()).is(DeepSiphonContent.DEEP_SIPHON)),
                "A siphon should not stand anywhere but on a bridge of the Daedalus");
        var message = overlay(context);
        expect(message.contains("bridge of the Daedalus"), "Refusing should say where it belongs, the action bar says " + message);
    }

    /**
     * Into the Daedalus by the way, then a siphon placed by hand on the deck, short of the rift. It takes up the placer's way.
     */
    private static BlockPos placeOnTheBridge(ClientGameTestContext context, TestServerContext server, int id)
    {
        server.runOnServer(minecraft -> DaedalusConnections.enter(player(minecraft), id));
        context.waitTicks(40);
        expect(onServer(server, minecraft -> player(minecraft).level().dimension() == VoidDaedalus.WORLD_KEY), "Entering by the way should lead into the Daedalus");

        var bridge = onServer(server, minecraft -> bridge(minecraft, id));
        var siphon = new BlockPos(bridge.worldX(SIPHON_SPAN, SiphonBridge.LANE_CENTRE), bridge.walkY(SIPHON_SPAN), bridge.worldZ(SIPHON_SPAN, SiphonBridge.LANE_CENTRE));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(DeepSiphonContent.DEEP_SIPHON));
            useOn(player, siphon.below());
        });
        context.waitTicks(5);
        expect(onServer(server, minecraft -> daedalus(minecraft).getBlockState(siphon).is(DeepSiphonContent.DEEP_SIPHON)),
                "A siphon should stand on the way's bridge at " + siphon.toShortString());
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).link()) == id, "The siphon should take up the way its placer came in by");

        // The well still answers the armillary's offers, so the siphon draws all it can
        waitFor(context, server, 20, "The siphon should draw its full output", minecraft -> siphon(minecraft, siphon).lastDrawn() == DeepSiphonBalance.OUTPUT);
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).status()) == SiphonStatus.DRAWING, "A siphon whose way is held should be DRAWING");
        context.waitTicks(30);

        // Looked at from the deck, then from out over the chasm where the threads rise out of the dark
        context.getInput().pressKey(InputConstants.KEY_F1);
        lookAt(context, server, siphon, 2.6, 0.2, 1.4, 1.1);
        context.takeScreenshot("siphon-drawing-deck");
        var side = sideways(bridge);
        lookAt(context, server, siphon, side[0] * 9, -6, side[1] * 9, -1.5);
        context.takeScreenshot("siphon-drawing-chasm");
        // Far enough out to see the whole run of the threads, from the dark below up to the deck
        lookAt(context, server, siphon, side[0] * 26, -18, side[1] * 26, -17);
        context.takeScreenshot("siphon-drawing-threads-whole");
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            lookAt(context, server, siphon, side[0] * 9 + step * 0.06, -6 + step * 0.04, side[1] * 9 + step * 0.09, -1.5);
            context.takeScreenshot("siphon-threads-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        context.getInput().pressKey(InputConstants.KEY_F1);
        lookAt(context, server, siphon, 1.8, 0, 1.2, 0.8);
        context.waitTicks(20);
        context.takeScreenshot("siphon-jade");
        return siphon;
    }

    /**
     * The well goes, so nothing else feeds the armillary. Back at the base, the siphon pays the hold and the machine by the armillary draws through the pylon.
     */
    private static void checkPowersTheBase(ClientGameTestContext context, TestServerContext server, BlockPos siphon)
    {
        server.runCommand("setblock " + at(WELL) + " dirt");
        machineOn = true;
        server.runOnServer(minecraft -> DaedalusConnections.leave(player(minecraft), armillary(minecraft).getDaedalusConnection()));
        context.waitTicks(40);
        expect(onServer(server, minecraft -> player(minecraft).level().dimension() == Level.OVERWORLD), "Leaving by the way should lead back to the armillary");
        expect(onServer(server, minecraft -> daedalus(minecraft).players().isEmpty()), "Nobody should be left in the Daedalus");
        expect(onServer(server, minecraft -> holdsChunk(minecraft, siphon)), "A siphon whose way holds should keep its chunk loaded");

        context.waitTicks(SETTLE);
        long before = DRAWN.get();
        long noticeBefore = onServer(server, minecraft -> siphon(minecraft, siphon).notice().banked());
        context.waitTicks(100);
        long drawn = DRAWN.get() - before;
        expect(drawn >= MACHINE_DRAW * 95, "The machine by the armillary should be powered by the siphon alone, it drew " + drawn + " in 100 ticks");
        expect(onServer(server, minecraft -> daedalus(minecraft).shouldTickBlocksAt(ChunkPos.containing(siphon).pack())), "The siphon should keep ticking with nobody near");
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).status()) == SiphonStatus.DRAWING, "The siphon should still be DRAWING");
        long last = onServer(server, minecraft -> siphon(minecraft, siphon).lastDrawn());
        expect(last >= 64 && last <= DeepSiphonBalance.OUTPUT, "It should draw the hold and the machine's share, not more than its output: " + last);
        long noticed = onServer(server, minecraft -> siphon(minecraft, siphon).notice().banked()) - noticeBefore;
        expect(noticed >= 100 * 64 && noticed <= 100 * DeepSiphonBalance.OUTPUT, "Everything it draws should add to its notice: " + noticed + " in 100 ticks");
        expect(onServer(server, minecraft -> armillary(minecraft).getHoldDebt()) == 0, "The siphon should pay the hold");
        expect(onServer(server, minecraft -> armillary(minecraft).getPower()) == MachinePower.POWERED, "The armillary should be POWERED by the siphon");

        expectClient(context, "The armillary should show the siphon flowing", minecraft -> clientArmillary(minecraft).isSiphonFlowing());
        expectClient(context, "Arcs should run from the rings to the pylon", minecraft -> SiphonArcRender.arcsAt(ARMILLARY) == 1);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 2.5, FEET + 1.5, -6.5, 3.5, FEET + 2.2, 0.5, 10);
        context.takeScreenshot("siphon-arcs-armillary");
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, 2.5 + step * 0.05, FEET + 1.5, -6.5 + step * 0.08, 3.5, FEET + 2.2, 0.5, 2);
            context.takeScreenshot("siphon-arcs-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        aimFrom(context, server, 9.5, FEET + 2.5, 6.5, 2, FEET + 2, 0.5, 10);
        context.takeScreenshot("siphon-arcs-armillary-other-side");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * Notice banked while nobody is there brings nothing until someone comes, then waves of four a wave interval apart, onto the bridge.
     */
    private static void checkWaves(ClientGameTestContext context, TestServerContext server, int id, BlockPos siphon)
    {
        server.runCommand("execute in " + VoidDaedalus.WORLD_KEY.identifier() + " run voidheart energy siphon notice " + at(siphon) + " 6");
        context.waitTicks(60);
        expect(onServer(server, minecraft -> waveMobs(minecraft, siphon).isEmpty()), "Nothing should climb out while nobody is near");
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).notice().mobs()) == 6, "The notice should wait for someone to come");

        server.runOnServer(minecraft -> DaedalusConnections.enter(player(minecraft), id));
        waitFor(context, server, 60, "A first wave should climb out as a player arrives", minecraft -> !waveMobs(minecraft, siphon).isEmpty());
        context.waitTicks(2);
        var first = onServer(server, minecraft -> waveMobs(minecraft, siphon).size());
        expect(first == DeepSiphonBalance.WAVE_SIZE, "The first wave should bring " + DeepSiphonBalance.WAVE_SIZE + " mobs, it brought " + first);
        checkOnTheBridge(server, siphon);
        // Held where they climbed out, so none wanders off the deck before they are counted
        server.runOnServer(minecraft -> waveMobs(minecraft, siphon).forEach(mob -> mob.setNoAi(true)));

        context.getInput().pressKey(InputConstants.KEY_F1);
        var bridge = onServer(server, minecraft -> bridge(minecraft, id));
        var side = sideways(bridge);
        int middle = DistrictPos.CHASM / 2;
        var centre = new BlockPos(bridge.worldX(middle, SiphonBridge.LANE_CENTRE), bridge.walkY(middle), bridge.worldZ(middle, SiphonBridge.LANE_CENTRE));
        lookAt(context, server, centre, side[0] * 15, 10, side[1] * 15, 0);
        context.takeScreenshot("siphon-first-wave");
        context.getInput().pressKey(InputConstants.KEY_F1);

        context.waitTicks(DeepSiphonBalance.WAVE_INTERVAL - 60);
        expect(onServer(server, minecraft -> waveMobs(minecraft, siphon).size()) == first, "The next wave should wait its interval");
        waitFor(context, server, 100, "A second wave should come a wave interval later", minecraft -> waveMobs(minecraft, siphon).size() > first);
        context.waitTicks(2);
        int total = onServer(server, minecraft -> waveMobs(minecraft, siphon).size());
        expect(total == 6, "The second wave should bring the last 2 noticed, there are " + total);
        checkOnTheBridge(server, siphon);
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).notice().mobs()) == 0, "The notice should be spent");
        server.runOnServer(minecraft -> waveMobs(minecraft, siphon).forEach(Entity::discard));

        server.runOnServer(minecraft -> DaedalusConnections.leave(player(minecraft), id));
        context.waitTicks(40);
    }

    private static void checkOnTheBridge(TestServerContext server, BlockPos siphon)
    {
        server.runOnServer(minecraft ->
        {
            for (var mob : waveMobs(minecraft, siphon))
            {
                expect(mob.getType() != VoidHeartEntities.KERNACIAN_DRAGOON, "No Dragoon should come with a wave");
                expect(!DistrictPos.isInsideDistrict(mob.getBlockX(), mob.getBlockZ()), mob.getType() + " should climb out onto the bridge, not into a district, at " + mob.blockPosition().toShortString());
                expect(mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(siphon)) < 20 * 20, mob.getType() + " should be on the siphon's bridge, it is at " + mob.blockPosition().toShortString());
            }
        });
    }

    /**
     * Severing stops the siphon at once: nothing more reaches the machine, the arcs fade, and the siphon lets its chunk go.
     */
    private static void checkSevering(ClientGameTestContext context, TestServerContext server, BlockPos siphon)
    {
        expect(onServer(server, minecraft -> siphon(minecraft, siphon).status()) == SiphonStatus.DRAWING, "The siphon should still draw before the way is severed");
        server.runOnServer(minecraft -> DaedalusConnections.get(minecraft).sever(armillary(minecraft).getDaedalusConnection(), ArmillaryConnections.now(minecraft)));
        waitFor(context, server, 5, "A severed way should stop the siphon at once",
                minecraft -> siphon(minecraft, siphon).status() == SiphonStatus.SEVERED && siphon(minecraft, siphon).lastDrawn() == 0);
        context.waitTicks(20);
        long before = DRAWN.get();
        context.waitTicks(40);
        expect(DRAWN.get() == before, "Nothing should reach the machine once the way is severed, it drew " + (DRAWN.get() - before));
        expect(!onServer(server, minecraft -> holdsChunk(minecraft, siphon)), "A severed siphon should let its chunk go");
        expectClient(context, "The arcs should fade once nothing flows", minecraft -> SiphonArcRender.arcsAt(ARMILLARY) == 0);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 2.5, FEET + 1.5, -6.5, 3.5, FEET + 2.2, 0.5, 10);
        context.takeScreenshot("siphon-arcs-severed");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    // Helpers

    private static DaedalusBridge bridge(MinecraftServer minecraft, int id)
    {
        return DaedalusConnections.get(minecraft).get(id).bridgeIn(daedalus(minecraft).getSeed());
    }

    /**
     * Across the bridge, toward its side, as a unit step in x and z.
     */
    private static int[] sideways(DaedalusBridge bridge)
    {
        return bridge.isAlongX() ? new int[] { 0, 1 } : new int[] { 1, 0 };
    }

    /**
     * Flies the player to an offset from a block in the Daedalus and aims at a height above its base.
     */
    private static void lookAt(ClientGameTestContext context, TestServerContext server, BlockPos siphon, double dx, double dy, double dz, double aimY)
    {
        double x = siphon.getX() + 0.5;
        double z = siphon.getZ() + 0.5;
        server.runCommand("execute in " + VoidDaedalus.WORLD_KEY.identifier() + " run tp @a "
                + aimedPose(x + dx, siphon.getY() + dy, z + dz, x, siphon.getY() + aimY, z));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(4);
    }

    private static List<Mob> waveMobs(MinecraftServer minecraft, BlockPos siphon)
    {
        return daedalus(minecraft).getEntitiesOfClass(Mob.class, new AABB(siphon).inflate(48), mob -> mob.isAlive() && mob.entityTags().contains(DeepSiphonTile.WAVE_TAG));
    }

    private static boolean holdsChunk(MinecraftServer minecraft, BlockPos siphon)
    {
        var tickets = daedalus(minecraft).getDataStorage().computeIfAbsent(TicketStorage.TYPE).getTickets(ChunkPos.containing(siphon).pack());
        return tickets.stream().anyMatch(ticket -> ticket.getType() == DeepSiphonChunkHold.TICKET);
    }

    private static ServerLevel daedalus(MinecraftServer minecraft)
    {
        return VoidDaedalus.level(minecraft);
    }

    private static DeepSiphonTile siphon(MinecraftServer minecraft, BlockPos pos)
    {
        return (DeepSiphonTile) daedalus(minecraft).getBlockEntity(pos);
    }

    private static ArmillaryTile armillary(MinecraftServer minecraft)
    {
        return (ArmillaryTile) minecraft.overworld().getBlockEntity(ARMILLARY);
    }

    private static ArmillaryTile clientArmillary(Minecraft minecraft)
    {
        return (ArmillaryTile) minecraft.level.getBlockEntity(ARMILLARY);
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static <T> T onServer(TestServerContext server, Function<MinecraftServer, T> function)
    {
        return server.computeOnServer(function::apply);
    }
}
